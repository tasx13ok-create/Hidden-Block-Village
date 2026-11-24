package village.bijuu;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.block.BlockStoneBrick;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;

/**
 * Server-owned Steam Dash Punch. Everything that changes the world happens here: charging, the
 * springboard, the dash (through a line of enemies at full charge), the afterimage barrage, damage,
 * hit-stop, the boil launch and its tunnelling, juggle relays, the tail pin and spike, wall splatter,
 * craters and molten scars. Clients receive cues (DashNet.Fx) for the camera, impact frames,
 * afterimages, mach cone, rings and rubble; particles and sounds below go to everyone nearby.
 */
final class DashServer {
    private static final int KOKUO = 4, KURAMA = 8;
    static final String BOIL_TAG = "hvbBoilLaunchedTicks";
    static final Map<UUID, Seq> RUNNING = new HashMap<>();
    static final Map<UUID, Charge> CHARGING = new HashMap<>();
    private static final Map<UUID, Long> FALL_SAFE = new HashMap<>();
    static final Set<Integer> FROZEN = new HashSet<>();
    private static final Random RAND = new Random();

    enum Phase { TELEGRAPH, DASH, PASS, BARRAGE, FREEZE, LAUNCH, RELAY, PIN, SPIKE, DONE }

    static final class Charge { final long start; int tier = 1; Charge(long s) { start = s; } }

    /** A body knocked aside by a piercing dash: flies sideways and tunnels a little on its own. */
    static final class Side { final EntityLivingBase body; final double[] flight; int boil; Side(EntityLivingBase b, double[] f, int boil) { body = b; flight = f; this.boil = boil; } }

    static final class Seq {
        EntityPlayerMP attacker; EntityLivingBase target; WorldServer world;
        Phase phase = Phase.TELEGRAPH; int phaseAge, budget, boil, telegraph = DashMath.TELEGRAPH, tier = 1, relays, lineIndex, segmentAge;
        double[] dir = {0, 0, 1}, flight = {0, 0, 1}, look = {0, 0, 1};
        Vec3d frozenA, frozenT, lastT, lastA, hit;
        boolean spiked, sprung, splattered;
        final List<EntityLivingBase> line = new ArrayList<>();
        final List<Side> sides = new ArrayList<>();
        final Map<Integer, Boolean> gravity = new HashMap<>();   // entity id -> had noGravity before we touched it
        final Set<BlockPos> lining = new LinkedHashSet<>();
        final List<int[]> debris = new ArrayList<>();
        List<double[]> stabs;
        int stabIndex;
    }

    // ---- input ----

    /** The dash key went down. Mid-sequence it is a juggle input; otherwise it springboards or starts a charge. */
    static void press(EntityPlayerMP p) {
        if (LariatServer.wearing(p)) { LariatServer.press(p); return; }   // one signature key: each set has its own move
        Seq running = RUNNING.get(p.getUniqueID());
        if (running != null) { juggle(running); return; }
        if (CHARGING.containsKey(p.getUniqueID())) return;
        if (!ready(p)) return;
        double[] wall = wallNormal(p);
        if (wall != null && pick(p) == null) { springboard(p, BijuuArmor.state(p), wall, p.world.getTotalWorldTime()); return; }
        if (!SteamDash.overcharge) { startDash(p, 1); return; }
        Charge c = new Charge(p.world.getTotalWorldTime());
        CHARGING.put(p.getUniqueID(), c);
        broadcast(p, DashNet.CHARGE, new double[] {0, 0, 0}, new int[] {1});
        play(p.getServerWorld(), p, SteamDash.CHARGE, 0.7f, 0.85f);
    }

    /** The dash key came up: the hold time picks the tier. */
    static void release(EntityPlayerMP p) {
        Charge c = CHARGING.remove(p.getUniqueID());
        if (c == null) return;
        broadcast(p, DashNet.CHARGE_END, new double[] {0, 0, 0}, null);
        startDash(p, DashMath.tier((int) (p.world.getTotalWorldTime() - c.start)));
    }

    private static boolean ready(EntityPlayerMP p) {
        if (!p.isEntityAlive() || p.isSpectator() || p.isRiding() || p.capabilities.isFlying) return false;
        if (!channeling(p)) { BijuuArmor.tell(p, "Wear the full Kurama set and carry a Kokuo piece (or the reverse) to channel the steam dash."); return false; }
        return true;
    }

    private static void startDash(EntityPlayerMP p, int tier) {
        if (!ready(p)) return;
        NBTTagCompound s = BijuuArmor.state(p);
        long now = p.world.getTotalWorldTime();
        double[] wall = wallNormal(p);
        EntityLivingBase target = pick(p);
        int cost = DashMath.tierCost(tier, SteamDash.cost);
        if (now < s.getLong("dashReady")) { BijuuArmor.tell(p, "Steam dash recharging: " + ((s.getLong("dashReady") - now + 19) / 20) + "s."); return; }
        if (s.getInteger("energy") < cost) { BijuuArmor.tell(p, "Need " + cost + " resonance for a tier " + tier + " steam dash."); return; }
        if (target == null) { BijuuArmor.tell(p, "No target in sight."); return; }
        if (BijuuArmor.useNarutoChakra && !ChakraBridge.pay(p)) { BijuuArmor.tell(p, "Not enough chakra."); return; }
        s.setInteger("energy", s.getInteger("energy") - cost);
        s.setLong("dashReady", now + SteamDash.cooldownSeconds * 20L);
        Seq q = new Seq();
        q.attacker = p; q.world = p.getServerWorld(); q.budget = SteamDash.blockBudget + (tier - 1) * 250; q.tier = tier;
        q.target = target;
        if (tier >= 3) pierceLine(q, p, target);
        EntityLivingBase first = q.line.isEmpty() ? q.target : q.line.get(0);
        q.dir = DashMath.direction(p.posX, p.posY + p.height / 2, p.posZ, first.posX, first.posY + first.height / 2, first.posZ);
        RUNNING.put(p.getUniqueID(), q);
        if (wall != null) {   // kick off the wall straight into the dash: the crack is the telegraph
            q.sprung = true; q.telegraph = 2;
            crackWall(p, wall, true);
            p.motionX = wall[0] * 0.6; p.motionY = 0.25; p.motionZ = wall[2] * 0.6; p.velocityChanged = true;
        }
        int[] ids = new int[2 + q.line.size()];
        ids[0] = q.telegraph; ids[1] = tier;
        for (int i = 0; i < q.line.size(); i++) ids[2 + i] = q.line.get(i).getEntityId();
        send(q, DashNet.START, p.posX, p.posY, p.posZ, q.dir, ids);
        play(q.world, p, SoundEvents.BLOCK_FIRE_EXTINGUISH, 1.0f, 1.8f);
        play(q.world, p, SteamDash.HISS, 1.0f, 1.15f);
    }

    /** Tier 3: every enemy within PIERCE_WIDTH of the line to (and a little past) the aimed one; the farthest takes the finish. */
    private static void pierceLine(Seq q, EntityPlayerMP p, EntityLivingBase aimed) {
        Vec3d a = new Vec3d(p.posX, p.posY + p.height / 2, p.posZ);
        Vec3d t = new Vec3d(aimed.posX, aimed.posY + aimed.height / 2, aimed.posZ);
        Vec3d d = t.subtract(a).normalize();
        Vec3d end = a.add(d.scale(DashMath.range()));   // the whole dash range, not just up to the aimed body
        List<EntityLivingBase> found = new ArrayList<>();
        for (EntityLivingBase e : p.world.getEntitiesWithinAABB(EntityLivingBase.class, new AxisAlignedBB(a.x, a.y, a.z, end.x, end.y, end.z).grow(DashMath.PIERCE_WIDTH + 1))) {
            if (e == p || !e.isEntityAlive() || e instanceof EntityArmorStand || !hostileOk(p, e)) continue;
            Vec3d c = new Vec3d(e.posX, e.posY + e.height / 2, e.posZ);
            double[] cp = DashMath.closest(a.x, a.y, a.z, end.x, end.y, end.z, c.x, c.y, c.z);
            if (c.distanceTo(new Vec3d(cp[0], cp[1], cp[2])) <= DashMath.PIERCE_WIDTH && c.subtract(a).dotProduct(d) > 1) found.add(e);
        }
        if (!found.contains(aimed)) found.add(aimed);
        found.sort(Comparator.comparingDouble(e -> e.getDistanceSq(a.x, a.y, a.z)));
        while (found.size() > DashMath.PIERCE_MAX) found.remove(found.size() - 1);
        q.target = found.get(found.size() - 1);
        q.line.addAll(found.subList(0, found.size() - 1));
    }

