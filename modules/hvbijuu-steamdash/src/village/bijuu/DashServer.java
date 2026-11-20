package village.bijuu;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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
 * Server-owned Steam Dash Punch. Everything that changes the world happens here: the springboard,
 * dash, damage, hit-stop, boil launch, tunnelling, wall splatter and spike crater. Clients receive
 * cues (DashNet.Fx) for the camera, impact frames, afterimages, mach cone and flying rubble; the
 * particles and sounds below go to everyone nearby.
 */
final class DashServer {
    private static final int KOKUO = 4, KURAMA = 8;
    static final String BOIL_TAG = "hvbBoilLaunchedTicks";
    static final Map<UUID, Seq> RUNNING = new HashMap<>();
    private static final Map<UUID, Long> FALL_SAFE = new HashMap<>();
    static final Set<Integer> FROZEN = new HashSet<>();
    private static final Random RAND = new Random();

    enum Phase { TELEGRAPH, DASH, FREEZE, LAUNCH, SPIKE, DONE }

    static final class Seq {
        EntityPlayerMP attacker; EntityLivingBase target; WorldServer world;
        Phase phase = Phase.TELEGRAPH; int phaseAge, budget, boil, telegraph = DashMath.TELEGRAPH;
        double[] dir = {0, 0, 1}, flight = {0, 0, 1}, look = {0, 0, 1};
        Vec3d frozenA, frozenT, lastT, lastA;
        boolean spiked, sprung, targetNoGravity, splattered;
        List<int[]> debris = new ArrayList<>();
    }

    // ---- request ----

