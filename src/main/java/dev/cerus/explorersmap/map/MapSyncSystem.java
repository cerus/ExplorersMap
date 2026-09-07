package dev.cerus.explorersmap.map;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.lang.reflect.Method;

/**
 * System responsible for bridging ECS data to the Map Tracker.
 * Runs on the World Thread.
 */
public class MapSyncSystem extends EntityTickingSystem<EntityStore> {

    @Override
    public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {
        Player player = chunk.getComponent(index, Player.getComponentType());

        if (player != null && player.getWorldMapTracker() instanceof CustomWorldMapTracker tracker) {
            TransformComponent transform = resolveTransformComponent(player, chunk, index);
            if (transform != null) {
                tracker.pushTransform(transform);
            }
        }
    }

    @Override
    public Query<EntityStore> getQuery() {
        // Only tick for players
        return Player.getComponentType();
    }

    private TransformComponent resolveTransformComponent(Player player, ArchetypeChunk<EntityStore> chunk, int index) {
        // Keep compatibility across server builds where Player#getTransformComponent may exist or be removed.
        try {
            Method method = player.getClass().getMethod("getTransformComponent");
            Object result = method.invoke(player);
            if (result instanceof TransformComponent transformComponent) {
                return transformComponent;
            }
        } catch (NoSuchMethodException | NoSuchMethodError ignored) {
            // Fall through to ECS component lookup.
        } catch (Throwable ignored) {
            // Fall through to ECS component lookup.
        }

        Ref<EntityStore> reference = player.getReference();
        if (reference != null && reference.isValid()) {
            Store<EntityStore> referenceStore = reference.getStore();
            TransformComponent transform = referenceStore.getComponent(reference, TransformComponent.getComponentType());
            if (transform != null) {
                return transform;
            }
        }

        return chunk.getComponent(index, TransformComponent.getComponentType());
    }
}
