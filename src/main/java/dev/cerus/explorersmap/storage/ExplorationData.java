package dev.cerus.explorersmap.storage;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector2d;
import com.hypixel.hytale.protocol.packets.worldmap.MapChunk;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class ExplorationData {

    public static final BuilderCodec<ExplorationData> CODEC = BuilderCodec.builder(ExplorationData.class, ExplorationData::new)
            .append(new KeyedCodec<>("World", Codec.STRING), ExplorationData::setWorldName, ExplorationData::getWorldName).add()
            .append(new KeyedCodec<>("Regions", new ArrayCodec<>(ExploredRegion.CODEC, ExploredRegion[]::new)), (comp, explored) -> {
                comp.setRegions(new ArrayList<>(Arrays.asList(explored)));
            }, comp -> comp.getRegions().toArray(ExploredRegion[]::new)).add()
            .build();
    private String worldName;
    private List<ExploredRegion> regions;

    public ExplorationData() {
        regions = new ArrayList<>();
    }

    public void setRegions(List<ExploredRegion> regions) {
        this.regions = regions;
    }

    public List<ExploredRegion> getRegions() {
        return regions;
    }

    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    public String getWorldName() {
        return worldName;
    }

    public ExploredRegion getOrCreateRegionForChunk(MapChunk chunk) {
        int rx = chunk.chunkX >> 4;
        int rz = chunk.chunkZ >> 4;
        long rk = ChunkUtil.indexChunk(rx, rz);

        return regions.stream()
                .filter(exploredRegion -> exploredRegion.getKey() == rk)
                .findAny()
                .orElseGet(() -> {
                    ExploredRegion region = new ExploredRegion(rk, new ArrayList<>());
                    regions.add(region);
                    return region;
                });
    }

    public void markExplored(MapChunk chunk) {
        getOrCreateRegionForChunk(chunk).markExplored(chunk);
    }

    public List<ExploredRegion> copyRegionsForSending(int cx, int cz) {
        List<ExploredRegion> copy = new ArrayList<>();
        regions.forEach(exploredRegion -> copy.add(exploredRegion.copyForSending(cx, cz)));
        copy.sort(Comparator.comparingDouble(r -> Vector2d.distanceSquared(
                cx >> 4, cz >> 4,
                ChunkUtil.xOfChunkIndex(r.getKey()), ChunkUtil.zOfChunkIndex(r.getKey())
        )));
        return copy;
    }
}
