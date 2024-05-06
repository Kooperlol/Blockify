package codes.kooper.blockify.models;

import codes.kooper.blockify.Blockify;
import codes.kooper.blockify.types.BlockifyChunk;
import codes.kooper.blockify.types.BlockifyPosition;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Getter
@Setter
public class Stage {
    private final String name;
    private final World world;
    private BlockifyPosition maxPosition, minPosition;
    private final Set<View> views;
    private int chunksPerTick;
    private final Audience audience;

    /**
     * Create a new stage.
     *
     * @param name     Name of the stage
     * @param world    World the stage is in
     * @param pos1     First position of the stage
     * @param pos2     Second position of the stage
     * @param audience Audience to send blocks to
     */
    public Stage(String name, World world, BlockifyPosition pos1, BlockifyPosition pos2, Audience audience) {
        this.name = name;
        this.world = world;
        this.maxPosition = new BlockifyPosition(Math.max(pos1.getX(), pos2.getX()), Math.max(pos1.getY(), pos2.getY()), Math.max(pos1.getZ(), pos2.getZ()));
        this.minPosition = new BlockifyPosition(Math.min(pos1.getX(), pos2.getX()), Math.min(pos1.getY(), pos2.getY()), Math.min(pos1.getZ(), pos2.getZ()));
        this.views = new HashSet<>();
        this.audience = audience;
        this.chunksPerTick = 1;
    }


    /**
     * Check if a location is within the stage.
     *
     * @param location Location to check
     * @return True if the location is within the stage
     */
    public boolean isLocationWithin(Location location) {
        return location.getWorld().equals(world) && location.getBlockX() >= minPosition.getX() && location.getBlockX() <= maxPosition.getX() && location.getBlockY() >= minPosition.getY() && location.getBlockY() <= maxPosition.getY() && location.getBlockZ() >= minPosition.getZ() && location.getBlockZ() <= maxPosition.getZ();
    }

    /**
     * Send blocks to the audience. Should be called asynchronously.
     */
    public void sendBlocksToAudience() {
        ConcurrentHashMap<BlockifyChunk, ConcurrentHashMap<BlockifyPosition, BlockData>> blocks = new ConcurrentHashMap<>();
        for (View view : views) {
            blocks.putAll(view.getBlocks());
        }
        Blockify.getInstance().getBlockChangeManager().sendBlockChanges(this, audience, blocks);
    }

    /**
     * Refresh blocks to the audience. Should be called after modifying blocks.
     * Should be called asynchronously.
     *
     * @param blocks Blocks to refresh to the audience.
     */
    public void refreshBlocksToAudience(Set<BlockifyPosition> blocks) {
        ConcurrentHashMap<BlockifyChunk, ConcurrentHashMap<BlockifyPosition, BlockData>> blockChanges = new ConcurrentHashMap<>();
        for (View view : views) {
            for (BlockifyPosition position : blocks) {
                if (view.hasBlock(position)) {
                    if (blockChanges.containsKey(position.toBlockifyChunk())) {
                        blockChanges.get(position.toBlockifyChunk()).put(position, view.getBlock(position));
                    } else {
                        ConcurrentHashMap<BlockifyPosition, BlockData> blockData = new ConcurrentHashMap<>();
                        blockData.put(position, view.getBlock(position));
                        blockChanges.put(position.toBlockifyChunk(), blockData);
                    }
                }
            }
        }
        Blockify.getInstance().getBlockChangeManager().sendBlockChanges(this, audience, blockChanges);
    }

    /**
     * Add a view to the stage.
     *
     * @param view View to add
     */
    public void addView(View view) {
        if (views.stream().anyMatch(v -> v.getName().equalsIgnoreCase(view.getName()))) {
            Blockify.getInstance().getLogger().warning("View with name " + view.getName() + " already exists in stage " + name + "!");
            return;
        }
        views.add(view);
    }

    /**
     * Remove a view from the stage.
     *
     * @param view View to remove
     */
    public void removeView(View view) {
        views.remove(view);
    }

    /**
     * Get a view by name.
     *
     * @param name Name of the view
     * @return View or null if not found
     */
    public View getView(String name) {
        for (View view : views) {
            if (view.getName().equalsIgnoreCase(name)) {
                return view;
            }
        }
        return null;
    }

    /**
     * Get all chunks that are being used by this stage.
     * If a lot of chunks are present, it is recommended to use this method asynchronously.
     *
     * @return Set of chunks
     */
    public Set<BlockifyChunk> getChunks() {
        Set<BlockifyChunk> chunks = new HashSet<>();
        for (int x = minPosition.getX() >> 4; x <= maxPosition.getX() >> 4; x++) {
            for (int z = minPosition.getZ() >> 4; z <= maxPosition.getZ() >> 4; z++) {
                chunks.add(new BlockifyChunk(x, z));
            }
        }
        return chunks;
    }
}