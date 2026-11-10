package village.standards;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** A five-block square centered on the clicked backing block. */
public final class StandardGeometry {
    public static final int BLOCKS = 5;
    public static final int PIXELS = BLOCKS * 16;
    public static final double HALF = BLOCKS / 2.0;
    public static final double HALF_DEPTH = 1.0 / 32.0;
    private StandardGeometry() {}
    public static boolean horizontal(EnumFacing side) {
        return side != null && side.getAxis().isHorizontal();
    }
    public static List<BlockPos> displayCells(BlockPos anchor, EnumFacing side) {
        if (!horizontal(side)) throw new IllegalArgumentException("A standard requires a horizontal face");
        List<BlockPos> cells = new ArrayList<>(BLOCKS * BLOCKS);
        EnumFacing across = side.rotateY();
        for (int row = -2; row <= 2; row++) for (int column = -2; column <= 2; column++) {
            cells.add(anchor.up(row).offset(across, column));
        }
        return cells;
    }
    public static List<BlockPos> backingCells(BlockPos anchor, EnumFacing side) {
        return displayCells(anchor.offset(side.getOpposite()), side);
    }
}
