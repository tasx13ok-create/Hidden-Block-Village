package village.bijuu;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;

/**
 * The Gyuki Lariat, server side: a four-beat state machine. Wear the full Gyuki set and press the
 * signature key. Everything that changes the world is here; clients get one cue per beat.
 *
 *   1 ANTICIPATION  rooted for 6 ticks, Resistance V, the ground under you cracks (temporarily)
 *   2 BLINK         one tick: along your look up to 18 blocks in half-block steps, crashing through
 *                   glass, leaves, plants, snow and fences (temporarily), stopping at the first enemy
 *                   or the first solid block
 *   3 HIT-STOP      4 ticks: both of you frozen in place, 24 damage on contact
 *   4 RELEASE       the victim rockets away (3.4 out, 0.85 up) through soft blocks, gouging a trench
 *                   in loose ground; you skid 1.5 blocks with your arm still out, venting steam
 * Every block it touches comes back after `regenSeconds` (TerrainRegen).
 */
final class LariatServer {
    static final int GYUKI = 7;
    static final Map<UUID, L> RUNNING = new HashMap<>();
    private static final Map<UUID, Long> FALL_SAFE = new HashMap<>();

    static final class L {
        EntityPlayerMP caster; WorldServer world; int stage = 1, age;
        double[] dir; Vec3d hold, start, end, contact, victimHold;
        EntityLivingBase victim; boolean victimNoGravity, casterNoGravity;
        double skid; int trench, broken; double[] vel;
    }

    static boolean wearing(EntityPlayer p) { return BijuuArmor.full(p) == GYUKI; }

    static void press(EntityPlayerMP p) {
        if (RUNNING.containsKey(p.getUniqueID())) return;
        if (!p.isEntityAlive() || p.isSpectator() || p.isRiding() || p.capabilities.isFlying) return;
        NBTTagCompound s = BijuuArmor.state(p);
        long now = p.world.getTotalWorldTime();
        if (now < s.getLong("lariatReady")) { BijuuArmor.tell(p, "Lariat recharging: " + ((s.getLong("lariatReady") - now + 19) / 20) + "s."); return; }
        if (s.getInteger("energy") < SteamDash.lariatCost) { BijuuArmor.tell(p, "Need " + SteamDash.lariatCost + " resonance for the lariat."); return; }
        if (BijuuArmor.useNarutoChakra && !ChakraBridge.pay(p)) { BijuuArmor.tell(p, "Not enough chakra."); return; }
        s.setInteger("energy", s.getInteger("energy") - SteamDash.lariatCost);
        s.setLong("lariatReady", now + SteamDash.lariatCooldownSeconds * 20L);
        L l = new L();
        l.caster = p; l.world = p.getServerWorld();
        Vec3d look = p.getLook(1f);
        l.dir = LariatMath.blinkDir(look.x, look.y, look.z);
        l.hold = new Vec3d(p.posX, p.posY, p.posZ);
        RUNNING.put(p.getUniqueID(), l);
        send(l, DashNet.LARIAT_START, p.posX, p.posY, p.posZ, l.dir, null);
        play(l.world, p.posX, p.posY, p.posZ, SteamDash.LARIAT_CHARGE, 1.4f, 1.0f);
        play(l.world, p.posX, p.posY, p.posZ, SoundEvents.ENTITY_LIGHTNING_THUNDER, 0.5f, 1.6f);
    }

