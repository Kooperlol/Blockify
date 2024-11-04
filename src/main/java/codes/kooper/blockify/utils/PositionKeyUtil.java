package codes.kooper.blockify.utils;

import codes.kooper.blockify.types.BlockifyPosition;

public class PositionKeyUtil {
    // Bit masks and shifts
    private static final long X_MASK = 0x3FFFFFFL;
    private static final long Y_MASK = 0xFFFL;
    private static final long Z_MASK = 0x3FFFFFFL;
    private static final int X_SHIFT = 38;
    private static final int Y_SHIFT = 26;

    public static long getPositionKey(int x, int y, int z) {
        return ((x & X_MASK) << X_SHIFT)
                | ((y & Y_MASK) << Y_SHIFT)
                | (z & Z_MASK);
    }

    public static int getX(long positionKey) {
        return (int) ((positionKey >> X_SHIFT) & X_MASK);
    }

    public static int getY(long positionKey) {
        return (int) ((positionKey >> Y_SHIFT) & Y_MASK);
    }

    public static int getZ(long positionKey) {
        return (int) (positionKey & Z_MASK);
    }

    public static BlockifyPosition toBlockifyPosition(long positionKey) {
        return new BlockifyPosition(getX(positionKey), getY(positionKey), getZ(positionKey));

    }
}
