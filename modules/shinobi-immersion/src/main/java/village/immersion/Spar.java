package village.immersion;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.network.play.server.SPacketTitle;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Agreed sparring: /spar <player>, the other types /spar accept. After a 3-2-1 countdown the two
 * fight; a blow that would take either below one health ends the match instead, so nobody dies or
 * drops items. Ends on forfeit, distance over 48 blocks, a dimension change, logout or 5 minutes.
 * Only damage between the two partners is capped; everything else in the world works as normal.
 * Needs PvP enabled on the server.
 */
final class Spar {
    private static final class Match { UUID a, b; long start; boolean live; }
    private static final Map<UUID, Match> MATCHES = new HashMap<>();
    private static final Map<UUID, UUID> INVITES = new HashMap<>();   // invitee -> challenger
    private static final Map<UUID, Long> INVITE_AT = new HashMap<>();

    private static long now(MinecraftServer s) { return s.getWorld(0).getTotalWorldTime(); }
    private static UUID partner(Match m, UUID id) { return m.a.equals(id) ? m.b : m.a; }
    private static boolean between(Entity victim, Entity attacker) {
        if (victim == null || attacker == null) return false;
        Match m = MATCHES.get(victim.getUniqueID());
        return m != null && partner(m, victim.getUniqueID()).equals(attacker.getUniqueID());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void countdown(LivingAttackEvent e) {
        Match m = MATCHES.get(e.getEntityLiving().getUniqueID());
        if (m != null && !m.live && between(e.getEntityLiving(), e.getSource().getTrueSource())) e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void cap(LivingDamageEvent e) {
        if (!(e.getEntityLiving() instanceof EntityPlayerMP) || !between(e.getEntityLiving(), e.getSource().getTrueSource())) return;
        EntityPlayerMP v = (EntityPlayerMP) e.getEntityLiving();
        float[] c = ImmersionMath.sparCap(e.getAmount(), v.getHealth());
        e.setAmount(c[0]);
        if (c[1] == 1) finish(MATCHES.get(v.getUniqueID()), e.getSource().getTrueSource().getUniqueID(), "won the spar");
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void safety(LivingDeathEvent e) {
        if (!(e.getEntityLiving() instanceof EntityPlayerMP) || !between(e.getEntityLiving(), e.getSource().getTrueSource())) return;
        e.setCanceled(true);
        e.getEntityLiving().setHealth(1f);
        finish(MATCHES.get(e.getEntityLiving().getUniqueID()), e.getSource().getTrueSource().getUniqueID(), "won the spar");
    }

    @SubscribeEvent public void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || MATCHES.isEmpty()) return;
        MinecraftServer s = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (s == null) return;
        long t = now(s);
        for (Match m : new java.util.HashSet<>(MATCHES.values())) {
            EntityPlayerMP a = s.getPlayerList().getPlayerByUUID(m.a), b = s.getPlayerList().getPlayerByUUID(m.b);
            if (a == null || b == null || a.dimension != b.dimension || a.getDistance(b) > ImmersionMath.SPAR_RANGE) { finish(m, null, "The spar ended: you moved apart"); continue; }
            long age = t - m.start;
            if (!m.live) {
                if (age % 20 == 0 && age < ImmersionMath.SPAR_COUNTDOWN) { String n = String.valueOf(3 - age / 20); title(a, n, TextFormatting.GOLD); title(b, n, TextFormatting.GOLD); tick(a); tick(b); }
                if (age >= ImmersionMath.SPAR_COUNTDOWN) { m.live = true; title(a, "Fight", TextFormatting.RED); title(b, "Fight", TextFormatting.RED); }
            } else if (age > ImmersionMath.SPAR_LIMIT) finish(m, null, "Time is up: the spar is a draw");
        }
    }

    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent e) { Match m = MATCHES.get(e.player.getUniqueID()); if (m != null) finish(m, null, "The spar ended: your partner left"); }

    private static void finish(Match m, UUID winner, String text) {
        if (m == null || MATCHES.get(m.a) != m) return;
        MATCHES.remove(m.a); MATCHES.remove(m.b);
        MinecraftServer s = FMLCommonHandler.instance().getMinecraftServerInstance();
        for (UUID id : new UUID[] {m.a, m.b}) {
            EntityPlayerMP p = s == null ? null : s.getPlayerList().getPlayerByUUID(id);
            if (p == null) continue;
            if (winner == null) { title(p, "Spar over", TextFormatting.YELLOW); Squad.say(p, text + "."); continue; }
            boolean won = winner.equals(id);
            EntityPlayerMP other = s.getPlayerList().getPlayerByUUID(partner(m, id));
            title(p, won ? "Victory" : "Defeat", won ? TextFormatting.GOLD : TextFormatting.GRAY);
            Squad.say(p, won ? "You won the spar" + (other == null ? "." : " against " + other.getName() + ".") : (other == null ? "You lost the spar." : other.getName() + " won the spar."));
            if (!won) p.addPotionEffect(new PotionEffect(MobEffects.REGENERATION, 100, 1, false, true));
        }
    }

