package codes.kooper.blockify.models;

import codes.kooper.blockify.types.BlockifyChunk;
import codes.kooper.blockify.types.BlockifyPosition;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.block.data.BlockData;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Getter
@Setter
public class View {
    private final ConcurrentHashMap<BlockifyChunk, ConcurrentHashMap<BlockifyPosition, BlockData>> blocks;
    private final Stage stage;
    private final String name;
    private boolean breakable, placeable;
    private Pattern pattern;

    /**
     * Constructor for the View class.
     *
     * @param name      The name of the view.
     * @param stage     The stage the view is in.
     * @param pattern   The pattern of the view.
     * @param breakable Whether the view is breakable or not.
     */
    public View(String name, Stage stage, Pattern pattern, boolean breakable) {
        this.name = name;
        this.blocks = new ConcurrentHashMap<>();
        this.stage = stage;
        this.breakable = breakable;
        this.pattern = pattern;
    }

    /**
     * Get the highest block at a given x and z coordinate.
     *
     * @param x The x coordinate.
     * @param z The z coordinate.
     * @return The highest block at the given x and z coordinate.
     */
    public BlockifyPosition getHighestBlock(int x, int z) {
        for (int y = stage.getMaxPosition().getY(); y >= stage.getMinPosition().getY(); y--) {
            BlockifyPosition position = new BlockifyPosition(x, y, z);
            if (hasBlock(position) && getBlock(position).getMaterial().isSolid()) {
                return position;
            }
        }
        return null;
    }

    /**
     * Get the lowest block at a given x and z coordinate.
     *
     * @param x The x coordinate.
     * @param z The z coordinate.
     * @return The lowest block at the given x and z coordinate.
     */
    public BlockifyPosition getLowestBlock(int x, int z) {
        for (int y = stage.getMinPosition().getY(); y <= stage.getMaxPosition().getY(); y++) {
            BlockifyPosition position = new BlockifyPosition(x, y, z);
            if (hasBlock(position) && getBlock(position).getMaterial().isSolid()) {
                return position;
            }
        }
        return null;
    }

    // Returns all blocks in the view
    public ConcurrentHashMap<BlockifyChunk, ConcurrentHashMap<BlockifyPosition, BlockData>> getBlocks() {
        return new ConcurrentHashMap<>(blocks);
    }

    /**
     * Remove a block from the view.
     *
     * @param position The block to remove.
     */
    public void removeBlock(BlockifyPosition position) {
        if (hasBlock(position)) {
            blocks.get(position.toBlockifyChunk()).remove(position);
            if (blocks.get(position.toBlockifyChunk()).isEmpty()) {
                blocks.remove(position.toBlockifyChunk());
            }
        }
    }

    /**
     * Remove a set of blocks from the view.
     * Call this method asynchronously if you are removing a large number of blocks.
     *
     * @param positions The set of blocks to remove.
     */
    public void removeBlocks(Set<BlockifyPosition> positions) {
        for (BlockifyPosition position : positions) {
            removeBlock(position);
        }
    }

    /**
     * Add a block to the view.
     *
     * @param position The block to add.
     */
    public void addBlock(BlockifyPosition position) {
        if (!blocks.containsKey(position.toBlockifyChunk())) {
            blocks.put(position.toBlockifyChunk(), new ConcurrentHashMap<>());
        }
        blocks.get(position.toBlockifyChunk()).put(position, pattern.getRandomBlockData());
    }

    /**
     * Add a set of blocks to the view.
     * Call this method asynchronously if you are adding a large number of blocks.
     *
     * @param positions The set of blocks to add.
     */
    public void addBlocks(Set<BlockifyPosition> positions) {
        for (BlockifyPosition position : positions) {
            addBlock(position);
        }
    }

    /**
     * Check if a block is in the view.
     *
     * @param position The position of the block.
     * @return Whether the block is in the view.
     */
    public boolean hasBlock(BlockifyPosition position) {
        return blocks.containsKey(position.toBlockifyChunk()) && blocks.get(position.toBlockifyChunk()).containsKey(position);
    }

    /**
     * Check if a set of blocks are in the view.
     * Call this method asynchronously if you are checking a large number of blocks.
     *
     * @param positions The set of blocks to check.
     * @return Whether the set of blocks are in the view.
     */
    public boolean hasBlocks(Set<BlockifyPosition> positions) {
        for (BlockifyPosition position : positions) {
            if (!hasBlock(position)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Get the block data at a given position.
     *
     * @param position The position of the block.
     * @return The block data at the given position.
     */
    public BlockData getBlock(BlockifyPosition position) {
        return blocks.get(position.toBlockifyChunk()).get(position);
    }


    /**
     * Check if a chunk is in the view.
     *
     * @param x The x coordinate of the chunk.
     * @param z The z coordinate of the chunk.
     * @return Whether the chunk is in the view.
     */
    public boolean hasChunk(int x, int z) {
        return blocks.containsKey(new BlockifyChunk(x, z));
    }

    /**
     * Set positions to a given block data.
     * Call this method asynchronously if you are setting a large number of blocks.
     *
     * @param positions The set of positions to set.
     * @param blockData The block data.
     */
    public void setBlocks(Set<BlockifyPosition> positions, BlockData blockData) {
        for (BlockifyPosition position : positions) {
            setBlock(position, blockData);
        }
    }

    /**
     * Set a position to a given block data.
     *
     * @param position  The position of the block.
     * @param blockData The block data.
     */
    public void setBlock(BlockifyPosition position, BlockData blockData) {
        if (hasBlock(position)) {
            blocks.get(position.toBlockifyChunk()).put(position, blockData);
        }
    }

    /**
     * Reset a block to a random block data from the pattern.
     *
     * @param position The position of the block.
     */
    public void resetBlock(BlockifyPosition position) {
        if (hasBlock(position)) {
            blocks.get(position.toBlockifyChunk()).put(position, pattern.getRandomBlockData());
        }
    }

    /**
     * Reset a set of blocks to random block data from the pattern.
     * Call this method asynchronously if you are resetting a large number of blocks.
     *
     * @param positions The set of blocks to reset.
     */
    public void resetBlocks(Set<BlockifyPosition> positions) {
        for (BlockifyPosition position : positions) {
            resetBlock(position);
        }
    }

    /**
     * Reset all blocks in the view to random block data from the pattern.
     * Call this method asynchronously.
     */
    public void resetViewBlocks() {
        for (BlockifyChunk chunk : blocks.keySet()) {
            for (BlockifyPosition position : blocks.get(chunk).keySet()) {
                blocks.get(chunk).put(position, pattern.getRandomBlockData());
            }
        }
    }

    /**
     * Changes the pattern of the view.
     *
     * @param pattern The new pattern.
     */
    public void changePattern(Pattern pattern) {
        this.pattern = pattern;
    }
}