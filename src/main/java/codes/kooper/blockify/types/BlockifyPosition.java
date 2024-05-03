package codes.kooper.blockify.types;

import io.papermc.paper.math.BlockPosition;
import io.papermc.paper.math.Position;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.Vector;

import java.util.Set;
import java.util.stream.Collectors;

@Getter
@Setter
public class BlockifyPosition {

    private int x, y, z;

    public BlockifyPosition(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static BlockifyPosition fromLocation(Location location) {
        return new BlockifyPosition(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public static Set<BlockifyPosition> fromLocations(Set<Location> locations) {
        return locations.stream().map(BlockifyPosition::fromLocation).collect(Collectors.toSet());
    }

    public static Set<BlockifyPosition> fromPositions(Set<BlockPosition> blockPositions) {
        return blockPositions.stream().map(BlockifyPosition::fromPosition).collect(Collectors.toSet());
    }

    public static BlockifyPosition fromPosition(Position position) {
        return new BlockifyPosition(position.blockX(), position.blockY(), position.blockZ());
    }

    public BlockPosition toBlockPosition() {
        return Position.block(x, y, z);
    }

    public BlockifyChunk toBlockifyChunk() {
        return new BlockifyChunk(x >> 4, z >> 4);
    }

    public Location toLocation(World world) {
        return new Location(world, x, y, z);
    }

    public Position toPosition() {
        return Position.block(x, y, z);
    }

    public Vector toVector() {
        return new Vector(x, y, z);
    }

    public double distanceSquared(BlockifyPosition other) {
        return Math.pow(x - other.x, 2) + Math.pow(y - other.y, 2) + Math.pow(z - other.z, 2);
    }

    public BlockState getBlockState(World world, BlockData blockData) {
        BlockState state = toLocation(world).getBlock().getState().copy();
        state.setBlockData(blockData);
        state.setType(blockData.getMaterial());
        return state;
    }

    public double distance(BlockifyPosition other) {
        return Math.sqrt(distanceSquared(other));
    }

    @Override
    public String toString() {
        return "BlockifyPosition{x=" + x + ", y=" + y + ", z=" + z + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof BlockifyPosition other)) return false;
        return this.x == other.x && this.y == other.y && this.z == other.z;
    }

    @Override
    public int hashCode() {
        int result = x;
        result = 31 * result + y;
        result = 31 * result + z;
        return result;
    }
}
