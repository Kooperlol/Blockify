package codes.kooper.blockify.runnables;

import codes.kooper.blockify.Blockify;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.Queue;

public class BlockSendRunnable extends BukkitRunnable {

    public BlockSendRunnable() {
        this.runTaskTimerAsynchronously(Blockify.getInstance(), 0L, 1L);
    }

    @Override
    public void run() {
        for (Map.Entry<Player, Queue<BukkitRunnable>> entry : Blockify.getInstance().getBlockChangeManager().getBlockChangeTasks().entrySet()) {
            Queue<BukkitRunnable> tasks = entry.getValue();
            if (tasks.peek() == null) {
                Blockify.getInstance().getBlockChangeManager().getBlockChangeTasks().remove(entry.getKey());
                continue;
            }
            tasks.remove().run();
            if (tasks.isEmpty()) {
                Blockify.getInstance().getBlockChangeManager().getBlockChangeTasks().remove(entry.getKey());
            }
        }
    }

}
