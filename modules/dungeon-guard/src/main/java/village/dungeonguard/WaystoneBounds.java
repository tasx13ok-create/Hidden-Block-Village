package village.dungeonguard;

import village.dungeonguard.GuardRegion;

public final class WaystoneBounds {
    public static final int WIDTH = 300;
    public static final int HEIGHT = 250;
    public static final int LENGTH = 300;
    public static final int VOLUME = 22500000;

    private WaystoneBounds() {
    }

    public static GuardRegion around(String name, int dimension, int x, int y, int z) {
        if (y < 1 || y > 254 || x < -29999834 || x > 29999834 || z < -29999834 || z > 29999834) {
            throw new IllegalArgumentException("Waystone is too close to the world limit for a 300 x 250 x 300 dungeon.");
        }
        int bottom = Math.max(0, Math.min(6, y - 125));
        GuardRegion region = new GuardRegion(name, dimension, x - 150, bottom, z - 150, x + 150 - 1, bottom + 250 - 1, z + 150 - 1);
        region.validate();
        return region;
    }
}