    private static boolean hostileOk(EntityPlayerMP p, EntityLivingBase e) {
        return MatatabiPowers.allowed(p, e) && !(e instanceof EntityPlayer && !p.server.isPVPEnabled());
    }

    /** Full Kurama set plus a carried Kokuo piece, or the reverse. */
    static boolean channeling(EntityPlayer p) {
        int set = BijuuArmor.full(p);
        return set == KURAMA && carries(p, KOKUO) || set == KOKUO && carries(p, KURAMA);
    }

    private static boolean carries(EntityPlayer p, int beast) {
        for (int i = 0; i < p.inventory.getSizeInventory(); i++) {
            ItemStack s = p.inventory.getStackInSlot(i);
            if (s.getItem() instanceof BijuuArmor.Armor && ((BijuuArmor.Armor) s.getItem()).beast == beast && BijuuArmor.valid(p.world, s)) return true;
        }
        return false;
    }

    /** The living thing nearest the crosshair inside the dash range, in clear sight. */
    private static EntityLivingBase pick(EntityPlayerMP p) {
        Vec3d eye = p.getPositionEyes(1f), look = p.getLook(1f);
        double range = DashMath.range();
        EntityLivingBase best = null; double bestScore = -1;
        for (EntityLivingBase e : p.world.getEntitiesWithinAABB(EntityLivingBase.class, p.getEntityBoundingBox().grow(range))) {
            if (e == p || !e.isEntityAlive() || e instanceof EntityArmorStand) continue;
            Vec3d aim = new Vec3d(e.posX, e.posY + e.height * 0.6, e.posZ), to = aim.subtract(eye);
            double dist = to.length();
            if (dist > range || dist < 1) continue;
            double cos = to.normalize().dotProduct(look);
            if (cos < DashMath.AIM_COS) continue;
            if (!hostileOk(p, e)) continue;
            RayTraceResult hit = p.world.rayTraceBlocks(eye, aim, false, true, false);
            if (hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK) continue;
            double score = cos - dist / range * 0.05;
            if (score > bestScore) { bestScore = score; best = e; }
        }
        return best;
    }

    // ---- springboard ----

    private static Field parkourServer, srvState, srvNx, srvNz;
    private static boolean parkourLooked;

