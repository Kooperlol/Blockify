package codes.kooper.blockify.types;

import org.bukkit.Chunk;

/**
 * Simple class to represent a chunk.
 * @param x The x coordinate of the chunk.
 * @param z The z coordinate of the chunk.
 */
public record BlockifyChunk(int x, int z) {

    /**
     * Stringifies the chunk data.
     *
     * @return The string representation of the chunk.
     */
    @Override
    public String toString() {
        return "BlockifyChunk{x=" + x + ", z=" + z + "}";
    }

    /**
     * Calculate the hash code based on the x and z coordinates.
     *
     * @return The hash code.
     */
    @Override
    public int hashCode() {
        return x * 31 + z;
    }

    /**
     * Get the chunk key.
     *
     * @return The chunk key.
     */
    public long getChunkKey() {
        return Chunk.getChunkKey(x, z);
    }

    /**
     * Check if the object is equal to this chunk.
     *
     * @param o The object to check.
     * @return True if the object is equal to this chunk, false otherwise.
     */
    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof BlockifyChunk other)) return false;
        return this.x == other.x && this.z == other.z;
    }
}
