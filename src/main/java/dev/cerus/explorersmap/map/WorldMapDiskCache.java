package dev.cerus.explorersmap.map;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.packets.worldmap.MapImage;
import com.hypixel.hytale.server.core.universe.world.World;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;

/**
 * A on-disk cache for world map tiles
 */
public class WorldMapDiskCache {

    private final Map<String, Long2ObjectMap<CompletableFuture<MapImage>>> loadingImages = new HashMap<>();

    private final Path folder;

    public WorldMapDiskCache(Path folder) {
        this.folder = folder;
    }

    public CompletableFuture<Void> saveImageToDiskAsync(World world, int chunkX, int chunkZ, float scale, @Nullable Resolution resolution, MapImage mapImage) {
        return CompletableFuture.runAsync(() -> {
            try {
                saveImageToDisk(CustomWorldMapTracker.sanitizeWorldName(world), chunkX, chunkZ, scale, mapImage);
                if (resolution != null && resolution.getScale() != scale) {
                    saveImageToDisk(CustomWorldMapTracker.sanitizeWorldName(world), chunkX, chunkZ, resolution.getScale(), resolution.rescale(mapImage));
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, world);
    }

    public void saveImageToDisk(String world, int chunkX, int chunkZ, float scale, MapImage mapImage) throws IOException {
        if (mapImage.data == null) {
            return;
        }

        Path path = getImagePath(world, chunkX, chunkZ, scale);
        Files.createDirectories(path.getParent());

        BufferedImage image = new BufferedImage(mapImage.width, mapImage.height, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < mapImage.width; x++) {
            for (int z = 0; z < mapImage.height; z++) {
                int color = mapImage.data[x * mapImage.width + z];
                int r = color >> 24 & 0xFF;
                int g = color >> 16 & 0xFF;
                int b = color >> 8 & 0xFF;
                int a = color >> 0 & 0xFF;

                image.setRGB(x, z, ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | ((b & 0xFF) << 0));
            }
        }

        try (OutputStream os = Files.newOutputStream(path, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
            ImageIO.write(image, "png", os);
        }
    }

    public CompletableFuture<MapImage> readStoredImageAsync(World world, int chunkX, int chunkZ, float scale, Resolution resolution) {
        String worldName = CustomWorldMapTracker.sanitizeWorldName(world);

        Long2ObjectMap<CompletableFuture<MapImage>> cache = loadingImages.computeIfAbsent(worldName, o -> new Long2ObjectOpenHashMap<>());
        long index = ChunkUtil.indexChunk(chunkX, chunkZ);
        CompletableFuture<MapImage> future = cache.get(index);
        if (future != null) {
            if (future.isDone()) {
                cache.remove(index);
            }
            return future;
        }

        if (Files.exists(getImagePath(worldName, chunkX, chunkZ, resolution.getScale()))) {
            future = readStoredImageAsync(world, chunkX, chunkZ, resolution.getScale());
        } else if (Files.exists(getImagePath(worldName, chunkX, chunkZ, scale))) {
            future = readStoredImageAsync(world, chunkX, chunkZ, scale).thenApply(mapImage -> {
                MapImage rescaled = resolution.rescale(mapImage);
                saveImageToDiskAsync(world, chunkX, chunkZ, resolution.getScale(), null, rescaled);
                return rescaled;
            });
        } else {
            return CompletableFuture.completedFuture(null);
        }

        cache.put(index, future);
        return future;
    }

    public CompletableFuture<MapImage> readStoredImageAsync(World world, int chunkX, int chunkZ, float scale) {
        String worldName = CustomWorldMapTracker.sanitizeWorldName(world);
        if (!Files.exists(getImagePath(worldName, chunkX, chunkZ, scale))) {
            return CompletableFuture.completedFuture(null);
        }

        Long2ObjectMap<CompletableFuture<MapImage>> cache = loadingImages.computeIfAbsent(worldName, o -> new Long2ObjectOpenHashMap<>());
        long index = ChunkUtil.indexChunk(chunkX, chunkZ);
        CompletableFuture<MapImage> future = cache.get(index);
        if (future != null) {
            if (future.isDone()) {
                cache.remove(index);
            }
            return future;
        }

        future = CompletableFuture.supplyAsync(() -> {
            try {
                return readStoredImage(worldName, chunkX, chunkZ, scale);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, world);
        cache.put(index, future);
        return future;
    }

    @Nullable
    public MapImage readStoredImage(String world, int chunkX, int chunkZ, float scale) throws IOException {
        Path path = getImagePath(world, chunkX, chunkZ, scale);
        if (!Files.exists(path)) {
            return null;
        }

        BufferedImage img;
        try (InputStream in = Files.newInputStream(path)) {
            img = ImageIO.read(in);
        }

        MapImage mapImage = new MapImage(img.getWidth(), img.getHeight(), new int[img.getWidth() * img.getHeight()]);
        for (int x = 0; x < img.getWidth(); x++) {
            for (int y = 0; y < img.getHeight(); y++) {
                int rgb = img.getRGB(x, y);
                mapImage.data[x * mapImage.width + y] = (((rgb >> 16) & 0xFF) & 255) << 24 | (((rgb >> 8) & 0xFF) & 255) << 16 | ((rgb & 0xFF) & 255) << 8 | (((rgb >> 24) & 0xFF) & 255);
            }
        }
        return mapImage;
    }

    private Path getImagePath(String world, int chunkX, int chunkZ, float scale) {
        int rx = chunkX >> 4;
        int rz = chunkZ >> 4;
        return folder.resolve(world).resolve(rx + "." + rz).resolve("scale_" + scale).resolve(chunkX + "." + chunkZ + ".png");
    }
}
