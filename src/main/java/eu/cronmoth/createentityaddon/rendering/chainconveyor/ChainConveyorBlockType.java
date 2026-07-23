package eu.cronmoth.createentityaddon.rendering.chainconveyor;

import de.bluecolored.bluemap.core.util.Key;
import de.bluecolored.bluemap.core.world.BlockEntity;
import de.bluecolored.bluemap.core.world.mca.blockentity.BlockEntityType;
import de.bluecolored.bluemap.core.world.mca.blockentity.MCABlockEntity;
import eu.cronmoth.createentityaddon.rendering.chainconveyor.entitymodel.ChainConveyorEntity;

public class ChainConveyorBlockType implements BlockEntityType {
    @Override
    public Class<? extends BlockEntity> getBlockEntityClass() {
        return ChainConveyorEntity.class;
    }

    @Override
    public Key getKey() {
        return null;
    }
}


