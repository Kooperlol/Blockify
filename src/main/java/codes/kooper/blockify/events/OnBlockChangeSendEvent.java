package codes.kooper.blockify.events;

import codes.kooper.blockify.models.Stage;
import codes.kooper.blockify.types.BlockifyPosition;
import org.bukkit.Material;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class OnBlockChangeSendEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Stage stage;
    private final HashMap<BlockifyPosition, Material> blocks;

    public OnBlockChangeSendEvent(Stage stage, Map<BlockifyPosition, Material> blocks) {
        this.stage = stage;
        this.blocks = (HashMap<BlockifyPosition, Material>) blocks;
    }


    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
