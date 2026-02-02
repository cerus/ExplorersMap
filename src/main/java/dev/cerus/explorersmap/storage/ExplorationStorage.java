package dev.cerus.explorersmap.storage;

import com.hypixel.hytale.logger.HytaleLogger;
import dev.cerus.explorersmap.ExplorersMapPlugin;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class ExplorationStorage {
    public static final UUID UUID_GLOBAL = new UUID(0, 0);
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final Map<String, WorldData> worldDataMap = new ConcurrentHashMap<>();

    public static ExplorationData getOrLoad(String world, UUID uuid) {
        WorldData worldData = worldDataMap.computeIfAbsent(world, o -> new WorldData(world));
        ExplorationData explorationData = worldData.get(uuid);
        if (explorationData == null) {
            try {
                worldData.load(uuid);
            } catch (IOException e) {
                LOGGER.atSevere().withCause(e).log("Failed to load exploration data for UUID " + uuid);
                return new ExplorationData();
            }
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
        try {
            worldData.load(uuid);
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to load exploration data for UUID " + uuid);
        }
    }

    public static void unload(String world, UUID uuid) {
        WorldData worldData = worldDataMap.get(world);
        if (worldData != null) {
            try {
                worldData.unload(uuid);
            } catch (IOException e) {
                LOGGER.atSevere().withCause(e).log("Failed to unload exploration data for UUID " + uuid);
            }
            if (worldData.isEmpty())  {
                worldDataMap.remove(world);
            }
        }
    }

    public static void save(String world, UUID uuid) {
        WorldData worldData = worldDataMap.get(world);
        if (worldData != null) {
            try {
                worldData.save(uuid);
            } catch (IOException e) {
                LOGGER.atSevere().withCause(e).log("Failed to save exploration data for UUID " + uuid);
            }
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
            try {
                worldData.saveAll();
            } catch (IOException e) {
                LOGGER.atSevere().withCause(e).log("Failed to save all loaded exploration data for world " + worldName);
            }
        }
    }

    private static class WorldData {
        private final Map<UUID, ReentrantLock> loading = new HashMap<>();
        private final Map<UUID, LoadedExplorationData> playerData = new HashMap<>();
        private final String worldName;

        private WorldData(String worldName) {
            this.worldName = worldName;
        }

        public ExplorationData get(UUID uuid) {
            LoadedExplorationData loadedExplorationData = playerData.get(uuid);
            return loadedExplorationData != null ? loadedExplorationData.data() : null;
        }

        public void load(UUID uuid) throws IOException {
            ReentrantLock lock = loading.computeIfAbsent(uuid, o -> new ReentrantLock());
            lock.lock();

            try {
                if (playerData.containsKey(uuid)) {
                    return;
                }

                Path dir = ExplorersMapPlugin.getInstance().getDataDirectory().resolve("discovered").resolve(worldName);
                Path path = dir.resolve(uuid.toString() + ".bin");
                Path legacyPath = dir.resolve(uuid.toString() + ".json");
                ExplorationDataFile file = new ExplorationDataFile(path);
                ExplorationData explorationData = file.readAndConvertIfRequired(legacyPath);

                playerData.put(uuid, new LoadedExplorationData(file, explorationData));
            } finally {
                lock.unlock();
            }
        }

        public void unload(UUID uuid) throws IOException {
            LoadedExplorationData loadedExplorationData = playerData.remove(uuid);
            if (loadedExplorationData != null) {
                loadedExplorationData.file().write(loadedExplorationData.data());
            }
        }

        public void save(UUID uuid) throws IOException {
            LoadedExplorationData loadedExplorationData = playerData.get(uuid);
            if (loadedExplorationData != null) {
                loadedExplorationData.file().write(loadedExplorationData.data());
            }
        }

        public void saveAll() throws IOException {
            for (UUID uuid : playerData.keySet()) {
                save(uuid);
            }
        }

        public boolean isEmpty() {
            return playerData.isEmpty();
        }
    }
}
