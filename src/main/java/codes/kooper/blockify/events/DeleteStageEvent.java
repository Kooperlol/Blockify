package codes.kooper.blockify.events;

import codes.kooper.blockify.models.Stage;
import lombok.Getter;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

@Getter
public class DeleteStageEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Stage stage;

    /**
     * Event that is called when a stage is deleted.
     *
     * @param stage The stage that was deleted.
     */
    public DeleteStageEvent(Stage stage) {
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
