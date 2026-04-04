package eu.cronmoth.createentityaddon;

import com.flowpowered.math.vector.Vector3d;
import de.bluecolored.bluemap.core.map.BmMap;
import de.bluecolored.bluemap.core.map.TextureGallery;
import de.bluecolored.bluemap.core.map.hires.ArrayTileModel;
import de.bluecolored.bluemap.core.map.hires.PRBMWriter;
import de.bluecolored.bluemap.core.map.hires.RenderSettings;
import de.bluecolored.bluemap.core.map.hires.TileModelView;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePack;
import de.bluecolored.bluemap.core.util.Key;
import de.bluecolored.bluemap.core.world.block.BlockNeighborhood;
import de.bluecolored.bluenbt.BlueNBT;
import eu.cronmoth.createentityaddon.rendering.contraptions.ContraptionBlock;
import eu.cronmoth.createentityaddon.rendering.contraptions.ContraptionEntityRenderer;
import eu.cronmoth.createentityaddon.rendering.contraptions.entitymodel.ContraptionAttribute;
import eu.cronmoth.createentityaddon.rendering.contraptions.entitymodel.ContraptionEntity;
import eu.cronmoth.createentityaddon.rendering.contraptions.trainmodel.Carriage;
import eu.cronmoth.createentityaddon.rendering.contraptions.trainmodel.TrainRoot;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.zip.GZIPInputStream;

public class FileWatcher extends Thread {
    private final File file;
    private AtomicBoolean stop = new AtomicBoolean(false);
    private final BmMap map;

    public FileWatcher(File file, BmMap map) {
        this.file = file;
        this.map = map;
    }

    public boolean isStopped() { return stop.get(); }
    public void stopThread() { stop.set(true); }

    public void doOnChange() {
        try {
            loadTrainModels();
        } catch (Throwable t) {
            System.err.println("Error loading train models!");
            t.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            loadTrainModels();
        } catch (Throwable t) {
            System.err.println("Error loading train models!");
            t.printStackTrace();
        }
        try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
            Path path = file.toPath().getParent();
            path.register(watcher,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE
            );
            while (!isStopped()) {
                WatchKey key;
                try { key = watcher.poll(25, TimeUnit.MILLISECONDS); }
                catch (InterruptedException e) { return; }
                if (key == null) { Thread.yield(); continue; }

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path filename = ev.context();

                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        Thread.yield();
                        continue;
                    } else if (filename.toString().equals(file.getName())) {
                        doOnChange();
                    }
                    boolean valid = key.reset();
                    if (!valid) { break; }
                }
                Thread.yield();
            }
        } catch (Throwable e) {
            System.err.println("Error watching file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadTrainModels() {
        String workingDir = System.getProperty("user.dir");
        String path = workingDir + "/bluemap/train_models/";

        File folder = new File(path);
        File[] files = folder.listFiles();

        BlueNBT blueNBT = new BlueNBT();
        Path createTrainData = file.toPath();
        if (!createTrainData.toFile().exists()) {
            return;
        }

        try (
                InputStream in = Files.newInputStream(createTrainData);
                InputStream compressedIn = new BufferedInputStream(new GZIPInputStream(in))
        ) {
            TrainRoot networkData = blueNBT.read(compressedIn, TrainRoot.class);

            Set<String> expectedFiles = new HashSet<>();
            networkData.data.Trains.forEach(train -> {
                UUID uuid = IntArrayToUUID(train.Id);
                for (int i = 0; i < train.Carriages.size(); i++) {
                    expectedFiles.add(uuid + "_" + i + ".prbm");
                }
            });

            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && !expectedFiles.contains(file.getName())) {
                        file.delete();
                    }
                }
            }

            networkData.data.Trains.forEach(train -> {
                UUID uuid = IntArrayToUUID(train.Id);
                int i = 0;
                for (Carriage carriage : train.Carriages) {
                    String filename = uuid + "_" + i + ".prbm";
                    File outFile = new File(path, filename);
                    if (!outFile.exists()) {
                        ContraptionEntity entity = new ContraptionEntity();
                        ContraptionAttribute contraption = carriage.Entity.Contraption;
                        entity.setId(new Key(carriage.Entity.id));
                        entity.setUuid(IntArrayToUUID(carriage.Entity.UUID));
                        List<Double> posList = carriage.Entity.Pos;
                        entity.setPos(new Vector3d(posList.get(0), posList.get(1), posList.get(2)));
                        entity.setContraption(contraption);
                        entity.setAssemblyDirection(carriage.Entity.Contraption.getAssemblyDirection());
                        entity.setTrain(true);

                        saveTrainModel(entity, outFile.getAbsolutePath());
                    }
                    i++;
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    private void saveTrainModel(ContraptionEntity entity, String path) {
        ResourcePack resourcePack = map.getResourcePack();
        TextureGallery textureGallery = map.getTextureGallery();
        RenderSettings renderSettings = map.getMapSettings();

        BlockNeighborhood block = new BlockNeighborhood(new ContraptionBlock(null), resourcePack, renderSettings, map.getWorld().getDimensionType());
        ContraptionEntityRenderer renderer = new ContraptionEntityRenderer(resourcePack, textureGallery, renderSettings);

        ArrayTileModel arrayTileModel = new ArrayTileModel(100);
        TileModelView tileModelView = new TileModelView(arrayTileModel);
        renderer.render(entity, block, null, tileModelView);

        Path filePath = Path.of(path);
        try {
            Files.createDirectories(filePath.getParent());
            try (OutputStream outputStream = new FileOutputStream(filePath.toFile())) {
                new PRBMWriter(outputStream).write((ArrayTileModel)tileModelView.getTileModel());

            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write", e);
        }
    }

    public UUID IntArrayToUUID(int[] array) {
        ByteBuffer bb = ByteBuffer.allocate(16);
        for (int i : array) {
            bb.putInt(i);
        }
        bb.flip();

        long high = bb.getLong();
        long low = bb.getLong();

        return new UUID(high, low);
    }
}