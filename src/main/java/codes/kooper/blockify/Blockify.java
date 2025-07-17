package codes.kooper.blockify;

import codes.kooper.blockify.listeners.StageBoundListener;
import codes.kooper.blockify.managers.BlockChangeManager;
import codes.kooper.blockify.managers.StageManager;
import codes.kooper.blockify.protocol.BlockDigAdapter;
import codes.kooper.blockify.protocol.BlockPlaceAdapter;
import codes.kooper.blockify.protocol.ChunkLoadAdapter;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public final class Blockify extends JavaPlugin {
    private StageManager stageManager;
    private BlockChangeManager blockChangeManager;
    private ServerVersion serverVersion;

    @Override
    public void onEnable() {
        serverVersion = PacketEvents.getAPI().getServerManager().getVersion();
        getLogger().info("Blockify has been enabled!");

        stageManager = new StageManager();
        blockChangeManager = new BlockChangeManager();

        getServer().getPluginManager().registerEvents(new StageBoundListener(), this);

        PacketEvents.getAPI().getEventManager().registerListeners(new BlockDigAdapter(), new BlockPlaceAdapter(), new ChunkLoadAdapter());
    }

    @Override
    public void onDisable() {
        blockChangeManager.shutdown();
        getLogger().info("Blockify has been disabled!");
    }

    public static Blockify getInstance() {
        return Blockify.getPlugin(Blockify.class);
    }
}
