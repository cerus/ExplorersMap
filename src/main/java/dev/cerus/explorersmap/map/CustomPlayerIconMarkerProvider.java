package dev.cerus.explorersmap.map;

import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.asset.type.gameplay.WorldMapConfig;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MapMarkerTracker;
import com.hypixel.hytale.server.core.util.PositionUtil;
import dev.cerus.explorersmap.ExplorersMapPlugin;
import java.util.function.Predicate;
import javax.annotation.Nonnull;

/**
 * A marker provider implementation that sends player markers with unlimited range
 */
public class CustomPlayerIconMarkerProvider implements WorldMapManager.MarkerProvider {

    private final WorldMapManager.MarkerProvider original;

    public CustomPlayerIconMarkerProvider(WorldMapManager.MarkerProvider original) {
        this.original = original;
    }

    @Override
    public void update(@Nonnull World world, @Nonnull MapMarkerTracker tracker, int chunkViewRadius, int playerChunkX, int playerChunkZ) {
        if (!ExplorersMapPlugin.getInstance().getConfig().get().isUnlimitedPlayerTracking()) {
            original.update(world, tracker, chunkViewRadius, playerChunkX, playerChunkZ);
            return;
        }

        WorldMapConfig worldMapConfig = world.getGameplayConfig().getWorldMapConfig();
        if (!worldMapConfig.isDisplayPlayers()) {
            return;
        }

        Player player = tracker.getPlayer();
        Predicate<PlayerRef> playerMapFilter = tracker.getPlayerMapFilter();

        for (PlayerRef otherPlayer : world.getPlayerRefs()) {
            if (otherPlayer.getUuid().equals(player.getUuid())) {
                continue;
            }
            if (playerMapFilter != null && !playerMapFilter.test(otherPlayer)) {
                continue;
            }

            Transform otherPlayerTransform = otherPlayer.getTransform();
            Vector3d otherPos = otherPlayerTransform.getPosition();

            if (player.getWorldMapTracker() instanceof CustomWorldMapTracker customTracker) {
                int otherChunkX = (int)otherPos.x >> 5;
                int otherChunkZ = (int)otherPos.z >> 5;
                if (!customTracker.isLoaded(otherChunkX, otherChunkZ)) {
                    continue;
                }
            }

            tracker.trySendMarker(-1, playerChunkX, playerChunkZ, otherPos, otherPlayer.getHeadRotation().getYaw(), "Player-" + otherPlayer.getUuid(), "Player: " + otherPlayer.getUsername(), otherPlayer, (id, name, op) -> {
                return new MapMarker(id, name, "Player.png", PositionUtil.toTransformPacket(op.getTransform()), null);
            });
        }
    }
}
