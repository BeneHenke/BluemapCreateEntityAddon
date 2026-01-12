package eu.cronmoth.createentityaddon.rendering.tracks;

import de.bluecolored.bluemap.core.util.Key;
import de.bluecolored.bluemap.core.world.BlockEntity;
import de.bluecolored.bluemap.core.world.mca.blockentity.BlockEntityType;
import eu.cronmoth.createentityaddon.rendering.tracks.entitymodel.TrackEntity;

public class TrackBlockType implements BlockEntityType {
    @Override
    public Class<? extends BlockEntity> getBlockEntityClass() {
        return TrackEntity.class;
    }

    @Override
    public Key getKey() {
        return null;
    }
}
