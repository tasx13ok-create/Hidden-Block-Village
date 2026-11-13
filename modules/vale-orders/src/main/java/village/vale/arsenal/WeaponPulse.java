package village.vale.arsenal;

import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import village.vale.ArsenalAccess;
import village.vale.WeaponMath;

/**
 * Visible-only carrier for an arsenal technique: a cast flash, a chain arc, a melee slash or a
 * timed aura. Spawned by the server after the gameplay already resolved; it never collides,
 * never deals damage, never saves and dies on its own short timer.
 */
public final class WeaponPulse extends Entity {
    public static final byte CAST = 0, ARC = 1, SWING = 2, AURA = 3;
    private static final DataParameter<Integer> SPEC = EntityDataManager.createKey(WeaponPulse.class, DataSerializers.VARINT);
    private static final DataParameter<Byte> KIND = EntityDataManager.createKey(WeaponPulse.class, DataSerializers.BYTE);
    private static final DataParameter<Integer> OWNER = EntityDataManager.createKey(WeaponPulse.class, DataSerializers.VARINT);
    private static final DataParameter<Integer> LIFE = EntityDataManager.createKey(WeaponPulse.class, DataSerializers.VARINT);
    private static final DataParameter<Float> POWER = EntityDataManager.createKey(WeaponPulse.class, DataSerializers.FLOAT);
    private static final DataParameter<Float> END_X = EntityDataManager.createKey(WeaponPulse.class, DataSerializers.FLOAT);
    private static final DataParameter<Float> END_Y = EntityDataManager.createKey(WeaponPulse.class, DataSerializers.FLOAT);
    private static final DataParameter<Float> END_Z = EntityDataManager.createKey(WeaponPulse.class, DataSerializers.FLOAT);
    private static final DataParameter<Float> YAW = EntityDataManager.createKey(WeaponPulse.class, DataSerializers.FLOAT);
    private static final DataParameter<Float> PITCH = EntityDataManager.createKey(WeaponPulse.class, DataSerializers.FLOAT);
    private static final DataParameter<Boolean> ALT = EntityDataManager.createKey(WeaponPulse.class, DataSerializers.BOOLEAN);
    private UUID ownerUuid;

    public WeaponPulse(World world) {
        super(world);
        setSize(0.2f, 0.2f);
        noClip = true;
        ignoreFrustumCheck = true;
        isImmuneToFire = true;
    }

    @Override protected void entityInit() {
        dataManager.register(SPEC, -1);
        dataManager.register(KIND, CAST);
        dataManager.register(OWNER, -1);
        dataManager.register(LIFE, 10);
        dataManager.register(POWER, 0f);
        dataManager.register(END_X, 0f);
        dataManager.register(END_Y, 0f);
        dataManager.register(END_Z, 0f);
        dataManager.register(YAW, 0f);
        dataManager.register(PITCH, 0f);
        dataManager.register(ALT, false);
    }

    public ArsenalSpec spec() { return ArsenalSpec.at(dataManager.get(SPEC)); }
    public byte kind() { return dataManager.get(KIND); }
    public int ownerId() { return dataManager.get(OWNER); }
    public int life() { return Math.max(1, dataManager.get(LIFE)); }
    public float power() { float f = dataManager.get(POWER); return Float.isFinite(f) ? Math.max(0, Math.min(1, f)) : 0; }
    public double endX() { return dataManager.get(END_X); }
    public double endY() { return dataManager.get(END_Y); }
    public double endZ() { return dataManager.get(END_Z); }
    public float lookYaw() { return dataManager.get(YAW); }
    public float lookPitch() { return dataManager.get(PITCH); }
    /** Alternate form (sneak variant: wind guard instead of gust, snare instead of lash). */
    public boolean alt() { return dataManager.get(ALT); }

