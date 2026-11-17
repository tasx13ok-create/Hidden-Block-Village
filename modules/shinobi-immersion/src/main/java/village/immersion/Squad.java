package village.immersion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Squads of up to six players for the session (/squad invite, accept, leave, list). Players on the
 * same vanilla scoreboard team count as squadmates too. Every half second each member gets the
 * others' name, position and health, so markers work at any distance in the same dimension.
 */
final class Squad {
    private static final Map<UUID, Set<UUID>> SQUADS = new HashMap<>();   // member -> shared member set
    private static final Map<UUID, UUID> INVITES = new HashMap<>();       // invitee -> inviter
    private static final Map<UUID, Long> INVITE_AT = new HashMap<>();
    private static final Set<UUID> SYNCED = new HashSet<>();

    static Set<UUID> of(UUID id) { return SQUADS.getOrDefault(id, Collections.emptySet()); }

    @SubscribeEvent public void tick(TickEvent.ServerTickEvent e) {
        if (!Config.squad || e.phase != TickEvent.Phase.END) return;
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null || server.getTickCounter() % 10 != 0) return;
        List<EntityPlayerMP> online = server.getPlayerList().getPlayers();
        for (EntityPlayerMP p : online) {
            Net.SquadMsg msg = new Net.SquadMsg();
            Set<UUID> mine = of(p.getUniqueID());
            for (EntityPlayerMP o : online) {
                if (o == p || msg.members.size() >= ImmersionMath.SQUAD_MAX) continue;
                boolean mate = mine.contains(o.getUniqueID()) || (p.getTeam() != null && p.isOnSameTeam(o));
                if (!mate) continue;
                Net.Member m = new Net.Member();
                m.name = o.getName(); m.dim = o.dimension; m.x = o.posX; m.y = o.posY; m.z = o.posZ; m.health = o.getHealth(); m.max = o.getMaxHealth();
                msg.members.add(m);
            }
            if (!msg.members.isEmpty() || SYNCED.contains(p.getUniqueID())) {   // one empty update clears the HUD
                Net.CH.sendTo(msg, p);
                if (msg.members.isEmpty()) SYNCED.remove(p.getUniqueID()); else SYNCED.add(p.getUniqueID());
            }
        }
    }

    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent e) { leave(e.player.getUniqueID()); SYNCED.remove(e.player.getUniqueID()); }

    private static void leave(UUID id) {
        Set<UUID> s = SQUADS.remove(id);
        if (s == null) return;
        s.remove(id);
        if (s.size() <= 1) for (UUID last : new ArrayList<>(s)) SQUADS.remove(last);
    }

    static final class Command extends CommandBase {
        @Override public String getName() { return "squad"; }
        @Override public String getUsage(ICommandSender s) { return "/squad invite <player> | accept | leave | list"; }
        @Override public int getRequiredPermissionLevel() { return 0; }
        @Override public boolean checkPermission(MinecraftServer server, ICommandSender sender) { return sender instanceof EntityPlayerMP; }
        @Override public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] a, BlockPos pos) {
            if (a.length == 1) return getListOfStringsMatchingLastWord(a, "invite", "accept", "leave", "list");
            if (a.length == 2 && a[0].equals("invite")) return getListOfStringsMatchingLastWord(a, server.getOnlinePlayerNames());
            return Collections.emptyList();
        }
        @Override public void execute(MinecraftServer server, ICommandSender sender, String[] a) throws CommandException {
            EntityPlayerMP p = getCommandSenderAsPlayer(sender);
            if (!Config.squad) { say(p, "Squads are turned off on this server."); return; }
            if (a.length == 0) throw new WrongUsageException(getUsage(sender));
            switch (a[0]) {
                case "invite": {
                    if (a.length < 2) throw new WrongUsageException(getUsage(sender));
                    EntityPlayerMP t = getPlayer(server, sender, a[1]);
                    if (t == p) { say(p, "You can't invite yourself."); return; }
                    if (of(p.getUniqueID()).size() >= ImmersionMath.SQUAD_MAX) { say(p, "Your squad is full (" + ImmersionMath.SQUAD_MAX + ")."); return; }
                    if (of(t.getUniqueID()).contains(p.getUniqueID())) { say(p, t.getName() + " is already in your squad."); return; }
                    INVITES.put(t.getUniqueID(), p.getUniqueID()); INVITE_AT.put(t.getUniqueID(), server.getWorld(0).getTotalWorldTime());
                    say(p, "Invited " + t.getName() + ".");
                    say(t, p.getName() + " invited you to their squad. Type /squad accept within 60 seconds.");
                    return;
                }
                case "accept": {
                    UUID from = INVITES.remove(p.getUniqueID()); Long at = INVITE_AT.remove(p.getUniqueID());
                    EntityPlayerMP inviter = from == null ? null : server.getPlayerList().getPlayerByUUID(from);
                    if (inviter == null || at == null || server.getWorld(0).getTotalWorldTime() - at > 1200) { say(p, "You have no open squad invite."); return; }
                    Set<UUID> squad = SQUADS.get(from);
                    if (squad == null) { squad = new LinkedHashSet<>(); squad.add(from); SQUADS.put(from, squad); }
                    if (squad.size() >= ImmersionMath.SQUAD_MAX) { say(p, "That squad is full."); return; }
                    leave(p.getUniqueID());
                    squad.add(p.getUniqueID()); SQUADS.put(p.getUniqueID(), squad);
                    for (UUID id : squad) { EntityPlayerMP m = server.getPlayerList().getPlayerByUUID(id); if (m != null) say(m, p.getName() + " joined the squad."); }
                    return;
                }
                case "leave": {
                    Set<UUID> s = new HashSet<>(of(p.getUniqueID()));
                    if (s.isEmpty()) { say(p, "You're not in a squad."); return; }
                    leave(p.getUniqueID());
                    say(p, "You left the squad.");
                    for (UUID id : s) { EntityPlayerMP m = server.getPlayerList().getPlayerByUUID(id); if (m != null && m != p) say(m, p.getName() + " left the squad."); }
                    return;
                }
                case "list": {
                    Set<UUID> s = of(p.getUniqueID());
                    if (s.isEmpty()) { say(p, "You're not in a squad."); return; }
                    StringBuilder b = new StringBuilder("Squad: ");
                    for (UUID id : s) { EntityPlayerMP m = server.getPlayerList().getPlayerByUUID(id); b.append(m == null ? "(offline)" : m.getName()).append("  "); }
                    say(p, b.toString().trim());
                    return;
                }
                default: throw new WrongUsageException(getUsage(sender));
            }
        }
    }

    static void say(EntityPlayerMP p, String text) {
        TextComponentString c = new TextComponentString(text);
        c.getStyle().setColor(TextFormatting.GREEN);
        p.sendMessage(c);
    }
}
