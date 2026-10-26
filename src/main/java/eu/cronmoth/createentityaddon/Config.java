package eu.cronmoth.createentityaddon;

import lombok.Data;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@ConfigSerializable
@Data
public class Config {

    private boolean renderContraptions = true;

    private boolean bakeSableSublevelsIntoMap = false;

    private boolean watchTrackDataFile = true;

    private String trackDataFile = "data/create_tracks.dat";

    private String trainModelsDirectory = "bluemap/train_models";

    private String sublevelsDirectory = "bluemap/sublevel_models";

}