    /** Presentation for a technique that the server just resolved successfully. */
    static void cast(EntityPlayerMP p, ArsenalSpec spec, float power) {
        if (p == null || spec == null) return;
        Vec3d eye = p.getPositionEyes(1), look = p.getLookVec();
        boolean sneak = p.isSneaking();
        switch (spec.role) {
            case GUARD: case MEND: spawn(p, spec, AURA, 40, power, eye, eye, false); return;
            case WIND:
                if (sneak) spawn(p, spec, AURA, 40, power, eye, eye, true);
                else spawn(p, spec, CAST, WeaponMath.CAST_TICKS, power, eye, eye.add(look.scale(8)), false);
                return;
            case OVERDRIVE: spawn(p, spec, AURA, 60, power, eye, eye, false); return;
            case SWEEP: spawn(p, spec, AURA, 100, power, eye, eye, false); return;
            case SLAM: spawn(p, spec, CAST, WeaponMath.CAST_TICKS + 4, power, p.getPositionVector(), p.getPositionVector(), false); return;
            case SONIC:
                if (sneak) break;
                spawn(p, spec, CAST, WeaponMath.CAST_TICKS, power, eye, eye.add(look.scale(10)), false); return;
            case EXTEND: case CHAIN: case DRAIN: case SHOCK: case BREAK: case RESTORE: case RIME: case BURST: case FINISH: {
                double range = spec.role == ArsenalSpec.Role.CHAIN ? 10 : spec.role == ArsenalSpec.Role.EXTEND ? 8 : spec.role == ArsenalSpec.Role.FINISH ? 7 : 4.5;
                EntityLivingBase target = ArsenalAccess.aimedTarget(p, range);
                Vec3d end = target != null ? target.getPositionVector().add(0, target.height * 0.55, 0) : eye.add(look.scale(range * 0.7));
                int life = spec.role == ArsenalSpec.Role.BURST ? WeaponMath.BURST_TICKS : WeaponMath.CAST_TICKS;
                spawn(p, spec, CAST, life, power, eye, end, spec.role == ArsenalSpec.Role.EXTEND && sneak);
                return;
            }
            default: break;
        }
        // Projectile and field roles: a short muzzle flash at the hand; the flight itself is drawn by the shot renderer.
        spawn(p, spec, CAST, WeaponMath.MUZZLE_TICKS, power, eye, eye.add(look.scale(2)), sneak);
    }

    /** A secondary chain arc between two struck bodies. */
    static void arc(EntityPlayerMP p, ArsenalSpec spec, Vec3d from, Vec3d to) {
        if (p != null && spec != null) spawn(p, spec, ARC, WeaponMath.ARC_TICKS, 1, from, to, false);
    }

    /** A weapon-coloured slash for a confirmed melee hit (wider for an active sweep). */
    static void swing(EntityPlayerMP p, ArsenalSpec spec, boolean wide) {
        if (p != null && spec != null) spawn(p, spec, SWING, WeaponMath.SWING_TICKS + (wide ? 3 : 0), wide ? 1 : 0.5f, p.getPositionVector(), p.getPositionEyes(1).add(p.getLookVec()), wide);
    }

    private static void spawn(EntityPlayerMP p, ArsenalSpec spec, byte kind, int life, float power, Vec3d at, Vec3d end, boolean alt) {
        if (p.world.isRemote || !Double.isFinite(end.x + end.y + end.z)) return;
        WeaponPulse w = new WeaponPulse(p.world);
        w.ownerUuid = p.getUniqueID();
        w.dataManager.set(SPEC, spec.ordinal());
        w.dataManager.set(KIND, kind);
        w.dataManager.set(OWNER, p.getEntityId());
        w.dataManager.set(LIFE, life);
        w.dataManager.set(POWER, Math.max(0, Math.min(1, power)));
        w.dataManager.set(END_X, (float) end.x);
        w.dataManager.set(END_Y, (float) end.y);
        w.dataManager.set(END_Z, (float) end.z);
        w.dataManager.set(YAW, p.rotationYaw);
        w.dataManager.set(PITCH, p.rotationPitch);
        w.dataManager.set(ALT, alt);
        w.setPosition(at.x, at.y, at.z);
        p.world.spawnEntity(w);
    }

    @Override public void onUpdate() {
        super.onUpdate();
        if (world.isRemote) return;
        if (ticksExisted > life() + 1) { setDead(); return; }
        if (kind() == AURA || kind() == SWING) {
            EntityPlayerMP p = ownerUuid == null || world.getMinecraftServer() == null ? null : world.getMinecraftServer().getPlayerList().getPlayerByUUID(ownerUuid);
            if (p == null || p.world != world || !p.isEntityAlive()) { setDead(); return; }
            if (p.getEntityId() != ownerId()) dataManager.set(OWNER, p.getEntityId());
            setPosition(p.posX, p.posY + p.getEyeHeight(), p.posZ);
        }
    }

    // Never persisted: a reloaded chunk must not replay an effect.
    @Override public boolean writeToNBTOptional(NBTTagCompound compound) { return false; }
    @Override protected void readEntityFromNBT(NBTTagCompound compound) { setDead(); }
    @Override protected void writeEntityToNBT(NBTTagCompound compound) {}
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean canBePushed() { return false; }
    @Override public boolean isInRangeToRenderDist(double distance) { return distance < 64 * 64; }
}
