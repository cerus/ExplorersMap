package dev.cerus.explorersmap.map;

import com.hypixel.hytale.common.fastutil.HLongOpenHashSet;
import com.hypixel.hytale.common.fastutil.HLongSet;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.iterator.CircleSpiralIterator;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.worldmap.MapChunk;
import com.hypixel.hytale.protocol.packets.worldmap.MapImage;
import com.hypixel.hytale.protocol.packets.worldmap.UpdateWorldMap;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapSettings;
import dev.cerus.explorersmap.ExplorersMapPlugin;
import dev.cerus.explorersmap.config.ExplorersMapConfig;
import dev.cerus.explorersmap.storage.ExplorationData;
import dev.cerus.explorersmap.storage.ExplorationStorage;
import dev.cerus.explorersmap.storage.ExploredRegion;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import javax.annotation.Nonnull;

/**
 * Custom map tracker implementation
 */
public class CustomWorldMapTracker extends WorldMapTracker {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static Method POI_UPDATE_METHOD;
    private static Field TRANSFORM_COMPONENT_FIELD;

    static {
        try {
            POI_UPDATE_METHOD = WorldMapTracker.class.getDeclaredMethod("updatePointsOfInterest", World.class, int.class, int.class, int.class);
            POI_UPDATE_METHOD.setAccessible(true);

            TRANSFORM_COMPONENT_FIELD = WorldMapTracker.class.getDeclaredField("transformComponent");
            TRANSFORM_COMPONENT_FIELD.setAccessible(true);
        } catch (NoSuchMethodException e) {
            LOGGER.atSevere().log("Failed to find updatePointsOfInterest()", e);
        } catch (NoSuchFieldException e) {
            LOGGER.atSevere().log("Failed to find transformComponent field", e);
        }
    }

    private final ExplorersMapConfig config;

    private final ReentrantReadWriteLock loadedLock = new ReentrantReadWriteLock();
    private final CircleSpiralIterator spiralIterator = new CircleSpiralIterator();
    private final HLongSet loaded = new HLongOpenHashSet();
    private final HLongSet pendingReloadChunks = new HLongOpenHashSet();

    private boolean started;
    private TransformComponent transformComponent;
    private List<ExploredRegion> loadFromDisk;
    private ExplorationData explorationData;

    public CustomWorldMapTracker(Player player) {
        super(player);
        this.config = ExplorersMapPlugin.getInstance().getConfig().get();
    }

    public void tick(float dt) {
        if (!this.started) {
            this.started = true;
            LOGGER.at(Level.INFO).log("Started Generating Map!");
        }

        World world = getPlayer().getWorld();
        if (world == null) {
            return;
        }

        if (this.transformComponent == null) {
            this.transformComponent = getPlayer().getTransformComponent();
            if (this.transformComponent == null) {
                return;
            }

            try {
                TRANSFORM_COMPONENT_FIELD.set(this, transformComponent);
            } catch (IllegalAccessException e) {
                LOGGER.atSevere().log("Failed to set transformComponent", e);
            }
        }

        WorldMapManager worldMapManager = world.getWorldMapManager();
        WorldMapSettings worldMapSettings = worldMapManager.getWorldMapSettings();
        int viewRadius;
        if (this.getViewRadiusOverride() != null) {
            viewRadius = this.getViewRadiusOverride();
        } else {
            viewRadius = worldMapSettings.getViewRadius(getPlayer().getViewRadius());
        }

        Vector3d position = this.transformComponent.getPosition();
        int playerX = MathUtil.floor(position.getX());
        int playerZ = MathUtil.floor(position.getZ());
        int playerChunkX = playerX >> 5;
        int playerChunkZ = playerZ >> 5;

        // Load already explored tiles to send to the player
        if (loadFromDisk == null) {
            explorationData = ExplorationStorage.get(world.getName(), getPlayer().getUuid());
            if (explorationData != null) {
                ExplorationData dataToUse = ExplorersMapPlugin.getInstance().getConfig().get().isPerPlayerMap()
                        ? explorationData   // use the players own storage
                        : ExplorationStorage.get(world.getName(), ExplorationStorage.UUID_GLOBAL); // use the global storage
                loadFromDisk = dataToUse.copyRegionsForSending(playerChunkX, playerChunkZ);
            }
        }

        if (world.isCompassUpdating()) {
            //this.updatePointsOfInterest(world, viewRadius, playerChunkX, playerChunkZ);
            try {
                POI_UPDATE_METHOD.invoke(this, world, viewRadius, playerChunkX, playerChunkZ);
            } catch (IllegalAccessException | InvocationTargetException e) {
                LOGGER.atSevere().log("Failed to invoke updatePointsOfInterest()", e);
            }
        }

        if (worldMapManager.isWorldMapEnabled()) {
            //this.updateWorldMap(world, dt, worldMapSettings, viewRadius, playerChunkX, playerChunkZ);
            tickWorldMap(world, worldMapSettings, playerChunkX, playerChunkZ, config.getGenerationRate());
        }
    }

