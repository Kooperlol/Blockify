package codes.kooper.blockify.utils;

import codes.kooper.blockify.types.BlockifyPosition;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;

import java.util.HashSet;
import java.util.Set;

public class BlockUtils {

    public static Set<BlockifyPosition> getBlocksBetween(BlockifyPosition pos1, BlockifyPosition pos2) {
        Set<BlockifyPosition> blocks = new HashSet<>();
        int minX = Math.min(pos1.getX(), pos2.getX());
        int maxX = Math.max(pos1.getX(), pos2.getX());
        int minY = Math.min(pos1.getY(), pos2.getY());
        int maxY = Math.max(pos1.getY(), pos2.getY());
        int minZ = Math.min(pos1.getZ(), pos2.getZ());
        int maxZ = Math.max(pos1.getZ(), pos2.getZ());
        for (int x = minX; x <= maxX && x != pos2.getX(); x++) {
            for (int y = minY; y <= maxY && y != pos2.getY(); y++) {
                for (int z = minZ; z <= maxZ && z != pos2.getZ(); z++) {
                    blocks.add(new BlockifyPosition(x, y, z));
                }
            }
        }
        System.out.println(blocks.size());
        return blocks;
    }

    public static BlockData setAge(BlockData blockData, int age) {
        Ageable ageable = (Ageable) blockData;
        ageable.setAge(age);
        return ageable;
    }

}