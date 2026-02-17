package dev.cerus.explorersmap.map;

import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MapMarkerTracker;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MarkersCollector;
import dev.cerus.explorersmap.ExplorersMapPlugin;
import java.util.function.Predicate;
import javax.annotation.Nullable;

public class CustomMarkersCollector implements MarkersCollector {
    private final MapMarkerTracker tracker;
    private final int chunkViewRadius;
    private final int playerChunkX;
    private final int playerChunkZ;

    public CustomMarkersCollector(MapMarkerTracker tracker, int chunkViewRadius, int playerChunkX, int playerChunkZ) {
        this.tracker = tracker;
        this.chunkViewRadius = chunkViewRadius;
        this.playerChunkX = playerChunkX;
        this.playerChunkZ = playerChunkZ;
    }

    public int getChunkViewRadius() {
        return this.chunkViewRadius;
    }

    public int getPlayerChunkX() {
        return this.playerChunkX;
    }

    public int getPlayerChunkZ() {
        return this.playerChunkZ;
    }

    @Override
    public boolean isInViewDistance(double x, double z) {
        if (ExplorersMapPlugin.getInstance().getConfig().get().isUnlimitedMarkerTracking()) {
            return true;
        }
        return WorldMapTracker.shouldBeVisible(this.chunkViewRadius, MathUtil.floor(x) >> 5, MathUtil.floor(z) >> 5, this.playerChunkX, this.playerChunkZ);
    }

    @Override
    public void add(MapMarker marker) {
        Position position = marker.transform.position;
        if (this.isInViewDistance(position.x, position.z)) {
            this.tracker.sendMapMarker(marker);
        }
    }

    @Override
    public void addIgnoreViewDistance(MapMarker marker) {
        this.tracker.sendMapMarker(marker);
    }

    @Nullable
    @Override
    public Predicate<PlayerRef> getPlayerMapFilter() {
        return this.tracker.getPlayerMapFilter();
    }
}