package dev.cerus.explorersmap.map;

import com.hypixel.hytale.common.fastutil.HLongOpenHashSet;
import com.hypixel.hytale.common.fastutil.HLongSet;
import com.hypixel.hytale.common.plugin.AuthorInfo;
import com.hypixel.hytale.common.plugin.PluginManifest;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.iterator.CircleSpiralIterator;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.worldmap.ClearWorldMap;
import com.hypixel.hytale.protocol.packets.worldmap.MapChunk;
import com.hypixel.hytale.protocol.packets.worldmap.MapImage;
import com.hypixel.hytale.protocol.packets.worldmap.UpdateWorldMap;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.PluginClassLoader;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapSettings;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MapMarkerTracker;
import dev.cerus.explorersmap.ExplorersMapPlugin;
import dev.cerus.explorersmap.config.ExplorersMapConfig;
import dev.cerus.explorersmap.storage.ExplorationData;
import dev.cerus.explorersmap.storage.ExplorationStorage;
import dev.cerus.explorersmap.storage.ExploredRegion;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

/**
 * Custom map tracker implementation - Fixed for Thread Safety
 */
public class CustomWorldMapTracker extends WorldMapTracker {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Pattern INSTANCE_SUFFIX_PATTERN = Pattern.compile("-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final int UNLOAD_RATE = 100;

    private static Field TRANSFORM_COMPONENT_FIELD;
    private static Field MARKER_TRACKER_FIELD;
    private static Field PLUGIN_FIELD;

    static {
        try {
            MARKER_TRACKER_FIELD = WorldMapTracker.class.getDeclaredField("markerTracker");
            MARKER_TRACKER_FIELD.setAccessible(true);

            TRANSFORM_COMPONENT_FIELD = WorldMapTracker.class.getDeclaredField("transformComponent");
            TRANSFORM_COMPONENT_FIELD.setAccessible(true);

            PLUGIN_FIELD = PluginClassLoader.class.getDeclaredField("plugin");
            PLUGIN_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            LOGGER.atSevere().log("Failed to find required fields", e);
        }
    }

    private ExplorersMapConfig config;

    private final ReentrantReadWriteLock loadedLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock tickLock = new ReentrantReadWriteLock();
    private final CircleSpiralIterator spiralIterator = new CircleSpiralIterator();
    private final HLongSet loaded = new HLongOpenHashSet();
    private final HLongSet pendingReloadChunks = new HLongOpenHashSet();
    private MapMarkerTracker mapMarkerTracker;

    // FIXED: Atomic reference to store position data pushed from the World Thread
    private final AtomicReference<TransformComponent> transformRef = new AtomicReference<>(null);

    private boolean started;
    private boolean transformInitialized;
    private List<ExploredRegion> loadFromDisk;
    private ExplorationData explorationData;
    private Resolution currentResolution;

    public CustomWorldMapTracker(Player player) {
        super(player);
        this.config = ExplorersMapPlugin.getInstance().getConfig().get();
        this.currentResolution = config.getResolution();

        try {
            this.mapMarkerTracker = (MapMarkerTracker) MARKER_TRACKER_FIELD.get(this);
        } catch (IllegalAccessException e) {
            LOGGER.atSevere().log("Failed to access mapMarkerTracker", e);
        }
    }

    public void tick(float dt) {
        tickLock.writeLock().lock();
        try {
            tick0(dt);
        } finally {
            tickLock.writeLock().unlock();
        }
    }

    private void tick0(float dt) {
        if (!this.started) {
            this.started = true;
            LOGGER.at(Level.INFO).log("Started Generating Map!");
        }

        World world = getPlayer().getWorld();
        if (world == null) {
            return;
        }

        // FIXED: Instead of calling getTransformComponent() which triggers the Async warning,
        // we use the position pushed into our safe AtomicReference.
        TransformComponent transformComponent = this.transformRef.get();
        if (transformComponent == null) {
            // No position yet
            return;
        }

        if (!transformInitialized) {
            try {
                TRANSFORM_COMPONENT_FIELD.set(this, transformComponent);
            } catch (IllegalAccessException e) {
                LOGGER.atSevere().log("Failed to set transformComponent", e);
            }
            transformInitialized = true;
        }

        Vector3d position = transformComponent.getPosition();
        int playerX = MathUtil.floor(position.getX());
        int playerZ = MathUtil.floor(position.getZ());
        int playerChunkX = playerX >> 5;
        int playerChunkZ = playerZ >> 5;

        WorldMapManager worldMapManager = world.getWorldMapManager();
        WorldMapSettings worldMapSettings = worldMapManager.getWorldMapSettings();
        int viewRadius;

        if (this.getViewRadiusOverride() != null) {
            viewRadius = this.getViewRadiusOverride();
        } else if (ExplorersMapPlugin.getInstance().getConfig().get().isUnlimitedMarkerTracking()) {
            viewRadius = -1;
        } else {
            viewRadius = worldMapSettings.getViewRadius(getPlayer().getViewRadius());
        }

        // Load already explored tiles to send to the player
        if (loadFromDisk == null) {
            explorationData = ExplorationStorage.getOrLoad(sanitizeWorldName(world), getPlayer().getUuid());
            if (explorationData != null) {
                ExplorationData dataToUse = config.isPerPlayerMap()
                        ? explorationData
                        : ExplorationStorage.getOrLoad(sanitizeWorldName(world), ExplorationStorage.UUID_GLOBAL);
                loadFromDisk = dataToUse.copyRegionsForSending(playerChunkX, playerChunkZ);
            }
        }

        if (world.isCompassUpdating() && mapMarkerTracker != null) {
            updatePointsOfInterest(worldMapManager, dt, world, viewRadius, playerChunkX, playerChunkZ);
        }

        if (worldMapManager.isWorldMapEnabled()) {
            tickWorldMap(world, worldMapSettings, playerChunkX, playerChunkZ, config.getGenerationRate());
        }
    }

    private void updatePointsOfInterest(WorldMapManager worldMapManager, float dt, World world, int viewRadius, int playerChunkX, int playerChunkZ) {
        try {
            mapMarkerTracker.updatePointsOfInterest(dt, world, viewRadius, playerChunkX, playerChunkZ);
        } catch (Throwable t) {
            LOGGER.atWarning().log("Failed to update markers! Are you using any marker mods?");
            t.printStackTrace(System.err);

            // Try to find out which one is causing issues
            Set<Map.Entry<String, WorldMapManager.MarkerProvider>> providers = Set.copyOf(worldMapManager.getMarkerProviders().entrySet());
            for (Map.Entry<String, WorldMapManager.MarkerProvider> entry : providers) {
                WorldMapManager.MarkerProvider provider = entry.getValue();
                try {
                    provider.update(world, mapMarkerTracker, viewRadius, playerChunkX, playerChunkZ);
                } catch (Throwable tt) {
                    LOGGER.atWarning().log("Identified marker provider '%s' (%s) as erroneous! Removing this provider for now.",
                            entry.getKey(), provider.getClass().getName());
                    if (provider.getClass().getClassLoader() instanceof PluginClassLoader pluginClassLoader) {
                        try {
                            JavaPlugin cause = (JavaPlugin) PLUGIN_FIELD.get(pluginClassLoader);
                            PluginManifest manifest = cause.getManifest();
                            LOGGER.atWarning().log("Identified mod '%s' by %s as the cause. Consider removing this mod until this is fixed.",
                                    manifest.getName(), manifest.getAuthors().stream().map(AuthorInfo::getName).collect(Collectors.joining(", ")));
                        } catch (IllegalAccessException ignored) {
                        }
                    }

                    worldMapManager.getMarkerProviders().remove(entry.getKey());
                }
            }
        }
    }

    private void tickWorldMap(World world, WorldMapSettings worldMapSettings, int playerChunkX, int playerChunkZ, int maxGeneration) {
        List<MapChunk> toSend = new ArrayList<>();

        maxGeneration = loadArea(world, worldMapSettings, playerChunkX, playerChunkZ, maxGeneration, toSend);

        if (!toSend.isEmpty()) {
            if (shouldPersist(world)) {
                // Mark loaded area as explored
                toSend.forEach(chunk -> {
                    if (explorationData != null) {
                        explorationData.markExplored(chunk);
                    }
                    ExplorationStorage.getOrLoad(sanitizeWorldName(world), ExplorationStorage.UUID_GLOBAL).markExplored(chunk);
                });
            }

            if (!config.isPerPlayerMap()) {
                // Broadcast to other players
                List<MapChunk> chunksCopy = List.copyOf(toSend);
                world.execute(() -> {
                    world.getPlayers().forEach(player -> {
                        if (!player.getUuid().equals(getPlayer().getUuid())
                            && player.getWorldMapTracker() instanceof CustomWorldMapTracker customWorldMapTracker) {
                            customWorldMapTracker.writeUpdatePacket(chunksCopy);
                        }
                    });
                });
            }
        }

        // Reload pending chunks (from building tools or mods)
        maxGeneration = reloadPending(world, worldMapSettings, maxGeneration, toSend);

        // Send pending already explored tiles
        loadStored(world, worldMapSettings, config.getDiskLoadRate(), toSend);

        if (!toSend.isEmpty()) {
            writeUpdatePacket(toSend);
        }
    }

    private int loadArea(World world, WorldMapSettings worldMapSettings, int playerChunkX, int playerChunkZ, int maxGeneration, List<MapChunk> out) {
        loadedLock.writeLock().lock();
        try {
            this.spiralIterator.init(playerChunkX, playerChunkZ, 0, config.getExplorationRadius());
            while (maxGeneration > 0 && this.spiralIterator.hasNext()) {
                long chunkCoordinates = this.spiralIterator.next();
                if (!this.loaded.contains(chunkCoordinates)) {
                    CompletableFuture<MapImage> future = world.getWorldMapManager().getImageAsync(chunkCoordinates);
                    if (!future.isDone()) {
                        --maxGeneration;
                    } else if (loaded.add(chunkCoordinates)) {
                        int mapChunkX = ChunkUtil.xOfChunkIndex(chunkCoordinates);
                        int mapChunkZ = ChunkUtil.zOfChunkIndex(chunkCoordinates);

                        MapImage mapImage = future.getNow(null);

                        if (shouldPersist(world)) {
                            ExplorersMapPlugin.getInstance().getWorldMapDiskCache().saveImageToDiskAsync(world, mapChunkX, mapChunkZ, worldMapSettings.getImageScale(), currentResolution, mapImage).whenComplete((unused, throwable) -> {
                                if (throwable != null) {
                                    LOGGER.atSevere().log("Failed to save map tile", throwable);
                                }
                            });
                        }

                        mapImage = currentResolution.rescale(mapImage);
                        out.add(new MapChunk(mapChunkX, mapChunkZ, mapImage));
                    }
                }
            }
            return maxGeneration;
        } finally {
            loadedLock.writeLock().unlock();
        }
    }

    private int loadStored(World world, WorldMapSettings worldMapSettings, int maxGeneration, List<MapChunk> out) {
        if (loadFromDisk == null) {
            return maxGeneration;
        }

        loadedLock.writeLock().lock();
        try {
            Iterator<ExploredRegion> regionIterator = loadFromDisk.iterator();
            while (maxGeneration > 0 && regionIterator.hasNext()) {
                ExploredRegion region = regionIterator.next();
                Iterator<Long> iterator = region.getChunks().iterator();
                while (maxGeneration > 0 && iterator.hasNext()) {
                    long chunkCoordinates = iterator.next();
                    int mapChunkX = ChunkUtil.xOfChunkIndex(chunkCoordinates);
                    int mapChunkZ = ChunkUtil.zOfChunkIndex(chunkCoordinates);

                    if (!this.loaded.contains(chunkCoordinates)) {
                        CompletableFuture<MapImage> future = ExplorersMapPlugin.getInstance().getWorldMapDiskCache().readStoredImageAsync(world, mapChunkX, mapChunkZ, worldMapSettings.getImageScale(), currentResolution);
                        if (!future.isDone()) {
                            --maxGeneration;
                        } else if (loaded.add(chunkCoordinates)) {
                            iterator.remove();
                            MapImage mapImage = future.getNow(null);
                            int imageSize = MathUtil.fastFloor(32.0F * currentResolution.getScale());
                            if (mapImage == null || mapImage.width != imageSize || mapImage.height != imageSize) {
                                loaded.remove(chunkCoordinates);
                                continue;
                            }
                            out.add(new MapChunk(mapChunkX, mapChunkZ, mapImage));
                        }
                    } else {
                        iterator.remove();
                    }
                }

                if (region.isDone()) {
                    regionIterator.remove();
                }
            }
            return maxGeneration;
        } finally {
            loadedLock.writeLock().unlock();
        }
    }

    private int reloadPending(World world, WorldMapSettings worldMapSettings, int maxGeneration, List<MapChunk> out) {
        loadedLock.writeLock().lock();
        try {
            LongIterator iterator = pendingReloadChunks.iterator();
            while (maxGeneration > 0 && iterator.hasNext()) {
                long chunkCoordinates = iterator.nextLong();

                if (!this.loaded.contains(chunkCoordinates)) {
                    CompletableFuture<MapImage> future = world.getWorldMapManager().getImageAsync(chunkCoordinates);
                    if (!future.isDone()) {
                        --maxGeneration;
                    } else if (loaded.add(chunkCoordinates)) {
                        iterator.remove();
                        int mapChunkX = ChunkUtil.xOfChunkIndex(chunkCoordinates);
                        int mapChunkZ = ChunkUtil.zOfChunkIndex(chunkCoordinates);

                        MapImage mapImage = future.getNow(null);

                        if (shouldPersist(world)) {
                            ExplorersMapPlugin.getInstance().getWorldMapDiskCache().saveImageToDiskAsync(world, mapChunkX, mapChunkZ, worldMapSettings.getImageScale(), currentResolution, mapImage).whenComplete((unused, throwable) -> {
                                if (throwable != null) {
                                    LOGGER.atSevere().log("Failed to save map tile", throwable);
                                }
                            });
                        }

                        mapImage = currentResolution.rescale(mapImage);
                        out.add(new MapChunk(mapChunkX, mapChunkZ, mapImage));
                    }
                } else {
                    iterator.remove();
                }
            }
            return maxGeneration;
        } finally {
            loadedLock.writeLock().unlock();
        }
    }

    @Override
    public void clearChunks(@Nonnull LongSet chunkIndices) {
        this.loadedLock.writeLock().lock();
        try {
            chunkIndices.forEach((index) -> {
                if (!loaded.contains(index) && (loadFromDisk == null || loadFromDisk.stream().noneMatch(reg -> reg.getChunks().contains(index)))) {
                    return;
                }
                this.loaded.remove(index);
                this.pendingReloadChunks.add(index);
            });
        } finally {
            this.loadedLock.writeLock().unlock();
        }
    }

    private void writeUpdatePacket(List<MapChunk> list) {
        UpdateWorldMap packet = new UpdateWorldMap(list.toArray(MapChunk[]::new), null, null);
        getPlayer().getPlayerConnection().write((Packet) packet);
    }

    @Override
    public void clear() {
        reset(true);
    }

    public void reset() {
        reset(true);
    }

    public void reset(boolean unload) {
        loadedLock.writeLock().lock();
        tickLock.writeLock().lock();

        try {
            if (unload) {
                getPlayer().getPlayerConnection().write(new ClearWorldMap());
            }

            explorationData = null;
            loaded.clear();
            loadFromDisk = null;
            config = ExplorersMapPlugin.getInstance().getConfig().get();
            currentResolution = config.getResolution();
        } finally {
            loadedLock.writeLock().unlock();
            tickLock.writeLock().unlock();
        }
    }

    /**
     * Called by MapSyncSystem on the World Thread to safely bridge the data.
     */
    public void pushTransform(TransformComponent component) {
        this.transformRef.set(component);
    }

    public boolean hasTransform() {
        return transformRef.get() != null;
    }

    private boolean shouldPersist(World world) {
        return !world.getName().startsWith("instance-") || config.isSaveInstanceTiles();
    }

    public boolean isLoaded(int chunkX, int chunkZ) {
        return loaded.contains(ChunkUtil.indexChunk(chunkX, chunkZ));
    }

    public static String sanitizeWorldName(World world) {
        String name = world.getName();
        if (name.startsWith("instance-")) {
            return INSTANCE_SUFFIX_PATTERN.matcher(name).replaceFirst("");
        }
        return name;
    }
}
