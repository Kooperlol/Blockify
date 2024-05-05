package codes.kooper.blockify.listeners;

import codes.kooper.blockify.Blockify;
import codes.kooper.blockify.events.PlayerEnterStageEvent;
import codes.kooper.blockify.events.PlayerExitStageEvent;
import codes.kooper.blockify.events.PlayerJoinStageEvent;
import codes.kooper.blockify.events.PlayerLeaveStageEvent;
import codes.kooper.blockify.models.Stage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.UUID;

public class StageBoundListener implements Listener {

    /**
     * This method is an event handler for PlayerMoveEvent.
     * It is triggered when a player moves in the game.
     * <p>
     * The method retrieves the stages associated with the player and checks if the player's new location
     * is within any of these stages. If the player has entered a new stage, a PlayerEnterStageEvent is called.
     * If the player has exited a stage, a PlayerExitStageEvent is called.
     *
     * @param event The PlayerMoveEvent object containing information about the move event.
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        List<Stage> stages = Blockify.instance.getStageManager().getStages(player.getUniqueId());
        for (Stage stage : stages) {
            if (stage.isLocationWithin(event.getTo())) {
                new PlayerEnterStageEvent(stage, player).callEvent();
            } else if (stage.isLocationWithin(event.getFrom())) {
                new PlayerExitStageEvent(stage, player).callEvent();
            }
        }
    }

    /**
     * This method is an event handler for PlayerJoinEvent.
     * It is triggered when a player joins the game.
     * <p>
     * The method retrieves the stages associated with the player and checks if the player's location
     * is within any of these stages. If the player is within a stage, a PlayerEnterStageEvent is called.
     *
     * @param event The PlayerJoinEvent object containing information about the join event.
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        List<Stage> stages = Blockify.instance.getStageManager().getStages(player.getUniqueId());
        for (Stage stage : stages) {
            if (stage.isLocationWithin(player.getLocation())) {
                new PlayerEnterStageEvent(stage, player).callEvent();
            }
        }
    }

    /**
     * This method is an event handler for PlayerQuitEvent.
     * It is triggered when a player quits the game.
     * <p>
     * The method retrieves the stages associated with the player and calls a PlayerExitStageEvent for each stage.
     *
     * @param event The PlayerQuitEvent object containing information about the quit event.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        List<Stage> stages = Blockify.instance.getStageManager().getStages(player.getUniqueId());
        for (Stage stage : stages) {
            new PlayerExitStageEvent(stage, player).callEvent();
        }
    }

    /**
     * This method is an event handler for PlayerJoinStageEvent.
     * It is triggered when a player joins a stage.
     * <p>
     * The method checks if the player's location is within the stage and calls a PlayerEnterStageEvent if it is.
     *
     * @param event The PlayerJoinStageEvent object containing information about the join stage event.
     */
    @EventHandler
    public void onPlayerStageJoin(PlayerJoinStageEvent event) {
        if (event.getStage().isLocationWithin(event.getPlayer().getLocation())) {
            new PlayerEnterStageEvent(event.getStage(), event.getPlayer()).callEvent();
        }
    }

    /**
     * This method is an event handler for PlayerLeaveStageEvent.
     * It is triggered when a player leaves a stage.
     * <p>
     * The method checks if the player's location is within the stage and calls a PlayerExitStageEvent if it is.
     *
     * @param event The PlayerLeaveStageEvent object containing information about the leave stage event.
     */
    @EventHandler
    public void onPlayerStageLeave(PlayerLeaveStageEvent event) {
        if (event.getStage().isLocationWithin(event.getPlayer().getLocation())) {
            new PlayerExitStageEvent(event.getStage(), event.getPlayer()).callEvent();
        }
    }

    /**
     * This method is an event handler for PlayerEnterStageEvent.
     * It is triggered when a player enters a stage.
     * <p>
     * The method checks if the stage has players hidden and hides all players in the stage from the player.
     *
     * @param event The PlayerEnterStageEvent object containing information about the enter stage event.
     */
    @EventHandler
    public void onPlayerEnterStage(PlayerEnterStageEvent event) {
        if (!event.getStage().getAudience().isArePlayersHidden()) return;
        for (UUID uuid : event.getStage().getAudience().getPlayers()) {
            Player player = Blockify.instance.getServer().getPlayer(uuid);
            if (player == null) continue;
            event.getPlayer().hidePlayer(Blockify.instance, player);

        }
    }

    /**
     * This method is an event handler for PlayerExitStageEvent.
     * It is triggered when a player exits a stage.
     * <p>
     * The method checks if the stage has players hidden and shows all players in the stage to the player.
     *
     * @param event The PlayerExitStageEvent object containing information about the exit stage event.
     */
    @EventHandler
    public void onPlayerExitStage(PlayerExitStageEvent event) {
        if (!event.getStage().getAudience().isArePlayersHidden()) return;
        for (UUID uuid : event.getStage().getAudience().getPlayers()) {
            Player player = Blockify.instance.getServer().getPlayer(uuid);
            if (player == null) continue;
            event.getPlayer().showPlayer(Blockify.instance, player);
        }
    }

}
