package codes.kooper.blockify.types;

public record BlockifyChunk(int x, int z) {

    public String toString() {
        return "BlockifyChunk{x=" + x + ", z=" + z + "}";
    }

    public int hashCode() {
        return 31 * x + z;
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        BlockifyChunk other = (BlockifyChunk) obj;
        return x == other.x && z == other.z;
    }

}
