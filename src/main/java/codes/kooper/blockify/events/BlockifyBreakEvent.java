package codes.kooper.blockify.events;

import codes.kooper.blockify.models.Stage;
import codes.kooper.blockify.models.View;
import io.papermc.paper.math.Position;
import lombok.Getter;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

@Getter
public class BlockifyBreakEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;
    private final Player player;
    private final Position position;
    private final BlockData blockData;
    private final View view;
    private final Stage stage;

    /**
     * Event that is called when a player breaks a block in a Blockify stage.
     *
     * @param player The player that broke the block.
     * @param position The position of the block that was broken.
     * @param blockData The block data of the block that was broken.
     * @param view The view that the player is in.
     * @param stage The stage that the player is in.
     */
    public BlockifyBreakEvent(Player player, Position position, BlockData blockData, View view, Stage stage) {
        this.player = player;
        this.position = position;
        this.blockData = blockData;
        this.view = view;
        this.stage = stage;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
