package codes.kooper.blockify.events;

import codes.kooper.blockify.models.Stage;
import codes.kooper.blockify.models.View;
import io.papermc.paper.math.Position;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class BlockifyInteractEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;
    private final Player player;
    private final Position position;
    private final BlockData blockData;
    private final View view;
    private final Stage stage;

    public BlockifyInteractEvent(Player player, Position position, BlockData blockData, View view, Stage stage) {
        this.player = player;
        this.position = position;
        this.blockData = blockData;
        this.view = view;
        this.stage = stage;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }
}
