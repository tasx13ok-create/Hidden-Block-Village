package village.vale.arsenal;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.World;
import village.vale.ArsenalAccess;

/**
 * Visible carrier for a hand-held scroll mode (Spiral Palm, Storm Lance, edges, mantles).
 * Server-owned, never saved, never collides and never deals damage: combat stays in
 * ScrollEvents. Every tick it re-reads the owner's real mode, so it cannot outlive the technique.
 */
public final class HandTechnique extends Entity {
    public static final String[] STYLES = {"spiral", "charge", "fireedge", "windedge", "lava", "lightmode"};
    public static final byte HELD = 0, IMPACT = 1, FIZZLE = 2;
    private static final DataParameter<Integer> OWNER = EntityDataManager.createKey(HandTechnique.class, DataSerializers.VARINT);
    private static final DataParameter<String> STYLE = EntityDataManager.createKey(HandTechnique.class, DataSerializers.STRING);
    private static final DataParameter<Byte> STATE = EntityDataManager.createKey(HandTechnique.class, DataSerializers.BYTE);
    /** Client lookup: owner entity id -> live technique (for arm poses). */
    public static final Map<Integer, HandTechnique> CLIENT_BY_OWNER = new java.util.concurrent.ConcurrentHashMap<>();
    private int stateAge, dying = -1, visualTicks = -1;
    private java.util.UUID ownerUuid;
    private int clientOwner = -1;
    public float impactX, impactY, impactZ;

    public HandTechnique(World world) {
        super(world);
        setSize(0.2f, 0.2f);
        noClip = true;
        ignoreFrustumCheck = true;
        isImmuneToFire = true;
    }

    @Override protected void entityInit() {
        dataManager.register(OWNER, -1);
        dataManager.register(STYLE, "");
        dataManager.register(STATE, HELD);
    }

    public static boolean known(String style) {
        for (String s : STYLES) if (s.equals(style)) return true;
        return false;
    }

    public int ownerId() { return dataManager.get(OWNER); }
    public String style() { return dataManager.get(STYLE); }
    public byte state() { return dataManager.get(STATE); }
    /** Ticks spent in the current state (client and server). */
    public int stateAge() { return stateAge; }

    static HandTechnique find(EntityPlayerMP p) {
        for (HandTechnique h : p.world.getEntities(HandTechnique.class, h -> h != null && !h.isDead))
            if (p.getUniqueID().equals(h.ownerUuid) && h.state() == HELD) return h;
        return null;
    }
    static void end(EntityPlayerMP p){HandTechnique h=find(p);if(h!=null)h.finish(FIZZLE);}
    public static void clearClientWorld(World world){CLIENT_BY_OWNER.entrySet().removeIf(e->world==null||e.getValue()==null||e.getValue().world==world);}
    private void forgetClient(){CLIENT_BY_OWNER.entrySet().removeIf(e->e.getValue()==this);clientOwner=-1;}

    /** Called when a hand mode becomes active on the server. Replaces any previous carrier. */
    static void begin(EntityPlayerMP p, String style) {
        if (!known(style) || p.world.isRemote) return;
        HandTechnique old = find(p);
        if (old != null) old.finish(FIZZLE);
        HandTechnique h = new HandTechnique(p.world);
        h.dataManager.set(OWNER, p.getEntityId());
        h.ownerUuid = p.getUniqueID();
        h.dataManager.set(STYLE, style);
        h.setPosition(p.posX, p.posY + 1.2, p.posZ);
        boolean spawned = p.world.spawnEntity(h);
        if (spawned) {
            p.world.playSound(null, p.posX, p.posY + 1.2, p.posZ, startSound(style), SoundCategory.PLAYERS, 0.8f, startPitch(style));
        }
    }

