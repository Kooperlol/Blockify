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

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        List<Stage> stages = Blockify.instance.getStageManager().getStages(player.getUniqueId());
        for (Stage stage : stages) {
            new PlayerExitStageEvent(stage, player).callEvent();
        }
    }

    @EventHandler
    public void onPlayerStageJoin(PlayerJoinStageEvent event) {
        if (event.getStage().isLocationWithin(event.getPlayer().getLocation())) {
            new PlayerEnterStageEvent(event.getStage(), event.getPlayer()).callEvent();
        }
    }

    @EventHandler
    public void onPlayerStageLeave(PlayerLeaveStageEvent event) {
        if (event.getStage().isLocationWithin(event.getPlayer().getLocation())) {
            new PlayerExitStageEvent(event.getStage(), event.getPlayer()).callEvent();
        }
    }

    @EventHandler
    public void onPlayerEnterStage(PlayerEnterStageEvent event) {
        if (!event.getStage().getAudience().isArePlayersHidden()) return;
        for (UUID uuid : event.getStage().getAudience().getPlayers()) {
            Player player = Blockify.instance.getServer().getPlayer(uuid);
            if (player == null) continue;
            event.getPlayer().hidePlayer(Blockify.instance, player);
        }
    }

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
