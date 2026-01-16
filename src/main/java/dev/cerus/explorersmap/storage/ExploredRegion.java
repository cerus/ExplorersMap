package dev.cerus.explorersmap.storage;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector2d;
import com.hypixel.hytale.protocol.packets.worldmap.MapChunk;
import dev.cerus.explorersmap.util.BetterLongArrayCodec;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public final class ExploredRegion {

    public static final BuilderCodec<ExploredRegion> CODEC = BuilderCodec.builder(ExploredRegion.class, ExploredRegion::new)
            .append(new KeyedCodec<>("Index", Codec.LONG), ExploredRegion::setKey, ExploredRegion::getKey).add()
            .append(new KeyedCodec<>("Chunks", BetterLongArrayCodec.INSTANCE), (comp, explored) -> {
                comp.chunks.clear();
                for (long index : explored) {
                    comp.chunks.add(index);
                }
            }, comp -> {
                long[] arr = new long[comp.chunks.size()];
                for (int i = 0; i < comp.chunks.size(); i++) {
                    arr[i] = comp.chunks.get(i);
                }
                return arr;
            }).add()
            .build();
    private long key;
    private List<Long> chunks;

    public ExploredRegion() {
        chunks = new ArrayList<>();
    }

    public ExploredRegion(long key, List<Long> chunks) {
        this.key = key;
        this.chunks = chunks;
    }

    public void markExplored(MapChunk chunk) {
        long key = ChunkUtil.indexChunk(chunk.chunkX, chunk.chunkZ);
        if (!chunks.contains(key)) {
            chunks.add(key);
        }
    }

    public ExploredRegion copyForSending(int cx, int cz) {
        return new ExploredRegion(key, chunks.stream()
                .sorted(Comparator.comparingDouble(k -> Vector2d.distanceSquared(
                        cx, cz,
                        ChunkUtil.xOfChunkIndex(k), ChunkUtil.zOfChunkIndex(k)
                )))
                .collect(Collectors.toList()));
    }

    public void setKey(long key) {
        this.key = key;
    }

    public void setChunks(List<Long> chunks) {
        this.chunks = chunks;
    }

    public long getKey() {
        return key;
    }

    public List<Long> getChunks() {
        return chunks;
    }

    public boolean isDone() {
        return chunks.isEmpty();
    }
}