    /** Visual-only carrier for a bridged licensed move: lives {@code ticks} without a Vale scroll mode. */
    static void visual(EntityPlayerMP p, String style, int ticks) {
        if (!known(style) || p.world.isRemote) return;
        HandTechnique old = find(p);
        if (old != null) old.finish(FIZZLE);
        HandTechnique h = new HandTechnique(p.world);
        h.dataManager.set(OWNER, p.getEntityId());
        h.ownerUuid = p.getUniqueID();
        h.dataManager.set(STYLE, style);
        h.visualTicks = Math.max(1, Math.min(200, ticks));
        h.setPosition(p.posX, p.posY + 1.2, p.posZ);
        if (p.world.spawnEntity(h)) p.world.playSound(null, p.posX, p.posY + 1.2, p.posZ, startSound(style), SoundCategory.PLAYERS, 0.8f, startPitch(style));
    }

    /** Called by ScrollEvents when the mode's strike actually lands on {@code target}. */
    static void impact(EntityPlayerMP p, EntityLivingBase target) {
        HandTechnique h = find(p);
        if (h == null) return;
        h.setPosition(target.posX, target.posY + target.height * 0.55, target.posZ);
        h.finish(IMPACT);
        p.world.playSound(null, target.posX, target.posY + 1, target.posZ, impactSound(h.style()), SoundCategory.PLAYERS, 1.0f, impactPitch(h.style()));
    }

    private void finish(byte state) {
        if (state() != HELD) return;
        dataManager.set(STATE, state);
        stateAge = 0;
        dying = state == IMPACT ? village.vale.JutsuMath.IMPACT_TICKS : village.vale.JutsuMath.FIZZLE_TICKS;
    }

    @Override public void notifyDataManagerChange(DataParameter<?> key) {
        super.notifyDataManagerChange(key);
        if (STATE.equals(key)) {
            stateAge = 0;
            if (world.isRemote && state() == IMPACT) { impactX = (float) posX; impactY = (float) posY; impactZ = (float) posZ; }
        }
        if(world!=null&&world.isRemote&&(OWNER.equals(key)||STATE.equals(key)))forgetClient();
    }

    @Override public void onUpdate() {
        super.onUpdate();
        stateAge++;
        if (world.isRemote) {
            if (state() == HELD && ownerId() >= 0&&!isDead) {if(clientOwner!=ownerId())forgetClient();clientOwner=ownerId();CLIENT_BY_OWNER.put(clientOwner,this);}
            else forgetClient();
            clientParticles();
            return;
        }
        if (dying >= 0) { if (--dying <= 0) setDead(); return; }
        EntityPlayerMP p = ownerUuid == null || world.getMinecraftServer() == null ? null : world.getMinecraftServer().getPlayerList().getPlayerByUUID(ownerUuid);
        if (p == null || p.world != world || !p.isEntityAlive() || p.isSpectator()) { finish(FIZZLE); return; }
        if (p.getEntityId() != ownerId()) dataManager.set(OWNER, p.getEntityId());
        if (visualTicks >= 0) { if (--visualTicks <= 0) { finish(FIZZLE); return; } }
        else {
            NBTTagCompound s = Scrolls.state(p);
            if (!style().equals(s.getString("mode")) || s.getLong("modeUntil") < ArsenalAccess.now(p)) { finish(FIZZLE); return; }
        }
        // Follow the owner so tracking/chunk visibility match; rendering anchors to the animated arm.
        setPosition(p.posX, p.posY + 1.2, p.posZ);
        if (ticksExisted % loopTicks(style()) == 0)
            world.playSound(null, posX, posY, posZ, loopSound(style()), SoundCategory.PLAYERS, loopVolume(style()), loopPitch(style(), ticksExisted));
    }

