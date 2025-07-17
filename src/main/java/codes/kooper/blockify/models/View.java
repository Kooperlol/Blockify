package codes.kooper.blockify.models;

import codes.kooper.blockify.Blockify;
import codes.kooper.blockify.types.BlockifyChunk;
import codes.kooper.blockify.types.BlockifyPosition;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Getter
@Setter
public class View {
    @Getter
    private ConcurrentHashMap<BlockifyChunk, ConcurrentHashMap<BlockifyPosition, BlockData>> blocks;
    private Stage stage;
    private String name;
    private int zIndex;
    private boolean breakable, placeable;
    private Pattern pattern;

    public View(String name, Stage stage, Pattern pattern, boolean breakable) {
        this.name = name;
        this.blocks = new ConcurrentHashMap<>();
        this.stage = stage;
        this.breakable = breakable;
        this.pattern = pattern;
        this.zIndex = 0;
    }

    public BlockifyPosition getHighestBlock(int x, int z) {
        for (int y = stage.getMaxPosition().getY(); y >= stage.getMinPosition().getY(); y--) {
            BlockifyPosition position = new BlockifyPosition(x, y, z);
            if (hasBlock(position) && getBlock(position).getMaterial().isSolid()) {
                return position;
            }
        }
        return null;
    }

    public BlockifyPosition getLowestBlock(int x, int z) {
        for (int y = stage.getMinPosition().getY(); y <= stage.getMaxPosition().getY(); y++) {
            BlockifyPosition position = new BlockifyPosition(x, y, z);
            if (hasBlock(position) && getBlock(position).getMaterial().isSolid()) {
                return position;
            }
        }
        return null;
    }

    public void removeBlock(BlockifyPosition position) {
        BlockifyChunk chunk = position.toBlockifyChunk();
        ConcurrentHashMap<BlockifyPosition, BlockData> chunkMap = blocks.get(chunk);
        if (chunkMap != null && chunkMap.remove(position) != null && chunkMap.isEmpty()) {
            blocks.remove(chunk);
        }

        // Also update each viewer's cache: data = null means remove the block
        for (Player viewer : stage.getAudience().getOnlinePlayers()) {
            Blockify.getInstance().getBlockChangeManager().applyBlockChange(viewer, chunk, position, null, this.name);
        }
    }

    public void removeBlocks(Set<BlockifyPosition> positions) {
        for (BlockifyPosition position : positions) {
            removeBlock(position);
        }
    }

    public void removeAllBlocks() {
        // Removing all blocks in bulk, update caches accordingly
        for (BlockifyChunk chunk : blocks.keySet()) {
            ConcurrentHashMap<BlockifyPosition, BlockData> chunkMap = blocks.get(chunk);
            if (chunkMap == null) continue;
            for (BlockifyPosition position : chunkMap.keySet()) {
                // Apply removal to each viewer
                for (Player viewer : stage.getAudience().getOnlinePlayers()) {
                    Blockify.getInstance().getBlockChangeManager().applyBlockChange(viewer, chunk, position, null, this.name);
                }
            }
        }
        blocks.clear();
    }

    public void addBlock(BlockifyPosition position) {
        BlockData newData = pattern.getRandomBlockData();
        BlockifyChunk chunk = position.toBlockifyChunk();
        blocks.computeIfAbsent(chunk, c -> new ConcurrentHashMap<>()).put(position, newData);

        // Update each viewer's cache with the new block
        for (Player viewer : stage.getAudience().getOnlinePlayers()) {
            Blockify.getInstance().getBlockChangeManager().applyBlockChange(viewer, chunk, position, newData, this.name);
        }
    }

    public void addBlocks(Set<BlockifyPosition> positions) {
        for (BlockifyPosition position : positions) {
            addBlock(position);
        }
    }

    public boolean hasBlock(BlockifyPosition position) {
        ConcurrentHashMap<BlockifyPosition, BlockData> chunkMap = blocks.get(position.toBlockifyChunk());
        return chunkMap != null && chunkMap.containsKey(position);
    }

    public boolean hasBlocks(Set<BlockifyPosition> positions) {
        for (BlockifyPosition position : positions) {
            if (!hasBlock(position)) {
                return false;
            }
        }
        return true;
    }

    public BlockData getBlock(BlockifyPosition position) {
        ConcurrentHashMap<BlockifyPosition, BlockData> chunkMap = blocks.get(position.toBlockifyChunk());
        return (chunkMap == null) ? null : chunkMap.get(position);
    }

    public boolean hasChunk(int x, int z) {
        return blocks.containsKey(new BlockifyChunk(x, z));
    }

    public void setBlocks(Set<BlockifyPosition> positions, BlockData blockData) {
        for (BlockifyPosition position : positions) {
            setBlock(position, blockData);
        }
    }

    public void setBlock(BlockifyPosition position, BlockData blockData) {
        if (hasBlock(position)) {
            BlockifyChunk chunk = position.toBlockifyChunk();
            blocks.get(chunk).put(position, blockData);

            // Update each viewer's cache with the updated block
            for (Player viewer : stage.getAudience().getOnlinePlayers()) {
                Blockify.getInstance().getBlockChangeManager().applyBlockChange(viewer, chunk, position, blockData, this.name);
            }
        }
    }

    public void resetBlock(BlockifyPosition position) {
        if (hasBlock(position)) {
            BlockData newData = pattern.getRandomBlockData();
            BlockifyChunk chunk = position.toBlockifyChunk();
            blocks.get(chunk).put(position, newData);

            // Update viewers
            for (Player viewer : stage.getAudience().getOnlinePlayers()) {
                Blockify.getInstance().getBlockChangeManager().applyBlockChange(viewer, chunk, position, newData, this.name);
            }
        }
    }

    public void resetBlocks(Set<BlockifyPosition> positions) {
        for (BlockifyPosition position : positions) {
            resetBlock(position);
        }
    }

    public void resetViewBlocks() {
        for (BlockifyChunk chunk : blocks.keySet()) {
            ConcurrentHashMap<BlockifyPosition, BlockData> chunkMap = blocks.get(chunk);
            for (BlockifyPosition position : chunkMap.keySet()) {
                BlockData newData = pattern.getRandomBlockData();
                chunkMap.put(position, newData);
                // Update viewers
                for (Player viewer : stage.getAudience().getOnlinePlayers()) {
                    Blockify.getInstance().getBlockChangeManager().applyBlockChange(viewer, chunk, position, newData, this.name);
                }
            }
        }
    }

    public void changePattern(Pattern pattern) {
        this.pattern = pattern;
    }
}