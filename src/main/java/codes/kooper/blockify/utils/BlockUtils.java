package codes.kooper.blockify.utils;

import codes.kooper.blockify.types.BlockifyPosition;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;

import java.util.HashSet;
import java.util.Set;

public class BlockUtils {

    public static Set<BlockifyPosition> getBlocksBetween(BlockifyPosition pos1, BlockifyPosition pos2) {
        Set<BlockifyPosition> blocks = new HashSet<>();
        for (int x = Math.min(pos1.getX(), pos2.getX()); x <= Math.max(pos1.getX(), pos2.getX()); x++) {
            for (int y = Math.min(pos1.getY(), pos2.getY()); y <= Math.max(pos1.getY(), pos2.getY()); y++) {
                for (int z = Math.min(pos1.getZ(), pos2.getZ()); z <= Math.max(pos1.getZ(), pos2.getZ()); z++) {
                    blocks.add(new BlockifyPosition(x, y, z));
                }
            }
        }
        return blocks;
    }

    public static BlockData setAge(BlockData blockData, int age) {
        Ageable ageable = (Ageable) blockData;
        ageable.setAge(age);
        return ageable;
    }

}