    private void clientParticles() {
        String s = style();
        if (state() == IMPACT && stateAge == 1) {
            boolean fire = s.equals("fireedge") || s.equals("lava"), bolt = s.equals("charge") || s.equals("lightmode");
            net.minecraft.util.EnumParticleTypes spark = fire ? net.minecraft.util.EnumParticleTypes.FLAME
                : bolt ? net.minecraft.util.EnumParticleTypes.CRIT_MAGIC : net.minecraft.util.EnumParticleTypes.FIREWORKS_SPARK;
            for (int i = 0; i < 18; i++) {
                double a = i * Math.PI * 2 / 18, v = 0.10 + rand.nextDouble() * 0.08;
                world.spawnParticle(spark, posX, posY, posZ, Math.cos(a) * v, (rand.nextDouble() - 0.4) * 0.06, Math.sin(a) * v);
            }
            for (int i = 0; i < 3; i++) world.spawnParticle(net.minecraft.util.EnumParticleTypes.CLOUD, posX, posY - 0.3, posZ, (rand.nextDouble() - 0.5) * 0.06, 0.01, (rand.nextDouble() - 0.5) * 0.06);
            return;
        }
        if (state() != HELD || ticksExisted % 3 != 0) return;
        Entity owner = world.getEntityByID(ownerId());
        if (!(owner instanceof EntityLivingBase)) return;
        EntityLivingBase o = (EntityLivingBase) owner;
        double[] palm = village.vale.JutsuMath.palm(o.posX, o.posY, o.posZ, o.renderYawOffset, s.equals("spiral") ? 1.45 : s.equals("charge") ? 0.95 : 1.15, o.isSneaking(), true);
        if (s.equals("fireedge")) world.spawnParticle(net.minecraft.util.EnumParticleTypes.FLAME, palm[0], palm[1], palm[2], 0, 0.02, 0);
        else if (s.equals("lava")) world.spawnParticle(net.minecraft.util.EnumParticleTypes.DRIP_LAVA, o.posX + (rand.nextDouble() - 0.5) * 0.6, o.posY + 0.8, o.posZ + (rand.nextDouble() - 0.5) * 0.6, 0, 0, 0);
    }

    @Override public void setDead() {
        super.setDead();
        if (world.isRemote) forgetClient();
    }

    // Never persisted: a reloaded chunk must not resurrect an effect without its server mode.
    @Override public boolean writeToNBTOptional(NBTTagCompound compound) { return false; }
    @Override protected void readEntityFromNBT(NBTTagCompound compound) { setDead(); }
    @Override protected void writeEntityToNBT(NBTTagCompound compound) {}
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean canBePushed() { return false; }
    @Override public boolean isInRangeToRenderDist(double distance) { return distance < 64 * 64; }

    // Vanilla sound events only (no new assets); distinct timbre and pitch per technique.
    static net.minecraft.util.SoundEvent startSound(String s) {
        switch (s) {
            case "spiral": return SoundEvents.ENTITY_ENDERDRAGON_FLAP;
            case "charge": case "lightmode": return SoundEvents.ENTITY_LIGHTNING_IMPACT;
            case "fireedge": case "lava": return SoundEvents.ENTITY_BLAZE_SHOOT;
            default: return SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP;
        }
    }
    static float startPitch(String s) { return s.equals("spiral") ? 1.6f : s.equals("charge") ? 1.8f : 1.2f; }
    static net.minecraft.util.SoundEvent loopSound(String s) {
        switch (s) {
            case "spiral": return SoundEvents.ENTITY_ENDERDRAGON_FLAP;
            case "charge": case "lightmode": return SoundEvents.ENTITY_FIREWORK_TWINKLE;
            case "fireedge": case "lava": return SoundEvents.BLOCK_FIRE_AMBIENT;
            default: return SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP;
        }
    }
    static int loopTicks(String s) { return s.equals("spiral") ? 4 : s.equals("charge") ? 3 : 10; }
    static float loopVolume(String s) { return s.equals("spiral") ? 0.35f : s.equals("charge") ? 0.4f : 0.25f; }
    static float loopPitch(String s, int tick) { return s.equals("spiral") ? 1.9f + 0.1f * ((tick / 4) % 2) : s.equals("charge") ? 1.6f + 0.3f * ((tick / 3) % 3) / 2f : 1.0f; }
    static net.minecraft.util.SoundEvent impactSound(String s) {
        switch (s) {
            case "spiral": return SoundEvents.ENTITY_GENERIC_EXPLODE;
            case "charge": case "lightmode": return SoundEvents.ENTITY_LIGHTNING_THUNDER;
            case "fireedge": case "lava": return SoundEvents.ENTITY_FIREWORK_BLAST;
            default: return SoundEvents.ENTITY_PLAYER_ATTACK_STRONG;
        }
    }
    static float impactPitch(String s) { return s.equals("spiral") ? 1.35f : s.equals("charge") ? 1.7f : 1.1f; }
}
