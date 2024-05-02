package codes.kooper.blockify.managers;

import codes.kooper.blockify.Blockify;
import codes.kooper.blockify.events.OnBlockChangeSendEvent;
import codes.kooper.blockify.models.Audience;
import codes.kooper.blockify.models.Stage;
import codes.kooper.blockify.types.BlockifyChunk;
import codes.kooper.blockify.types.BlockifyPosition;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
public class BlockChangeManager {

    private final HashMap<UUID, BukkitTask> blockChangeTasks;
    private final HashMap<UUID, List<BlockifyChunk>> chunksBeingSent;
    private final HashMap<BlockData, Integer> blockDataToId = new HashMap<>();

    public BlockChangeManager() {
        this.blockChangeTasks = new HashMap<>();
        this.chunksBeingSent = new HashMap<>();
    }

    public void sendBlockChanges(Stage stage, Audience audience, HashMap<BlockifyChunk, HashMap<BlockifyPosition, BlockData>> blockChanges) {
        if (blockChanges.isEmpty()) {
            return;
        }
        Bukkit.getScheduler().runTask(Blockify.instance, () -> new OnBlockChangeSendEvent(stage, blockChanges).callEvent());

        if (blockChanges.size() == 1) {
            for (UUID uuid : audience.getPlayers()) {
                Player onlinePlayer = Bukkit.getPlayer(uuid);
                if (onlinePlayer != null) {
                    for (Map.Entry<BlockifyChunk, HashMap<BlockifyPosition, BlockData>> entry : blockChanges.entrySet()) {
                        BlockifyPosition position = entry.getValue().keySet().iterator().next();
                        BlockData blockData = entry.getValue().values().iterator().next();
                        onlinePlayer.sendBlockChange(position.toLocation(stage.getWorld()), blockData);
                    }
                }
            }
            return;
        }

        for (UUID uuid : audience.getPlayers()) {
            Player onlinePlayer = Bukkit.getPlayer(uuid);
            AtomicInteger chunkIndex = new AtomicInteger(0);
            if (onlinePlayer != null) {
                BlockifyChunk[] chunksToSend = blockChanges.keySet().toArray(new BlockifyChunk[0]);
                blockChangeTasks.put(uuid, Bukkit.getScheduler().runTaskTimer(Blockify.instance, () -> {
                    if (chunkIndex.get() >= chunksToSend.length) {
                        blockChangeTasks.get(uuid).cancel();
                        blockChangeTasks.remove(uuid);
                        return;
                    }
                    BlockifyChunk chunk = chunksToSend[chunkIndex.get()];
                    sendChunkPacket(stage, onlinePlayer, chunk, blockChanges);
                    chunkIndex.getAndIncrement();
                }, 0L, 1L));
            }
        }
    }

    public void sendChunkPacket(Stage stage, Player player, BlockifyChunk chunk, HashMap<BlockifyChunk, HashMap<BlockifyPosition, BlockData>> blockChanges) {
        User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        chunksBeingSent.computeIfAbsent(user.getUUID(), k -> new ArrayList<>());
        if (chunksBeingSent.get(user.getUUID()).contains(chunk)) {
            return;
        }
        chunksBeingSent.get(user.getUUID()).add(chunk);
        int minHeight = stage.getWorld().getMinHeight();
        int maxHeight = stage.getWorld().getMaxHeight();
        HashMap<BlockifyPosition, BlockData> chunkData = blockChanges.get(chunk);
        for (int chunkY = stage.getMinPosition().getY() >> 4; chunkY <= stage.getMaxPosition().getY() >> 4; chunkY++) {
            List<WrapperPlayServerMultiBlockChange.EncodedBlock> encodedBlocks = new ArrayList<>();
            for (int x = 0; x < 16; x++) {
                for (int y = minHeight; y < maxHeight; y++) {
                    for (int z = 0; z < 16; z++) {
                        BlockifyPosition position = new BlockifyPosition(chunk.x() << 4 | x, y, chunk.z() << 4 | z);
                        if (chunkData.containsKey(position)) {
                            BlockData blockData = chunkData.get(position);
                            int id = blockDataToId.computeIfAbsent(blockData, data -> WrappedBlockState.getByString(PacketEvents.getAPI().getServerManager().getVersion().toClientVersion(), data.getAsString(false)).getGlobalId());
                            encodedBlocks.add(new WrapperPlayServerMultiBlockChange.EncodedBlock(id, x, y, z));
                        }
                    }
                }
            }
            WrapperPlayServerMultiBlockChange.EncodedBlock[] encodedBlocksArray = encodedBlocks.toArray(new WrapperPlayServerMultiBlockChange.EncodedBlock[0]);
            WrapperPlayServerMultiBlockChange wrapperPlayServerMultiBlockChange = new WrapperPlayServerMultiBlockChange(new Vector3i(chunk.x(), chunkY, chunk.z()), true, encodedBlocksArray);
            Bukkit.getScheduler().runTask(Blockify.instance, () -> user.sendPacket(wrapperPlayServerMultiBlockChange));
            user.sendPacket(wrapperPlayServerMultiBlockChange);
        }
        chunksBeingSent.get(user.getUUID()).remove(chunk);
        if (chunksBeingSent.get(user.getUUID()).isEmpty()) {
            chunksBeingSent.remove(user.getUUID());
        }
    }

}