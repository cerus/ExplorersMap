package dev.cerus.explorersmap.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.cerus.explorersmap.ui.SettingsUi;
import javax.annotation.Nonnull;

public class ExplorersMapCommand extends AbstractPlayerCommand {

    public ExplorersMapCommand(@Nonnull String name, @Nonnull String description) {
        super(name, description);
        requirePermission("explorersmap.command");
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        Player player = commandContext.senderAs(Player.class);

        world.execute(() -> {
            player.getPageManager().openCustomPage(ref, store, new SettingsUi(playerRef));
        });
    }
}