    /** Outward wall normal while the player is on a wall (Shinobi Immersion's wall state first, then a probe), else null. */
    static double[] wallNormal(EntityPlayerMP p) {
        if (p.onGround) return null;
        try {
            if (!parkourLooked) {
                parkourLooked = true;
                Class<?> c = Class.forName("village.immersion.Parkour");
                Field server = c.getDeclaredField("SERVER"); server.setAccessible(true);
                Class<?> srv = Class.forName("village.immersion.Parkour$Srv");
                srvState = srv.getDeclaredField("state"); srvNx = srv.getDeclaredField("nx"); srvNz = srv.getDeclaredField("nz");
                srvState.setAccessible(true); srvNx.setAccessible(true); srvNz.setAccessible(true);
                parkourServer = server;
            }
            if (parkourServer != null) {
                Object s = ((Map<?, ?>) parkourServer.get(null)).get(p.getUniqueID());
                if (s != null && srvState.getInt(s) != 0) return new double[] {srvNx.getInt(s), 0, srvNz.getInt(s)};
            }
        } catch (Throwable notInstalled) { parkourServer = null; }
        AxisAlignedBB b = p.getEntityBoundingBox();
        AxisAlignedBB body = new AxisAlignedBB(b.minX, b.minY + 0.3, b.minZ, b.maxX, b.maxY - 0.1, b.maxZ);
        Vec3d look = p.getLook(1f);
        double[] best = null; double bestDot = -2;
        for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            if (p.world.getCollisionBoxes(p, body.offset(d[0] * 0.35, 0, d[1] * 0.35)).isEmpty()) continue;
            double dot = look.x * d[0] + look.z * d[1];   // prefer the wall the player is facing
            if (dot > bestDot) { bestDot = dot; best = new double[] {-d[0], 0, -d[1]}; }
        }
        return best;
    }

    private static void springboard(EntityPlayerMP p, NBTTagCompound s, double[] n, long now) {
        if (now < s.getLong("springReady")) return;
        if (s.getInteger("energy") < SteamDash.springCost) { BijuuArmor.tell(p, "Need " + SteamDash.springCost + " resonance to springboard."); return; }
        s.setInteger("energy", s.getInteger("energy") - SteamDash.springCost);
        s.setLong("springReady", now + SteamDash.springCooldownTicks);
        Vec3d look = p.getLook(1f);
        double[] v = DashMath.springboard(n, new double[] {look.x, look.y, look.z});
        p.motionX = v[0]; p.motionY = v[1]; p.motionZ = v[2]; p.velocityChanged = true;
        p.fallDistance = 0;
        FALL_SAFE.put(p.getUniqueID(), now + 60);
        crackWall(p, n, false);
        broadcast(p, DashNet.SPRING, v, new int[] {(int) n[0], (int) n[2]});
        WorldServer w = p.getServerWorld();
        play(w, p, SteamDash.BLINK, 0.8f, 1.35f);
        play(w, p, SoundEvents.BLOCK_STONE_BREAK, 1.2f, 0.7f);
        play(w, p, SteamDash.HISS, 0.8f, 1.4f);
    }

    /** Cracks the wall behind the feet (stone to cobble or cracked bricks) and bursts a steam ring off its surface. */
    private static void crackWall(EntityPlayerMP p, double[] n, boolean big) {
        WorldServer w = p.getServerWorld();
        BlockPos face = new BlockPos(p.posX - n[0] * 0.8, p.posY + 0.3, p.posZ - n[2] * 0.8);
        IBlockState faceState = w.getBlockState(face);
        int ux = (int) -n[2], uz = (int) n[0];   // along the wall
        int reach = big ? 2 : 1;
        for (int a = -reach; a <= reach; a++) for (int b = -reach; b <= reach; b++) {
            int kind = DashMath.splatter(a, b, p.getEntityId() ^ w.getTotalWorldTime());
            if (!big && Math.abs(a) + Math.abs(b) <= 1) kind = Math.max(kind, a == 0 && b == 0 ? 1 : 2);
            if (kind > 0) crack(w, p, face.add(ux * a, b, uz * a), kind);
        }
        double cx = p.posX - n[0] * 0.3, cy = p.posY + 0.6, cz = p.posZ - n[2] * 0.3;
        for (double[] r : DashMath.ring(n, 1.0, 28))
            w.spawnParticle(EnumParticleTypes.CLOUD, true, cx + r[0] * 0.4, cy + r[1] * 0.4, cz + r[2] * 0.4, 0, r[0] * 0.45 + n[0] * 0.08, r[1] * 0.45, r[2] * 0.45 + n[2] * 0.08, 1.0);
        if (faceState.getMaterial().blocksMovement())
            w.spawnParticle(EnumParticleTypes.BLOCK_CRACK, true, face.getX() + 0.5 + n[0] * 0.6, face.getY() + 0.5, face.getZ() + 0.5 + n[2] * 0.6, 20, 0.3, 0.4, 0.3, 0.1, Block.getStateId(faceState));
    }

    /** Stone turns to cobblestone (1) or cracked stone bricks (2); stone bricks crack. Asks protection first. */
    private static boolean crack(WorldServer w, EntityPlayerMP by, BlockPos pos, int kind) {
        if (!SteamDash.crackWalls || !w.isBlockLoaded(pos)) return false;
        IBlockState state = w.getBlockState(pos);
        IBlockState into;
        if (state.getBlock() == Blocks.STONE) into = kind == 2 ? cracked() : Blocks.COBBLESTONE.getDefaultState();
        else if (state.getBlock() == Blocks.STONEBRICK && state.getValue(BlockStoneBrick.VARIANT) == BlockStoneBrick.EnumType.DEFAULT) into = cracked();
        else return false;
        if (MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(w, pos, state, by))) return false;
        return w.setBlockState(pos, into, 3);
    }

    private static IBlockState cracked() { return Blocks.STONEBRICK.getDefaultState().withProperty(BlockStoneBrick.VARIANT, BlockStoneBrick.EnumType.CRACKED); }

    // ---- ticking ----

    @SubscribeEvent public void tick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote) return;
        if (!CHARGING.isEmpty()) charging((WorldServer) e.world);
        if (RUNNING.isEmpty()) return;
        for (Iterator<Seq> it = RUNNING.values().iterator(); it.hasNext();) {
            Seq q = it.next();
            if (q.world != e.world) continue;
            try { step(q); } catch (RuntimeException failure) { q.phase = Phase.DONE; org.apache.logging.log4j.LogManager.getLogger("SteamDash").warn("Steam dash stopped", failure); }
            if (q.phase == Phase.DONE) { finish(q); it.remove(); }
        }
    }

    /** Overcharge: steam and gold swirl tighter, the ground cracks under you, each tier announces itself; a full charge fires itself. */
    private void charging(WorldServer w) {
        long now = w.getTotalWorldTime();
        for (Iterator<Map.Entry<UUID, Charge>> it = CHARGING.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, Charge> en = it.next();
            EntityPlayer pl = w.getPlayerEntityByUUID(en.getKey());
            if (!(pl instanceof EntityPlayerMP)) continue;
            EntityPlayerMP p = (EntityPlayerMP) pl;
            Charge c = en.getValue();
            int held = (int) (now - c.start), tier = DashMath.tier(held);
            if (!p.isEntityAlive() || !channeling(p)) { it.remove(); broadcast(p, DashNet.CHARGE_END, new double[3], null); continue; }
            if (tier > c.tier) {
                c.tier = tier;
                broadcast(p, DashNet.CHARGE, new double[3], new int[] {tier});
                play(w, p, SteamDash.CHARGE, 1.0f, 0.75f + 0.25f * tier);
                play(w, p, SoundEvents.BLOCK_STONE_BREAK, 1.2f, 0.5f);
                for (double[] r : DashMath.ring(new double[] {0, 1, 0}, 0.6 + tier * 0.6, 32))
                    w.spawnParticle(EnumParticleTypes.CLOUD, true, p.posX + r[0], p.posY + 0.1, p.posZ + r[2], 0, r[0] * 0.25, 0.02, r[2] * 0.25, 1.0);
            }
            double k = Math.min(1, held / 20.0);
            for (int i = 0; i < 3 + tier * 2; i++) {
                double ang = now * 0.7 + i * 2.1, r = 1.4 - k * 0.7;
                w.spawnParticle(EnumParticleTypes.CLOUD, true, p.posX + Math.cos(ang) * r, p.posY + 0.2 + RAND.nextDouble() * 1.6, p.posZ + Math.sin(ang) * r, 0, -Math.cos(ang) * 0.06, 0.12, -Math.sin(ang) * 0.06, 1.0);
                w.spawnParticle(EnumParticleTypes.REDSTONE, true, p.posX + Math.cos(-ang) * r * 0.8, p.posY + 0.1 + k * i * 0.2, p.posZ + Math.sin(-ang) * r * 0.8, 0, 1.0, 0.72, 0.12, 1.0);
            }
            IBlockState below = w.getBlockState(new BlockPos(p.posX, p.posY - 0.2, p.posZ));
            if (below.getMaterial().blocksMovement())
                w.spawnParticle(EnumParticleTypes.BLOCK_CRACK, true, p.posX, p.posY + 0.05, p.posZ, 2 + tier * 3, 0.4 + tier * 0.35, 0.0, 0.4 + tier * 0.35, 0.15, Block.getStateId(below));
            if (tier >= 3) w.spawnParticle(EnumParticleTypes.FLAME, true, p.posX, p.posY + 1.0, p.posZ, 2, 0.5, 0.7, 0.5, 0.02);
            if (held >= DashMath.CHARGE_MAX) { it.remove(); broadcast(p, DashNet.CHARGE_END, new double[3], null); startDash(p, 3); }
        }
    }

    /** Hit-stop and pins: a held mob skips its whole update, so it hangs in its pose like a held frame. */
    @SubscribeEvent public void living(LivingEvent.LivingUpdateEvent e) {
        if (!FROZEN.isEmpty() && !e.getEntityLiving().world.isRemote && !(e.getEntityLiving() instanceof EntityPlayer) && FROZEN.contains(e.getEntityLiving().getEntityId())) e.setCanceled(true);
    }

    private static boolean alive(Entity e, WorldServer w) { return e != null && e.isEntityAlive() && e.world == w; }

    private static void step(Seq q) {
        EntityPlayerMP a = q.attacker; EntityLivingBase t = q.target;
        if (!alive(a, q.world) || a.connection == null) { q.phase = Phase.DONE; return; }
        q.phaseAge++;
        a.fallDistance = 0;
        sides(q);
        if (q.phase != Phase.TELEGRAPH && q.phase != Phase.DASH && q.phase != Phase.PASS && q.phase != Phase.DONE && !alive(t, q.world)) { q.phase = Phase.DONE; return; }
        switch (q.phase) {
            case TELEGRAPH: telegraph(q); break;
            case DASH: dash(q); break;
            case PASS: pass(q); break;
            case BARRAGE: barrage(q); break;
            case FREEZE: freeze(q); break;
            case LAUNCH: launch(q); break;
            case RELAY: relayHold(q); break;
            case PIN: pin(q); break;
            case SPIKE: spikeFall(q); break;
            default: break;
        }
    }

    private static void telegraph(Seq q) {
        EntityPlayerMP a = q.attacker; WorldServer w = q.world;
        if (!q.sprung) { a.motionX = 0; a.motionZ = 0; a.motionY = Math.max(a.motionY, 0); a.velocityChanged = true; }
        double yaw = Math.toRadians(a.renderYawOffset);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw), rx = -fz, rz = fx;
        // Overheat: high-velocity steam and smoke jets backward from the shoulders and the tail base.
        double[][] vents = {{0.36, 1.38}, {-0.36, 1.38}, {0, 0.78}};
        for (double[] j : vents) {
            double x = a.posX + rx * j[0] - fx * 0.2, y = a.posY + j[1], z = a.posZ + rz * j[0] - fz * 0.2;
            for (int i = 0; i < 3; i++) {
                double sp = 0.45 + RAND.nextDouble() * 0.35;
                w.spawnParticle(EnumParticleTypes.CLOUD, true, x, y, z, 0, -fx * sp + (RAND.nextDouble() - 0.5) * 0.12, 0.05 + RAND.nextDouble() * 0.1, -fz * sp + (RAND.nextDouble() - 0.5) * 0.12, 1.0);
                w.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, true, x, y, z, 0, -fx * sp * 0.7, 0.04, -fz * sp * 0.7, 1.0);
            }
        }
        for (int i = 0; i < 6; i++) {   // golden dust spiralling around the feet
            double ang = q.phaseAge * 1.1 + i * Math.PI / 3, r = 0.95 - q.phaseAge * 0.12;
            gold(w, a.posX + Math.cos(ang) * r, a.posY + 0.1 + q.phaseAge * 0.12 + i * 0.05, a.posZ + Math.sin(ang) * r);
        }
        if (q.phaseAge >= q.telegraph) {
            q.phase = Phase.DASH; q.phaseAge = 0; q.segmentAge = 0;
            hover(q, a, true);
            send(q, DashNet.BLINK, a.posX, a.posY, a.posZ, q.dir, null);
            play(w, a, SteamDash.BLINK, 1.6f, 1.0f);
            play(w, a, SoundEvents.ENTITY_FIREWORK_LARGE_BLAST, 1.4f, 0.6f);
            play(w, a, SoundEvents.ITEM_ELYTRA_FLYING, 1.2f, 1.25f);
            play(w, a, SteamDash.LAUNCH, 0.9f, 1.3f);
        }
    }

    private static void gold(WorldServer w, double x, double y, double z) {
        // Redstone dust with count 0 takes its colour from the offsets: warm gold.
        w.spawnParticle(EnumParticleTypes.REDSTONE, true, x, y, z, 0, 1.0, 0.72, 0.12, 1.0);
    }

    /** The current waypoint: the next body in the pierce line, then the final target. */
    private static EntityLivingBase waypoint(Seq q) {
        while (q.lineIndex < q.line.size() && !alive(q.line.get(q.lineIndex), q.world)) q.lineIndex++;
        return q.lineIndex < q.line.size() ? q.line.get(q.lineIndex) : q.target;
    }

    private static void dash(Seq q) {
        EntityPlayerMP a = q.attacker; WorldServer w = q.world;
        EntityLivingBase t = waypoint(q);
        if (!alive(t, w)) { hover(q, a, false); q.phase = Phase.DONE; return; }
        q.segmentAge++;
        Vec3d real = new Vec3d(a.posX, a.posY + a.height / 2, a.posZ), tc = new Vec3d(t.posX, t.posY + t.height / 2, t.posZ);
        // The server owns the dash path: a virtual point advances every tick. The client normally flies it
        // smoothly from the velocity; if it hitches or lags behind, the server carries the attacker along.
        if (q.lastA == null) q.lastA = real;
        Vec3d from = q.lastA;
        double gap = from.distanceTo(tc);
        q.dir = DashMath.direction(from.x, from.y, from.z, tc.x, tc.y, tc.z);
        double speed = DashMath.dashStep(gap);
        Vec3d virt = from.add(q.dir[0] * speed, q.dir[1] * speed, q.dir[2] * speed);
        q.lastA = virt;
        // Clear the hitbox's path ahead (through walls, not a trench in the ground unless the dash dives); tier 3 bores 5x5.
        int floor = q.dir[1] < -0.35 ? Integer.MIN_VALUE : (int) Math.floor(from.y - a.height / 2 + 0.01);
        AxisAlignedBB body = new AxisAlignedBB(from.x - 0.3, from.y - a.height / 2, from.z - 0.3, from.x + 0.3, from.y + a.height / 2, from.z + 0.3);
        sweep(q, body, q.dir[0] * (speed + 1.5), q.dir[1] * (speed + 1.5), q.dir[2] * (speed + 1.5), 0.45 + DashMath.tierMargin(q.tier), floor);
        a.motionX = q.dir[0] * speed; a.motionY = q.dir[1] * speed; a.motionZ = q.dir[2] * speed; a.velocityChanged = true;
        if (real.distanceTo(virt) > 2.5) a.connection.setPlayerLocation(virt.x, virt.y - a.height / 2, virt.z, a.rotationYaw, a.rotationPitch);
        double[][] ring = DashMath.ring(q.dir, 1.4 + (q.tier - 1) * 0.5, DashMath.RING_POINTS);   // wind-tunnel rings of steam and golden flame
        for (int i = 0; i < ring.length; i++) {
            double[] r = ring[i];
            w.spawnParticle(i % 2 == 0 ? EnumParticleTypes.CLOUD : EnumParticleTypes.FLAME, true,
                    virt.x - q.dir[0] * 1.2 + r[0], virt.y - q.dir[1] * 1.2 + r[1], virt.z - q.dir[2] * 1.2 + r[2], 0, r[0] * 0.05, r[1] * 0.05, r[2] * 0.05, 1.0);
        }
        w.spawnParticle(EnumParticleTypes.CLOUD, true, virt.x, virt.y, virt.z, 8, 0.3, 0.3, 0.3, 0.02);
        double[] cp = DashMath.closest(from.x, from.y, from.z, virt.x, virt.y, virt.z, tc.x, tc.y, tc.z);
        double dist = tc.distanceTo(new Vec3d(cp[0], cp[1], cp[2]));
        if (dist <= DashMath.CONTACT || q.segmentAge >= DashMath.MAX_DASH) {
            if (dist > DashMath.CONTACT + 3) { hover(q, a, false); q.phase = Phase.DONE; return; }   // the target got away
            Vec3d at = new Vec3d(tc.x - q.dir[0] * 1.3, tc.y - q.dir[1] * 1.3, tc.z - q.dir[2] * 1.3);
            a.connection.setPlayerLocation(at.x, at.y - a.height / 2, at.z, a.rotationYaw, a.rotationPitch);
            q.lastA = at;
            if (t != q.target) passHit(q, t, at, tc); else contact(q, at, tc);
        }
    }

    /** Remembers whether we turned an entity's gravity off, so it is put back exactly as it was. */
    private static void hover(Seq q, EntityLivingBase e, boolean on) {
        if (on) { q.gravity.putIfAbsent(e.getEntityId(), e.hasNoGravity()); e.setNoGravity(true); }
        else { Boolean before = q.gravity.get(e.getEntityId()); e.setNoGravity(before != null && before); }
    }

    private static void hold(EntityLivingBase t, Vec3d at) {
        t.motionX = t.motionY = t.motionZ = 0; t.velocityChanged = true;
        if (t instanceof EntityPlayerMP) ((EntityPlayerMP) t).connection.setPlayerLocation(at.x, at.y, at.z, t.rotationYaw, t.rotationPitch);
        else t.setPosition(at.x, at.y, at.z);
    }

    // -- pierce (tier 3): each body in the line gets its own held frame, then is knocked aside --

    private static void passHit(Seq q, EntityLivingBase t, Vec3d ac, Vec3d tc) {
        EntityPlayerMP a = q.attacker; WorldServer w = q.world;
        q.phase = Phase.PASS; q.phaseAge = 0;
        q.frozenA = new Vec3d(a.posX, a.posY, a.posZ); q.frozenT = new Vec3d(t.posX, t.posY, t.posZ);
        hover(q, t, true);
        FROZEN.add(t.getEntityId());
        a.swingArm(EnumHand.MAIN_HAND);
        t.hurtResistantTime = 0;
        t.attackEntityFrom(DamageSource.causePlayerDamage(a), (float) (SteamDash.punchDamage * SteamDash.pierceDamageShare * DashMath.tierDamage(q.tier)));
        Vec3d hit = ac.add(tc).scale(0.5);
        send(q, DashNet.PASS, hit.x, hit.y, hit.z, q.dir, new int[] {t.getEntityId(), q.lineIndex});
        play(w, t, SteamDash.IMPACT, 1.4f, 1.4f);
        play(w, t, SoundEvents.BLOCK_ANVIL_PLACE, 0.9f, 1.8f);
    }

    private static void pass(Seq q) {
        EntityPlayerMP a = q.attacker; WorldServer w = q.world;
        EntityLivingBase t = q.line.get(q.lineIndex);
        a.motionX = a.motionY = a.motionZ = 0; a.velocityChanged = true;
        a.connection.setPlayerLocation(q.frozenA.x, q.frozenA.y, q.frozenA.z, a.rotationYaw, a.rotationPitch);
        if (alive(t, w)) hold(t, q.frozenT);
        if (q.phaseAge < DashMath.PIERCE_FREEZE) return;
        FROZEN.remove(t.getEntityId());
        if (alive(t, w)) {
            hover(q, t, false);
            // Knocked to the side it stood on, away from the dash line.
            double sx = t.posX - q.frozenA.x, sz = t.posZ - q.frozenA.z;
            double side = sx * -q.dir[2] + sz * q.dir[0] >= 0 ? 1 : -1;
            double[] f = {-q.dir[2] * side * 2.2 + q.dir[0] * 0.8, 0.8, q.dir[0] * side * 2.2 + q.dir[2] * 0.8};
            t.motionX = f[0]; t.motionY = f[1]; t.motionZ = f[2]; t.velocityChanged = true;
            q.sides.add(new Side(t, f, 12));
            w.spawnParticle(EnumParticleTypes.EXPLOSION_LARGE, true, t.posX, t.posY + 1, t.posZ, 2, 0.3, 0.3, 0.3, 0.0);
            play(w, t, SoundEvents.ENTITY_GENERIC_EXPLODE, 1.2f, 1.1f);
        }
        q.lineIndex++;
        q.phase = Phase.DASH; q.phaseAge = 0; q.segmentAge = 0;
    }

    /** Side-knocked bodies tunnel a little on their own until their short boil runs out. */
    private static void sides(Seq q) {
        for (Iterator<Side> it = q.sides.iterator(); it.hasNext();) {
            Side s = it.next();
            if (!alive(s.body, q.world) || --s.boil <= 0) { it.remove(); continue; }
            double sp = Math.sqrt(s.body.motionX * s.body.motionX + s.body.motionY * s.body.motionY + s.body.motionZ * s.body.motionZ);
            if (DashMath.smashing(sp)) sweep(q, s.body.getEntityBoundingBox(), s.body.motionX * 1.5, s.body.motionY * 1.5, s.body.motionZ * 1.5, 0.2, Integer.MIN_VALUE);
            q.world.spawnParticle(EnumParticleTypes.CLOUD, true, s.body.posX, s.body.posY + 1, s.body.posZ, 3, 0.3, 0.3, 0.3, 0.02);
        }
    }

    // -- the finish: afterimage barrage, then the real punch --

    private static void contact(Seq q, Vec3d ac, Vec3d tc) {
        EntityPlayerMP a = q.attacker; EntityLivingBase t = q.target;
        q.frozenA = new Vec3d(a.posX, a.posY, a.posZ); q.frozenT = new Vec3d(t.posX, t.posY, t.posZ);
        q.hit = ac.add(tc).scale(0.5);
        hover(q, t, true);
        FROZEN.add(t.getEntityId());
        if (SteamDash.afterimageBarrage) { q.phase = Phase.BARRAGE; q.phaseAge = 0; }
        else impact(q);
    }

    /** One clone punch per tick: a jab, a small knock and a flash; the target is held between them. */
    private static void barrage(Seq q) {
        EntityPlayerMP a = q.attacker; EntityLivingBase t = q.target; WorldServer w = q.world;
        a.motionX = a.motionY = a.motionZ = 0; a.velocityChanged = true;
        a.connection.setPlayerLocation(q.frozenA.x, q.frozenA.y, q.frozenA.z, a.rotationYaw, a.rotationPitch);
        int i = q.phaseAge;
        hold(t, q.frozenT.add(q.dir[0] * 0.07 * i, 0.03 * i, q.dir[2] * 0.07 * i));
        if (i <= DashMath.BARRAGE) {
            t.hurtResistantTime = 0;
            t.attackEntityFrom(DamageSource.causePlayerDamage(a), SteamDash.barrageDamage);
            send(q, DashNet.BARRAGE, t.posX, t.posY + t.height * 0.6, t.posZ, q.dir, new int[] {i});
            play(w, t, SteamDash.JAB, 1.3f, 0.9f + 0.12f * i);
            w.spawnParticle(EnumParticleTypes.CRIT, true, t.posX, t.posY + t.height * 0.6, t.posZ, 10, 0.3, 0.3, 0.3, 0.4);
            w.spawnParticle(EnumParticleTypes.CLOUD, true, t.posX, t.posY + t.height * 0.6, t.posZ, 0, -q.dir[0] * 0.5, 0.05, -q.dir[2] * 0.5, 1.0);
        }
        if (i >= DashMath.BARRAGE) { q.frozenT = new Vec3d(t.posX, t.posY, t.posZ); impact(q); }
    }

    /** Contact: the vacuum frame. Damage lands, everything stops, a bell-like ring; the blast comes on release. */
    private static void impact(Seq q) {
        EntityPlayerMP a = q.attacker; EntityLivingBase t = q.target; WorldServer w = q.world;
        q.phase = Phase.FREEZE; q.phaseAge = 0;
        a.swingArm(EnumHand.MAIN_HAND);
        t.hurtResistantTime = 0;
        t.attackEntityFrom(DamageSource.causePlayerDamage(a), (float) (SteamDash.punchDamage * DashMath.tierDamage(q.tier)));
        t.motionX = t.motionY = t.motionZ = 0; t.velocityChanged = true;
        send(q, DashNet.IMPACT, q.hit.x, q.hit.y, q.hit.z, q.dir, new int[] {q.tier});
        play(w, t, SoundEvents.BLOCK_ANVIL_PLACE, 1.4f, 2.0f);
        play(w, t, SteamDash.IMPACT, 1.4f, 1.3f);
        // Bullet time around the hit for everything but the fighters.
        Set<Integer> fighters = new HashSet<>();
        fighters.add(a.getEntityId()); fighters.add(t.getEntityId());
        for (EntityLivingBase e : q.line) fighters.add(e.getEntityId());
        if (SteamDash.timeBubble) {
            TimeBubble.open(w, q.hit, fighters);
            send(q, DashNet.BUBBLE, q.hit.x, q.hit.y, q.hit.z, new double[] {DashMath.BUBBLE_RADIUS, DashMath.BUBBLE_TICKS, 0}, null);
            play(w, t, SteamDash.TIMEWARP, 1.6f, 1.0f);
        }
    }

    private static void freeze(Seq q) {
        EntityPlayerMP a = q.attacker; EntityLivingBase t = q.target; WorldServer w = q.world;
        a.motionX = a.motionY = a.motionZ = 0; a.velocityChanged = true;
        a.connection.setPlayerLocation(q.frozenA.x, q.frozenA.y, q.frozenA.z, a.rotationYaw, a.rotationPitch);
        hold(t, q.frozenT);
        Vec3d hit = q.frozenA.add(q.frozenT).scale(0.5).add(0, 1, 0);
        // Pressure building: a tight steam ring that swells each held frame.
        for (double[] r : DashMath.ring(q.dir, 0.6 + q.phaseAge * 0.5, 24)) w.spawnParticle(EnumParticleTypes.CLOUD, true, hit.x + r[0], hit.y + r[1], hit.z + r[2], 0, 0, 0, 0, 1.0);
        if (q.phaseAge >= DashMath.tierFreeze(q.tier)) release(q, hit);
    }

    /** End of hit-stop: the explosion lands, the exit cone bursts out of their back and they fly along your look. */
    private static void release(Seq q, Vec3d hit) {
        EntityPlayerMP a = q.attacker; EntityLivingBase t = q.target; WorldServer w = q.world;
        hover(q, a, false);
        FROZEN.remove(t.getEntityId());
        hover(q, t, false);
        Vec3d lv = a.getLook(1f);
        q.look = new double[] {lv.x, lv.y, lv.z};
        double[] v = DashMath.launch(q.look, boss(t));
        double m = DashMath.tierLaunch(q.tier);
        v = new double[] {v[0] * m, v[1], v[2] * m};
        launchBody(q, v);
        FALL_SAFE.put(a.getUniqueID(), w.getTotalWorldTime() + 100);
        w.spawnParticle(EnumParticleTypes.EXPLOSION_HUGE, true, hit.x, hit.y, hit.z, 1, 0, 0, 0, 0.0);
        sphere(w, hit, 1.2, 40);
        exitCone(w, t, DashMath.norm(q.look));
        send(q, DashNet.RELEASE, t.posX, t.posY, t.posZ, v, new int[] {q.tier});
        play(w, t, SoundEvents.ENTITY_GENERIC_EXPLODE, 1.8f, 0.75f);
        play(w, t, SoundEvents.BLOCK_ANVIL_LAND, 1.2f, 0.5f);
        play(w, t, SoundEvents.ENTITY_ZOMBIE_BREAK_DOOR_WOOD, 1.2f, 0.6f);
        if (!SteamDash.delayedThunder) play(w, t, SoundEvents.ENTITY_LIGHTNING_THUNDER, 2.0f, 0.75f);
        play(w, t, SteamDash.IMPACT, 2.0f, 0.9f);
        play(w, t, SteamDash.LAUNCH, 1.6f, 0.9f);
    }

    private static boolean boss(EntityLivingBase t) { return t instanceof EntityLiving && !t.isNonBoss(); }

    private static void launchBody(Seq q, double[] v) {
        EntityLivingBase t = q.target;
        q.flight = v;
        t.motionX = v[0]; t.motionY = v[1]; t.motionZ = v[2]; t.velocityChanged = true;
        t.fallDistance = 0;
        q.boil = DashMath.LAUNCH;
        q.splattered = false;
        t.getEntityData().setInteger(BOIL_TAG, q.boil);
        q.lastT = new Vec3d(t.posX, t.posY, t.posZ);
        q.phase = Phase.LAUNCH; q.phaseAge = 0;
        sweep(q, t.getEntityBoundingBox(), v[0] * 1.6, v[1] * 1.6, v[2] * 1.6, 0.4 + DashMath.tierMargin(q.tier), Integer.MIN_VALUE);
    }

    private static void exitCone(WorldServer w, EntityLivingBase t, double[] look) {
        Vec3d back = new Vec3d(t.posX, t.posY + t.height / 2, t.posZ);
        for (int i = 0; i < 18; i++) {
            double[] c = cone(look, 0.45);
            double d = 0.8 + RAND.nextDouble() * 3.5, sp = 0.6 + RAND.nextDouble() * 0.8;
            if (i % 3 == 0) w.spawnParticle(EnumParticleTypes.EXPLOSION_LARGE, true, back.x + c[0] * d, back.y + c[1] * d, back.z + c[2] * d, 1, 0, 0, 0, 0.0);
            w.spawnParticle(EnumParticleTypes.CLOUD, true, back.x + c[0], back.y + c[1], back.z + c[2], 0, c[0] * sp, c[1] * sp, c[2] * sp, 1.0);
        }
    }

    /** A random unit vector within `spread` radians of a direction. */
    private static double[] cone(double[] d, double spread) {
        double[] u = DashMath.ring(d, 1, 1)[0], v = DashMath.cross(d, u);
        double ang = RAND.nextDouble() * 2 * Math.PI, off = Math.tan(RAND.nextDouble() * spread), c = Math.cos(ang) * off, s = Math.sin(ang) * off;
        return DashMath.norm(new double[] {d[0] + u[0] * c + v[0] * s, d[1] + u[1] * c + v[1] * s, d[2] + u[2] * c + v[2] * s});
    }

    private static void sphere(WorldServer w, Vec3d c, double r, int n) {
        double golden = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < n; i++) {
            double y = 1 - 2.0 * (i + 0.5) / n, rad = Math.sqrt(1 - y * y), th = golden * i;
            double dx = Math.cos(th) * rad, dz = Math.sin(th) * rad;
            w.spawnParticle(i % 3 == 0 ? EnumParticleTypes.EXPLOSION_NORMAL : EnumParticleTypes.CLOUD, true, c.x + dx * r, c.y + y * r, c.z + dz * r, 0, dx * 0.5, y * 0.5, dz * 0.5, 1.0);
        }
    }

    /** Speed in blocks per tick (players move themselves, so use their position change) and its direction. */
    private static double speedOf(Seq q, EntityLivingBase t, Vec3d now, double[] dirOut) {
        Vec3d moved = now.subtract(q.lastT);
        boolean player = t instanceof EntityPlayerMP;
        double mx = player ? moved.x : t.motionX, my = player ? moved.y : t.motionY, mz = player ? moved.z : t.motionZ;
        double speed = Math.sqrt(mx * mx + my * my + mz * mz);
        if (speed > 1e-3) { dirOut[0] = mx / speed; dirOut[1] = my / speed; dirOut[2] = mz / speed; }
        else System.arraycopy(DashMath.norm(q.flight), 0, dirOut, 0, 3);
        return speed;
    }

    private static void launch(Seq q) {
        EntityLivingBase t = q.target; WorldServer w = q.world;
        if (q.boil > 0) q.boil--;
        t.getEntityData().setInteger(BOIL_TAG, q.boil);
        Vec3d now = new Vec3d(t.posX, t.posY, t.posZ);
        double[] dir = new double[3];
        double speed = speedOf(q, t, now, dir);
        if (q.phaseAge == 1) { speed = Math.max(speed, DashMath.LAUNCH_SPEED); System.arraycopy(DashMath.norm(q.flight), 0, dir, 0, 3); }
        Vec3d c = now.add(0, t.height / 2, 0);
        // Boil thrust: the steam keeps driving them for the whole boil instead of vanilla air drag killing the flight.
        if (q.boil > 0 && !q.splattered && q.phaseAge > 1) {
            double want = DashMath.boilSpeed(q.phaseAge, boss(t)) * DashMath.tierLaunch(q.tier);
            double hx = q.flight[0], hz = q.flight[2], hl = Math.sqrt(hx * hx + hz * hz);
            double cur = Math.sqrt(t.motionX * t.motionX + t.motionZ * t.motionZ);
            if (hl > 1e-4 && (t instanceof EntityPlayerMP || cur < want)) { t.motionX = hx / hl * want; t.motionZ = hz / hl * want; t.velocityChanged = true; }
        }
        if (q.boil > 0 && t.collidedHorizontally && !q.splattered && q.phaseAge > 1) splatter(q);
        else if (q.boil > 0 && DashMath.smashing(speed)) {
            double s1 = Math.max(speed, 1.2);
            sweep(q, t.getEntityBoundingBox(), dir[0] * s1 * 1.6, dir[1] * s1 * 1.6, dir[2] * s1 * 1.6, 0.2 + RAND.nextDouble() * 0.6 + DashMath.tierMargin(q.tier), Integer.MIN_VALUE);
            w.spawnParticle(EnumParticleTypes.CLOUD, true, c.x, c.y, c.z, 6, 0.4, 0.4, 0.4, 0.02);
            if (q.phaseAge % 2 == 0) w.spawnParticle(EnumParticleTypes.FLAME, true, c.x, c.y, c.z, 4, 0.3, 0.3, 0.3, 0.01);
            if (q.phaseAge % 4 == 0) play(w, t, SoundEvents.ITEM_ELYTRA_FLYING, 0.6f, 1.6f);
        }
        flushDebris(q);
        q.lastT = now;
        boolean stopped = q.phaseAge > 3 && speed < 0.15;
        if (q.boil <= 0 && (stopped || t.onGround || q.phaseAge > DashMath.LAUNCH + 20)) q.phase = Phase.DONE;
    }

    /** Wall-splatter finisher: a blast (no terrain damage) and a permanent spiderweb of cracked stone. */
    private static void splatter(Seq q) {
        EntityLivingBase t = q.target; WorldServer w = q.world;
        q.splattered = true;
        boolean alongX = Math.abs(q.flight[0]) >= Math.abs(q.flight[2]);
        int sx = alongX ? (int) Math.signum(q.flight[0]) : 0, sz = alongX ? 0 : (int) Math.signum(q.flight[2]);
        if (sx == 0 && sz == 0) sz = 1;
        double cx = t.posX, cy = t.posY + t.height / 2, cz = t.posZ;
        w.createExplosion(q.attacker, cx, cy, cz, 2.0f, false);
        BlockPos centre = new BlockPos(cx + sx * (t.width / 2 + 0.5), cy, cz + sz * (t.width / 2 + 0.5));
        int ux = -sz, uz = sx;   // along the wall
        long seed = w.getTotalWorldTime() * 31 + t.getEntityId();
        int r = (int) DashMath.SPLATTER_RADIUS;
        for (int a = -r; a <= r; a++) for (int b = -r; b <= r; b++) {
            int kind = DashMath.splatter(a, b, seed);
            if (kind == 0) continue;
            for (int depth = 0; depth <= 1; depth++) {   // the first solid layer of the wall
                BlockPos p = centre.add(ux * a + sx * depth, b, uz * a + sz * depth);
                if (w.getBlockState(p).getMaterial().blocksMovement()) { crack(w, q.attacker, p, kind); break; }
            }
        }
        t.hurtResistantTime = 0;
        t.attackEntityFrom(DamageSource.causePlayerDamage(q.attacker), SteamDash.wallDamage);
        t.motionX *= 0.1; t.motionZ *= 0.1; t.velocityChanged = true;
        w.spawnParticle(EnumParticleTypes.EXPLOSION_LARGE, true, cx, cy, cz, 4, 0.8, 0.8, 0.8, 0);
        w.spawnParticle(EnumParticleTypes.BLOCK_DUST, true, centre.getX() + 0.5, centre.getY() + 0.5, centre.getZ() + 0.5, 60, 1.6, 1.6, 1.6, 0.15, Block.getStateId(Blocks.STONE.getDefaultState()));
        send(q, DashNet.SPLATTER, centre.getX() + 0.5, centre.getY() + 0.5, centre.getZ() + 0.5, new double[] {sx, 0, sz}, null);
        play(w, t, SteamDash.CRASH, 2.0f, 0.8f);
        play(w, t, SoundEvents.BLOCK_ANVIL_LAND, 1.0f, 0.6f);
    }

    // -- juggle: relays and the tail pin --

    /** A press while they fly: sneak (or after two relays) pins and spikes; otherwise blink ahead and punch them back. */
    private static void juggle(Seq q) {
        if (q.phase != Phase.LAUNCH || q.boil <= 0 || q.phaseAge > DashMath.SPIKE_WINDOW || q.spiked) return;
        if (q.attacker.isSneaking() || !SteamDash.juggle || q.relays >= DashMath.RELAYS) pinStart(q);
        else relay(q);
    }

    private static boolean pay(Seq q, int cost, String what) {
        NBTTagCompound s = BijuuArmor.state(q.attacker);
        if (s.getInteger("energy") < cost) { BijuuArmor.tell(q.attacker, "Need " + cost + " resonance for the " + what + "."); return false; }
        s.setInteger("energy", s.getInteger("energy") - cost);
        return true;
    }

    private static void relay(Seq q) {
        if (!pay(q, SteamDash.relayCost, "relay")) return;
        EntityPlayerMP a = q.attacker; EntityLivingBase t = q.target; WorldServer w = q.world;
        q.relays++;
        double[] heading = heading(q, t);
        double[] spot = DashMath.relaySpot(t.posX, t.posY, t.posZ, heading);
        // Make room where you appear, then face back at them.
        sweep(q, new AxisAlignedBB(spot[0] - 0.3, spot[1], spot[2] - 0.3, spot[0] + 0.3, spot[1] + 1.8, spot[2] + 0.3), 0, 0, 0, 0.2, Integer.MIN_VALUE);
        float[] face = DashMath.lookAngles(spot[0], spot[1] + 1.6, spot[2], t.posX, t.posY + t.height / 2, t.posZ);
        Vec3d from = new Vec3d(a.posX, a.posY, a.posZ);
        a.connection.setPlayerLocation(spot[0], spot[1], spot[2], face[0], face[1]);
        hover(q, a, true);
        a.swingArm(EnumHand.MAIN_HAND);
        q.frozenA = new Vec3d(spot[0], spot[1], spot[2]); q.frozenT = new Vec3d(t.posX, t.posY, t.posZ);
        q.hit = q.frozenT.add(0, t.height / 2, 0);
        hover(q, t, true);
        FROZEN.add(t.getEntityId());
        t.hurtResistantTime = 0;
        t.attackEntityFrom(DamageSource.causePlayerDamage(a), (float) (SteamDash.relayDamage * DashMath.tierDamage(q.tier)));
        q.phase = Phase.RELAY; q.phaseAge = 0;
        FALL_SAFE.put(a.getUniqueID(), w.getTotalWorldTime() + 120);
        send(q, DashNet.RELAY, q.hit.x, q.hit.y, q.hit.z, new double[] {from.x, from.y, from.z}, new int[] {q.relays});
        play(w, t, SteamDash.RELAY, 1.8f, 1.0f);
        play(w, t, SteamDash.IMPACT, 1.5f, 1.25f);
        play(w, t, SoundEvents.BLOCK_ANVIL_PLACE, 1.0f, 2.0f);
    }

    private static void relayHold(Seq q) {
        EntityPlayerMP a = q.attacker; EntityLivingBase t = q.target; WorldServer w = q.world;
        a.motionX = a.motionY = a.motionZ = 0; a.velocityChanged = true;
        a.connection.setPlayerLocation(q.frozenA.x, q.frozenA.y, q.frozenA.z, a.rotationYaw, a.rotationPitch);
        hold(t, q.frozenT);
        if (q.phaseAge < DashMath.RELAY_FREEZE) return;
        FROZEN.remove(t.getEntityId());
        hover(q, t, false);
        hover(q, a, false);
        double[] v = DashMath.relayLaunch(q.flight, boss(t));
        double m = DashMath.tierLaunch(q.tier);
        v = new double[] {v[0] * m, v[1], v[2] * m};
        q.look = DashMath.norm(new double[] {v[0], 0, v[2]});
        launchBody(q, v);
        w.spawnParticle(EnumParticleTypes.EXPLOSION_HUGE, true, q.hit.x, q.hit.y, q.hit.z, 1, 0, 0, 0, 0.0);
        exitCone(w, t, q.look);
        send(q, DashNet.RELEASE, t.posX, t.posY, t.posZ, v, new int[] {q.tier, q.relays});
        play(w, t, SoundEvents.ENTITY_GENERIC_EXPLODE, 1.6f, 0.85f);
        play(w, t, SteamDash.LAUNCH, 1.4f, 1.0f);
    }

    private static double[] heading(Seq q, EntityLivingBase t) {
        if (t instanceof EntityPlayerMP && q.lastT != null) { Vec3d m = new Vec3d(t.posX, t.posY, t.posZ).subtract(q.lastT); if (m.lengthSquared() > 1e-4) return new double[] {m.x, m.y, m.z}; }
        else if (t.motionX * t.motionX + t.motionZ * t.motionZ > 1e-4) return new double[] {t.motionX, t.motionY, t.motionZ};
        return q.flight;
    }

    /** The tail pin: you appear above and behind them, they hang in the air while nine tails spear the ground around them. */
    private static void pinStart(Seq q) {
        if (!pay(q, SteamDash.spikeCost, "spike")) return;
        EntityPlayerMP a = q.attacker; EntityLivingBase t = q.target; WorldServer w = q.world;
        q.spiked = true;
        q.flight = heading(q, t);
        double[] spot = DashMath.spikeSpot(t.posX, t.posY, t.posZ, q.flight);
        float yaw = (float) Math.toDegrees(Math.atan2(-(t.posX - spot[0]), t.posZ - spot[2]));
        send(q, DashNet.SPIKE, a.posX, a.posY, a.posZ, spot, new int[] {(int) Math.round(t.posX * 8), (int) Math.round(t.posY * 8), (int) Math.round(t.posZ * 8)});
        a.connection.setPlayerLocation(spot[0], spot[1], spot[2], yaw, 70f);
        hover(q, a, true);
        q.frozenA = new Vec3d(spot[0], spot[1], spot[2]); q.frozenT = new Vec3d(t.posX, t.posY, t.posZ);
        hover(q, t, true);
        FROZEN.add(t.getEntityId());
        q.stabs = new ArrayList<>();
        for (double[] s : DashMath.pinStabs(t.posX, t.posY, t.posZ)) {
            BlockPos top = w.getTopSolidOrLiquidBlock(new BlockPos(s[0], t.posY + 2, s[2]));
            q.stabs.add(new double[] {s[0], Math.min(top.getY(), t.posY), s[2]});
        }
        q.stabIndex = 0;
        q.phase = Phase.PIN; q.phaseAge = 0;
        FALL_SAFE.put(a.getUniqueID(), w.getTotalWorldTime() + 160);
        send(q, DashNet.PIN, t.posX, t.posY, t.posZ, q.flight, null);
        play(w, t, SoundEvents.ENTITY_FIREWORK_LARGE_BLAST, 1.4f, 0.5f);
        play(w, t, SteamDash.HISS, 1.2f, 0.7f);
    }

    private static void pin(Seq q) {
        EntityPlayerMP a = q.attacker; EntityLivingBase t = q.target; WorldServer w = q.world;
        a.motionX = a.motionY = a.motionZ = 0; a.velocityChanged = true;
        a.connection.setPlayerLocation(q.frozenA.x, q.frozenA.y, q.frozenA.z, a.rotationYaw, a.rotationPitch);
        hold(t, q.frozenT);
        // Two stabs a tick until all nine have landed.
        int[] batch = new int[4]; int n = 0;
        for (int k = 0; k < 2 && q.stabIndex < q.stabs.size(); k++, q.stabIndex++) {
            double[] s = q.stabs.get(q.stabIndex);
            BlockPos ground = new BlockPos(s[0], s[1] - 0.5, s[2]);
            IBlockState g = w.getBlockState(ground);
            if (g.getMaterial().blocksMovement()) w.spawnParticle(EnumParticleTypes.BLOCK_CRACK, true, s[0], s[1] + 0.1, s[2], 24, 0.3, 0.2, 0.3, 0.25, Block.getStateId(g));
            w.spawnParticle(EnumParticleTypes.EXPLOSION_NORMAL, true, s[0], s[1] + 0.3, s[2], 4, 0.2, 0.1, 0.2, 0.05);
            batch[n++] = (int) Math.round(s[0] * 8); batch[n++] = (int) Math.round(s[2] * 8);
            play(w, null, SteamDash.STAB, 1.2f, 0.85f + RAND.nextFloat() * 0.3f, ground);
        }
        if (n > 0) send(q, DashNet.STAB, t.posX, q.stabs.get(0)[1], t.posZ, new double[] {0, 0, 0}, java.util.Arrays.copyOf(batch, n));
        if (q.phaseAge < DashMath.PIN) return;
        FROZEN.remove(t.getEntityId());
        hover(q, t, false);
        hover(q, a, false);
        spikeSlam(q);
    }

    private static void spikeSlam(Seq q) {
        EntityPlayerMP a = q.attacker; EntityLivingBase t = q.target; WorldServer w = q.world;
        a.swingArm(EnumHand.MAIN_HAND);
        t.hurtResistantTime = 0;
        t.attackEntityFrom(DamageSource.causePlayerDamage(a), (float) (SteamDash.spikeDamage * DashMath.tierDamage(q.tier)));
        double[] v = DashMath.spikeVelocity(q.flight);
        q.flight = v;
        t.motionX = v[0]; t.motionY = v[1]; t.motionZ = v[2]; t.velocityChanged = true;
        q.lastT = new Vec3d(t.posX, t.posY, t.posZ);
        q.phase = Phase.SPIKE; q.phaseAge = 0;
        send(q, DashNet.PIN, t.posX, t.posY, t.posZ, v, new int[] {1});
        play(w, t, SteamDash.IMPACT, 1.8f, 1.25f);
        play(w, t, SoundEvents.ENTITY_FIREWORK_LARGE_BLAST, 1.4f, 0.5f);
        w.spawnParticle(EnumParticleTypes.EXPLOSION_LARGE, true, t.posX, t.posY + 1, t.posZ, 2, 0.3, 0.3, 0.3, 0);
    }

    private static void spikeFall(Seq q) {
        EntityLivingBase t = q.target; WorldServer w = q.world;
        Vec3d now = new Vec3d(t.posX, t.posY, t.posZ);
        double fell = q.lastT.y - now.y;
        if (!(t instanceof EntityPlayerMP)) { t.motionY = Math.min(t.motionY, -DashMath.SPIKE_SPEED); t.velocityChanged = true; }
        w.spawnParticle(EnumParticleTypes.CLOUD, true, t.posX, t.posY + 1, t.posZ, 6, 0.3, 0.6, 0.3, 0.02);
        flushDebris(q);
        q.lastT = now;
        if (q.phaseAge > 1 && (t.onGround || fell < 0.2) || q.phaseAge > 40) { crater(q); q.phase = Phase.DONE; }
    }

    private static void crater(Seq q) {
        EntityLivingBase t = q.target; WorldServer w = q.world;
        BlockPos centre = new BlockPos(t.posX, t.posY - 0.5, t.posZ);
        int r = SteamDash.craterRadius + (q.tier - 1);
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            int depth = DashMath.craterDepth(Math.sqrt(dx * dx + dz * dz), r);
            for (int y = 0; y < depth; y++) smash(q, centre.add(dx, -y, dz));
        }
        q.debris.clear();
        t.hurtResistantTime = 0;
        t.attackEntityFrom(DamageSource.causePlayerDamage(q.attacker), (float) (SteamDash.craterDamage * DashMath.tierDamage(q.tier)));
        for (EntityLivingBase o : w.getEntitiesWithinAABB(EntityLivingBase.class, new AxisAlignedBB(centre).grow(r, 3, r))) {
            if (o == q.attacker || o == t || !MatatabiPowers.allowed(q.attacker, o)) continue;
            double ox = o.posX - centre.getX(), oz = o.posZ - centre.getZ(), d = Math.max(1, Math.sqrt(ox * ox + oz * oz));
            o.motionX += ox / d * 0.9; o.motionY += 0.55; o.motionZ += oz / d * 0.9; o.velocityChanged = true;
        }
        Vec3d c = new Vec3d(centre.getX() + 0.5, centre.getY() + 1, centre.getZ() + 0.5);
        for (int i = 0; i < 3; i++) w.spawnParticle(EnumParticleTypes.EXPLOSION_HUGE, true, c.x + (i - 1) * 2.5, c.y, c.z, 1, 0, 0, 0, 0.0);
        for (double[] p : DashMath.ring(new double[] {0, 1, 0}, 1.0, 64)) w.spawnParticle(EnumParticleTypes.CLOUD, true, c.x + p[0], c.y, c.z + p[2], 0, p[0] * 0.9, 0.05, p[2] * 0.9, 1.0);
        // The rim's block types for the radial ring of tilted debris (client side).
        int[] ring = new int[24 * 4];
        for (int i = 0; i < 24; i++) {
            double ang = i * Math.PI * 2 / 24;
            BlockPos rim = w.getTopSolidOrLiquidBlock(centre.add(Math.cos(ang) * r, 0, Math.sin(ang) * r)).down();
            IBlockState st = w.getBlockState(rim);
            if (st.getMaterial() == Material.AIR || st.getMaterial().isLiquid()) st = Blocks.DIRT.getDefaultState();
            ring[i * 4] = rim.getX(); ring[i * 4 + 1] = rim.getY(); ring[i * 4 + 2] = rim.getZ(); ring[i * 4 + 3] = Block.getStateId(st);
        }
        send(q, DashNet.CRATER, c.x, c.y, c.z, new double[] {r, 0, 0}, ring);
        play(w, t, SteamDash.IMPACT, 2.5f, 0.8f);
        play(w, t, SteamDash.CRASH, 2.2f, 0.8f);
        play(w, t, SoundEvents.ENTITY_GENERIC_EXPLODE, 2.0f, 0.6f);
        if (!SteamDash.delayedThunder) play(w, t, SoundEvents.ENTITY_LIGHTNING_THUNDER, 2.0f, 0.6f);
    }

    // ---- terrain ----

    /** Clears every block the box touches on its way along (mx, my, mz), grown by `margin`, leaving anything below minY. */
    private static void sweep(Seq q, AxisAlignedBB box, double mx, double my, double mz, double margin, int minY) {
        AxisAlignedBB s = box.union(box.offset(mx, my, mz)).grow(margin);
        int x0 = (int) Math.floor(s.minX), y0 = Math.max(minY, (int) Math.floor(s.minY)), z0 = (int) Math.floor(s.minZ);
        int x1 = (int) Math.floor(s.maxX), y1 = (int) Math.floor(s.maxY), z1 = (int) Math.floor(s.maxZ);
        if ((long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1) > 1200) return;   // never a runaway volume
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) smash(q, new BlockPos(x, y, z));
    }

    /** True when the block is passable afterwards. Protected, unbreakable or storage blocks stay as walls. */
    private static boolean smash(Seq q, BlockPos pos) {
        WorldServer w = q.world;
        if (!w.isBlockLoaded(pos)) return false;
        IBlockState state = w.getBlockState(pos);
        if (state.getBlock().isAir(state, w, pos) || state.getMaterial().isLiquid() || !state.getMaterial().blocksMovement()) return true;
        if (!SteamDash.breakBlocks || q.budget <= 0 || state.getBlock().hasTileEntity(state)) return false;
        if (!DashMath.breakable(state.getBlockHardness(w, pos), SteamDash.maxHardness)) return false;
        if (MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(w, pos, state, q.attacker))) return false;
        if (SteamDash.dropBlocks) state.getBlock().dropBlockAsItem(w, pos, state, 0);
        if (!w.setBlockState(pos, Blocks.AIR.getDefaultState(), 3)) return false;   // Dungeon Guard or another guard refused
        q.budget--;
        q.lining.remove(pos);
        if (q.lining.size() < 900) for (EnumFacing f : EnumFacing.values()) { BlockPos n = pos.offset(f); if (MoltenScars.natural(w.getBlockState(n))) q.lining.add(n); }
        Material m = state.getMaterial();
        boolean rubble = m == Material.ROCK || m == Material.GROUND || m == Material.GRASS || m == Material.SAND || m == Material.CLAY;
        if (rubble && RAND.nextDouble() < DashMath.DEBRIS_SHARE) q.debris.add(new int[] {pos.getX(), pos.getY(), pos.getZ(), Block.getStateId(state)});
        if (RAND.nextInt(6) == 0) play(w, null, SteamDash.CRASH, 0.9f, 0.9f + RAND.nextFloat() * 0.3f, pos);
        return true;
    }

    private static void flushDebris(Seq q) {
        if (q.debris.isEmpty()) return;
        int n = Math.min(12, q.debris.size());
        int[] out = new int[n * 4];
        for (int i = 0; i < n; i++) System.arraycopy(q.debris.get(i), 0, out, i * 4, 4);
        q.debris.clear();
        // Rubble is flung back toward the attacker: the client uses -look * 0.8 plus a random spread.
        send(q, DashNet.DEBRIS, q.target.posX, q.target.posY, q.target.posZ, q.look, out);
    }

    // ---- output ----

    private static void send(Seq q, int kind, double x, double y, double z, double[] d, int[] data) {
        DashNet.Fx m = new DashNet.Fx(kind, q.attacker.getEntityId(), q.target == null ? -1 : q.target.getEntityId(), x, y, z, d[0], d[1], d[2], data);
        SteamDash.NET.sendToAllAround(m, new NetworkRegistry.TargetPoint(q.world.provider.getDimension(), x, y, z, 160));
    }

    private static void broadcast(EntityPlayerMP p, int kind, double[] d, int[] data) {
        SteamDash.NET.sendToAllAround(new DashNet.Fx(kind, p.getEntityId(), -1, p.posX, p.posY, p.posZ, d[0], d[1], d[2], data),
                new NetworkRegistry.TargetPoint(p.dimension, p.posX, p.posY, p.posZ, 128));
    }

    private static void play(WorldServer w, Entity at, SoundEvent s, float volume, float pitch) { play(w, at, s, volume, pitch, null); }

    private static void play(WorldServer w, Entity at, SoundEvent s, float volume, float pitch, BlockPos pos) {
        if (s == null) return;
        double x = pos != null ? pos.getX() + 0.5 : at.posX, y = pos != null ? pos.getY() + 0.5 : at.posY, z = pos != null ? pos.getZ() + 0.5 : at.posZ;
        w.playSound(null, x, y, z, s, SoundCategory.PLAYERS, volume, pitch);
    }

    private static void finish(Seq q) {
        if (q.attacker != null) hover(q, q.attacker, false);
        List<EntityLivingBase> touched = new ArrayList<>(q.line);
        if (q.target != null) touched.add(q.target);
        for (EntityLivingBase e : touched) {
            FROZEN.remove(e.getEntityId());
            if (q.gravity.containsKey(e.getEntityId())) hover(q, e, false);
            e.getEntityData().removeTag(BOIL_TAG);
        }
        if (q.attacker != null && q.world != null) MoltenScars.scar(q.world, q.attacker, q.lining);
        if (q.attacker != null && q.attacker.connection != null) send(q, DashNet.END, q.attacker.posX, q.attacker.posY, q.attacker.posZ, q.dir, null);
    }

    @SubscribeEvent public void fall(LivingFallEvent e) {
        if (e.getEntityLiving().world.isRemote) return;
        Long until = FALL_SAFE.get(e.getEntityLiving().getUniqueID());
        if (until == null) return;
        if (e.getEntityLiving().world.getTotalWorldTime() <= until) e.setDamageMultiplier(0);
        else FALL_SAFE.remove(e.getEntityLiving().getUniqueID());
    }

    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        Seq q = RUNNING.remove(e.player.getUniqueID());
        if (q != null) finish(q);
        CHARGING.remove(e.player.getUniqueID());
        FALL_SAFE.remove(e.player.getUniqueID());
    }
}
