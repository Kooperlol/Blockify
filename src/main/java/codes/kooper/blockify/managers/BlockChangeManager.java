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
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUnloadChunk;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import io.papermc.paper.math.Position;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
public class BlockChangeManager {
    private final ConcurrentHashMap<UUID, BukkitTask> blockChangeTasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<BlockData, Integer> blockDataToId = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    // Per-player block changes: PlayerUUID -> (BlockifyChunk -> (BlockifyPosition -> BlockData))
    // This is updated incrementally as views are added/removed or blocks change.
    private final Map<UUID, Map<BlockifyChunk, Map<BlockifyPosition, BlockData>>> playerBlockChanges = new ConcurrentHashMap<>();

    // Track which blocks came from which view for each player:
    // PlayerUUID -> (ViewName -> (Chunk -> Positions))
    private final Map<UUID, Map<String, Map<BlockifyChunk, Set<BlockifyPosition>>>> playerViewBlocks = new ConcurrentHashMap<>();

    public void initializePlayer(Player player) {
        playerBlockChanges.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>());
        playerViewBlocks.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>());
    }

    public void removePlayer(Player player) {
        playerBlockChanges.remove(player.getUniqueId());
        playerViewBlocks.remove(player.getUniqueId());
    }

    /**
     * Hide a view from a player.
     * This removes the view's blocks from the player's cache and then sends updated blocks so the player no longer sees them.
     */
    public void hideView(Player player, View view) {
        removeViewFromPlayer(player, view);
        view.getStage().sendBlocksToAudience();
    }

    /**
     * Add a view's blocks to a player's cache in place.
     */
    public void addViewToPlayer(Player player, View view) {
        Map<BlockifyChunk, Map<BlockifyPosition, BlockData>> playerCache = playerBlockChanges.get(player.getUniqueId());
        Map<String, Map<BlockifyChunk, Set<BlockifyPosition>>> viewMap = playerViewBlocks.get(player.getUniqueId());

        if (playerCache == null || viewMap == null) return;

        Map<BlockifyChunk, Set<BlockifyPosition>> viewBlockPositions = new HashMap<>();

        // Merge view blocks into player cache
        for (Map.Entry<BlockifyChunk, ConcurrentHashMap<BlockifyPosition, BlockData>> chunkEntry : view.getBlocks().entrySet()) {
            BlockifyChunk chunk = chunkEntry.getKey();
            Map<BlockifyPosition, BlockData> chunkMap = playerCache.computeIfAbsent(chunk, c -> new ConcurrentHashMap<>());

            for (Map.Entry<BlockifyPosition, BlockData> posEntry : chunkEntry.getValue().entrySet()) {
                chunkMap.put(posEntry.getKey(), posEntry.getValue());
                viewBlockPositions.computeIfAbsent(chunk, c -> new HashSet<>()).add(posEntry.getKey());
            }
        }

        viewMap.put(view.getName(), viewBlockPositions);
    }

    /**
     * Remove a view's blocks from a player's cache in place.
     */
    public void removeViewFromPlayer(Player player, View view) {
        Map<BlockifyChunk, Map<BlockifyPosition, BlockData>> playerCache = playerBlockChanges.get(player.getUniqueId());
        Map<String, Map<BlockifyChunk, Set<BlockifyPosition>>> viewMap = playerViewBlocks.get(player.getUniqueId());

        if (playerCache == null || viewMap == null) return;

        Map<BlockifyChunk, Set<BlockifyPosition>> viewBlocks = viewMap.remove(view.getName());
        if (viewBlocks == null) return;

        // Remove only the blocks associated with this view
        for (Map.Entry<BlockifyChunk, Set<BlockifyPosition>> chunkEntry : viewBlocks.entrySet()) {
            Map<BlockifyPosition, BlockData> chunkMap = playerCache.get(chunkEntry.getKey());
            if (chunkMap != null) {
                for (BlockifyPosition pos : chunkEntry.getValue()) {
                    chunkMap.remove(pos);
                }
                if (chunkMap.isEmpty()) {
                    playerCache.remove(chunkEntry.getKey());
                }
            }
        }
    }

    /**
     * Apply a single block change for a player. If data is null, remove block.
     */
    public void applyBlockChange(Player player, BlockifyChunk chunk, BlockifyPosition pos, BlockData data, String viewName) {
        Map<BlockifyChunk, Map<BlockifyPosition, BlockData>> playerCache = playerBlockChanges.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>());
        Map<BlockifyPosition, BlockData> chunkMap = playerCache.computeIfAbsent(chunk, c -> new ConcurrentHashMap<>());
        Map<String, Map<BlockifyChunk, Set<BlockifyPosition>>> viewMap = playerViewBlocks.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());

        if (data == null) {
            // Remove block
            chunkMap.remove(pos);
            if (chunkMap.isEmpty()) {
                playerCache.remove(chunk);
            }

            // Also remove from the associated view if known
            if (viewName != null) {
                Map<BlockifyChunk, Set<BlockifyPosition>> viewChunks = viewMap.get(viewName);
                if (viewChunks != null) {
                    Set<BlockifyPosition> positions = viewChunks.get(chunk);
                    if (positions != null) {
                        positions.remove(pos);
                        if (positions.isEmpty()) {
                            viewChunks.remove(chunk);
                            if (viewChunks.isEmpty()) {
                                viewMap.remove(viewName);
                            }
                        }
                    }
                }
            }
        } else {
            // Add or update block
            chunkMap.put(pos, data);

            if (viewName != null) {
                viewMap.computeIfAbsent(viewName, k -> new HashMap<>())
                        .computeIfAbsent(chunk, c -> new HashSet<>())
                        .add(pos);
            }
        }
    }

    /**
     * Retrieve block changes for a player filtered by requested chunks.
     */
    private Map<BlockifyChunk, Map<BlockifyPosition, BlockData>> getBlockChangesForPlayer(Player player, Collection<BlockifyChunk> chunks) {
        Map<BlockifyChunk, Map<BlockifyPosition, BlockData>> changes = playerBlockChanges.get(player.getUniqueId());
        if (changes == null || changes.isEmpty()) {
            return Collections.emptyMap();
        }

        if (chunks.isEmpty()) return Collections.emptyMap();

        Map<BlockifyChunk, Map<BlockifyPosition, BlockData>> result = new HashMap<>();
        for (BlockifyChunk chunk : chunks) {
            Map<BlockifyPosition, BlockData> data = changes.get(chunk);
            if (data != null && !data.isEmpty()) {
                result.put(chunk, data);
            }
        }
        return result;
    }

    public void sendBlockChanges(Stage stage, Audience audience, Collection<BlockifyChunk> chunks) {
        sendBlockChanges(stage, audience, chunks, false);
    }

    public void sendBlockChanges(Stage stage, Audience audience, Collection<BlockifyChunk> chunks, boolean unload) {
        for (Player player : audience.getOnlinePlayers()) {
            if (!player.isOnline() || player.getWorld() != stage.getWorld()) continue;

            Map<BlockifyChunk, Map<BlockifyPosition, BlockData>> blockChanges = getBlockChangesForPlayer(player, chunks);
            Bukkit.getScheduler().runTask(Blockify.getInstance(), () -> new OnBlockChangeSendEvent(stage, blockChanges).callEvent());

            AtomicInteger chunkIndex = new AtomicInteger(0);
            List<BlockifyChunk> chunkList = new ArrayList<>(chunks);

            BukkitTask task = Bukkit.getScheduler().runTaskTimer(Blockify.getInstance(), () -> {
                if (chunkIndex.get() >= chunkList.size()) {
                    cancelTask(player.getUniqueId());
                    return;
                }
                for (int i = 0; i < stage.getChunksPerTick() && chunkIndex.get() < chunkList.size(); i++) {
                    sendChunkPacket(player, chunkList.get(chunkIndex.getAndIncrement()), unload);
                }
            }, 0L, 1L);

            blockChangeTasks.put(player.getUniqueId(), task);
        }
    }

    public void sendMultiBlockChange(Player player, Set<BlockifyPosition> blocks) {
        final Map<Position, BlockData> blocksToSend = new HashMap<>();
        for (BlockifyPosition position : blocks) {
            BlockData blockData = playerBlockChanges.get(player.getUniqueId()).get(position.toBlockifyChunk()).get(position);
            if (blockData == null) continue;
            blocksToSend.put(position.toPosition(), blockData);
        }
        player.sendMultiBlockChange(blocksToSend);
    }

    private void cancelTask(UUID playerId) {
        Optional.ofNullable(blockChangeTasks.remove(playerId)).ifPresent(BukkitTask::cancel);
    }

    public void sendChunkPacket(Player player, BlockifyChunk chunk, boolean unload) {
        executorService.submit(() -> processAndSendChunk(player, chunk, unload));
    }

    private void processAndSendChunk(Player player, BlockifyChunk chunk, boolean unload) {
        try {
            User packetUser = PacketEvents.getAPI().getPlayerManager().getUser(player);
            int ySections = packetUser.getTotalWorldHeight() >> 4;
            Map<BlockifyPosition, BlockData> blockData = null;

            if (!unload) {
                blockData = getBlockChangesForPlayer(player, Collections.singleton(chunk)).get(chunk);
            }

            Map<BlockData, WrappedBlockState> blockDataToState = new HashMap<>();
            List<BaseChunk> chunks = new ArrayList<>(ySections);
            Chunk bukkitChunk = player.getWorld().getChunkAt(chunk.x(), chunk.z());
            ChunkSnapshot chunkSnapshot = bukkitChunk.getChunkSnapshot();
            int maxHeight = player.getWorld().getMaxHeight();
            int minHeight = player.getWorld().getMinHeight();

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

            byte[] fullLightSection = new byte[2048];
            Arrays.fill(fullLightSection, (byte) 0xFF);
            byte[][] fullLightArray = new byte[ySections][];
            BitSet fullBitSet = new BitSet(ySections);
            for (int i = 0; i < ySections; i++) {
                fullLightArray[i] = fullLightSection;
                fullBitSet.set(i);
            }
            BitSet emptyBitSet = new BitSet(ySections);

            for (int section = 0; section < ySections; section++) {
                Chunk_v1_18 baseChunk = new Chunk_v1_18();

                long baseY = (section << 4) + minHeight;
                for (int x = 0; x < 16; x++) {
                    for (int y = 0; y < 16; y++) {
                        long worldY = baseY + y;
                        if (worldY >= minHeight && worldY < maxHeight) {
                            for (int z = 0; z < 16; z++) {
                                BlockData data = null;
                                BlockifyPosition position = new BlockifyPosition(x + (chunk.x() << 4),
                                        (section << 4) + y + minHeight, z + (chunk.z() << 4));

                                if (!unload && blockData != null) {
                                    data = blockData.get(position);
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

                int biomeId = baseChunk.getBiomeData().palette.stateToId(1);
                int storageSize = baseChunk.getBiomeData().storage.getData().length;
                for (int index = 0; index < storageSize; index++) {
                    baseChunk.getBiomeData().storage.set(index, biomeId);
                }

                chunks.add(baseChunk);
            }

            LightData lightData = new LightData();
            lightData.setBlockLightArray(fullLightArray);
            lightData.setSkyLightArray(fullLightArray);
            lightData.setBlockLightCount(ySections);
            lightData.setSkyLightCount(ySections);
            lightData.setBlockLightMask(fullBitSet);
            lightData.setSkyLightMask(fullBitSet);
            lightData.setEmptyBlockLightMask(emptyBitSet);
            lightData.setEmptySkyLightMask(emptyBitSet);

            Column column = new Column(chunk.x(), chunk.z(), true, chunks.toArray(BaseChunk[]::new), null);
            WrapperPlayServerUnloadChunk wrapperPlayServerUnloadChunk = new WrapperPlayServerUnloadChunk(chunk.x(), chunk.z());
            packetUser.sendPacketSilently(wrapperPlayServerUnloadChunk);
            WrapperPlayServerChunkData chunkData = new WrapperPlayServerChunkData(column, lightData);
            packetUser.sendPacketSilently(chunkData);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    System.err.println("Executor service did not terminate");
                }
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}