package codes.kooper.blockify;

import codes.kooper.blockify.bstats.Metrics;
import codes.kooper.blockify.listeners.StageBoundListener;
import codes.kooper.blockify.managers.BlockChangeManager;
import codes.kooper.blockify.managers.StageManager;
import codes.kooper.blockify.protocol.BlockDigAdapter;
import codes.kooper.blockify.protocol.BlockPlaceAdapter;
import codes.kooper.blockify.protocol.ChunkLoadAdapter;
import codes.kooper.blockify.protocol.PlayerInfoAdapter;
import com.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

@Getter
public final class Blockify extends JavaPlugin {
    private StageManager stageManager;
    private BlockChangeManager blockChangeManager;

    @Override
    public void onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().getSettings().reEncodeByDefault(false)
                .checkForUpdates(true)
                .bStats(true);
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        new Metrics(this, 21782);
        getLogger().info("Blockify has been enabled!");

        stageManager = new StageManager();
        blockChangeManager = new BlockChangeManager();

        getServer().getPluginManager().registerEvents(new StageBoundListener(), this);

        PacketEvents.getAPI().getEventManager().registerListeners(new BlockDigAdapter(), new BlockPlaceAdapter(), new ChunkLoadAdapter(), new PlayerInfoAdapter());
        PacketEvents.getAPI().init();
    }

    @Override
    public void onDisable() {
        getLogger().info("Blockify has been disabled!");
        blockChangeManager.getBlockChangeTasks().values().forEach(BukkitTask::cancel);
        PacketEvents.getAPI().terminate();
    }

    public static Blockify getInstance() {
        return Blockify.getPlugin(Blockify.class);
    }
}
