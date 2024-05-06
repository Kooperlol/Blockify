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
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
public class BlockChangeManager {

    private final ConcurrentHashMap<UUID, BukkitTask> blockChangeTasks;
    private final ConcurrentHashMap<UUID, Vector<Long>> chunksBeingSent;
    private final ConcurrentHashMap<BlockData, Integer> blockDataToId;

    public BlockChangeManager() {
        this.blockChangeTasks = new ConcurrentHashMap<>();
        this.chunksBeingSent = new ConcurrentHashMap<>();
        this.blockDataToId = new ConcurrentHashMap<>();
    }

    /**
     * Sends a block change to the audience.
     *
     * @param stage        the stage
     * @param audience     the audience
     * @param position     the position
     * @param blockData    the block data
     */
    public void sendBlockChange(Stage stage, Audience audience, BlockifyPosition position, BlockData blockData) {
        ConcurrentHashMap<BlockifyChunk, ConcurrentHashMap<BlockifyPosition, BlockData>> blockChanges = new ConcurrentHashMap<>();
        BlockifyChunk chunk = new BlockifyChunk(position.getX() >> 4, position.getZ() >> 4);
        blockChanges.put(chunk, new ConcurrentHashMap<>());
        blockChanges.get(chunk).put(position, blockData);
        sendBlockChanges(stage, audience, blockChanges);
    }

    /**
     * Sends block changes to the audience.
     *
     * @param stage        the stage
     * @param audience     the audience
     * @param blockChanges the block changes
     */
    public void sendBlockChanges(Stage stage, Audience audience, ConcurrentHashMap<BlockifyChunk, ConcurrentHashMap<BlockifyPosition, BlockData>> blockChanges) {
        if (blockChanges.isEmpty()) {
            return;
        }
        Bukkit.getScheduler().runTask(Blockify.getInstance(), () -> new OnBlockChangeSendEvent(stage, blockChanges).callEvent());

        // If there is only one block change, send it to the player directly
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

        // Send multiple block changes to the players
        for (UUID uuid : audience.getPlayers()) {
            Player onlinePlayer = Bukkit.getPlayer(uuid);
            if (onlinePlayer == null) continue;
            Location playerLocation = onlinePlayer.getLocation();

            // The chunk index is used to keep track of the current chunk being sent
            AtomicInteger chunkIndex = new AtomicInteger(0);
            // Create an array of chunks to send from the block changes map
            List<BlockifyChunk> chunksToSend = new ArrayList<>(List.of(blockChanges.keySet().toArray(new BlockifyChunk[0])));
            chunksToSend.sort((chunk1, chunk2) -> {
                // Get distance from chunks to player
                int x = playerLocation.getBlockX() / 16;
                int z = playerLocation.getBlockZ() / 16;
                int chunkX1 = chunk1.x();
                int chunkZ1 = chunk1.z();
                int chunkX2 = chunk2.x();
                int chunkZ2 = chunk2.z();

                // Calculate squared distances (more efficient than using square root)
                int distanceSquared1 = (chunkX1 - x) * (chunkX1 - x) + (chunkZ1 - z) * (chunkZ1 - z);
                int distanceSquared2 = (chunkX2 - x) * (chunkX2 - x) + (chunkZ2 - z) * (chunkZ2 - z);

                // Compare distances and return accordingly
                return Integer.compare(distanceSquared1, distanceSquared2);
            });

            // Create a task to send a chunk to the player every tick
            blockChangeTasks.put(uuid, Bukkit.getScheduler().runTaskTimer(Blockify.getInstance(), () -> {
                // Loop through the chunks per tick
                for (int i = 0; i < stage.getChunksPerTick(); i++) {
                    // Check if the chunk index is greater than the chunks to send length
                    if (chunkIndex.get() >= chunksToSend.size()) {
                        blockChangeTasks.get(uuid).cancel();
                        blockChangeTasks.remove(uuid);
                        return;
                    }
                    // Get the chunk from the chunks to send array
                    BlockifyChunk chunk = chunksToSend.get(chunkIndex.get());
                    chunkIndex.getAndIncrement();
                    // Check if the chunk is loaded, if not, return
                    if (!stage.getWorld().isChunkLoaded(chunk.x(), chunk.z())) return;
                    // Send the chunk packet to the player
                    Bukkit.getScheduler().runTaskAsynchronously(Blockify.getInstance(), () -> sendChunkPacket(stage, onlinePlayer, chunk, blockChanges));
                }
            }, 0L, 1L));
        }
    }

    /**
     * Sends a chunk packet to the player.
     *
     * @param stage         the stage
     * @param player        the player
     * @param chunk         the chunk
     * @param blockChanges  the block changes
     */
    public void sendChunkPacket(Stage stage, Player player, BlockifyChunk chunk, ConcurrentHashMap<BlockifyChunk, ConcurrentHashMap<BlockifyPosition, BlockData>> blockChanges) {
        // Get the user from packet events API
        User user = PacketEvents.getAPI().getPlayerManager().getUser(player);

        // Add the chunk to the chunks being sent list
        chunksBeingSent.computeIfAbsent(player.getUniqueId(), k -> new Vector<>());
        if (chunksBeingSent.get(player.getUniqueId()).contains(chunk.getChunkKey())) {
            return;
        }
        chunksBeingSent.get(player.getUniqueId()).add(chunk.getChunkKey());

        // Loop through the chunks y positions
        for (int chunkY = stage.getMinPosition().getY() >> 4; chunkY <= stage.getMaxPosition().getY() >> 4; chunkY++) {
            // Create a list of encoded blocks for packet events wrapper
            List<WrapperPlayServerMultiBlockChange.EncodedBlock> encodedBlocks = new ArrayList<>();
            // Loop through the blocks to send
            for (Map.Entry<BlockifyPosition, BlockData> entry : blockChanges.get(chunk).entrySet()) {
                BlockifyPosition position = entry.getKey();
                int blockY = position.getY();
                // Check if the block is in the current y section
                if (blockY >> 4 == chunkY) {
                    // Get the block data id from the block data to id map
                    BlockData blockData = entry.getValue();
                    if (!blockDataToId.containsKey(blockData)) {
                        blockDataToId.put(blockData, WrappedBlockState.getByString(PacketEvents.getAPI().getServerManager().getVersion().toClientVersion(), blockData.getAsString(false)).getGlobalId());
                    }
                    int id = blockDataToId.get(blockData);
                    // Get the x, y, z positions of the block relative to the chunk
                    int x = position.getX() & 0xF;
                    int y = position.getY();
                    int z = position.getZ() & 0xF;
                    // Add the encoded block to the list
                    encodedBlocks.add(new WrapperPlayServerMultiBlockChange.EncodedBlock(id, x, y, z));
                }
            }
            // Send the packet to the player
            WrapperPlayServerMultiBlockChange.EncodedBlock[] encodedBlocksArray = encodedBlocks.toArray(new WrapperPlayServerMultiBlockChange.EncodedBlock[0]);
            WrapperPlayServerMultiBlockChange wrapperPlayServerMultiBlockChange = new WrapperPlayServerMultiBlockChange(new Vector3i(chunk.x(), chunkY, chunk.z()), true, encodedBlocksArray);
            Bukkit.getScheduler().runTask(Blockify.getInstance(), () -> user.sendPacket(wrapperPlayServerMultiBlockChange));
        }
        // Remove the chunk from the chunks being sent list
        chunksBeingSent.get(player.getUniqueId()).remove(chunk.getChunkKey());
        if (chunksBeingSent.get(player.getUniqueId()).isEmpty()) {
            chunksBeingSent.remove(player.getUniqueId());
        }
    }

}