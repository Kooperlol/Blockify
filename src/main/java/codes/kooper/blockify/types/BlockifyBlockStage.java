package codes.kooper.blockify.types;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BlockifyBlockStage {
    private byte stage;
    private long lastUpdated;
    private int task = 0;

    public BlockifyBlockStage(byte stage, long lastUpdated) {
        this.stage = stage;
        this.lastUpdated = lastUpdated;
    }
}
