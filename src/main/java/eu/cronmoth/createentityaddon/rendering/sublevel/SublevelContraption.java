package eu.cronmoth.createentityaddon.rendering.sublevel;

import eu.cronmoth.createentityaddon.rendering.contraptions.entitymodel.ContraptionEntity;

class SublevelContraption {

    final ContraptionEntity entity;
    final int localX, localY, localZ;

    SublevelContraption(ContraptionEntity entity, int localX, int localY, int localZ) {
        this.entity = entity;
        this.localX = localX;
        this.localY = localY;
        this.localZ = localZ;
    }
}
