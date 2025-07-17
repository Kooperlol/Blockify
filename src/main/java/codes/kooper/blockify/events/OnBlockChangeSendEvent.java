package codes.kooper.blockify.events;

import codes.kooper.blockify.models.Stage;
import codes.kooper.blockify.types.BlockifyChunk;
import codes.kooper.blockify.types.BlockifyPosition;
import lombok.Getter;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

@Getter
public class OnBlockChangeSendEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Stage stage;
    private final Map<BlockifyChunk, Map<BlockifyPosition, BlockData>> blocks;

    /**
     * Event that is called when block(s) are being changed.
     * @param stage The stage that the block change is happening in.
     * @param blocks The blocks that are being changed.
     */
    public OnBlockChangeSendEvent(Stage stage, Map<BlockifyChunk, Map<BlockifyPosition, BlockData>> blocks) {
        this.stage = stage;
        this.blocks = blocks;
    }


    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
