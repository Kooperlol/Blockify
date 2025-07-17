package codes.kooper.blockify.listeners;

import codes.kooper.blockify.Blockify;
import codes.kooper.blockify.events.PlayerEnterStageEvent;
import codes.kooper.blockify.events.PlayerExitStageEvent;
import codes.kooper.blockify.events.PlayerJoinStageEvent;
import codes.kooper.blockify.events.PlayerLeaveStageEvent;
import codes.kooper.blockify.models.Stage;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class StageBoundListener implements Listener {

    private final LoadingCache<UUID, List<Stage>> stageCache = CacheBuilder.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build(new CacheLoader<>() {
                @Override
                public @NotNull List<Stage> load(@NotNull UUID playerUUID) {
                    Player player = Bukkit.getPlayer(playerUUID);
                    if (player == null) {
                        return Collections.emptyList();
                    }
                    return Blockify.getInstance().getStageManager().getStages(player);
                }
            });

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Only process if the player has moved a significant distance
        if (event.getFrom().distanceSquared(event.getTo()) < 0.25) return;

        // Get the cached stages; if not present, this will call the loader.
        List<Stage> stages;
        try {
            stages = stageCache.get(player.getUniqueId());
        } catch (ExecutionException e) {
            return;
        }

        // Iterate through stages and fire enter/exit events accordingly.
        for (Stage stage : stages) {
            if (stage.isLocationWithin(event.getTo())) {
                PlayerEnterStageEvent enterEvent = new PlayerEnterStageEvent(stage, player);
                enterEvent.callEvent();
                if (enterEvent.isCancelled()) {
                    event.setCancelled(true);
                }
            } else if (stage.isLocationWithin(event.getFrom())) {
                PlayerExitStageEvent exitEvent = new PlayerExitStageEvent(stage, player);
                exitEvent.callEvent();
                if (exitEvent.isCancelled()) {
                    event.setCancelled(true);
                }
            }
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
}