    private void tickWorldMap(World world, WorldMapSettings worldMapSettings, int playerChunkX, int playerChunkZ, int maxGeneration) {
        List<MapChunk> toSend = new ArrayList<>();

        // Load area around player
        maxGeneration = loadArea(world, worldMapSettings, playerChunkX, playerChunkZ, maxGeneration, toSend);

        if (!toSend.isEmpty()) {
            // Mark loaded area as explored
            toSend.forEach(chunk -> {
                explorationData.markExplored(chunk);
                ExplorationStorage.get(world.getName(), ExplorationStorage.UUID_GLOBAL).markExplored(chunk);
            });

            // Broadcast to other players
            if (!ExplorersMapPlugin.getInstance().getConfig().get().isPerPlayerMap()) {
                world.execute(() -> {
                    world.getPlayers().forEach(player -> {
                        if (!player.getUuid().equals(getPlayer().getUuid())
                            && player.getWorldMapTracker() instanceof CustomWorldMapTracker customWorldMapTracker) {
                            customWorldMapTracker.writeUpdatePacket(toSend);
                        }
                    });
                });
            }
        }

        // Reload pending chunks (from building tools or mods)
        maxGeneration = reloadPending(world, worldMapSettings, maxGeneration, toSend);

        // Send pending already explored tiles
        loadStored(world, worldMapSettings, config.getDiskLoadRate(), toSend);

        if (toSend.isEmpty()) {
            return;
        }

        writeUpdatePacket(toSend);
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
                        out.add(new MapChunk(mapChunkX, mapChunkZ, mapImage));
                        ExplorersMapPlugin.getInstance().getWorldMapDiskCache().saveImageToDiskAsync(world, mapChunkX, mapChunkZ, worldMapSettings.getImageScale(), mapImage);
                    }
                }
            }
            return maxGeneration;
        } finally {
            loadedLock.writeLock().unlock();
        }
    }

    private int loadStored(World world, WorldMapSettings worldMapSettings, int maxGeneration, List<MapChunk> out) {
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
                        CompletableFuture<MapImage> future = ExplorersMapPlugin.getInstance().getWorldMapDiskCache().readStoredImageAsync(world, mapChunkX, mapChunkZ, worldMapSettings.getImageScale());
                        if (!future.isDone()) {
                            --maxGeneration;
                        } else if (loaded.add(chunkCoordinates)) {
                            iterator.remove();
                            MapImage mapImage = future.getNow(null);
                            if (mapImage == null) {
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
                        out.add(new MapChunk(mapChunkX, mapChunkZ, mapImage));
                        ExplorersMapPlugin.getInstance().getWorldMapDiskCache().saveImageToDiskAsync(world, mapChunkX, mapChunkZ, worldMapSettings.getImageScale(), mapImage);
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
        LOGGER.atInfo().log("Reloading " + chunkIndices.size() + " chunks for " + getPlayer().getDisplayName());

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
        LOGGER.at(Level.FINE).log("Sending world map update to %s - %d chunks", getPlayer().getUuid(), list.size());
        getPlayer().getPlayerConnection().write((Packet) packet);
    }

    public boolean isLoaded(int chunkX, int chunkZ) {
        return loaded.contains(ChunkUtil.indexChunk(chunkX, chunkZ));
    }
}
