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
import java.util.concurrent.atomic.AtomicReference;

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
     * @param stage    the stage
     * @param audience the audience
     * @param position the position
     */
    public void sendBlockChange(Stage stage, Audience audience, BlockifyPosition position) {
        BlockifyChunk chunk = new BlockifyChunk(position.getX() >> 4, position.getZ() >> 4);
        sendBlockChanges(stage, audience, Collections.singleton(chunk));
    }

    /**
     * Sends block changes to the audience.
     * Call Asynchronously
     *
     * @param stage    the stage
     * @param audience the audience
     * @param chunks   the chunks to send
     */
    public void sendBlockChanges(Stage stage, Audience audience, Collection<BlockifyChunk> chunks) {
        sendBlockChanges(stage, audience, chunks, false);
    }

    /**
     * Sends block changes to the audience.
     * Call Asynchronously
     *
     * @param stage    the stage
     * @param audience the audience
     * @param chunks   the chunks to send
     * @param unload   whether to unload the chunks
     */
    public void sendBlockChanges(Stage stage, Audience audience, Collection<BlockifyChunk> chunks, boolean unload) {
        ConcurrentMap<BlockifyChunk, ConcurrentMap<BlockifyPosition, BlockData>> blockChanges = getBlockChanges(stage, chunks);
        Bukkit.getScheduler().runTask(Blockify.getInstance(), () -> new OnBlockChangeSendEvent(stage, blockChanges).callEvent());

        // If there is only one block change, send it to the player directly
        int blockCount = 0;
        for (Map.Entry<BlockifyChunk, ConcurrentMap<BlockifyPosition, BlockData>> entry : blockChanges.entrySet()) {
            blockCount += entry.getValue().size();
        }
        if (blockCount == 1) {
            for (Player onlinePlayer : audience.getOnlinePlayers()) {
                if (onlinePlayer.getWorld() != stage.getWorld()) continue;
                for (Map.Entry<BlockifyChunk, ConcurrentMap<BlockifyPosition, BlockData>> entry : blockChanges.entrySet()) {
                    BlockifyPosition position = entry.getValue().keySet().iterator().next();
                    BlockData blockData = entry.getValue().values().iterator().next();
                    onlinePlayer.sendBlockChange(position.toLocation(stage.getWorld()), blockData);
                }
            }
            return;
        }

        // Less than 3,000 blocks then use the player.sendBlockChanges method
        if (blockCount < 3000) {
            Map<Position, BlockData> multiBlockChange = new HashMap<>();
            for (BlockifyChunk chunk : chunks) {
                if (!stage.getWorld().isChunkLoaded(chunk.x(), chunk.z()) || !blockChanges.containsKey(chunk)) continue;
                for (Map.Entry<BlockifyPosition, BlockData> entry : blockChanges.get(chunk).entrySet()) {
                    multiBlockChange.put(entry.getKey().toPosition(), entry.getValue());
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
     * Call Asynchronously
     *
     * @param stage  the stage
     * @param player the player
     * @param chunk  the chunk
     */
    public void sendChunkPacket(Stage stage, Player player, BlockifyChunk chunk, boolean unload) {
        User packetUser = PacketEvents.getAPI().getPlayerManager().getUser(player);
        if (packetUser == null) return;
        int ySections = packetUser.getTotalWorldHeight() >> 4;
        ConcurrentMap<BlockifyPosition, BlockData> blockData = null;
        if (!unload) {
            ConcurrentMap<BlockifyChunk, ConcurrentMap<BlockifyPosition, BlockData>> blockChanges = getBlockChanges(stage, Collections.singleton(chunk));
            blockData = blockChanges.get(chunk);
        }
        Map<BlockData, WrappedBlockState> blockDataToState = new HashMap<>();
        List<BaseChunk> chunks = new ArrayList<>();
        Chunk bukkitChunk = player.getWorld().getChunkAt(chunk.x(), chunk.z());
        ChunkSnapshot chunkSnapshot = bukkitChunk.getChunkSnapshot();
        int maxHeight = player.getWorld().getMaxHeight();
        int minHeight = player.getWorld().getMinHeight();

        for (int i = 0; i < ySections; i++) {
            Chunk_v1_18 baseChunk = new Chunk_v1_18();

            // Set block data for the chunk section
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        int worldY = (i << 4) + y + minHeight;
                        BlockifyPosition position = new BlockifyPosition(x + (chunk.x() << 4), worldY, z + (chunk.z() << 4));

                        if (!unload && blockData != null && blockData.containsKey(position)) {
                            BlockData data = blockData.get(position);
                            WrappedBlockState state = blockDataToState.computeIfAbsent(data, SpigotConversionUtil::fromBukkitBlockData);
                            baseChunk.set(x, y, z, state);
                        } else if (worldY >= minHeight && worldY < maxHeight) {
                            BlockData defaultData = chunkSnapshot.getBlockData(x, worldY, z);
                            WrappedBlockState defaultState = SpigotConversionUtil.fromBukkitBlockData(defaultData);
                            baseChunk.set(x, y, z, defaultState);
                        }
                    }
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
        // TODO: Implement Tile Entities
        Column column = new Column(chunk.x(), chunk.z(), true, chunks.toArray(new BaseChunk[0]), null);
        LightData lightData = new LightData();
        byte[][] fullLightArray = new byte[ySections][2048];
        for (int i = 0; i < ySections; i++) {
            Arrays.fill(fullLightArray[i], (byte) 0xFF);
        }
        BitSet fullBitSet = new BitSet();
        BitSet emptyBitSet = new BitSet();
        for (int i = 0; i < ySections; i++) {
            fullBitSet.set(i, true);
        }
        lightData.setBlockLightArray(fullLightArray);
        lightData.setSkyLightArray(fullLightArray);
        lightData.setBlockLightCount(ySections);
        lightData.setSkyLightCount(ySections);
        lightData.setBlockLightMask(fullBitSet);
        lightData.setSkyLightMask(fullBitSet);
        lightData.setEmptyBlockLightMask(emptyBitSet);
        lightData.setEmptySkyLightMask(emptyBitSet);
        WrapperPlayServerChunkData chunkData = new WrapperPlayServerChunkData(column, lightData);
        packetUser.sendPacketSilently(chunkData);
    }

    private ConcurrentMap<BlockifyChunk, ConcurrentMap<BlockifyPosition, BlockData>> getBlockChanges(Stage stage, Collection<BlockifyChunk> chunks) {
        // Set for fast chunk containment checks
        ConcurrentHashMap.KeySetView<BlockifyChunk, Boolean> chunkSet = ConcurrentHashMap.newKeySet();
        chunkSet.addAll(chunks);

        // Map to store the highest zIndex and corresponding BlockData for each BlockifyChunk and BlockifyPosition
        ConcurrentMap<BlockifyChunk, ConcurrentMap<BlockifyPosition, AtomicReference<ZIndexBlockData>>> highestZIndexMap = new ConcurrentHashMap<>();

        // Process each view in parallel
        stage.getViews().parallelStream().forEach(view -> {
            int zIndex = view.getZIndex();
            view.getBlocks().forEach((chunk, viewChunkBlocks) -> {
                if (chunkSet.contains(chunk)) {
                    viewChunkBlocks.forEach((position, blockData) -> {
                        highestZIndexMap.computeIfAbsent(chunk, k -> new ConcurrentHashMap<>())
                                .computeIfAbsent(position, k -> new AtomicReference<>(new ZIndexBlockData(zIndex, blockData)))
                                .updateAndGet(existing -> existing.zIndex < zIndex ? new ZIndexBlockData(zIndex, blockData) : existing);
                    });
                }
            });
        });

        // Convert highestZIndexMap to blockChanges
        ConcurrentMap<BlockifyChunk, ConcurrentMap<BlockifyPosition, BlockData>> blockChanges = new ConcurrentHashMap<>();
        highestZIndexMap.forEach((chunk, positionMap) -> {
            ConcurrentMap<BlockifyPosition, BlockData> blockDataMap = new ConcurrentHashMap<>();
            positionMap.forEach((position, zIndexBlockData) -> blockDataMap.put(position, zIndexBlockData.get().blockData));
            blockChanges.put(chunk, blockDataMap);
        });

        return blockChanges;
    }

    private static class ZIndexBlockData {
        int zIndex;
        BlockData blockData;

        ZIndexBlockData(int zIndex, BlockData blockData) {
            this.zIndex = zIndex;
            this.blockData = blockData;
        }
    }
}