    @SubscribeEvent public void tick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote || RUNNING.isEmpty()) return;
        for (Iterator<L> it = RUNNING.values().iterator(); it.hasNext();) {
            L l = it.next();
            if (l.world != e.world) continue;
            boolean done;
            try { done = step(l); } catch (RuntimeException x) { done = true; org.apache.logging.log4j.LogManager.getLogger("SteamDash").warn("Lariat stopped", x); }
            if (done) { finish(l); it.remove(); }
        }
    }

    /** Returns true when the lariat is over. */
    private static boolean step(L l) {
        EntityPlayerMP p = l.caster;
        if (!p.isEntityAlive() || p.world != l.world || p.connection == null) return true;
        l.age++;
        p.fallDistance = 0;
        switch (l.stage) {
            case 1: anticipation(l); break;
            case 3: hitstop(l); break;
            case 4: return release(l);
            default: break;
        }
        return false;
    }

    // ---- beat 1: anticipation ----

    private static void anticipation(L l) {
        EntityPlayerMP p = l.caster; WorldServer w = l.world;
        p.motionX = 0; p.motionZ = 0; p.velocityChanged = true;
        p.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE, 12, 4, true, false));
        double yaw = Math.toRadians(p.renderYawOffset);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        // Crimson pressure erupting upward, and the ground starting to give.
        for (int i = 0; i < 10; i++) {
            double a = l.age * 0.9 + i * 0.63, r = 0.5 + Math.random() * 0.4;
            w.spawnParticle(EnumParticleTypes.REDSTONE, true, p.posX + Math.cos(a) * r, p.posY + Math.random() * 2.2, p.posZ + Math.sin(a) * r, 0, 0.85, 0.05, 0.08, 1.0);
        }
        w.spawnParticle(EnumParticleTypes.CLOUD, true, p.posX - fx * 0.3, p.posY + 0.9, p.posZ - fz * 0.3, 0, -fx * 0.25, 0.25, -fz * 0.25, 1.0);
        IBlockState below = w.getBlockState(new BlockPos(p.posX, p.posY - 0.2, p.posZ));
        if (below.getMaterial().blocksMovement())
            w.spawnParticle(EnumParticleTypes.BLOCK_CRACK, true, p.posX, p.posY + 0.05, p.posZ, 6 + l.age * 2, 0.3 + l.age * 0.12, 0, 0.3 + l.age * 0.12, 0.12, Block.getStateId(below));
        if (l.age == 2 && SteamDash.lariatTerrain) {
            BlockPos feet = new BlockPos(p.posX, p.posY - 0.5, p.posZ);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                if (Math.abs(dx) + Math.abs(dz) == 2 && Math.random() < 0.5) continue;
                BlockPos b = feet.add(dx, 0, dz);
                IBlockState c = TerrainRegen.cracked(w.getBlockState(b));
                if (c != null) TerrainRegen.replace(w, b, c, p);
            }
            play(w, p.posX, p.posY, p.posZ, SoundEvents.BLOCK_STONE_BREAK, 1.2f, 0.5f);
        }
        if (l.age >= LariatMath.ANTICIPATION) blink(l);
    }

    // ---- beat 2: the blink ----

    private static void blink(L l) {
        EntityPlayerMP p = l.caster; WorldServer w = l.world;
        l.start = new Vec3d(p.posX, p.posY, p.posZ);
        double chest = 1.0;
        double[][] path = LariatMath.path(p.posX, p.posY + chest, p.posZ, l.dir, SteamDash.lariatRange);
        double[] last = {p.posX, p.posY + chest, p.posZ};
        EntityLivingBase hit = null; double[] hitAt = null;
        double lift = 0;   // steps up onto ledges as it goes, like a sprint over rough ground
        for (double[] raw : path) {
            double[] pt = {raw[0], raw[1] + lift, raw[2]};
            // The two blocks the caster's body fills at this point; crash through anything soft in them,
            // step up onto a one-block ledge, stop short of anything else solid.
            double feet = pt[1] - chest;
            BlockPos legs = new BlockPos(pt[0], feet + 0.2, pt[2]), body = new BlockPos(pt[0], feet + 1.2, pt[2]);
            IBlockState ls = w.getBlockState(legs);
            if (ls.getMaterial().blocksMovement() && !TerrainRegen.soft(ls) && lift < 3 && clear(w, body) && clear(w, body.up())) {
                lift += 1; pt[1] += 1; feet += 1;
                legs = body; body = body.up();
            }
            boolean blocked = false;
            for (BlockPos b : new BlockPos[] {legs, body}) {
                IBlockState s = w.getBlockState(b);
                if (!s.getMaterial().blocksMovement() && !TerrainRegen.soft(s)) continue;
                if (TerrainRegen.soft(s) && SteamDash.lariatTerrain && TerrainRegen.breakTemporarily(w, b, p)) {
                    l.broken++;
                    w.spawnParticle(EnumParticleTypes.BLOCK_CRACK, true, b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5, 10, 0.3, 0.3, 0.3, 0.2, Block.getStateId(s));
                    continue;
                }
                if (s.getMaterial().blocksMovement()) blocked = true;
            }
            if (blocked) break;
            EntityLivingBase e = victimAt(p, pt);
            if (e != null) { hit = e; hitAt = new double[] {e.posX, e.posY + e.height * 0.6, e.posZ}; break; }
            last = pt;
        }
        double[] land = hit != null ? LariatMath.landing(hitAt, l.dir) : last;
        double endY = land[1] - chest;
        if (w.getBlockState(new BlockPos(land[0], endY, land[2])).getMaterial().blocksMovement()) endY = Math.floor(endY) + 1;   // never inside the ground
        l.end = new Vec3d(land[0], endY, land[2]);
        p.connection.setPlayerLocation(l.end.x, l.end.y, l.end.z, p.rotationYaw, p.rotationPitch);
        p.swingArm(EnumHand.MAIN_HAND);
        send(l, DashNet.LARIAT_BLINK, l.start.x, l.start.y, l.start.z, new double[] {l.end.x, l.end.y, l.end.z}, new int[] {hit == null ? -1 : hit.getEntityId(), l.broken});
        play(w, l.start.x, l.start.y, l.start.z, SteamDash.LARIAT_BLINK, 1.8f, 1.0f);
        play(w, l.start.x, l.start.y, l.start.z, SoundEvents.ENTITY_FIREWORK_LARGE_BLAST, 1.2f, 0.6f);
        play(w, l.end.x, l.end.y, l.end.z, SteamDash.BLINK, 1.2f, 1.3f);
        // Vacuum at the take-off point and a shock ring rolling out along the ground.
        for (double[] r : DashMath.ring(new double[] {0, 1, 0}, 1.0, 28))
            w.spawnParticle(EnumParticleTypes.CLOUD, true, l.start.x + r[0] * 2.2, l.start.y + 0.2, l.start.z + r[2] * 2.2, 0, -r[0] * 0.35, 0.02, -r[2] * 0.35, 1.0);
        if (hit != null) {
            l.victim = hit;
            l.stage = 3; l.age = 0;
            l.contact = new Vec3d(hitAt[0], hitAt[1], hitAt[2]);
            l.victimHold = new Vec3d(hit.posX, hit.posY, hit.posZ);
            l.victimNoGravity = hit.hasNoGravity(); hit.setNoGravity(true);
            l.casterNoGravity = p.hasNoGravity(); p.setNoGravity(true);
            l.hold = l.end;
            DashServer.FROZEN.add(hit.getEntityId());
            hit.hurtResistantTime = 0;
            hit.attackEntityFrom(DamageSource.causePlayerDamage(p), SteamDash.lariatDamage);
            hit.motionX = hit.motionY = hit.motionZ = 0; hit.velocityChanged = true;
            send(l, DashNet.LARIAT_IMPACT, l.contact.x, l.contact.y, l.contact.z, l.dir, new int[] {hit.getEntityId()});
            play(w, l.contact.x, l.contact.y, l.contact.z, SteamDash.LARIAT_IMPACT, 2.2f, 1.0f);
            play(w, l.contact.x, l.contact.y, l.contact.z, SoundEvents.BLOCK_ANVIL_LAND, 1.0f, 0.5f);
        } else {
            l.stage = 4; l.age = 0;
            l.skid = LariatMath.skidStart();
        }
    }

    private static boolean clear(WorldServer w, BlockPos b) { IBlockState s = w.getBlockState(b); return !s.getMaterial().blocksMovement() || TerrainRegen.soft(s); }

    /** The first living thing (not you, not a team-mate, PvP rules respected) within HIT_RADIUS of a point. */
    private static EntityLivingBase victimAt(EntityPlayerMP p, double[] pt) {
        double r = LariatMath.HIT_RADIUS;
        EntityLivingBase best = null; double bestD = Double.MAX_VALUE;
        for (EntityLivingBase e : p.world.getEntitiesWithinAABB(EntityLivingBase.class, new AxisAlignedBB(pt[0] - r, pt[1] - r - 1, pt[2] - r, pt[0] + r, pt[1] + r, pt[2] + r))) {
            if (e == p || !e.isEntityAlive() || e instanceof EntityArmorStand) continue;
            if (!MatatabiPowers.allowed(p, e) || e instanceof EntityPlayer && !p.server.isPVPEnabled()) continue;
            double d = e.getDistanceSq(pt[0], pt[1] - 0.6, pt[2]);
            if (d < bestD) { bestD = d; best = e; }
        }
        return best;
    }

    // ---- beat 3: hit-stop ----

    private static void hitstop(L l) {
        EntityPlayerMP p = l.caster; WorldServer w = l.world;
        p.motionX = p.motionY = p.motionZ = 0; p.velocityChanged = true;
        p.connection.setPlayerLocation(l.hold.x, l.hold.y, l.hold.z, p.rotationYaw, p.rotationPitch);
        EntityLivingBase v = l.victim;
        if (v != null && v.isEntityAlive()) {
            v.motionX = v.motionY = v.motionZ = 0; v.velocityChanged = true;
            if (v instanceof EntityPlayerMP) ((EntityPlayerMP) v).connection.setPlayerLocation(l.victimHold.x, l.victimHold.y, l.victimHold.z, v.rotationYaw, v.rotationPitch);
            else v.setPosition(l.victimHold.x, l.victimHold.y, l.victimHold.z);
        }
        // Crackling at the contact point while time is held.
        w.spawnParticle(EnumParticleTypes.FIREWORKS_SPARK, true, l.contact.x, l.contact.y, l.contact.z, 4, 0.2, 0.2, 0.2, 0.05);
        if (l.age >= LariatMath.HITSTOP) launch(l);
    }

    private static void launch(L l) {
        EntityPlayerMP p = l.caster; WorldServer w = l.world;
        EntityLivingBase v = l.victim;
        DashServer.FROZEN.remove(v.getEntityId());
        v.setNoGravity(l.victimNoGravity);
        p.setNoGravity(l.casterNoGravity);
        double[] vel = LariatMath.launch(l.dir, v instanceof EntityLiving && !v.isNonBoss());
        v.motionX = vel[0]; v.motionY = vel[1]; v.motionZ = vel[2]; v.velocityChanged = true;
        v.fallDistance = 0;
        l.vel = vel.clone();
        plow(l, v);
        l.stage = 4; l.age = 0;
        l.skid = LariatMath.skidStart();
        w.spawnParticle(EnumParticleTypes.EXPLOSION_LARGE, true, l.contact.x, l.contact.y, l.contact.z, 2, 0.2, 0.2, 0.2, 0.0);
        for (double[] r : DashMath.ring(l.dir, 1.0, 32))
            w.spawnParticle(EnumParticleTypes.CLOUD, true, l.contact.x + r[0] * 0.5, l.contact.y + r[1] * 0.5, l.contact.z + r[2] * 0.5, 0, r[0] * 0.6 + l.dir[0] * 0.4, r[1] * 0.6, r[2] * 0.6 + l.dir[2] * 0.4, 1.0);
        send(l, DashNet.LARIAT_RELEASE, v.posX, v.posY, v.posZ, vel, new int[] {v.getEntityId()});
        play(w, l.contact.x, l.contact.y, l.contact.z, SoundEvents.ENTITY_GENERIC_EXPLODE, 1.6f, 0.8f);
        play(w, l.contact.x, l.contact.y, l.contact.z, SteamDash.LAUNCH, 1.5f, 1.0f);
        FALL_SAFE.put(p.getUniqueID(), w.getTotalWorldTime() + 60);
    }

    // ---- beat 4: release ----

    private static boolean release(L l) {
        EntityPlayerMP p = l.caster; WorldServer w = l.world;
        if (l.age <= LariatMath.ARM_HOLD) {
            double h = Math.sqrt(l.dir[0] * l.dir[0] + l.dir[2] * l.dir[2]);
            p.motionX = l.dir[0] / h * l.skid; p.motionZ = l.dir[2] / h * l.skid; p.velocityChanged = true;
            l.skid *= LariatMath.SKID_FRICTION;
            IBlockState under = w.getBlockState(new BlockPos(p.posX, p.posY - 0.2, p.posZ));
            if (under.getMaterial().blocksMovement())
                w.spawnParticle(EnumParticleTypes.BLOCK_CRACK, true, p.posX, p.posY + 0.05, p.posZ, 5, 0.2, 0.0, 0.2, 0.1, Block.getStateId(under));
        }
        // Steam venting off the shoulders, crimson embers drifting up.
        double yaw = Math.toRadians(p.renderYawOffset);
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        if (l.age <= LariatMath.ARM_HOLD && l.age % 2 == 0)   // a short vent while the arm is still out, not a cloud bank over the armor
            for (int side = -1; side <= 1; side += 2)
                w.spawnParticle(EnumParticleTypes.CLOUD, true, p.posX + rx * 0.38 * side, p.posY + 1.45, p.posZ + rz * 0.38 * side, 0, rx * 0.1 * side, 0.16, rz * 0.1 * side, 1.0);
        w.spawnParticle(EnumParticleTypes.REDSTONE, true, p.posX + (Math.random() - 0.5), p.posY + 1 + Math.random(), p.posZ + (Math.random() - 0.5), 0, 0.85, 0.05, 0.08, 1.0);
        EntityLivingBase v = l.victim;
        if (v != null && v.isEntityAlive() && l.age <= 15) {
            int before = l.broken;
            plow(l, v);
            // The mob moved before this sweep: if a pane it has now smashed stopped it, give the speed back.
            if (v.collidedHorizontally && l.broken > before && l.vel != null) {
                v.motionX = l.vel[0] * 0.85; v.motionZ = l.vel[2] * 0.85; v.motionY = Math.max(v.motionY, l.vel[1] * 0.85); v.velocityChanged = true;
                plow(l, v);
            }
            l.vel = new double[] {v.motionX, v.motionY, v.motionZ};
            w.spawnParticle(EnumParticleTypes.SMOKE_LARGE, true, v.posX, v.posY + v.height * 0.5, v.posZ, 2, 0.2, 0.2, 0.2, 0.01);
        }
        return l.age >= LariatMath.RELEASE + 3;
    }

    /** Clears soft blocks in the flying body's swept box (this tick and next), and gouges the trench where it skims the ground. */
    private static void plow(L l, EntityLivingBase v) {
        EntityPlayerMP p = l.caster; WorldServer w = l.world;
        {
            AxisAlignedBB box = v.getEntityBoundingBox();
            AxisAlignedBB sweep = box.union(box.offset(v.motionX * 1.2, v.motionY * 1.2, v.motionZ * 1.2)).grow(0.45, 0.2, 0.45);   // a body-and-a-half wide hole
            if (SteamDash.lariatTerrain) {
                for (int x = (int) Math.floor(sweep.minX); x <= (int) Math.floor(sweep.maxX); x++)
                    for (int y = (int) Math.floor(sweep.minY); y <= (int) Math.floor(sweep.maxY); y++)
                        for (int z = (int) Math.floor(sweep.minZ); z <= (int) Math.floor(sweep.maxZ); z++) {
                            BlockPos b = new BlockPos(x, y, z);
                            IBlockState st = w.getBlockState(b);
                            if (TerrainRegen.soft(st) && TerrainRegen.breakTemporarily(w, b, p)) {
                                l.broken++;
                                w.spawnParticle(EnumParticleTypes.BLOCK_CRACK, true, x + 0.5, y + 0.5, z + 0.5, 12, 0.3, 0.3, 0.3, 0.15, Block.getStateId(st));
                            }
                        }
                // Along the whole stretch it covers this tick (it moves 3+ blocks a tick), not just where it is.
                double h = Math.sqrt(l.dir[0] * l.dir[0] + l.dir[2] * l.dir[2]);
                int px = (int) Math.round(-l.dir[2] / h), pz = (int) Math.round(l.dir[0] / h);
                for (double u = 0; u <= 1.2; u += 0.2) {
                    double gx = v.posX + v.motionX * u, gy = v.posY + v.motionY * u, gz = v.posZ + v.motionZ * u;
                    BlockPos ground = new BlockPos(gx, gy - 0.1, gz);
                    for (int k = 0; k < 2 && !w.getBlockState(ground).getMaterial().blocksMovement(); k++) ground = ground.down();   // find the ground under it
                    if (!w.getBlockState(ground).getMaterial().blocksMovement() || gy - (ground.getY() + 1) >= 2.4) continue;
                    // The trench opens a little past the contact point, not under the caster's own skid.
                    if (l.contact != null && (gx - l.contact.x) * (gx - l.contact.x) + (gz - l.contact.z) * (gz - l.contact.z) < 2.5 * 2.5) continue;
                    for (int k = -1; k <= 1; k++) {
                        BlockPos g = k == 0 ? ground : ground.add(px * k, 0, pz * k);
                        IBlockState gs = w.getBlockState(g);
                        if (!TerrainRegen.gougeable(gs)) continue;
                        // The centre is gouged out; the lips are torn up into rough ground.
                        IBlockState with = k == 0 ? Blocks.AIR.getDefaultState() : TerrainRegen.cracked(gs);
                        if (with == null || with == gs || !TerrainRegen.replace(w, g, with, p)) continue;
                        l.trench++;
                        w.spawnParticle(EnumParticleTypes.BLOCK_CRACK, true, g.getX() + 0.5, g.getY() + 1, g.getZ() + 0.5, 14, 0.4, 0.2, 0.4, 0.2, Block.getStateId(gs));
                    }
                }
            }
        }
    }

    private static void finish(L l) {
        if (l.victim != null) { DashServer.FROZEN.remove(l.victim.getEntityId()); if (l.stage == 3) l.victim.setNoGravity(l.victimNoGravity); }
        if (l.stage == 3) l.caster.setNoGravity(l.casterNoGravity);
        if (l.caster.connection != null) send(l, DashNet.LARIAT_END, l.caster.posX, l.caster.posY, l.caster.posZ, l.dir, new int[] {l.broken, l.trench});
    }

    // ---- output ----

    private static void send(L l, int kind, double x, double y, double z, double[] d, int[] data) {
        SteamDash.NET.sendToAllAround(new DashNet.Fx(kind, l.caster.getEntityId(), l.victim == null ? -1 : l.victim.getEntityId(), x, y, z, d[0], d[1], d[2], data),
                new NetworkRegistry.TargetPoint(l.world.provider.getDimension(), x, y, z, 160));
    }

    private static void play(WorldServer w, double x, double y, double z, SoundEvent s, float volume, float pitch) {
        if (s != null) w.playSound(null, x, y, z, s, SoundCategory.PLAYERS, volume, pitch);
    }

    @SubscribeEvent public void fall(LivingFallEvent e) {
        if (e.getEntityLiving().world.isRemote) return;
        Long until = FALL_SAFE.get(e.getEntityLiving().getUniqueID());
        if (until != null && e.getEntityLiving().world.getTotalWorldTime() <= until) e.setDamageMultiplier(0);
    }

    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        L l = RUNNING.remove(e.player.getUniqueID());
        if (l != null) finish(l);
    }
}
