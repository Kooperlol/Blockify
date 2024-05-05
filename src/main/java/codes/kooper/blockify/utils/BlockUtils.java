package codes.kooper.blockify.utils;

import codes.kooper.blockify.types.BlockifyPosition;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;

import java.util.HashSet;
import java.util.Set;

public class BlockUtils {

    /**
     * Get all the blocks between two positions.
     * Call this method asynchronously if you are going to be getting a large amount of blocks.
     *
     * @param pos1 The first position.
     * @param pos2 The second position.
     * @return A set of all the blocks between the two positions.
     */
    public static Set<BlockifyPosition> getBlocksBetween(BlockifyPosition pos1, BlockifyPosition pos2) {
        Set<BlockifyPosition> positions = new HashSet<>();
        int minX = Math.min(pos1.getX(), pos2.getX());
        int minY = Math.min(pos1.getY(), pos2.getY());
        int minZ = Math.min(pos1.getZ(), pos2.getZ());
        int maxX = Math.max(pos1.getX(), pos2.getX());
        int maxY = Math.max(pos1.getY(), pos2.getY());
        int maxZ = Math.max(pos1.getZ(), pos2.getZ());
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    positions.add(new BlockifyPosition(x, y, z));
                }
            }
        }
        return positions;
    }

    /**
     * Set the age of a block.
     *
     * @param blockData The block data.
     * @param age The age to set.
     * @return The block data with the age set.
     */
    public static BlockData setAge(BlockData blockData, int age) {
        Ageable ageable = (Ageable) blockData;
        ageable.setAge(age);
        return ageable;
    }

}