    private static void title(EntityPlayerMP p, String text, TextFormatting color) {
        TextComponentString c = new TextComponentString(text); c.getStyle().setColor(color);
        p.connection.sendPacket(new SPacketTitle(2, 16, 4));
        p.connection.sendPacket(new SPacketTitle(SPacketTitle.Type.TITLE, c));
    }
    private static void tick(EntityPlayerMP p) { p.world.playSound(null, p.posX, p.posY, p.posZ, SoundEvents.BLOCK_NOTE_HAT, SoundCategory.PLAYERS, 0.8f, 1.2f); }

    static final class Command extends CommandBase {
        @Override public String getName() { return "spar"; }
        @Override public String getUsage(ICommandSender s) { return "/spar <player> | accept | forfeit"; }
        @Override public int getRequiredPermissionLevel() { return 0; }
        @Override public boolean checkPermission(MinecraftServer server, ICommandSender sender) { return sender instanceof EntityPlayerMP; }
        @Override public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] a, BlockPos pos) {
            if (a.length == 1) { java.util.List<String> o = new java.util.ArrayList<>(java.util.Arrays.asList(server.getOnlinePlayerNames())); o.add("accept"); o.add("forfeit"); return getListOfStringsMatchingLastWord(a, o); }
            return Collections.emptyList();
        }
        @Override public void execute(MinecraftServer server, ICommandSender sender, String[] a) throws CommandException {
            EntityPlayerMP p = getCommandSenderAsPlayer(sender);
            if (!Config.sparring) { Squad.say(p, "Sparring is turned off on this server."); return; }
            if (a.length == 0) throw new WrongUsageException(getUsage(sender));
            if (a[0].equals("forfeit")) {
                Match m = MATCHES.get(p.getUniqueID());
                if (m == null) { Squad.say(p, "You're not sparring."); return; }
                finish(m, partner(m, p.getUniqueID()), "forfeit");
                return;
            }
            if (!server.isPVPEnabled()) { Squad.say(p, "Sparring needs PvP, and PvP is off on this server."); return; }
            if (MATCHES.containsKey(p.getUniqueID())) { Squad.say(p, "You're already sparring. Use /spar forfeit to stop."); return; }
            if (a[0].equals("accept")) {
                UUID from = INVITES.remove(p.getUniqueID()); Long at = INVITE_AT.remove(p.getUniqueID());
                EntityPlayerMP c = from == null ? null : server.getPlayerList().getPlayerByUUID(from);
                if (c == null || at == null || now(server) - at > ImmersionMath.SPAR_INVITE) { Squad.say(p, "You have no open spar challenge."); return; }
                if (MATCHES.containsKey(c.getUniqueID())) { Squad.say(p, c.getName() + " is already sparring."); return; }
                if (c.dimension != p.dimension || c.getDistance(p) > ImmersionMath.SPAR_RANGE) { Squad.say(p, "Get within " + ImmersionMath.SPAR_RANGE + " blocks of " + c.getName() + " first."); return; }
                if (p.getTeam() != null && p.isOnSameTeam(c) && !p.getTeam().getAllowFriendlyFire()) { Squad.say(p, "Your scoreboard team has friendly fire off, so you can't hit each other."); return; }
                Match m = new Match(); m.a = c.getUniqueID(); m.b = p.getUniqueID(); m.start = now(server);
                MATCHES.put(m.a, m); MATCHES.put(m.b, m);
                Squad.say(c, p.getName() + " accepted. Nobody can drop below one health."); Squad.say(p, "Spar with " + c.getName() + " starts in 3 seconds.");
                return;
            }
            EntityPlayerMP t = getPlayer(server, sender, a[0]);
            if (t == p) { Squad.say(p, "You can't spar yourself."); return; }
            INVITES.put(t.getUniqueID(), p.getUniqueID()); INVITE_AT.put(t.getUniqueID(), now(server));
            Squad.say(p, "Challenged " + t.getName() + " to a spar.");
            Squad.say(t, p.getName() + " challenged you to a spar (no deaths, no item loss). Type /spar accept within 30 seconds.");
        }
    }
}
