package codes.kooper.blockify.events;

import codes.kooper.blockify.models.Stage;
import codes.kooper.blockify.models.View;
import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

@Getter
public class BlockifyPlaceEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final Vector position;
    private final View view;
    private final Stage stage;

    /**
     * Event that is called when a player places a block in the Blockify plugin.
     *
     * @param player The player that placed the block.
     * @param position The position of the block that was placed.
     * @param view The view that the player is currently in.
     * @param stage The stage that the player is currently in.
     */
    public BlockifyPlaceEvent(Player player, Vector position, View view, Stage stage) {
        this.player = player;
        this.position = position;
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
}
