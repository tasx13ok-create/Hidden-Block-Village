package village.dungeonguard;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.command.CommandException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;

/** Per-session operator selections, shared by typed corners and the vanilla-stick wand. */
public final class GuardSelections {
    private static final Map<UUID, Selection> SELECTIONS = new HashMap<UUID, Selection>();
    private GuardSelections() { }

    public static final class Selection {
        public final int dimension;
        public final BlockPos first, second;
        private Selection(int dimension, BlockPos first, BlockPos second) {
            this.dimension = dimension;
            this.first = first == null ? null : first.toImmutable();
            this.second = second == null ? null : second.toImmutable();
        }
        public boolean complete() { return first != null && second != null; }
        public GuardRegion region(String name) {
            if (!complete()) throw new IllegalStateException("Select both opposite corners first.");
            return new GuardRegion(name, dimension, first.getX(), first.getY(), first.getZ(), second.getX(), second.getY(), second.getZ());
        }
    }

    public static void clear() { SELECTIONS.clear(); }
    public static void forget(UUID player) { SELECTIONS.remove(player); }
    public static Selection get(EntityPlayerMP player) {
        if (player == null) return null;
        Selection selection = SELECTIONS.get(player.getUniqueID());
        return selection != null && selection.dimension == player.dimension ? selection : null;
    }
    public static Selection select(EntityPlayerMP player, BlockPos position, boolean first) throws CommandException {
        if (!OperatorAccess.isOperator(player)) throw new CommandException("Only connected server operators can select dungeon corners.");
        if (position == null) throw new CommandException("Point at a block to select that corner.");
        GuardRegion point = new GuardRegion("selection", player.dimension, position.getX(), position.getY(), position.getZ(),
                position.getX(), position.getY(), position.getZ());
        try { point.validate(); } catch (IllegalArgumentException invalid) { throw new CommandException("Select a block inside the world's building limits."); }
        Selection previous = get(player);
        Selection next = new Selection(player.dimension, first ? position : previous == null ? null : previous.first,
                first ? previous == null ? null : previous.second : position);
        SELECTIONS.put(player.getUniqueID(), next);
        return next;
    }
    public static Selection require(EntityPlayerMP player) throws CommandException {
        Selection selection = get(player);
        if (selection == null || !selection.complete())
            throw new CommandException("Select two opposite corners first: /dungeon wand, then left-click one corner and right-click the other.");
        return selection;
    }
    public static String describe(EntityPlayerMP player) {
        Selection selection = get(player);
        if (selection == null) return "No corners selected. Get a wand to begin.";
        if (!selection.complete()) return "Corner " + (selection.first != null ? "1" : "2") + " selected; choose the other corner.";
        GuardRegion bounds = selection.region("selection");
        return "Selection: " + coordinates(selection.first) + " to " + coordinates(selection.second)
                + " (" + bounds.volume() + " blocks, dimension " + selection.dimension + ").";
    }
    public static String coordinates(BlockPos pos) { return pos.getX() + " " + pos.getY() + " " + pos.getZ(); }
}
