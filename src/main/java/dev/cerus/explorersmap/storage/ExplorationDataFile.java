package dev.cerus.explorersmap.storage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class ExplorationDataFile {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Path path;

    public ExplorationDataFile(Path path) {
        this.path = path;
    }

    public ExplorationData readAndConvertIfRequired(Path legacyJsonPath) throws IOException {
        ExplorationData explorationData;
        if (Files.exists(legacyJsonPath)) {
            LOGGER.atInfo().log("Converting exploration data from " + legacyJsonPath + " to " + path.toString());
            explorationData = convert(legacyJsonPath);

            Path dir = legacyJsonPath.getParent().resolve("legacy");
            Files.createDirectories(dir);
            Files.move(legacyJsonPath, dir.resolve(legacyJsonPath.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        } else {
            explorationData = read();
        }
        return explorationData;
    }

    public ExplorationData read() throws IOException {
        if (!Files.exists(path)) {
            return new ExplorationData();
        }

        try (InputStream in = Files.newInputStream(path)) {
            byte[] buffer = new byte[8];
            in.readNBytes(buffer, 0, 8);
            ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
            long regionsAmt = byteBuffer.getLong();

            List<ExploredRegion> regions = new ArrayList<>();
            for (long i = 0; i < regionsAmt; i++) {
                in.readNBytes(buffer, 0, 8);
                byteBuffer.rewind();
                long regionIndex = byteBuffer.getLong();

                in.readNBytes(buffer, 0, 4);
                byteBuffer.rewind();
                int chunksAmt = byteBuffer.getInt();

                List<Long> chunks = new ArrayList<>();
                for (int j = 0; j < chunksAmt; j++) {
                    in.readNBytes(buffer, 0, 8);
                    byteBuffer.rewind();
                    long chunkIndex = byteBuffer.getLong();
                    chunks.add(chunkIndex);
                }

                ExploredRegion region = new ExploredRegion(regionIndex, chunks);
                regions.add(region);
            }

            return new ExplorationData(regions);
        }
    }

    public void write(ExplorationData data) throws IOException {
        List<ExploredRegion> regions = data.getRegions();
        int bytes = regions.stream()
                .mapToInt(r -> 12 + r.getChunks().size() * 8)
                .sum() + 8;

        try (OutputStream out = Files.newOutputStream(path, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buffer = ByteBuffer.allocate(bytes);
            buffer.putLong(regions.size());

            for (ExploredRegion region : regions) {
                buffer.putLong(region.getKey());

                List<Long> chunks = region.getChunks();
                buffer.putInt(chunks.size());
                for (long chunk : chunks) {
                    buffer.putLong(chunk);
                }
            }

            out.write(buffer.array());
        }
    }

    public ExplorationData convert(Path legacyJsonPath) throws IOException {
        if (!Files.exists(legacyJsonPath)) {
            LOGGER.atInfo().log("DEBUG: not exist");
            return read();
        }

        String jsonString = Files.readString(legacyJsonPath);
        JsonObject root = JsonParser.parseString(jsonString).getAsJsonObject();
        if (!root.has("Regions")) {
            LOGGER.atInfo().log("DEBUG: no regions");
            return read();
        }

        JsonArray regionsArr = root.getAsJsonArray("Regions");
        ExplorationData explorationData = new ExplorationData();
        for (JsonElement element : regionsArr) {
            JsonObject regionObj = element.getAsJsonObject();
            long index = regionObj.get("Index").getAsLong();
            JsonArray chunksArr = regionObj.getAsJsonArray("Chunks");
            List<Long> chunks = new ArrayList<>();
            for (JsonElement item : chunksArr) {
                chunks.add(item.getAsLong());
            }

            ExploredRegion region = new ExploredRegion(index, chunks);
            explorationData.getRegions().add(region);
            LOGGER.atInfo().log("DEBUG: conv r " + index + " chunks " + chunks.size());
        }

        write(explorationData);
        LOGGER.atInfo().log("DEBUG: write");
        return explorationData;
    }
}
