package com.smileprotocol.run;

/**
 * Estado de un grupo (README 6.1): {@code INTRO -> ZONE_n -> FINAL -> COMPLETED} o
 * {@code ELIMINATED}. La n de {@code ZONE} es {@link Group#getZoneIndex()}.
 */
public enum GroupState {
    INTRO, ZONE, FINAL, COMPLETED, ELIMINATED;

    public boolean isDone() {
        return this == COMPLETED || this == ELIMINATED;
    }
}
