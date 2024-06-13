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
import java.util.concurrent.*;

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
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        for (Player onlinePlayer : audience.getOnlinePlayers()) {
            Location playerLocation = onlinePlayer.getLocation();
            if (playerLocation.getWorld() != stage.getWorld()) continue;

            // Create an array of chunks to send from the block changes map
            Comparator<BlockifyChunk> comparator = (chunk1, chunk2) -> {
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
            };
            Queue<BlockifyChunk> chunksToSend = new PriorityQueue<>(comparator);
            chunksToSend.addAll(blockChanges.keySet());

            // Create a CountDownLatch to track the number of tasks
            CountDownLatch latch = new CountDownLatch(chunksToSend.size());

            // Create a task to send a chunk to the player every tick
            blockChangeTasks.put(onlinePlayer, Bukkit.getScheduler().runTaskTimer(Blockify.getInstance(), () -> {
                // Check if player is online, if not, cancel the task
                if (!onlinePlayer.isOnline()) {
                    cancelExecutorService(executorService, onlinePlayer, latch);
                }

                // Loop through chunks per tick
                for (int i = 0; i < stage.getChunksPerTick(); i++) {
                    // If the chunk index is greater than the chunks to send length
                    if (chunksToSend.isEmpty()) {
                        // Safely cancel the task and remove it from the map
                        cancelExecutorService(executorService, onlinePlayer, latch);
                        return;
                    }

                    // Get the chunk from the chunks to send array
                    BlockifyChunk chunk = chunksToSend.poll();

                    // Check if the chunk is loaded; if not, return
                    if (!stage.getWorld().isChunkLoaded(chunk.x(), chunk.z())) {
                        latch.countDown();
                        continue;
                    }

                    // Send the chunk packet to the player
                    executorService.submit(() -> {
                        try {
                            sendChunkPacket(stage, onlinePlayer, chunk, blockChanges);
                        } finally {
                            latch.countDown();
                        }
                    });
                }
            }, 0L, 1L));
        }
    }

    /**
     * Sends a chunk packet to the player.
     * Call Asynchronously
     *
     * @param stage        the stage
     * @param player       the player
     * @param chunk        the chunk
     * @param blockChanges the block changes
     */
    public void sendChunkPacket(Stage stage, Player player, BlockifyChunk chunk, ConcurrentHashMap<BlockifyChunk, ConcurrentHashMap<BlockifyPosition, BlockData>> blockChanges) {
        // Get the user from PacketEvents API
        final User user = PacketEvents.getAPI().getPlayerManager().getUser(player);

        // Loop through the chunks y positions
        for (int chunkY = stage.getMinPosition().getY() >> 4; chunkY <= stage.getMaxPosition().getY() >> 4; chunkY++) {
            // Create a list of encoded blocks for PacketEvents wrapper
            List<WrapperPlayServerMultiBlockChange.EncodedBlock> encodedBlocks = new ArrayList<>();

            // Loop through the blocks to send
            for (Map.Entry<BlockifyPosition, BlockData> entry : blockChanges.get(chunk).entrySet()) {
                BlockifyPosition position = entry.getKey();
                int blockY = position.getY();

                // Check if the block is in the current y section
                if (blockY >> 4 == chunkY) {
                    BlockData blockData = entry.getValue();
                    // Get the block data ID
                    blockDataToId.computeIfAbsent(blockData, bd -> WrappedBlockState.getByString(PacketEvents.getAPI().getServerManager().getVersion().toClientVersion(), bd.getAsString(false)).getGlobalId());

                    int id = blockDataToId.get(blockData);
                    int x = position.getX() & 0xF;
                    int y = position.getY();
                    int z = position.getZ() & 0xF;

                    // Add the encoded block to the list
                    encodedBlocks.add(new WrapperPlayServerMultiBlockChange.EncodedBlock(id, x, y, z));
                }
            }

            // Send the packet to the player
            WrapperPlayServerMultiBlockChange.EncodedBlock[] encodedBlocksArray = encodedBlocks.toArray(new WrapperPlayServerMultiBlockChange.EncodedBlock[0]);
            WrapperPlayServerMultiBlockChange wrapper = new WrapperPlayServerMultiBlockChange(new Vector3i(chunk.x(), chunkY, chunk.z()), true, encodedBlocksArray);
            user.sendPacket(wrapper);
        }
    }

    /**
     * Cancels the executor service and task for the player.
     *
     * @param executorService the executor service
     * @param onlinePlayer    the online player
     * @param latch           the latch to wait for tasks completion
     */
    private void cancelExecutorService(ExecutorService executorService, Player onlinePlayer, CountDownLatch latch) {
        blockChangeTasks.computeIfPresent(onlinePlayer, (key, task) -> {
            task.cancel();
            try {
                if (latch.await(60, TimeUnit.SECONDS)) {
                    executorService.shutdown();
                } else {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
            return null;
        });
    }
}