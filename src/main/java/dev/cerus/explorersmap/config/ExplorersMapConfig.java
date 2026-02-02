package dev.cerus.explorersmap.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.logger.HytaleLogger;
import dev.cerus.explorersmap.map.Resolution;

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
            .append(new KeyedCodec<>("Resolution", Codec.STRING),
                    ExplorersMapConfig::setResolutionType,
                    ExplorersMapConfig::getResolutionType).add()
            .append(new KeyedCodec<>("UnlimitedPlayerTracking", Codec.BOOLEAN),
                    ExplorersMapConfig::setUnlimitedPlayerTracking,
                    ExplorersMapConfig::isUnlimitedPlayerTracking).add()
            .append(new KeyedCodec<>("UnlimitedMarkerTracking", Codec.BOOLEAN),
                    ExplorersMapConfig::setUnlimitedMarkerTracking,
                    ExplorersMapConfig::isUnlimitedMarkerTracking).add()
            .build();

    private int explorationRadius = 3;
    private boolean perPlayerMap = true;
    private int diskLoadRate = 16;
    private int generationRate = 20;
    private boolean unlimitedPlayerTracking = true;
    private boolean unlimitedMarkerTracking = false;
    private float minZoom = 8;
    private boolean saveInstanceTiles = false;
    private Resolution resolution = Resolution.FAST;

    public void validate() {
        if (explorationRadius <= 0) {
            explorationRadius = 1;
        }
        if (diskLoadRate <= 0) {
            diskLoadRate = 1;
        }
        if (generationRate <= 0) {
            generationRate = 1;
        }
        if (minZoom <= 2) {
            minZoom = 2;
        }
        if (resolution == null) {
            resolution = Resolution.FAST;
        }
    }

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

    public void setUnlimitedMarkerTracking(boolean unlimitedMarkerTracking) {
        this.unlimitedMarkerTracking = unlimitedMarkerTracking;
    }

    public boolean isUnlimitedMarkerTracking() {
        return unlimitedMarkerTracking;
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

    public void setResolutionType(String str) {
        setResolution(switch (str.toUpperCase()) {
            case "BEST" -> Resolution.BEST;
            case "GOOD" -> Resolution.GOOD;
            case "FASTER" -> Resolution.FASTER;
            case "FASTEST" -> Resolution.FASTEST;
            default -> Resolution.FAST;
        });
    }

    public String getResolutionType() {
        return resolution.getType();
    }

    public Resolution getResolution() {
        return resolution;
    }

    public void setResolution(Resolution resolution) {
        if (resolution == null) {
            HytaleLogger.forEnclosingClass().atWarning().log("Trying to set resolution to null", new Throwable());
            return;
        }
        this.resolution = resolution;
    }
}