    static void request(EntityPlayerMP p) {
        Seq running = RUNNING.get(p.getUniqueID());
        if (running != null) {
            if (running.phase == Phase.LAUNCH && !running.spiked && running.boil > 0 && running.phaseAge <= DashMath.SPIKE_WINDOW) spike(running);
            return;
        }
        if (!p.isEntityAlive() || p.isSpectator() || p.isRiding() || p.capabilities.isFlying) return;
        if (!channeling(p)) { BijuuArmor.tell(p, "Wear the full Kurama set and carry a Kokuo piece (or the reverse) to channel the steam dash."); return; }
        NBTTagCompound s = BijuuArmor.state(p);
        long now = p.world.getTotalWorldTime();
        double[] wall = wallNormal(p);
        EntityLivingBase target = pick(p);
        boolean dashReady = now >= s.getLong("dashReady");

        if (wall != null && (target == null || !dashReady)) { springboard(p, s, wall, now); return; }
        if (!dashReady) { BijuuArmor.tell(p, "Steam dash recharging: " + ((s.getLong("dashReady") - now + 19) / 20) + "s."); return; }
        if (s.getInteger("energy") < SteamDash.cost) { BijuuArmor.tell(p, "Need " + SteamDash.cost + " resonance for the steam dash."); return; }
        if (target == null) { BijuuArmor.tell(p, "No target in sight."); return; }
        if (BijuuArmor.useNarutoChakra && !ChakraBridge.pay(p)) { BijuuArmor.tell(p, "Not enough chakra."); return; }
        s.setInteger("energy", s.getInteger("energy") - SteamDash.cost);
        s.setLong("dashReady", now + SteamDash.cooldownSeconds * 20L);
        Seq q = new Seq();
        q.attacker = p; q.target = target; q.world = p.getServerWorld(); q.budget = SteamDash.blockBudget;
        q.dir = DashMath.direction(p.posX, p.posY + p.height / 2, p.posZ, target.posX, target.posY + target.height / 2, target.posZ);
        RUNNING.put(p.getUniqueID(), q);
        if (wall != null) {   // kick off the wall straight into the dash: the crack is the telegraph
            q.sprung = true; q.telegraph = 2;
            crackWall(p, wall, true);
            p.motionX = wall[0] * 0.6; p.motionY = 0.25; p.motionZ = wall[2] * 0.6; p.velocityChanged = true;
        }
        send(q, DashNet.START, p.posX, p.posY, p.posZ, q.dir, new int[] {q.telegraph, q.sprung ? 1 : 0});
        play(q.world, p, SoundEvents.BLOCK_FIRE_EXTINGUISH, 1.0f, 1.8f);
        play(q.world, p, SteamDash.HISS, 1.0f, 1.15f);
        play(q.world, p, SteamDash.CHARGE, 0.9f, 1.0f);
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
            if (!MatatabiPowers.allowed(p, e) || e instanceof EntityPlayer && !p.server.isPVPEnabled()) continue;
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
        NetworkRegistry.TargetPoint at = new NetworkRegistry.TargetPoint(p.dimension, p.posX, p.posY, p.posZ, 96);
        SteamDash.NET.sendToAllAround(new DashNet.Fx(DashNet.SPRING, p.getEntityId(), -1, p.posX, p.posY, p.posZ, v[0], v[1], v[2], new int[] {(int) n[0], (int) n[2]}), at);
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

    // ---- sequence ----

    @SubscribeEvent public void tick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote || RUNNING.isEmpty()) return;
        for (Iterator<Seq> it = RUNNING.values().iterator(); it.hasNext();) {
            Seq q = it.next();
            if (q.world != e.world) continue;
            try { step(q); } catch (RuntimeException failure) { q.phase = Phase.DONE; org.apache.logging.log4j.LogManager.getLogger("SteamDash").warn("Steam dash stopped", failure); }
            if (q.phase == Phase.DONE) { finish(q); it.remove(); }
        }
    }

    /** Hit-stop: a frozen mob skips its whole update, so it hangs in its pose like a held frame. */
    @SubscribeEvent public void living(LivingEvent.LivingUpdateEvent e) {
        if (!FROZEN.isEmpty() && !e.getEntityLiving().world.isRemote && !(e.getEntityLiving() instanceof EntityPlayer) && FROZEN.contains(e.getEntityLiving().getEntityId())) e.setCanceled(true);
    }

    private static boolean alive(Entity e, WorldServer w) { return e != null && e.isEntityAlive() && e.world == w; }

    private static void step(Seq q) {
        EntityPlayerMP a = q.attacker; EntityLivingBase t = q.target;
        if (!alive(a, q.world) || a.connection == null) { q.phase = Phase.DONE; return; }
        q.phaseAge++;
        a.fallDistance = 0;
        if (q.phase != Phase.TELEGRAPH && q.phase != Phase.DONE && !alive(t, q.world)) { q.phase = Phase.DONE; return; }
        switch (q.phase) {
            case TELEGRAPH: telegraph(q); break;
            case DASH: dash(q); break;
            case FREEZE: freeze(q); break;
            case LAUNCH: launch(q); break;
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
        // Golden dust spiralling around the feet.
        for (int i = 0; i < 6; i++) {
            double ang = q.phaseAge * 1.1 + i * Math.PI / 3, r = 0.95 - q.phaseAge * 0.12;
            gold(w, a.posX + Math.cos(ang) * r, a.posY + 0.1 + q.phaseAge * 0.12 + i * 0.05, a.posZ + Math.sin(ang) * r);
        }
        if (q.phaseAge >= q.telegraph) {
            q.phase = Phase.DASH; q.phaseAge = 0;
            a.setNoGravity(true);
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

    private static void dash(Seq q) {
        EntityPlayerMP a = q.attacker; EntityLivingBase t = q.target; WorldServer w = q.world;
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
        // Clear a 3x3 tunnel ahead (through walls, not a trench in the ground unless the dash dives).
        int floor = q.dir[1] < -0.35 ? Integer.MIN_VALUE : (int) Math.floor(from.y - a.height / 2 + 0.01);
        AxisAlignedBB body = new AxisAlignedBB(from.x - 0.3, from.y - a.height / 2, from.z - 0.3, from.x + 0.3, from.y + a.height / 2, from.z + 0.3);
        sweep(q, body, q.dir[0] * (speed + 1.5), q.dir[1] * (speed + 1.5), q.dir[2] * (speed + 1.5), 0.45, floor);
        a.motionX = q.dir[0] * speed; a.motionY = q.dir[1] * speed; a.motionZ = q.dir[2] * speed; a.velocityChanged = true;
        if (real.distanceTo(virt) > 2.5) a.connection.setPlayerLocation(virt.x, virt.y - a.height / 2, virt.z, a.rotationYaw, a.rotationPitch);
        // Wind-tunnel ring of white steam and golden flame behind the dasher.
        double[][] ring = DashMath.ring(q.dir, 1.4, DashMath.RING_POINTS);
        for (int i = 0; i < ring.length; i++) {
            double[] r = ring[i];
            w.spawnParticle(i % 2 == 0 ? EnumParticleTypes.CLOUD : EnumParticleTypes.FLAME, true,
                    virt.x - q.dir[0] * 1.2 + r[0], virt.y - q.dir[1] * 1.2 + r[1], virt.z - q.dir[2] * 1.2 + r[2], 0, r[0] * 0.05, r[1] * 0.05, r[2] * 0.05, 1.0);
        }
        w.spawnParticle(EnumParticleTypes.CLOUD, true, virt.x, virt.y, virt.z, 8, 0.3, 0.3, 0.3, 0.02);
        double[] cp = DashMath.closest(from.x, from.y, from.z, virt.x, virt.y, virt.z, tc.x, tc.y, tc.z);
        double dist = tc.distanceTo(new Vec3d(cp[0], cp[1], cp[2]));
        if (dist <= DashMath.CONTACT || q.phaseAge >= DashMath.MAX_DASH) {
            if (dist > DashMath.CONTACT + 3) { a.setNoGravity(false); q.phase = Phase.DONE; return; }   // the target got away
            Vec3d at = new Vec3d(tc.x - q.dir[0] * 1.3, tc.y - q.dir[1] * 1.3, tc.z - q.dir[2] * 1.3);
            a.connection.setPlayerLocation(at.x, at.y - a.height / 2, at.z, a.rotationYaw, a.rotationPitch);
            impact(q, at, tc);
        }
    }

    /** Contact: the vacuum frame. Damage lands, everything stops, a bell-like ring; the blast comes on release. */
    private static void impact(Seq q, Vec3d ac, Vec3d tc) {
        EntityPlayerMP a = q.attacker; EntityLivingBase t = q.target; WorldServer w = q.world;
        Vec3d hit = ac.add(tc).scale(0.5);
        q.phase = Phase.FREEZE; q.phaseAge = 0;
        q.frozenA = new Vec3d(a.posX, a.posY, a.posZ); q.frozenT = new Vec3d(t.posX, t.posY, t.posZ);
        q.targetNoGravity = t.hasNoGravity();
        t.setNoGravity(true);
        FROZEN.add(t.getEntityId());
        a.swingArm(EnumHand.MAIN_HAND);
        t.hurtResistantTime = 0;
        t.attackEntityFrom(DamageSource.causePlayerDamage(a), SteamDash.punchDamage);
        t.motionX = t.motionY = t.motionZ = 0; t.velocityChanged = true;
        send(q, DashNet.IMPACT, hit.x, hit.y, hit.z, q.dir, null);
        play(w, t, SoundEvents.BLOCK_ANVIL_PLACE, 1.4f, 2.0f);
        play(w, t, SteamDash.IMPACT, 1.4f, 1.3f);
    }

    private static void freeze(Seq q) {
        EntityPlayerMP a = q.attacker; EntityLivingBase t = q.target; WorldServer w = q.world;
        a.motionX = a.motionY = a.motionZ = 0; a.velocityChanged = true;
        a.connection.setPlayerLocation(q.frozenA.x, q.frozenA.y, q.frozenA.z, a.rotationYaw, a.rotationPitch);
        hold(t, q.frozenT);
        Vec3d hit = q.frozenA.add(q.frozenT).scale(0.5).add(0, 1, 0);
        // Pressure building: a tight steam ring that swells each held frame.
        for (double[] r : DashMath.ring(q.dir, 0.6 + q.phaseAge * 0.5, 24)) w.spawnParticle(EnumParticleTypes.CLOUD, true, hit.x + r[0], hit.y + r[1], hit.z + r[2], 0, 0, 0, 0, 1.0);
        if (q.phaseAge >= DashMath.FREEZE) release(q, hit);
    }

    private static void hold(EntityLivingBase t, Vec3d at) {
        t.motionX = t.motionY = t.motionZ = 0; t.velocityChanged = true;
        if (t instanceof EntityPlayerMP) ((EntityPlayerMP) t).connection.setPlayerLocation(at.x, at.y, at.z, t.rotationYaw, t.rotationPitch);
        else t.setPosition(at.x, at.y, at.z);
    }

    /** End of hit-stop: the explosion lands, the exit cone bursts out of their back and they fly along your look. */
    private static void release(Seq q, Vec3d hit) {
        EntityPlayerMP a = q.attacker; EntityLivingBase t = q.target; WorldServer w = q.world;
        a.setNoGravity(false);
        FROZEN.remove(t.getEntityId());
        t.setNoGravity(q.targetNoGravity);
        Vec3d lv = a.getLook(1f);
        q.look = new double[] {lv.x, lv.y, lv.z};
        boolean boss = t instanceof EntityLiving && !t.isNonBoss();
        double[] v = DashMath.launch(q.look, boss);
        q.flight = v;
        t.motionX = v[0]; t.motionY = v[1]; t.motionZ = v[2]; t.velocityChanged = true;
        t.fallDistance = 0;
        q.boil = DashMath.LAUNCH;
        t.getEntityData().setInteger(BOIL_TAG, q.boil);
        q.lastT = new Vec3d(t.posX, t.posY, t.posZ);
        q.phase = Phase.LAUNCH; q.phaseAge = 0;
        sweep(q, t.getEntityBoundingBox(), v[0] * 1.6, v[1] * 1.6, v[2] * 1.6, 0.4, Integer.MIN_VALUE);
        FALL_SAFE.put(a.getUniqueID(), w.getTotalWorldTime() + 100);
        // Shockwave and the delayed exit blast out of their back.
        w.spawnParticle(EnumParticleTypes.EXPLOSION_HUGE, true, hit.x, hit.y, hit.z, 1, 0, 0, 0, 0.0);
        sphere(w, hit, 1.2, 40);
        double[] look = DashMath.norm(q.look);
        Vec3d back = new Vec3d(t.posX, t.posY + t.height / 2, t.posZ);
        for (int i = 0; i < 18; i++) {
            double[] c = cone(look, 0.45);
            double d = 0.8 + RAND.nextDouble() * 3.5, sp = 0.6 + RAND.nextDouble() * 0.8;
            if (i % 3 == 0) w.spawnParticle(EnumParticleTypes.EXPLOSION_LARGE, true, back.x + c[0] * d, back.y + c[1] * d, back.z + c[2] * d, 1, 0, 0, 0, 0.0);
            w.spawnParticle(EnumParticleTypes.CLOUD, true, back.x + c[0], back.y + c[1], back.z + c[2], 0, c[0] * sp, c[1] * sp, c[2] * sp, 1.0);
        }
        send(q, DashNet.RELEASE, t.posX, t.posY, t.posZ, v, null);
        play(w, t, SoundEvents.ENTITY_GENERIC_EXPLODE, 1.8f, 0.75f);
        play(w, t, SoundEvents.BLOCK_ANVIL_LAND, 1.2f, 0.5f);
        play(w, t, SoundEvents.ENTITY_ZOMBIE_BREAK_DOOR_WOOD, 1.2f, 0.6f);
        play(w, t, SoundEvents.ENTITY_LIGHTNING_THUNDER, 2.0f, 0.75f);
        play(w, t, SteamDash.IMPACT, 2.0f, 0.9f);
        play(w, t, SteamDash.LAUNCH, 1.6f, 0.9f);
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
            double want = DashMath.boilSpeed(q.phaseAge, t instanceof EntityLiving && !t.isNonBoss());
            double hx = q.flight[0], hz = q.flight[2], hl = Math.sqrt(hx * hx + hz * hz);
            double cur = Math.sqrt(t.motionX * t.motionX + t.motionZ * t.motionZ);
            if (hl > 1e-4 && (t instanceof EntityPlayerMP || cur < want)) { t.motionX = hx / hl * want; t.motionZ = hz / hl * want; t.velocityChanged = true; }
        }
        if (q.boil > 0 && t.collidedHorizontally && !q.splattered && q.phaseAge > 1) splatter(q);
        else if (q.boil > 0 && DashMath.smashing(speed)) {
            // Randomized 2x2 to 3x3 rubble around their body and along the path ahead.
            double s1 = Math.max(speed, 1.2);
            sweep(q, t.getEntityBoundingBox(), dir[0] * s1 * 1.6, dir[1] * s1 * 1.6, dir[2] * s1 * 1.6, 0.2 + RAND.nextDouble() * 0.6, Integer.MIN_VALUE);
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

    private static void spike(Seq q) {
        EntityPlayerMP a = q.attacker; EntityLivingBase t = q.target; WorldServer w = q.world;
        NBTTagCompound s = BijuuArmor.state(a);
        if (s.getInteger("energy") < SteamDash.spikeCost) { BijuuArmor.tell(a, "Need " + SteamDash.spikeCost + " resonance for the spike."); return; }
        s.setInteger("energy", s.getInteger("energy") - SteamDash.spikeCost);
        q.spiked = true;
        double[] heading = q.flight;
        if (t instanceof EntityPlayerMP && q.lastT != null) { Vec3d m = new Vec3d(t.posX, t.posY, t.posZ).subtract(q.lastT); if (m.lengthSquared() > 1e-4) heading = new double[] {m.x, m.y, m.z}; }
        else if (t.motionX * t.motionX + t.motionZ * t.motionZ > 1e-4) heading = new double[] {t.motionX, t.motionY, t.motionZ};
        double[] spot = DashMath.spikeSpot(t.posX, t.posY, t.posZ, heading);
        float yaw = (float) Math.toDegrees(Math.atan2(-(t.posX - spot[0]), t.posZ - spot[2]));
        send(q, DashNet.SPIKE, a.posX, a.posY, a.posZ, spot, new int[] {(int) Math.round(t.posX * 8), (int) Math.round(t.posY * 8), (int) Math.round(t.posZ * 8)});
        a.connection.setPlayerLocation(spot[0], spot[1], spot[2], yaw, 70f);
        a.swingArm(EnumHand.MAIN_HAND);
        t.hurtResistantTime = 0;
        t.attackEntityFrom(DamageSource.causePlayerDamage(a), SteamDash.spikeDamage);
        double[] v = DashMath.spikeVelocity(heading);
        q.flight = v;
        t.motionX = v[0]; t.motionY = v[1]; t.motionZ = v[2]; t.velocityChanged = true;
        q.lastT = new Vec3d(t.posX, t.posY, t.posZ);
        q.phase = Phase.SPIKE; q.phaseAge = 0;
        play(w, t, SteamDash.IMPACT, 1.8f, 1.25f);
        play(w, t, SoundEvents.ENTITY_FIREWORK_LARGE_BLAST, 1.4f, 0.5f);
        play(w, t, SoundEvents.ENTITY_LIGHTNING_THUNDER, 1.6f, 1.1f);
        w.spawnParticle(EnumParticleTypes.EXPLOSION_LARGE, true, t.posX, t.posY + 1, t.posZ, 2, 0.3, 0.3, 0.3, 0);
        FALL_SAFE.put(a.getUniqueID(), w.getTotalWorldTime() + 120);
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
        int r = SteamDash.craterRadius;
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            int depth = DashMath.craterDepth(Math.sqrt(dx * dx + dz * dz), r);
            for (int y = 0; y < depth; y++) smash(q, centre.add(dx, -y, dz));
        }
        q.debris.clear();
        t.hurtResistantTime = 0;
        t.attackEntityFrom(DamageSource.causePlayerDamage(q.attacker), SteamDash.craterDamage);
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
        play(w, t, SoundEvents.ENTITY_LIGHTNING_THUNDER, 2.0f, 0.6f);
    }

    // ---- terrain ----

    /** Clears every block the box touches on its way along (mx, my, mz), grown by `margin`, leaving anything below minY. */
    private static void sweep(Seq q, AxisAlignedBB box, double mx, double my, double mz, double margin, int minY) {
        AxisAlignedBB s = box.union(box.offset(mx, my, mz)).grow(margin);
        int x0 = (int) Math.floor(s.minX), y0 = Math.max(minY, (int) Math.floor(s.minY)), z0 = (int) Math.floor(s.minZ);
        int x1 = (int) Math.floor(s.maxX), y1 = (int) Math.floor(s.maxY), z1 = (int) Math.floor(s.maxZ);
        if ((long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1) > 600) return;   // never a runaway volume
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) smash(q, new BlockPos(x, y, z));
    }

    /** Clears a size x size cross-section (2 or 3) at a point. Returns false if something solid could not be cleared. */
    private static boolean carve(Seq q, double x, double y, double z, double[] dir, int size) { return carve(q, x, y, z, dir, size, Integer.MIN_VALUE); }

    /** As above, leaving everything below minY alone. */
    private static boolean carve(Seq q, double x, double y, double z, double[] dir, int size, int minY) {
        boolean open = true;
        for (int[] c : DashMath.box(dir, size)) {
            BlockPos pos = new BlockPos(Math.floor(x) + c[0], Math.floor(y) + c[1], Math.floor(z) + c[2]);
            if (pos.getY() >= minY) open &= smash(q, pos);
        }
        return open;
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

    private static void play(WorldServer w, Entity at, SoundEvent s, float volume, float pitch) { play(w, at, s, volume, pitch, null); }

    private static void play(WorldServer w, Entity at, SoundEvent s, float volume, float pitch, BlockPos pos) {
        if (s == null) return;
        double x = pos != null ? pos.getX() + 0.5 : at.posX, y = pos != null ? pos.getY() + 0.5 : at.posY, z = pos != null ? pos.getZ() + 0.5 : at.posZ;
        w.playSound(null, x, y, z, s, SoundCategory.PLAYERS, volume, pitch);
    }

    private static void finish(Seq q) {
        if (q.attacker != null) q.attacker.setNoGravity(false);
        if (q.target != null) {
            FROZEN.remove(q.target.getEntityId());
            if (!q.targetNoGravity) q.target.setNoGravity(false);
            q.target.getEntityData().removeTag(BOIL_TAG);
        }
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
        FALL_SAFE.remove(e.player.getUniqueID());
    }
}
