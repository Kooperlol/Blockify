package codes.kooper.blockify.managers;

import codes.kooper.blockify.Blockify;
import codes.kooper.blockify.events.OnBlockChangeSendEvent;
import codes.kooper.blockify.models.Audience;
import codes.kooper.blockify.models.Stage;
import codes.kooper.blockify.models.View;
import codes.kooper.blockify.types.BlockifyChunk;
import codes.kooper.blockify.types.BlockifyPosition;
import codes.kooper.blockify.utils.PositionKeyUtil;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.protocol.world.chunk.LightData;
import com.github.retrooper.packetevents.protocol.world.chunk.impl.v_1_18.Chunk_v1_18;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import io.papermc.paper.math.Position;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
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
    private final ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

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
        sendBlockChanges(view.getStage(), audience, view.getBlocks().keySet());
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
        sendBlockChanges(view.getStage(), audience, view.getBlocks().keySet(), true);
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
     */
    public void sendBlockChange(Stage stage, Audience audience, BlockifyPosition position) {
        BlockifyChunk chunk = new BlockifyChunk(position.getX() >> 4, position.getZ() >> 4);
        sendBlockChanges(stage, audience, Collections.singleton(chunk));
    }

    /**
     * Sends block changes to the audience.
     * Call Asynchronously
     *
     * @param stage        the stage
     * @param audience     the audience
     * @param chunks       the chunks to send
     */
    public void sendBlockChanges(Stage stage, Audience audience, Collection<BlockifyChunk> chunks) {
        sendBlockChanges(stage, audience, chunks, false);
    }

    /**
     * Sends block changes to the audience.
     * Call Asynchronously
     *
     * @param stage        the stage
     * @param audience     the audience
     * @param chunks       the chunks to send
     * @param unload        whether to unload the chunks
     */
    public void sendBlockChanges(Stage stage, Audience audience, Collection<BlockifyChunk> chunks, boolean unload) {
        Map<BlockifyChunk, Map<Long, BlockData>> blockChanges = getBlockChanges(stage, chunks);
        Bukkit.getScheduler().runTask(Blockify.getInstance(), () -> new OnBlockChangeSendEvent(stage, blockChanges).callEvent());

        // If there is only one block change, send it to the player directly
        int blockCount = 0;
        for (Map.Entry<BlockifyChunk, Map<Long, BlockData>> entry : blockChanges.entrySet()) {
            blockCount += entry.getValue().size();
        }
        if (blockCount == 1) {
            for (Player onlinePlayer : audience.getOnlinePlayers()) {
                if (onlinePlayer.getWorld() != stage.getWorld()) continue;
                for (Map.Entry<BlockifyChunk, Map<Long, BlockData>> entry : blockChanges.entrySet()) {
                    Long position = entry.getValue().keySet().iterator().next();
                    BlockData blockData = entry.getValue().values().iterator().next();
                    onlinePlayer.sendBlockChange(PositionKeyUtil.toBlockifyPosition(position).toLocation(onlinePlayer.getWorld()), blockData);
                }
            }
            return;
        }

        // Less than 3,000 blocks then use the player.sendBlockChanges method
        if (blockCount < 3000) {
            Map<Position, BlockData> multiBlockChange = new HashMap<>();
            for (BlockifyChunk chunk : chunks) {
                if (!stage.getWorld().isChunkLoaded(chunk.x(), chunk.z()) || !blockChanges.containsKey(chunk)) continue;
                for (Map.Entry<Long, BlockData> entry : blockChanges.get(chunk).entrySet()) {
                    multiBlockChange.put(PositionKeyUtil.toBlockifyPosition(entry.getKey()).toPosition(), entry.getValue());
                }
            }
            for (Player player : audience.getOnlinePlayers()) {
                player.sendMultiBlockChange(multiBlockChange);
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
            List<BlockifyChunk> chunksToSend = new ArrayList<>(chunks.stream().toList());
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
                    if (!stage.getWorld().isChunkLoaded(chunk.x(), chunk.z())) continue;

                    // Send the chunk packet to the player
                    Bukkit.getScheduler().runTaskAsynchronously(Blockify.getInstance(), () -> sendChunkPacket(stage, onlinePlayer, chunk, unload));
                }
            }, 0L, 1L));
        }
    }

    /**
     * Sends a chunk packet to the player.
     * This method submits the task to the thread pool for asynchronous execution.
     *
     * @param stage  the stage
     * @param player the player
     * @param chunk  the chunk
     * @param unload whether to unload the chunk
     */
    public void sendChunkPacket(Stage stage, Player player, BlockifyChunk chunk, boolean unload) {
        executorService.submit(() -> processAndSendChunk(stage, player, chunk, unload));
    }

    /**
     * Processes the chunk and sends the packet to the player.
     *
     * @param stage  the stage
     * @param player the player
     * @param chunk  the chunk
     * @param unload whether to unload the chunk
     */
    private void processAndSendChunk(Stage stage, Player player, BlockifyChunk chunk, boolean unload) {
        try {
            User packetUser = PacketEvents.getAPI().getPlayerManager().getUser(player);
            int ySections = packetUser.getTotalWorldHeight() >> 4;
            Map<Long, BlockData> blockData = null;

            if (!unload) {
                Map<BlockifyChunk, Map<Long, BlockData>> blockChanges = getBlockChanges(stage, Collections.singleton(chunk));
                blockData = blockChanges.get(chunk);
            }

            Map<BlockData, WrappedBlockState> blockDataToState = new HashMap<>();
            List<BaseChunk> chunks = new ArrayList<>(ySections);
            Chunk bukkitChunk = player.getWorld().getChunkAt(chunk.x(), chunk.z());
            ChunkSnapshot chunkSnapshot = bukkitChunk.getChunkSnapshot();
            int maxHeight = player.getWorld().getMaxHeight();
            int minHeight = player.getWorld().getMinHeight();

            // Pre-fetch default block data for the entire chunk to reduce calls to getBlockData()
            BlockData[][][][] defaultBlockData = new BlockData[ySections][16][16][16];
            for (int section = 0; section < ySections; section++) {
                int baseY = (section << 4) + minHeight;
                for (int x = 0; x < 16; x++) {
                    for (int y = 0; y < 16; y++) {
                        int worldY = baseY + y;
                        if (worldY >= minHeight && worldY < maxHeight) {
                            for (int z = 0; z < 16; z++) {
                                defaultBlockData[section][x][y][z] = chunkSnapshot.getBlockData(x, worldY, z);
                            }
                        }
                    }
                }
            }

            // Predefined full light array and bitsets to avoid recreating them for each chunk
            byte[] fullLightSection = new byte[2048];
            Arrays.fill(fullLightSection, (byte) 0xFF);
            byte[][] fullLightArray = new byte[ySections][];
            BitSet fullBitSet = new BitSet(ySections);
            for (int i = 0; i < ySections; i++) {
                fullLightArray[i] = fullLightSection;
                fullBitSet.set(i);
            }
            BitSet emptyBitSet = new BitSet(ySections);

            // Process each chunk section
            for (int section = 0; section < ySections; section++) {
                Chunk_v1_18 baseChunk = new Chunk_v1_18();

                // Use primitive long keys for block positions to reduce object creation
                long baseY = (section << 4) + minHeight;
                for (int x = 0; x < 16; x++) {
                    for (int y = 0; y < 16; y++) {
                        long worldY = baseY + y;
                        if (worldY >= minHeight && worldY < maxHeight) {
                            for (int z = 0; z < 16; z++) {
                                long positionKey = (((x + ((long) chunk.x() << 4)) & 0x3FFFFFF) << 38)
                                        | ((worldY & 0xFFF) << 26)
                                        | ((z + ((long) chunk.z() << 4)) & 0x3FFFFFF);
                                BlockData data = null;

                                if (!unload && blockData != null) {
                                    data = blockData.get(positionKey);
                                }

                                if (data == null) {
                                    data = defaultBlockData[section][x][y][z];
                                }

                                WrappedBlockState state = blockDataToState.computeIfAbsent(data, SpigotConversionUtil::fromBukkitBlockData);
                                baseChunk.set(x, y, z, state);
                            }
                        }
                    }
                }

                // Set biome data for the chunk section
                int biomeId = baseChunk.getBiomeData().palette.stateToId(1);
                int storageSize = baseChunk.getBiomeData().storage.getData().length;
                for (int index = 0; index < storageSize; index++) {
                    baseChunk.getBiomeData().storage.set(index, biomeId);
                }

                chunks.add(baseChunk);
            }

            // Reuse pre-created light data
            LightData lightData = new LightData();
            lightData.setBlockLightArray(fullLightArray);
            lightData.setSkyLightArray(fullLightArray);
            lightData.setBlockLightCount(ySections);
            lightData.setSkyLightCount(ySections);
            lightData.setBlockLightMask(fullBitSet);
            lightData.setSkyLightMask(fullBitSet);
            lightData.setEmptyBlockLightMask(emptyBitSet);
            lightData.setEmptySkyLightMask(emptyBitSet);

            Column column = new Column(chunk.x(), chunk.z(), true, chunks.toArray(new BaseChunk[0]), null);
            WrapperPlayServerChunkData chunkData = new WrapperPlayServerChunkData(column, lightData);
            packetUser.sendPacketSilently(chunkData);
        } catch (Exception e) {
            // Handle exceptions appropriately, possibly logging them
            e.printStackTrace();
        }
    }

    private Map<BlockifyChunk, Map<Long, BlockData>> getBlockChanges(Stage stage, Collection<BlockifyChunk> chunks) {
        Map<BlockifyChunk, Map<Long, BlockData>> blockChanges = new HashMap<>();
        Map<BlockifyChunk, Map<Long, Integer>> highestZIndexes = new HashMap<>();

        for (View view : stage.getViews()) {
            int zIndex = view.getZIndex();
            for (Map.Entry<BlockifyChunk, ConcurrentHashMap<BlockifyPosition, BlockData>> entry : view.getBlocks().entrySet()) {
                BlockifyChunk chunk = entry.getKey();
                if (!chunks.contains(chunk)) continue;

                highestZIndexes.computeIfAbsent(chunk, k -> new HashMap<>());
                Map<Long, Integer> chunkZIndexes = highestZIndexes.get(chunk);
                Map<Long, BlockData> chunkBlockChanges = blockChanges.computeIfAbsent(chunk, k -> new HashMap<>());

                for (Map.Entry<BlockifyPosition, BlockData> positionEntry : entry.getValue().entrySet()) {
                    BlockifyPosition positionKey = positionEntry.getKey();
                    BlockData blockData = positionEntry.getValue();

                    chunkZIndexes.compute(PositionKeyUtil.getPositionKey(positionKey.getX(), positionKey.getY(), positionKey.getZ()), (key, currentMaxZIndex) -> {
                        if (currentMaxZIndex == null || zIndex > currentMaxZIndex) {
                            chunkBlockChanges.put(PositionKeyUtil.getPositionKey(positionKey.getX(), positionKey.getY(), positionKey.getZ()), blockData);
                            return zIndex;
                        } else if (zIndex == currentMaxZIndex) {
                            chunkBlockChanges.put(PositionKeyUtil.getPositionKey(positionKey.getX(), positionKey.getY(), positionKey.getZ()), blockData);
                        }
                        return currentMaxZIndex;
                    });
                }
            }
        }
        return blockChanges;
    }

    /**
     * Shutdown the executor service gracefully.
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS))
                    System.err.println("Executor service did not terminate");
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}