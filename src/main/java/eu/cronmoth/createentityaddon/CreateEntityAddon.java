package eu.cronmoth.createentityaddon;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.common.BlueMapService;
import de.bluecolored.bluemap.common.api.BlueMapAPIImpl;
import de.bluecolored.bluemap.core.map.BmMap;
import de.bluecolored.bluemap.core.map.hires.block.BlockRendererType;
import de.bluecolored.bluemap.core.map.hires.entity.EntityRendererType;
import de.bluecolored.bluemap.core.util.Key;
import de.bluecolored.bluemap.core.world.mca.MCAWorld;
import de.bluecolored.bluemap.core.world.mca.blockentity.BlockEntityType;
import de.bluecolored.bluemap.core.world.mca.entity.EntityType;
import eu.cronmoth.createentityaddon.rendering.copycats.CopycatBlockType;
import eu.cronmoth.createentityaddon.rendering.copycats.entitymodel.CopycatBlockEntity;
import eu.cronmoth.createentityaddon.rendering.copycats.CopycatRenderer;
import eu.cronmoth.createentityaddon.rendering.contraptions.entitymodel.ContraptionEntity;
import eu.cronmoth.createentityaddon.rendering.contraptions.ContraptionEntityRenderer;

import java.io.*;
import java.util.ArrayList;
import java.util.List;



public class CreateEntityAddon implements Runnable {
    List<FileWatcher> fileWatchers;

    private void addBluemapRegistryValues() {
        EntityRendererType.REGISTRY.register(ContraptionEntityRenderer.TYPE);
        EntityType.REGISTRY.register(new EntityType.Impl(new Key("create", "stationary_contraption"), ContraptionEntity.class));
        BlockRendererType.REGISTRY.register(CopycatRenderer.TYPE);
        BlockRendererType.REGISTRY.register(CopycatRenderer.TYPE);
        BlockEntityType.REGISTRY.register(new CopycatBlockType.Impl(new Key("create", "copycat"), CopycatBlockEntity.class));
    }

    @Override
    public void run() {
        addBluemapRegistryValues();
        System.err.println("CreateEntityAddon enabled! - More Debugging info will be printed if the file watcher fails to start.");
        try {
            fileWatchers = new ArrayList<>();
            BlueMapAPI.onEnable(api ->
            {
                BlueMapService service = ((BlueMapAPIImpl) api).blueMapService();
                for (BmMap map : service.getMaps().values()) {
                    MCAWorld world = (MCAWorld) map.getWorld();
                    File dataDirectory = new File(world.getWorldFolder() + "/data");
                    if (dataDirectory.exists() && world.getDimension().getKey().getValue().equalsIgnoreCase("overworld")) {
                        FileWatcher fileWatcher = new FileWatcher(new File(world.getWorldFolder() + "/data/create_tracks.dat"), map);
                        fileWatcher.start();
                        fileWatchers.add(fileWatcher);
                    }
                }
            });
        } catch (Exception ex) {
            System.err.println("Failed to start FileWatcher: " + ex.getMessage());
            ex.printStackTrace();
        }

    }

    private void deleteDirectory(File directoryToBeDeleted) {
        if (directoryToBeDeleted == null || !directoryToBeDeleted.exists()) {
            return;
        }
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        directoryToBeDeleted.delete();
    }
}
