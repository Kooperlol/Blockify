package codes.kooper.blockify.managers;

import codes.kooper.blockify.Blockify;
import codes.kooper.blockify.events.OnBlockChangeSendEvent;
import codes.kooper.blockify.models.Audience;
import codes.kooper.blockify.models.Stage;
import codes.kooper.blockify.models.View;
import codes.kooper.blockify.types.BlockifyChunk;
import codes.kooper.blockify.types.BlockifyPosition;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.protocol.world.chunk.LightData;
import com.github.retrooper.packetevents.protocol.world.chunk.impl.v_1_18.Chunk_v1_18;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
public class BlockChangeManager {
    private final ConcurrentHashMap<Player, BukkitTask> blockChangeTasks;
    private final ConcurrentHashMap<BlockData, Integer> blockDataToId;

    public BlockChangeManager() {
        this.blockChangeTasks = new ConcurrentHashMap<>();
        this.blockDataToId = new ConcurrentHashMap<>();
    }

    /**
     * Sends views to the player.
     * Call Asynchronously
     *
     * @param stage  the stage
     * @param player the player
     */
    public void sendViews(Stage stage, Player player) {
        for (View view : stage.getViews()) {
            sendView(player, view);
        }
    }

    /**
     * Sends a view to the player.
     * Call Asynchronously
     *
     * @param player the player
     * @param view   the view
     */
    public void sendView(Player player, View view) {
        Audience audience = Audience.fromPlayers(new HashSet<>(Collections.singletonList(player)));
        ConcurrentHashMap<BlockifyChunk, ConcurrentHashMap<BlockifyPosition, BlockData>> blocks = new ConcurrentHashMap<>();
        for (Map.Entry<BlockifyChunk, ConcurrentHashMap<BlockifyPosition, BlockData>> entry : view.getBlocks().entrySet()) {
            if (!blocks.containsKey(entry.getKey())) {
                blocks.put(entry.getKey(), new ConcurrentHashMap<>());
            }
            blocks.get(entry.getKey()).putAll(entry.getValue());
        }
        sendBlockChanges(view.getStage(), audience, blocks);
    }

    /**
     * Hides a view from the player.
     * Call Asynchronously
     *
     * @param player the player
     * @param view   the view
     */
    public void hideView(Player player, View view) {
        Audience audience = Audience.fromPlayers(new HashSet<>(Collections.singletonList(player)));
        ConcurrentHashMap<BlockifyChunk, ConcurrentHashMap<BlockifyPosition, BlockData>> blocks = new ConcurrentHashMap<>();
        for (Map.Entry<BlockifyChunk, ConcurrentHashMap<BlockifyPosition, BlockData>> entry : view.getBlocks().entrySet()) {
            if (!blocks.containsKey(entry.getKey())) {
                blocks.put(entry.getKey(), new ConcurrentHashMap<>());
            }
            for (Map.Entry<BlockifyPosition, BlockData> blockEntry : entry.getValue().entrySet()) {
                blocks.get(entry.getKey()).put(blockEntry.getKey(), view.getStage().getWorld().getBlockData(blockEntry.getKey().getX(), blockEntry.getKey().getY(), blockEntry.getKey().getZ()));
            }
        }
        sendBlockChanges(view.getStage(), audience, blocks);
    }

    /**
     * Hides views from the player.
     * Call Asynchronously
     *
     * @param stage  the stage
     * @param player the player
     */
    public void hideViews(Stage stage, Player player) {
        for (View view : stage.getViews()) {
            hideView(player, view);
        }
    }

