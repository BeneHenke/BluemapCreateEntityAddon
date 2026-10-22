package eu.cronmoth.createentityaddon;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@ConfigSerializable
public class Config {

    private boolean renderContraptions = true;

    private boolean renderTracks = true;

    private boolean renderCopycats = true;

    private boolean renderChainConveyors = true;

    private boolean renderDisplayBoards = true;

    private boolean bakeSableSublevelsIntoMap = true;

    private boolean watchTrackDataFile = true;

    private String trackDataFile = "data/create_tracks.dat";

    private String trainModelsDirectory = "bluemap/train_models";

    private String sublevelsDirectory = "bluemap/sublevel_models";

    public boolean isRenderContraptions() {
        return renderContraptions;
    }

    public boolean isRenderTracks() {
        return renderTracks;
    }

    public boolean isRenderCopycats() {
        return renderCopycats;
    }

    public boolean isRenderChainConveyors() {
        return renderChainConveyors;
    }

    public boolean isRenderDisplayBoards() {
        return renderDisplayBoards;
    }

    public boolean isBakeSableSublevelsIntoMap() {
        return bakeSableSublevelsIntoMap;
    }

    public boolean isWatchTrackDataFile() {
        return watchTrackDataFile;
    }

    public String getTrackDataFile() {
        return trackDataFile;
    }

    public String getTrainModelsDirectory() {
        return trainModelsDirectory;
    }

    public String getSublevelsDirectory() {
        return sublevelsDirectory;
    }

}
