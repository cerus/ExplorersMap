package dev.cerus.explorersmap.storage;

import com.hypixel.hytale.server.core.util.Config;
import dev.cerus.explorersmap.ExplorersMapPlugin;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ExplorationStorage {
    public static final UUID UUID_GLOBAL = new UUID(0, 0);

    private static final Map<String, WorldData> worldDataMap = new HashMap<>();

    public static ExplorationData getOrLoad(String world, UUID uuid) {
        WorldData worldData = worldDataMap.computeIfAbsent(world, o -> new WorldData(world));
        ExplorationData explorationData = worldData.get(uuid);
        if (explorationData == null) {
            worldData.load(uuid);
            explorationData = worldData.get(uuid);
        }
        return explorationData;
    }

    public static ExplorationData get(String world, UUID uuid) {
        WorldData worldData = worldDataMap.get(world);
        return worldData != null ? worldData.get(uuid) : null;
    }

    public static void load(String world, UUID uuid) {
        WorldData worldData = worldDataMap.computeIfAbsent(world, o -> new WorldData(world));
        worldData.load(uuid);
    }

    public static void unload(String world, UUID uuid) {
        WorldData worldData = worldDataMap.get(world);
        if (worldData != null) {
            worldData.unload(uuid);
            if (worldData.isEmpty())  {
                worldDataMap.remove(world);
            }
        }
    }

    public static void save(String world, UUID uuid) {
        WorldData worldData = worldDataMap.get(world);
        if (worldData != null) {
            worldData.save(uuid);
        }
    }

    public static void unloadFromAll(UUID uuid) {
        for (String s : worldDataMap.keySet()) {
            unload(s, uuid);
        }
    }

    public static void saveAll(UUID uuid) {
        for (String s : worldDataMap.keySet()) {
            save(s, uuid);
        }
    }

    public static void saveAll(String worldName) {
        WorldData worldData = worldDataMap.get(worldName);
        if (worldData != null) {
            worldData.saveAll();
        }
    }

    private static class WorldData {
        private final Map<UUID, Config<ExplorationData>> playerData = new HashMap<>();
        private final String worldName;

        private WorldData(String worldName) {
            this.worldName = worldName;
        }

        public ExplorationData get(UUID uuid) {
            Config<ExplorationData> config = playerData.get(uuid);
            return config != null ? config.get() : null;
        }

        public void load(UUID uuid) {
            if (playerData.containsKey(uuid)) {
                return;
            }

            Path dir = ExplorersMapPlugin.getInstance().getDataDirectory().resolve("discovered").resolve(worldName);
            Config<ExplorationData> config = new Config<>(dir, uuid.toString(), ExplorationData.CODEC);
            config.load();
            playerData.put(uuid, config);
        }

        public void unload(UUID uuid) {
            Config<ExplorationData> config = playerData.remove(uuid);
            if (config != null) {
                config.save();
            }
        }

        public void save(UUID uuid) {
            Config<ExplorationData> config = playerData.get(uuid);
            if (config != null) {
                config.save();
            }
        }

        public void saveAll() {
            for (UUID uuid : playerData.keySet()) {
                save(uuid);
            }
        }

        public boolean isEmpty() {
            return playerData.isEmpty();
        }
    }
}
