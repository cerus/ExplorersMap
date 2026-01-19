package dev.cerus.explorersmap.map;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * System responsible for bridging ECS data to the Map Tracker.
 * Runs on the World Thread.
 */
public class MapSyncSystem extends EntityTickingSystem<EntityStore> {

    @Override
    public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {
        Player player = chunk.getComponent(index, Player.getComponentType());

        if (player != null && player.getWorldMapTracker() instanceof CustomWorldMapTracker tracker) {
            // Use getTransformComponent() to reach the getPosition() method
            TransformComponent transform = player.getTransformComponent();
            if (transform != null) {
                tracker.pushSafePosition(transform.getPosition());
            }
        }
    }

    @Override
    public Query<EntityStore> getQuery() {
        // Only tick for players
        return Player.getComponentType();
    }
}
