package codes.kooper.blockify.events;

import codes.kooper.blockify.models.Stage;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class DeleteStageEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Stage stage;

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
