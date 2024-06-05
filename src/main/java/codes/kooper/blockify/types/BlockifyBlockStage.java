package codes.kooper.blockify.types;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BlockifyBlockStage {
    private final int entityId;
    private byte stage;
    private long lastUpdated;
    private int task = 0;

    /**
     * Stores data for a block stage
     * @param stage The stage of the block
     * @param lastUpdated The last time the block was updated
     */
    public BlockifyBlockStage(int entityId, byte stage, long lastUpdated) {
        this.entityId = entityId;
        this.stage = stage;
        this.lastUpdated = lastUpdated;
    }
}
