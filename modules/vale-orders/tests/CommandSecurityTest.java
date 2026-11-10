package village.vale;

import net.minecraft.command.CommandException;
import net.minecraft.command.CommandResultStats;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;

/** Calls the actual command implementation. Null server ensures denial happens before any server access. */
public final class CommandSecurityTest {
    public static void main(String[] args) throws Exception {
        ValeCommand command = new ValeCommand();
        ICommandSender ordinary = new OrdinarySender();
        if(ordinary.canUseCommand(0,"vale"))throw new AssertionError("Fixture must model a real non-operator's level-zero rejection.");
        if(!command.checkPermission(null,ordinary))throw new AssertionError("Non-operator cannot enter public Vale commands.");
        denied(command, ordinary, new String[] {"grant", "Someone", "ember_saber"});
        denied(command, ordinary, new String[] {"grant", "Someone", "ember_saber", "16", "receipt-a"});
        denied(command, ordinary, new String[] {"target", "00000000-0000-0000-0000-000000000001", "allow"});
        denied(command, ordinary, new String[] {"boss", "sentinel"});
        denied(command, ordinary, new String[] {"beast", "Someone", "shukaku", "helmet"});
        denied(command, ordinary, new String[] {"standard", "Someone", "stone"});
        denied(command, ordinary, new String[] {"fieldmob", "0"});
        if(!command.getTabCompletions(null,ordinary,new String[]{"standard",""},null).isEmpty())throw new AssertionError("Standard privileged completion exposed.");
        if(!command.getTabCompletions(null,ordinary,new String[]{"beast",""},null).isEmpty())throw new AssertionError("Owned reward privileged completion exposed.");
        if (!command.getTabCompletions(null, ordinary, new String[] {"grant", ""}, null).isEmpty())
            throw new AssertionError("Ordinary sender reached privileged completion.");
        if (!command.getTabCompletions(null, ordinary, new String[] {"boss", ""}, null).isEmpty())
            throw new AssertionError("Ordinary sender reached boss completion.");
        if(command.getTabCompletions(null,ordinary,new String[]{"join",""},null).size()!=9)throw new AssertionError("Public order completion inaccessible.");
        final int[] messages={0};command.execute(null,new OrdinarySender(){@Override public void sendMessage(ITextComponent text){messages[0]++;}},new String[]{"catalog"});
        if(messages[0]!=1)throw new AssertionError("Public catalog command did not execute.");
        System.out.println("PASS: 15 actual non-op command-entry, public command and admin-denial/completion checks; no server or world needed.");
    }
    private static void denied(ValeCommand command, ICommandSender sender, String[] args) throws Exception {
        try { command.execute(null, sender, args); }
        catch (CommandException expected) {
            if ("commands.generic.permission".equals(expected.getMessage())) return;
            throw expected;
        }
        throw new AssertionError("Ordinary sender was allowed an administrative command.");
    }
    private static class OrdinarySender implements ICommandSender {
        @Override public String getName() { return "ordinary-player"; }
        @Override public boolean canUseCommand(int level, String name) { return false; }
        @Override public ITextComponent getDisplayName() { throw new AssertionError("Unexpected display access"); }
        @Override public void sendMessage(ITextComponent text) { throw new AssertionError("Unexpected success message"); }
        @Override public BlockPos getPosition() { throw new AssertionError("Unexpected world access"); }
        @Override public Vec3d getPositionVector() { throw new AssertionError("Unexpected world access"); }
        @Override public World getEntityWorld() { throw new AssertionError("Unexpected world access"); }
        @Override public Entity getCommandSenderEntity() { return null; }
        @Override public boolean sendCommandFeedback() { return false; }
        @Override public void setCommandStat(CommandResultStats.Type type, int value) { throw new AssertionError("Unexpected command mutation"); }
        @Override public MinecraftServer getServer() { throw new AssertionError("Unexpected server access"); }
    }
}
