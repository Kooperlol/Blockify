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

    /**
     * Create a new BlockifyPosition
     *
     * @param x The x coordinate
     * @param y The y coordinate
     * @param z The z coordinate
     */
    public BlockifyPosition(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * Create a new BlockifyPosition
     *
     * @param location The location to create the BlockifyPosition from
     */
    public static BlockifyPosition fromLocation(Location location) {
        return new BlockifyPosition(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    /**
     * Create a new BlockifyPosition
     *
     * @param vector The vector to create the BlockifyPosition from
     */
    public static BlockifyPosition fromVector(Vector vector) {
        return new BlockifyPosition(vector.getBlockX(), vector.getBlockY(), vector.getBlockZ());
    }

    /**
     * Creates new BlockifyPositions
     *
     * @param locations The locations to create the BlockifyPosition from
     */
    public static Set<BlockifyPosition> fromLocations(Set<Location> locations) {
        return locations.stream().map(BlockifyPosition::fromLocation).collect(Collectors.toSet());
    }

    /**
     * Creates new BlockifyPositions
     *
     * @param blockPositions The block positions to create the BlockifyPosition from
     */
    public static Set<BlockifyPosition> fromPositions(Set<BlockPosition> blockPositions) {
        return blockPositions.stream().map(BlockifyPosition::fromPosition).collect(Collectors.toSet());
    }

    /**
     * Create a new BlockifyPosition
     *
     * @param position The position to create the BlockifyPosition from
     */
    public static BlockifyPosition fromPosition(Position position) {
        return new BlockifyPosition(position.blockX(), position.blockY(), position.blockZ());
    }

    /**
     * Converts the BlockifyPosition to a BlockPosition
     *
     * @return The BlockPosition representation of the BlockifyPosition
     */
    public BlockPosition toBlockPosition() {
        return Position.block(x, y, z);
    }

    /**
     * Converts the BlockifyPosition to a BlockifyChunk
     *
     * @return The BlockifyChunk at the BlockifyPosition.
     */
    public BlockifyChunk toBlockifyChunk() {
        return new BlockifyChunk(x >> 4, z >> 4);
    }

    /**
     * Converts the BlockifyPosition to a Location
     *
     * @param world The world to convert the BlockifyPosition to
     * @return The Location representation of the BlockifyPosition
     */
    public Location toLocation(World world) {
        return new Location(world, x, y, z);
    }

    /**
     * Converts the BlockifyPosition to a Position
     *
     * @return The Position representation of the BlockifyPosition
     */
    public Position toPosition() {
        return Position.block(x, y, z);
    }

    /**
     * Converts the BlockifyPosition to a Vector
     *
     * @return The Vector representation of the BlockifyPosition
     */
    public Vector toVector() {
        return new Vector(x, y, z);
    }

    /**
     * Get the distance squared between two BlockifyPositions
     *
     * @param other The other BlockifyPosition
     * @return The distance squared between the two BlockifyPositions
     */
    public double distanceSquared(BlockifyPosition other) {
        return Math.pow(x - other.x, 2) + Math.pow(y - other.y, 2) + Math.pow(z - other.z, 2);
    }

    /**
     * Get the block state at the BlockifyPosition
     *
     * @param world The world to get the block state from
     * @return The block state at the BlockifyPosition
     */
    public BlockState getBlockState(World world, BlockData blockData) {
        BlockState state = toLocation(world).getBlock().getState().copy();
        state.setBlockData(blockData);
        state.setType(blockData.getMaterial());
        return state;
    }

    /**
     * Get the distance between two BlockifyPositions
     *
     * @param other The other BlockifyPosition
     * @return The distance between the two BlockifyPositions
     */
    public double distance(BlockifyPosition other) {
        return Math.sqrt(distanceSquared(other));
    }

    /**
     * Get the string representation of the BlockifyPosition
     *
     * @return The string representation of the BlockifyPosition
     */
    @Override
    public String toString() {
        return "BlockifyPosition{x=" + x + ", y=" + y + ", z=" + z + "}";
    }

    /**
     * Check if the BlockifyPosition is equal to another object
     *
     * @param o The object to compare to
     * @return Whether the BlockifyPosition is equal to the object
     */
    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof BlockifyPosition other)) return false;
        return this.x == other.x && this.y == other.y && this.z == other.z;
    }

    /**
     * Get the hash code of the BlockifyPosition
     *
     * @return The hash code of the BlockifyPosition
     */
    @Override
    public int hashCode() {
        int result = x;
        result = 31 * result + y;
        result = 31 * result + z;
        return result;
    }
}
