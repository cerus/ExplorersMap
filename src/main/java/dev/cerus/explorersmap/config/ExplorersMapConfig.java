package dev.cerus.explorersmap.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

public class ExplorersMapConfig {

    public static final BuilderCodec<ExplorersMapConfig> CODEC = BuilderCodec.builder(ExplorersMapConfig.class, ExplorersMapConfig::new)
            .append(new KeyedCodec<>("ExplorationRadius", Codec.INTEGER),
                    ExplorersMapConfig::setExplorationRadius,
                    ExplorersMapConfig::getExplorationRadius).add()
            .append(new KeyedCodec<>("PerPlayerMap", Codec.BOOLEAN),
                    ExplorersMapConfig::setPerPlayerMap,
                    ExplorersMapConfig::isPerPlayerMap).add()
            .append(new KeyedCodec<>("DiskLoadRate", Codec.INTEGER),
                    ExplorersMapConfig::setDiskLoadRate,
                    ExplorersMapConfig::getDiskLoadRate).add()
            .append(new KeyedCodec<>("GenerationRate", Codec.INTEGER),
                    ExplorersMapConfig::setGenerationRate,
                    ExplorersMapConfig::getGenerationRate).add()
            .append(new KeyedCodec<>("MinZoom", Codec.FLOAT),
                    ExplorersMapConfig::setMinZoom,
                    ExplorersMapConfig::getMinZoom).add()
            .append(new KeyedCodec<>("SaveInstanceTiles", Codec.BOOLEAN),
                    ExplorersMapConfig::setSaveInstanceTiles,
                    ExplorersMapConfig::isSaveInstanceTiles).add()
            // This does not work right now
            /*.append(new KeyedCodec<>("UnlimitedPlayerTracking", Codec.BOOLEAN),
                    ExplorersMapConfig::setUnlimitedPlayerTracking,
                    ExplorersMapConfig::isUnlimitedPlayerTracking).add()*/
            .build();

    private int explorationRadius = 3;
    private boolean perPlayerMap = true;
    private int diskLoadRate = 16;
    private int generationRate = 20;
    private boolean unlimitedPlayerTracking = true;
    private float minZoom = 8;
    private boolean saveInstanceTiles = false;

    public void setExplorationRadius(int explorationRadius) {
        this.explorationRadius = explorationRadius;
    }

    public void setPerPlayerMap(boolean perPlayerMap) {
        this.perPlayerMap = perPlayerMap;
    }

    public int getExplorationRadius() {
        return explorationRadius;
    }

    public boolean isPerPlayerMap() {
        return perPlayerMap;
    }

    public void setDiskLoadRate(int diskLoadRate) {
        this.diskLoadRate = diskLoadRate;
    }

    public int getDiskLoadRate() {
        return diskLoadRate;
    }

    public void setGenerationRate(int generationRate) {
        this.generationRate = generationRate;
    }

    public int getGenerationRate() {
        return generationRate;
    }

    public void setUnlimitedPlayerTracking(boolean unlimitedPlayerTracking) {
        this.unlimitedPlayerTracking = unlimitedPlayerTracking;
    }

    public boolean isUnlimitedPlayerTracking() {
        return unlimitedPlayerTracking;
    }

    public void setMinZoom(float minZoom) {
        this.minZoom = minZoom;
    }

    public float getMinZoom() {
        return minZoom;
    }

    public void setSaveInstanceTiles(boolean saveInstanceTiles) {
        this.saveInstanceTiles = saveInstanceTiles;
    }

    public boolean isSaveInstanceTiles() {
        return saveInstanceTiles;
    }
}
