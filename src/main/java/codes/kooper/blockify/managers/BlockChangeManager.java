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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
public class BlockChangeManager {

    private final ConcurrentHashMap<UUID, BukkitTask> blockChangeTasks;
    private final ConcurrentHashMap<UUID, List<Long>> chunksBeingSent;
    private final ConcurrentHashMap<BlockData, Integer> blockDataToId;

    public BlockChangeManager() {
        this.blockChangeTasks = new ConcurrentHashMap<>();
        this.chunksBeingSent = new ConcurrentHashMap<>();
        this.blockDataToId = new ConcurrentHashMap<>();
    }

    public void sendBlockChanges(Stage stage, Audience audience, ConcurrentHashMap<BlockifyChunk, ConcurrentHashMap<BlockifyPosition, BlockData>> blockChanges) {
        if (blockChanges.isEmpty()) {
            return;
        }
        Bukkit.getScheduler().runTask(Blockify.instance, () -> new OnBlockChangeSendEvent(stage, blockChanges).callEvent());

        if (blockChanges.size() == 1) {
            for (UUID uuid : audience.getPlayers()) {
                Player onlinePlayer = Bukkit.getPlayer(uuid);
                if (onlinePlayer != null) {
                    for (Map.Entry<BlockifyChunk, ConcurrentHashMap<BlockifyPosition, BlockData>> entry : blockChanges.entrySet()) {
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
                    chunkIndex.getAndIncrement();
                    if (!onlinePlayer.isChunkSent(chunk.getChunkKey())) return;
                    Bukkit.getScheduler().runTaskAsynchronously(Blockify.instance, () -> sendChunkPacket(stage, onlinePlayer, chunk, blockChanges));
                }, 0L, 1L));
            }
        }
    }

    public void sendChunkPacket(Stage stage, Player player, BlockifyChunk chunk, ConcurrentHashMap<BlockifyChunk, ConcurrentHashMap<BlockifyPosition, BlockData>> blockChanges) {
        User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        chunksBeingSent.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>());
        if (chunksBeingSent.get(player.getUniqueId()).contains(chunk.getChunkKey())) {
            return;
        }
        chunksBeingSent.get(player.getUniqueId()).add(chunk.getChunkKey());
        for (int chunkY = stage.getMinPosition().getY() >> 4; chunkY <= stage.getMaxPosition().getY() >> 4; chunkY++) {
            List<WrapperPlayServerMultiBlockChange.EncodedBlock> encodedBlocks = new ArrayList<>();
            for (Map.Entry<BlockifyPosition, BlockData> entry : blockChanges.get(chunk).entrySet()) {
                BlockifyPosition position = entry.getKey();
                int blockY = position.getY();
                if (blockY >> 4 == chunkY) {
                    BlockData blockData = entry.getValue();
                    if (!blockDataToId.containsKey(blockData)) {
                        blockDataToId.put(blockData, WrappedBlockState.getByString(PacketEvents.getAPI().getServerManager().getVersion().toClientVersion(), blockData.getAsString(false)).getGlobalId());
                    }
                    int id = blockDataToId.get(blockData);
                    int x = position.getX() & 0xF;
                    int y = position.getY();
                    int z = position.getZ() & 0xF;
                    encodedBlocks.add(new WrapperPlayServerMultiBlockChange.EncodedBlock(id, x, y, z));
                }
            }
            WrapperPlayServerMultiBlockChange.EncodedBlock[] encodedBlocksArray = encodedBlocks.toArray(new WrapperPlayServerMultiBlockChange.EncodedBlock[0]);
            WrapperPlayServerMultiBlockChange wrapperPlayServerMultiBlockChange = new WrapperPlayServerMultiBlockChange(new Vector3i(chunk.x(), chunkY, chunk.z()), true, encodedBlocksArray);
            Bukkit.getScheduler().runTask(Blockify.instance, () -> user.sendPacket(wrapperPlayServerMultiBlockChange));
        }
        chunksBeingSent.get(player.getUniqueId()).remove(chunk.getChunkKey());
        if (chunksBeingSent.get(player.getUniqueId()).isEmpty()) {
            chunksBeingSent.remove(player.getUniqueId());
        }
    }

}