package codes.kooper.blockify.utils;

import codes.kooper.blockify.types.BlockifyPosition;
import org.bukkit.Location;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
     * Get all the locations between two locations.
     * Call this method asynchronously if you are going to be getting a large amount of locations.
     *
     * @param loc1 The first location.
     * @param loc2 The second location.
     * @return A list of all the locations between the two locations.
     */
    public static List<Location> getLocationsBetween(Location loc1, Location loc2) {
        List<Location> locations = new ArrayList<>();
        int minX = Math.min(loc1.getBlockX(), loc2.getBlockX());
        int minY = Math.min(loc1.getBlockY(), loc2.getBlockY());
        int minZ = Math.min(loc1.getBlockZ(), loc2.getBlockZ());
        int maxX = Math.max(loc1.getBlockX(), loc2.getBlockX());
        int maxY = Math.max(loc1.getBlockY(), loc2.getBlockY());
        int maxZ = Math.max(loc1.getBlockZ(), loc2.getBlockZ());
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    locations.add(new Location(loc1.getWorld(), x, y, z));
                }
            }
        }
        return locations;
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