package codes.kooper.blockify.types;

import org.bukkit.Chunk;

public record BlockifyChunk(int x, int z) {

    @Override
    public String toString() {
        return "BlockifyChunk{x=" + x + ", z=" + z + "}";
    }

    @Override
    public int hashCode() {
        return x * 31 + z;
    }

    public long getChunkKey() {
        return Chunk.getChunkKey(x, z);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof BlockifyChunk other)) return false;
        return this.x == other.x && this.z == other.z;
    }
}
