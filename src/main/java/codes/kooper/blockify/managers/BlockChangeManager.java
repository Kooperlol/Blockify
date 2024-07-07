package codes.kooper.blockify.managers;

import codes.kooper.blockify.Blockify;
import codes.kooper.blockify.events.OnBlockChangeSendEvent;
import codes.kooper.blockify.models.Audience;
import codes.kooper.blockify.models.Stage;
import codes.kooper.blockify.models.View;
import codes.kooper.blockify.types.BlockifyChunk;
import codes.kooper.blockify.types.BlockifyPosition;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.ProtocolVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.protocol.world.chunk.LightData;
import com.github.retrooper.packetevents.protocol.world.chunk.TileEntity;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
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
                    Bukkit.getScheduler().runTaskAsynchronously(Blockify.getInstance(), () -> sendChunkPacket(stage, onlinePlayer, chunk, blockChanges));
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

        if (user == null) {
            System.err.println("User is null for player: " + player.getName());
            return;
        }

        // Check if the chunk exists in blockChanges
        if (!blockChanges.containsKey(chunk)) {
            System.err.println("Chunk not found in blockChanges for: " + chunk);
            return;
        }

        // List to hold chunk sections
        List<BaseChunk> chunks = new ArrayList<>();
        int minY = stage.getMinPosition().getY() >> 4;
        int maxY = stage.getMaxPosition().getY() >> 4;

        // BitSets for light masks
        BitSet blockLightMask = new BitSet();
        BitSet skyLightMask = new BitSet();
        BitSet emptyBlockLightMask = new BitSet();
        BitSet emptySkyLightMask = new BitSet();

        // Arrays for light data
        int numSections = maxY - minY + 1;
        byte[][] blockLightArray = new byte[numSections][];
        byte[][] skyLightArray = new byte[numSections][];

        // Loop through y sections in the chunk
        for (int chunkY = minY; chunkY <= maxY; chunkY++) {
            BaseChunk baseChunk = BaseChunk.create();
            ConcurrentHashMap<BlockifyPosition, BlockData> chunkBlockChanges = blockChanges.get(chunk);

            // Check for null to avoid NPE
            if (chunkBlockChanges != null) {
                for (Map.Entry<BlockifyPosition, BlockData> entry : chunkBlockChanges.entrySet()) {
                    BlockifyPosition position = entry.getKey();
                    int blockY = position.getY();

                    // Only process blocks in the current section
                    if ((blockY >> 4) == chunkY) {
                        BlockData blockData = entry.getValue();
                        int x = position.getX() & 0xF;
                        int y = blockY & 0xF;
                        int z = position.getZ() & 0xF;

                        // Set block in chunk
                        baseChunk.set(x, y, z, blockDataToId.computeIfAbsent(blockData, bd ->
                                WrappedBlockState.getByString(PacketEvents.getAPI().getServerManager().getVersion().toClientVersion(), bd.getAsString(false)).getGlobalId()));
                    }
                }
            }

            chunks.add(baseChunk);

            int sectionIndex = chunkY - minY;

            blockLightArray[sectionIndex] = createFullyLitArray();
            skyLightArray[sectionIndex] = createFullyLitArray();

            // Update light masks
            blockLightMask.set(sectionIndex, true);
            skyLightMask.set(sectionIndex, true);
            emptyBlockLightMask.set(sectionIndex, false);
            emptySkyLightMask.set(sectionIndex, false);
        }

        // Create light data object
        LightData lightData = new LightData(false, blockLightMask, skyLightMask, emptyBlockLightMask, emptySkyLightMask, skyLightArray.length, blockLightArray.length, skyLightArray, blockLightArray);

        // Assuming tile entities are handled separately
        TileEntity[] tileEntities = new TileEntity[0];

        // Create column with the modified chunks
        Column column = new Column(chunk.x(), chunk.z(), false, chunks.toArray(new BaseChunk[0]), tileEntities);

        // Send updated chunk data to the player
        WrapperPlayServerChunkData packet = new WrapperPlayServerChunkData(column, lightData);
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
    }

    private byte[] createFullyLitArray() {
        byte[] fullyLitArray = new byte[2048];
        Arrays.fill(fullyLitArray, (byte) 0xFF);
        return fullyLitArray;
    }
}