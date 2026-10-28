package eu.cronmoth.createentityaddon;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.common.BlueMapService;
import de.bluecolored.bluemap.common.api.BlueMapAPIImpl;
import de.bluecolored.bluemap.core.map.BmMap;
import de.bluecolored.bluemap.core.map.hires.RenderPassType;
import de.bluecolored.bluemap.core.map.hires.block.BlockRendererType;
import de.bluecolored.bluemap.core.map.hires.entity.EntityRendererType;
import de.bluecolored.bluemap.core.util.Key;
import de.bluecolored.bluemap.core.world.mca.MCAWorld;
import de.bluecolored.bluemap.core.world.mca.blockentity.BlockEntityType;
import de.bluecolored.bluemap.core.world.mca.entity.EntityType;
import eu.cronmoth.createentityaddon.rendering.chainconveyor.ChainConveyorRenderer;
import eu.cronmoth.createentityaddon.rendering.chainconveyor.entitymodel.ChainConveyorEntity;
import eu.cronmoth.createentityaddon.rendering.copycats.CopycatRenderer;
import eu.cronmoth.createentityaddon.rendering.copycats.entitymodel.CopycatBlockEntity;
import eu.cronmoth.createentityaddon.rendering.contraptions.entitymodel.ContraptionEntity;
import eu.cronmoth.createentityaddon.rendering.contraptions.ContraptionEntityRenderer;
import eu.cronmoth.createentityaddon.rendering.displayboard.DisplayBoardTextRenderer;
import eu.cronmoth.createentityaddon.rendering.displayboard.entitymodel.DisplayBoardEntity;
import eu.cronmoth.createentityaddon.rendering.sublevel.SubLevelRenderPass;
import eu.cronmoth.createentityaddon.rendering.text.FontAtlasExtension;
import eu.cronmoth.createentityaddon.rendering.text.LangExtension;
import eu.cronmoth.createentityaddon.rendering.tracks.TrackMeshExtension;
import eu.cronmoth.createentityaddon.rendering.tracks.TrackRenderer;
import eu.cronmoth.createentityaddon.rendering.tracks.entitymodel.TrackEntity;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePack;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class CreateEntityAddon implements Runnable {
    private List<FileWatcher> fileWatchers;
    public static Config config;

    private void addBluemapRegistryValues() {
        EntityRendererType.REGISTRY.register(ContraptionEntityRenderer.TYPE);
        EntityType.REGISTRY.register(new EntityType.Impl(new Key("create", "stationary_contraption"), ContraptionEntity.class));

        // Copycats (Copycats+ and Create native) — rendered by the dedicated CopycatRenderer
        BlockRendererType.REGISTRY.register(CopycatRenderer.TYPE);
        BlockEntityType.REGISTRY.register(new BlockEntityType.Impl(new Key("create", "copycat"), CopycatBlockEntity.class));
        BlockEntityType.REGISTRY.register(new BlockEntityType.Impl(new Key("copycats", "copycat"), CopycatBlockEntity.class));
        BlockEntityType.REGISTRY.register(new BlockEntityType.Impl(new Key("copycats", "multistate_copycat"), CopycatBlockEntity.class));
        BlockEntityType.REGISTRY.register(new BlockEntityType.Impl(new Key("copycats", "multistate_ladder_copycat"), CopycatBlockEntity.class));

        BlockRendererType.REGISTRY.register(TrackRenderer.TYPE);
        BlockEntityType.REGISTRY.register(new BlockEntityType.Impl(new Key("create", "track"), TrackEntity.class));
        ResourcePack.Extension.REGISTRY.register(TrackMeshExtension.TYPE);
        BlockRendererType.REGISTRY.register(ChainConveyorRenderer.TYPE);
        BlockEntityType.REGISTRY.register(new BlockEntityType.Impl(new Key("create", "chain_conveyor"), ChainConveyorEntity.class));
        //Display Board
        ResourcePack.Extension.REGISTRY.register(FontAtlasExtension.TYPE);
        ResourcePack.Extension.REGISTRY.register(LangExtension.TYPE);
        BlockRendererType.REGISTRY.register(DisplayBoardTextRenderer.TYPE);
        BlockEntityType.REGISTRY.register(new BlockEntityType.Impl(new Key("create", "flap_display"), DisplayBoardEntity.class));
        RenderPassType.REGISTRY.register(SubLevelRenderPass.TYPE);
    }

    @Override
    public void run() {
        try {
            fileWatchers = new ArrayList<>();
            addBluemapRegistryValues();

            BlueMapAPI.onEnable(api ->
            {
                Path configFolder = ((BlueMapAPIImpl) api)
                        .plugin()
                        .getServerInterface()
                        .getConfigFolder();

                try {
                    config = new ConfigLoader(configFolder).getConfig();
                } catch (IOException e) {
                    AddonLog.error("failed to load config, using defaults", e);
                    config = new Config();
                }

                BlueMapService service = ((BlueMapAPIImpl) api).blueMapService();

                if (config.isWatchTrackDataFile()) {
                    for (BmMap map : service.getMaps().values()) {
                        MCAWorld world = (MCAWorld) map.getWorld();
                        File trackDataFile = world.getWorldFolder().resolve(config.getTrackDataFile()).toFile();
                        if (trackDataFile.exists()) {
                            FileWatcher fileWatcher = new FileWatcher(trackDataFile, map);
                            fileWatcher.start();
                            fileWatchers.add(fileWatcher);
                        }
                    }
                }
            });

            BlueMapAPI.onDisable(api -> {
                if (fileWatchers != null) {
                    for (FileWatcher watcher : fileWatchers) {
                        watcher.stopThread();
                    }
                }
            });
        } catch (Exception ex) {
            AddonLog.error("failed to start watchers", ex);
        }
    }
}
