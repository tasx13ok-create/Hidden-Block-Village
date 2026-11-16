package village.dungeonguard;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;

/** Inclusive block bounds. Each arena has a separate dimension and baseline. */
public final class GuardRegion {
    public String name;
    public int dimension, minX, minY, minZ, maxX, maxY, maxZ;

    public GuardRegion() { }
    public GuardRegion(String name, int dimension, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.name = name; this.dimension = dimension;
        minX = Math.min(x1, x2); minY = Math.min(y1, y2); minZ = Math.min(z1, z2);
        maxX = Math.max(x1, x2); maxY = Math.max(y1, y2); maxZ = Math.max(z1, z2);
    }
    public boolean contains(BlockPos p) { return contains(p.getX(), p.getY(), p.getZ()); }
    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }
    public boolean intersects(AxisAlignedBB b) {
        return b.maxX > minX && b.minX < maxX + 1.0 && b.maxY > minY && b.minY < maxY + 1.0
                && b.maxZ > minZ && b.minZ < maxZ + 1.0;
    }
    public long volume() { return ((long) maxX - minX + 1) * ((long) maxY - minY + 1) * ((long) maxZ - minZ + 1); }
    public boolean overlaps(GuardRegion b) {
        return dimension == b.dimension && minX <= b.maxX && maxX >= b.minX && minY <= b.maxY
                && maxY >= b.minY && minZ <= b.maxZ && maxZ >= b.minZ;
    }
    public void validate() {
        if (name == null || !name.matches("[a-z0-9][a-z0-9_-]{0,47}"))
            throw new IllegalArgumentException("Dungeon names must use 1-48 lowercase letters, digits, underscores or hyphens.");
        if (minX > maxX || minY > maxY || minZ > maxZ || minY < 0 || maxY > 255
                || minX < -29999984 || maxX > 29999983 || minZ < -29999984 || maxZ > 29999983)
            throw new IllegalArgumentException("Invalid dungeon bounds: " + name);
    }
}
