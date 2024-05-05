package codes.kooper.blockify.events;

import codes.kooper.blockify.models.Stage;
import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

@Getter
public class PlayerLeaveStageEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Stage stage;
    private final Player player;

    /**
     * Event that is called when a player leaves a stage.
     *
     * @param stage  The stage the player is leaving.
     * @param player The player that is leaving the stage.
     */
    public PlayerLeaveStageEvent(Stage stage, Player player) {
        this.stage = stage;
        this.player = player;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
