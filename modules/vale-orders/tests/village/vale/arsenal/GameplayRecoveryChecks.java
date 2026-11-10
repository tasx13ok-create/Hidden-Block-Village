package village.vale.arsenal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScorePlayerTeam;

/** Persistent state regressions; native recipe/actor acceptance runs in the separate QA mod. */
public final class GameplayRecoveryChecks {
    private static int checks;
    private static void check(boolean condition, String why) {
        checks++;
        if (!condition) throw new AssertionError(why);
    }
    private static NBTTagCompound reload(NBTTagCompound value) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CompressedStreamTools.writeCompressed(value, out);
        return CompressedStreamTools.readCompressed(new ByteArrayInputStream(out.toByteArray()));
    }
    public static void main(String[] args) throws Exception {
        NBTTagCompound state = new NBTTagCompound();
        ArsenalSpec used = ArsenalSpec.OVERDRIVE_BLADE;
        ArsenalSpec other = ArsenalSpec.WARD_FAN;
        ArsenalCooldowns.record(state, used, 1000);
        state.setBoolean("busy", true);
        state.setLong("guardUntil", 10000);
        state = reload(state);
        ArsenalCooldowns.clearTransient(state);
        check(!state.hasKey("busy") && !state.hasKey("guardUntil"), "Transient combat mode survived reconnect cleanup");
        check(ArsenalCooldowns.remaining(state, used, 1012) == 148, "Specialist cooldown collapsed to shared recovery after save/reload");
        check(ArsenalCooldowns.remaining(state, other, 1011) == 1, "Shared recovery was lost");
        check(ArsenalCooldowns.remaining(state, other, 1012) == 0, "Other weapons inherited the long cooldown");
        check(ArsenalCooldowns.remaining(state, used, 1159) == 1, "Long cooldown expired early");
        check(ArsenalCooldowns.remaining(state, used, 1160) == 0, "Long cooldown did not expire at its deadline");
        ArsenalCooldowns.record(state, other, 1020);
        check(ArsenalCooldowns.readyAt(state, used) == 1160, "Using another specialist erased the first deadline");
        check(ArsenalCooldowns.readyAt(state, other) == 1100, "Second specialist got the wrong deadline");
        NBTTagCompound legacy = new NBTTagCompound();legacy.setLong("ready", 55);
        check(ArsenalCooldowns.remaining(legacy, used, 50) == 5, "Legacy shared-only save is not compatible");
        ArsenalCooldowns.record(legacy, used, Long.MAX_VALUE - 1);
        check(ArsenalCooldowns.readyAt(legacy, used) == Long.MAX_VALUE, "Cooldown deadline overflowed into readiness");

        Scoreboard board = new Scoreboard();
        UUID owner = UUID.fromString("12345678-1234-1234-1234-123456789abc");
        ScorePlayerTeam first = ScrollSupport.ownParty(board, owner, "RecoveryOwner");
        check(first != null && !first.getAllowFriendlyFire(), "Owned party was not created safely");
        board.addPlayerToTeam("RecoveryGuest", first.getName());
        board.removePlayerFromTeams("RecoveryOwner");
        ScorePlayerTeam again = ScrollSupport.ownParty(board, owner, "RecoveryOwner");
        check(again == first && first.getMembershipCollection().contains("RecoveryOwner"), "Owner could not rejoin the existing expedition");
        check(first.getMembershipCollection().contains("RecoveryGuest"), "Rejoin replaced the expedition and lost its members");
        check(ScrollSupport.ownParty(board, owner, "RecoveryOwner") == first && first.getMembershipCollection().size() == 2, "Repeated own-party use duplicated membership");
        board.removePlayerFromTeams("RecoveryOwner");
        ScorePlayerTeam outside = board.createTeam("outside");board.addPlayerToTeam("RecoveryOwner", "outside");
        check(ScrollSupport.ownParty(board, owner, "RecoveryOwner") == null && board.getPlayersTeam("RecoveryOwner") == outside, "Own-party recovery stole an outside team");
        board.removePlayerFromTeams("RecoveryOwner");
        ScorePlayerTeam otherParty = board.createTeam("vsanother");board.addPlayerToTeam("RecoveryOwner", otherParty.getName());
        check(ScrollSupport.ownParty(board, owner, "RecoveryOwner") == null && board.getPlayersTeam("RecoveryOwner") == otherParty, "Own-party recovery stole another expedition");

        ScrollJournal journal = new ScrollJournal();
        String serial = "issued-companion";UUID actor = UUID.randomUUID(), stale = UUID.randomUUID();
        NBTTagCompound row = journal.companion(serial);row.setUniqueId("actor", actor);row.setBoolean("deployed", true);row.setLong("actorExpires", 20000);
        check(journal.recordCompanion(serial, actor, 17, true), "Authoritative companion closure was rejected");
        ScrollJournal restored = new ScrollJournal();restored.readFromNBT(reload(journal.writeToNBT(new NBTTagCompound())));
        check(!restored.companion(serial).getBoolean("deployed") && restored.companion(serial).getFloat("health") == 17, "Companion closure/health did not survive a saved journal reload");
        restored.companion(serial).setBoolean("deployed", true);
        check(!restored.recordCompanion(serial, stale, 0, true), "A removed actor closed its replacement");
        check(restored.companion(serial).getBoolean("deployed") && restored.companion(serial).getFloat("health") == 17, "Stale actor changed replacement state");
        check(!restored.recordCompanion(serial, actor, Float.NaN, true), "Non-finite health poisoned the saved companion");
        check(restored.recordCompanion(serial, actor, -3, true) && restored.companion(serial).getFloat("health") == 0, "Dead companion health was not bounded");
        check(!restored.recordCompanion("", actor, 30, true), "An unissued actor created a companion row");
        System.out.println("PASS: " + checks + " gameplay recovery state checks (saved cooldowns, party rejoin, companion journal). Native crafting/actor checks are separate.");
    }
}