    /**
     * Sends a block change to the audience.
     *
     * @param stage     the stage
     * @param audience  the audience
     * @param position  the position
     * @param blockData the block data
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
     * Call Asynchronously
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
        int blockCount = 0;
        for (Map.Entry<BlockifyChunk, ConcurrentHashMap<BlockifyPosition, BlockData>> entry : blockChanges.entrySet()) {
            blockCount += entry.getValue().size();
        }
        if (blockCount == 1) {
            for (Player onlinePlayer : audience.getOnlinePlayers()) {
                if (onlinePlayer.getWorld() != stage.getWorld()) continue;
                for (Map.Entry<BlockifyChunk, ConcurrentHashMap<BlockifyPosition, BlockData>> entry : blockChanges.entrySet()) {
                    BlockifyPosition position = entry.getValue().keySet().iterator().next();
                    BlockData blockData = entry.getValue().values().iterator().next();
                    onlinePlayer.sendBlockChange(position.toLocation(stage.getWorld()), blockData);
                }
            }
            return;
        }

        // Send multiple block changes to the players
        for (Player onlinePlayer : audience.getOnlinePlayers()) {
            Location playerLocation = onlinePlayer.getLocation();
            if (playerLocation.getWorld() != stage.getWorld()) continue;

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
            blockChangeTasks.put(onlinePlayer, Bukkit.getScheduler().runTaskTimer(Blockify.getInstance(), () -> {
                // Check if player is online, if not, cancel the task
                if (!onlinePlayer.isOnline()) {
                    blockChangeTasks.computeIfPresent(onlinePlayer, (key, task) -> {
                        task.cancel();
                        return null;
                    });
                    return;
                }

                // Loop through chunks per tick
                for (int i = 0; i < stage.getChunksPerTick(); i++) {
                    // If the chunk index is greater than the chunks to send length
                    if (chunkIndex.get() >= chunksToSend.size()) {
                        // Safely cancel the task and remove it from the map
                        blockChangeTasks.computeIfPresent(onlinePlayer, (key, task) -> {
                            task.cancel();
                            return null; // Remove the task
                        });
                        return;
                    }

                    // Get the chunk from the chunks to send array
                    BlockifyChunk chunk = chunksToSend.get(chunkIndex.get());
                    chunkIndex.getAndIncrement();

                    // Check if the chunk is loaded; if not, return
                    if (!stage.getWorld().isChunkLoaded(chunk.x(), chunk.z())) return;

                    // Send the chunk packet to the player
                    Bukkit.getScheduler().runTaskAsynchronously(Blockify.getInstance(), () -> sendChunkPacket(onlinePlayer, chunk, blockChanges));
                }
            }, 0L, 1L));
        }
    }

    /**
     * Sends a chunk packet to the player.
     * Call Asynchronously
     *
     * @param player        the player
     * @param chunk         the chunk
     * @param blockChanges  the block changes
     */
    public void sendChunkPacket(Player player, BlockifyChunk chunk, ConcurrentHashMap<BlockifyChunk, ConcurrentHashMap<BlockifyPosition, BlockData>> blockChanges) {
        User packetUser = PacketEvents.getAPI().getPlayerManager().getUser(player);
        ConcurrentHashMap<BlockifyPosition, BlockData> blockData = blockChanges.get(chunk);
        int y = packetUser.getTotalWorldHeight() >> 4;
        Map<BlockData, WrappedBlockState> blockDataToState = new HashMap<>();
        List<BaseChunk> chunks = new ArrayList<>();
        for (int i = 0; i < y; i++) {
            Chunk_v1_18 baseChunk = new Chunk_v1_18();

            // Iterate over block data to set blocks in the chunk
            for (Map.Entry<BlockifyPosition, BlockData> entry : blockData.entrySet()) {
                BlockifyPosition pos = entry.getKey();
                BlockData data = entry.getValue();

                // Check if the block is within the current chunk section (i.e., y coordinate falls within the current section)
                int sectionY = pos.getY() >> 4;
                if (sectionY == i) {
                    baseChunk.set(pos.getX() & 0xF, pos.getY() & 0xF, pos.getZ() & 0xF, blockDataToState.computeIfAbsent(data, block -> SpigotConversionUtil.fromBukkitBlockData(data)));
                }
            }

            // Set biome data for the chunk section
            for (int j = 0; j < 4; j++) {
                for (int k = 0; k < 4; k++) {
                    for (int l = 0; l < 4; l++) {
                        int id = baseChunk.getBiomeData().palette.stateToId(1);
                        baseChunk.getBiomeData().storage.set(k << 2 | l << 2 | j, id);
                    }
                }
            }

            chunks.add(baseChunk);
        }
        Column column = new Column(chunk.x(), chunk.z(), true, chunks.toArray(new BaseChunk[0]), null);
        LightData lightData = new LightData();
        byte[][] emptyLightArray = new byte[y][0];
        BitSet emptyBitSet = new BitSet();
        BitSet lightBitSet = new BitSet();
        for (int i = 0; i < y; i++) {
            emptyBitSet.set(i, true);
        }
        lightData.setBlockLightArray(emptyLightArray);
        lightData.setSkyLightArray(emptyLightArray);
        lightData.setBlockLightCount(y);
        lightData.setSkyLightCount(y);
        lightData.setBlockLightMask(lightBitSet);
        lightData.setSkyLightMask(lightBitSet);
        lightData.setEmptyBlockLightMask(emptyBitSet);
        lightData.setEmptySkyLightMask(emptyBitSet);
        WrapperPlayServerChunkData chunkData = new WrapperPlayServerChunkData(column, lightData);
        packetUser.sendPacketSilently(chunkData);
    }

}