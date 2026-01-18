package dev.cerus.explorersmap;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.worldmap.UpdateWorldMapSettings;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ShutdownEvent;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.universe.world.events.AddWorldEvent;
import com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapSettings;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.PlayerIconMarkerProvider;
import com.hypixel.hytale.server.core.util.Config;
import com.hypixel.hytale.unsafe.UnsafeUtil;
import dev.cerus.explorersmap.command.ExplorersMapCommand;
import dev.cerus.explorersmap.config.ExplorersMapConfig;
import dev.cerus.explorersmap.map.CustomPlayerIconMarkerProvider;
import dev.cerus.explorersmap.map.CustomWorldMapTracker;
import dev.cerus.explorersmap.map.WorldMapDiskCache;
import dev.cerus.explorersmap.storage.ExplorationStorage;
import java.lang.reflect.Field;
import java.util.UUID;
import javax.annotation.Nonnull;

public class ExplorersMapPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static ExplorersMapPlugin instance;

    private final Config<ExplorersMapConfig> config;
    private WorldMapDiskCache worldMapDiskCache;

    public static ExplorersMapPlugin getInstance() {
        return instance;
    }

    public ExplorersMapPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        config = withConfig("ExplorersMap", ExplorersMapConfig.CODEC);
    }

    @Override
    protected void setup() {
        instance = this;
        config.save();

        worldMapDiskCache = new WorldMapDiskCache(getDataDirectory().resolve("tiles"));

        getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class, this::onPlayerAddToWorld);
        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, this::onPlayerDisconnect);
        getEventRegistry().registerGlobal(AddWorldEvent.class, this::onWorldAdd);
        getEventRegistry().registerGlobal(RemoveWorldEvent.class, this::onWorldRemove);
        getEventRegistry().registerGlobal(ShutdownEvent.class, this::onShutdown);

        getCommandRegistry().registerCommand(new ExplorersMapCommand("explorersmap", "Open Explorers Map settings"));
    }

    private void onShutdown(ShutdownEvent event) {
        ExplorationStorage.unloadFromAll(ExplorationStorage.UUID_GLOBAL);
    }

    private void onPlayerAddToWorld(AddPlayerToWorldEvent event) {
        Holder<EntityStore> holder = event.getHolder();
        Player player = holder.getComponent(Player.getComponentType());
        if (player == null) {
            // sanity check
            return;
        }

        World world = event.getWorld();

        // Allow more zoom
        WorldMapSettings worldMapSettings = world.getWorldMapManager().getWorldMapSettings();
        UpdateWorldMapSettings settingsPacket = worldMapSettings.getSettingsPacket();
        float minZoom = config.get().getMinZoom();
        if (settingsPacket.minScale > minZoom && minZoom < settingsPacket.maxScale) {
            settingsPacket.minScale = Math.max(2, minZoom);
            player.getWorldMapTracker().sendSettings(world);
        }

        // Load exploration data
        ExplorationStorage.load(CustomWorldMapTracker.sanitizeWorldName(world), player.getUuid());

        if (player.getWorldMapTracker().getClass() != WorldMapTracker.class) {
            if (player.getWorldMapTracker() instanceof CustomWorldMapTracker custom) {
                custom.reset();
                return;
            }
            // Another mod has injected their stuff, abort
            LOGGER.atWarning().log("Failed to inject custom tracker due to mod incompatibility!");
            return;
        }

        // Inject custom world map tracker
        try {
            Field field = player.getClass().getDeclaredField("worldMapTracker");
            long off = UnsafeUtil.UNSAFE.objectFieldOffset(field);
            UnsafeUtil.UNSAFE.putObject(player, off, new CustomWorldMapTracker(player));
        } catch (NoSuchFieldException e) {
            LOGGER.atSevere().log("Failed to inject custom tracker", e);
        }
    }

    private void onPlayerDisconnect(PlayerDisconnectEvent event) {
        UUID uuid = event.getPlayerRef().getUuid();
        ExplorationStorage.unloadFromAll(uuid);
    }

    private void onWorldAdd(AddWorldEvent event) {
        ExplorationStorage.load(CustomWorldMapTracker.sanitizeWorldName(event.getWorld()), ExplorationStorage.UUID_GLOBAL);

        WorldMapManager worldMapManager = event.getWorld().getWorldMapManager();
        WorldMapManager.MarkerProvider original = worldMapManager.getMarkerProviders().getOrDefault("playerIcons", PlayerIconMarkerProvider.INSTANCE);
        worldMapManager.addMarkerProvider("playerIcons", new CustomPlayerIconMarkerProvider(original));
    }

    private void onWorldRemove(RemoveWorldEvent event) {
        ExplorationStorage.unload(CustomWorldMapTracker.sanitizeWorldName(event.getWorld()), ExplorationStorage.UUID_GLOBAL);
    }

    public Config<ExplorersMapConfig> getConfig() {
        return config;
    }

    public WorldMapDiskCache getWorldMapDiskCache() {
        return worldMapDiskCache;
    }
}
