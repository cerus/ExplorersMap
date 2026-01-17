package dev.cerus.explorersmap.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;
import dev.cerus.explorersmap.ExplorersMapPlugin;
import dev.cerus.explorersmap.config.ExplorersMapConfig;
import dev.cerus.explorersmap.map.CustomWorldMapTracker;
import dev.cerus.explorersmap.map.Resolution;
import java.util.List;
import javax.annotation.Nonnull;

public class SettingsUi extends InteractiveCustomUIPage<SettingsUi.Data> {

    public SettingsUi(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, Data.CODEC);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder, @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("EMSettings.ui");
        //uiCommandBuilder.set("#MyLabel.TextSpans", Message.raw("hello"));

        ExplorersMapConfig config = ExplorersMapPlugin.getInstance().getConfig().get();

        uiCommandBuilder.set("#ResDropdown.Entries", List.of(
                new DropdownEntryInfo(LocalizableString.fromString("BEST"), "best"),
                new DropdownEntryInfo(LocalizableString.fromString("GOOD"), "good"),
                new DropdownEntryInfo(LocalizableString.fromString("FAST"), "fast"),
                new DropdownEntryInfo(LocalizableString.fromString("FASTER"), "faster"),
                new DropdownEntryInfo(LocalizableString.fromString("FASTEST"), "fastest")
        ));
        uiCommandBuilder.set("#ResDropdown.Value", config.getResolutionType().toLowerCase());
        uiCommandBuilder.set("#ResDropdown.TooltipText", """
                  The tile resolution. Available resolutions:
                  - BEST: 96x96 tiles. Vanilla. Will crash the game on very well explored maps.
                  - GOOD: 32x32 tiles. Still very detailed. Might crash the game on well explored maps.
                  - FAST: 16x16 tiles. Default. Looks good enough.
                  - FASTER: 8x8 tiles. Starting to lose a lot of detail.
                  - FASTEST: 4x4 tiles. Almost no detail left. Only use if all other resolutions crash the game.
                """);

        uiCommandBuilder.set("#ExplorationRadiusInput.TooltipText", """
                  The radius of chunks around your player that will be discovered.
                  Warning: Setting this any higher than 10 could make your game or server very laggy.
                """);
        uiCommandBuilder.set("#ExplorationRadiusInput.Value", config.getExplorationRadius());

        uiCommandBuilder.set("#DiskLoadRateInput.TooltipText", """
                The rate at which cached tile images are loaded from the disk. This value is per tick.
                """);
        uiCommandBuilder.set("#DiskLoadRateInput.Value", config.getDiskLoadRate());

        uiCommandBuilder.set("#GenerationRateInput.TooltipText", """
                The rate at which the chunks around the player are mapped into tiles. This value is per tick. Vanilla value is 20.
                """);
        uiCommandBuilder.set("#GenerationRateInput.Value", config.getGenerationRate());

        uiCommandBuilder.set("#MinZoomInput.TooltipText", """
                Sets how far you can zoom out. Min value is 2.
                """);
        uiCommandBuilder.set("#MinZoomInput.Value", config.getMinZoom());

        uiCommandBuilder.set("#PerPlayerMapCheck.TooltipText", """
                When set to true, each player will each have a different map and will only see the places they've personally been to.
                When set to false, all players share a map and will see where others have been to. Includes live updates.
                """);
        uiCommandBuilder.set("#PerPlayerMapCheck #CheckBox.Value", config.isPerPlayerMap());

        uiCommandBuilder.set("#SaveInstTilesCheck.TooltipText", """
                When set to true, tiles from instances (temporary worlds) will be saved to disk.
                Each instance type gets a dedicated folder.
                """);
        uiCommandBuilder.set("#SaveInstTilesCheck #CheckBox.Value", config.isSaveInstanceTiles());

        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SaveButton", new EventData()
                .append("@ResDropdown", "#ResDropdown.Value")
                .append("@ExplorationRadiusInput", "#ExplorationRadiusInput.Value")
                .append("@DiskLoadRateInput", "#DiskLoadRateInput.Value")
                .append("@GenerationRateInput", "#GenerationRateInput.Value")
                .append("@MinZoomInput", "#MinZoomInput.Value")
                .append("@PerPlayerMapCheck", "#PerPlayerMapCheck #CheckBox.Value")
                .append("@SaveInstTilesCheck", "#SaveInstTilesCheck #CheckBox.Value"));
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Data data) {
        super.handleDataEvent(ref, store, data);

        Config<ExplorersMapConfig> config = ExplorersMapPlugin.getInstance().getConfig();
        ExplorersMapConfig confObj = config.get();
        confObj.setResolution(data.getResolution());
        confObj.setExplorationRadius(data.getExplorationRadius());
        confObj.setDiskLoadRate(data.getDiskLoadRate());
        confObj.setGenerationRate(data.getGenerationRate());
        confObj.setMinZoom(data.getMinZoom());
        confObj.setPerPlayerMap(data.isPerPlayerMap());
        confObj.setSaveInstanceTiles(data.isSaveInstanceTiles());
        config.save();

        for (World world : Universe.get().getWorlds().values()) {
            world.execute(() -> {
                ExplorersMapPlugin.getInstance().getWorldMapDiskCache().clearCache(world);
                for (PlayerRef worldPlayerRef : world.getPlayerRefs()) {
                    Player player = world.getEntityStore().getStore().getComponent(worldPlayerRef.getReference(), Player.getComponentType());
                    if (player.getWorldMapTracker() instanceof CustomWorldMapTracker custom) {
                        custom.reset(true);
                    }
                }
            });
        }

        close();
    }

    public static class Data {

        public static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
                .append(new KeyedCodec<>("@ResDropdown", Codec.STRING), Data::setResolutionType, Data::getResolutionType).add()
                .append(new KeyedCodec<>("@ExplorationRadiusInput", Codec.INTEGER), Data::setExplorationRadius, Data::getExplorationRadius).add()
                .append(new KeyedCodec<>("@DiskLoadRateInput", Codec.INTEGER), Data::setDiskLoadRate, Data::getDiskLoadRate).add()
                .append(new KeyedCodec<>("@GenerationRateInput", Codec.INTEGER), Data::setGenerationRate, Data::getGenerationRate).add()
                .append(new KeyedCodec<>("@MinZoomInput", Codec.FLOAT), Data::setMinZoom, Data::getMinZoom).add()
                .append(new KeyedCodec<>("@PerPlayerMapCheck", Codec.BOOLEAN), Data::setPerPlayerMap, Data::isPerPlayerMap).add()
                .append(new KeyedCodec<>("@SaveInstTilesCheck", Codec.BOOLEAN), Data::setSaveInstanceTiles, Data::isSaveInstanceTiles).add()
                .build();

        private int explorationRadius;
        private boolean perPlayerMap;
        private int diskLoadRate;
        private int generationRate;
        private float minZoom;
        private boolean saveInstanceTiles;
        private Resolution resolution;

        @Override
        public String toString() {
            return "Data{" +
                   "explorationRadius=" + explorationRadius +
                   ", perPlayerMap=" + perPlayerMap +
                   ", diskLoadRate=" + diskLoadRate +
                   ", generationRate=" + generationRate +
                   ", minZoom=" + minZoom +
                   ", saveInstanceTiles=" + saveInstanceTiles +
                   ", resolution=" + resolution.getType() +
                   '}';
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
            this.resolution = resolution;
        }
    }
}
