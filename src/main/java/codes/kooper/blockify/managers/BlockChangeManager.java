package codes.kooper.blockify.managers;

import codes.kooper.blockify.Blockify;
import codes.kooper.blockify.events.OnBlockChangeSendEvent;
import codes.kooper.blockify.models.Audience;
import codes.kooper.blockify.models.Stage;
import codes.kooper.blockify.types.BlockifyPosition;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.stream.Collectors;

@Getter
public class BlockChangeManager {

    private final HashMap<UUID, BukkitTask> blockChangeTasks;

    public BlockChangeManager() {
        this.blockChangeTasks = new HashMap<>();
    }

    public void sendBlockChanges(Stage stage, Audience audience, Map<BlockifyPosition, BlockData> blockChanges) {
        if (blockChanges.isEmpty()) return;
        new OnBlockChangeSendEvent(stage, blockChanges).callEvent();
        if (blockChanges.size() == 1) {
            audience.getPlayers().forEach(player -> Optional.ofNullable(Bukkit.getPlayer(player)).ifPresent(p -> blockChanges.forEach((position, blockData) -> p.sendBlockChange(position.toLocation(stage.getWorld()), blockData))));
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(Blockify.instance, () -> {
            if (blockChanges.size() <= 2000) {
                audience.getPlayers().forEach(player -> Optional.ofNullable(Bukkit.getPlayer(player)).ifPresent(p -> p.sendBlockChanges(blockChanges.keySet().stream().map(value -> value.getBlockState(stage.getWorld(), blockChanges.get(value))).toList())));
                return;
            }
            audience.getPlayers().forEach(player -> Optional.ofNullable(Bukkit.getPlayer(player)).ifPresent(p -> {
                Set<BlockifyPosition> blockChangesSorted = blockChanges.keySet().stream().sorted(Comparator.comparingDouble(value -> value.toVector().distance(p.getLocation().toVector()))).collect(Collectors.toCollection(LinkedHashSet::new));
                blockChangeTasks.put(player, Bukkit.getScheduler().runTaskTimerAsynchronously(Blockify.instance, () -> {
                    if (blockChangesSorted.isEmpty()) {
                        blockChangeTasks.get(player).cancel();
                        blockChangeTasks.remove(player);
                        return;
                    }
                    Set<BlockState> blockChangesToSend = new HashSet<>();
                    Set<BlockifyPosition> blockChangesChunk = blockChangesSorted.stream().limit(7000).collect(Collectors.toSet());
                    blockChangesChunk.forEach(value -> blockChangesToSend.add(value.getBlockState(stage.getWorld(), blockChanges.get(value))));
                    blockChangesSorted.removeAll(blockChangesChunk);
                    p.sendBlockChanges(blockChangesToSend);
                }, 0L, 1L));
            }));
        });
    }

}
