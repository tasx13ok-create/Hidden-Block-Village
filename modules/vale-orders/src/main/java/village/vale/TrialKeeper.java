package village.vale;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.*;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.*;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.*;
import net.minecraft.network.datasync.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.*;

/** Original arena enemies. No block edits, terrain damage, minion spam or automatic world spawns. */
public final class TrialKeeper extends EntityMob {
    private static final DataParameter<Integer> KIND = EntityDataManager.createKey(TrialKeeper.class, DataSerializers.VARINT);
    private static final DataParameter<Integer> POSE = EntityDataManager.createKey(TrialKeeper.class, DataSerializers.VARINT);
    private final BossInfoServer bar = new BossInfoServer(new TextComponentString("Trial Keeper"), BossInfo.Color.YELLOW, BossInfo.Overlay.PROGRESS);
    private final Map<UUID, Float> contributors = new LinkedHashMap<>();
    private final Map<UUID, Long> lastHit = new LinkedHashMap<>();
    private int attackClock = 100, windup, followThrough;
    private double markX, markY, markZ;
    private boolean rewarded;
    private UUID challengeOwner;private long challengeExpires,lastPresent;
    public TrialKeeper(World world) {
        super(world); setSize(.7F, 1.95F); enablePersistence(); experienceValue = 0;
    }
    @Override protected void entityInit() { super.entityInit(); dataManager.register(KIND, 0);dataManager.register(POSE,0); }
    @Override protected void initEntityAI() {
        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(2, new EntityAIAttackMelee(this, 1, false));
        tasks.addTask(6, new EntityAIWatchClosest(this, EntityPlayer.class, 18));
        tasks.addTask(7, new EntityAILookIdle(this));
        targetTasks.addTask(1, new EntityAIHurtByTarget(this, false));
        targetTasks.addTask(2, new EntityAINearestAttackableTarget<>(this, EntityPlayer.class, 10, true, false,
            p -> p != null && !p.isSpectator() && !p.capabilities.isCreativeMode && isWithinHomeDistanceFromPosition(p.getPosition())));
    }
    @Override protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(320);
        getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(5);
        getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(.24);
        getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(24);
        getEntityAttribute(SharedMonsterAttributes.ARMOR).setBaseValue(10);
        getEntityAttribute(SharedMonsterAttributes.ARMOR_TOUGHNESS).setBaseValue(4);
        getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(.75);
    }
    public int kind() { return dataManager.get(KIND); }
    public float chargePose(){int n=dataManager.get(POSE);return n>0?1-n/(kind()==3?50F:kind()==1?26F:36F):0;}
    public float strikePose(){int n=dataManager.get(POSE);return n<0?1+n/8F:0;}
    public boolean isCharging(){return dataManager.get(POSE)>0;}
    public boolean isFollowingThrough(){return dataManager.get(POSE)<0;}
    public void challenge(EntityPlayerMP p){challengeOwner=p.getUniqueID();lastPresent=Adventures.now(p);challengeExpires=lastPresent+12000;}
    @Override public void notifyDataManagerChange(DataParameter<?> key){super.notifyDataManagerChange(key);if(KIND.equals(key))setSize(kind()==3?1.4F:.7F,kind()==3?3.9F:1.95F);}
    public int phase() { return getHealth() <= getMaxHealth() * .30F ? 3 : getHealth() <= getMaxHealth() * .65F ? 2 : 1; }
    public void configure(int type) {
        dataManager.set(KIND, MathHelper.clamp(type, 0, Bosses.KEYS.length-1));
        setSize(kind()==3?1.4F:.7F,kind()==3?3.9F:1.95F);
        getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(kind()==3?7:5);
        getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(Bosses.HEALTH[kind()]);
        getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(kind() == 1 ? .29 : .24);
        setHealth(getMaxHealth()); setCustomNameTag(Bosses.NAMES[kind()]);
        net.minecraft.item.ItemStack prop=new net.minecraft.item.ItemStack(Gear.ITEMS.get(kind()==3?"storm_glaive":kind()==0?"flint_axe":kind()==1?"current_spear":"dawn_staff"));
        net.minecraft.nbt.NBTTagCompound propTag=new net.minecraft.nbt.NBTTagCompound();propTag.setTag("AttributeModifiers",new NBTTagList());prop.setTagCompound(propTag);setItemStackToSlot(net.minecraft.inventory.EntityEquipmentSlot.MAINHAND,prop);
        setHomePosAndDistance(getPosition(), 24);
    }
    @Override protected boolean canDespawn() { return false; }
    @Override public boolean canBeLeashedTo(EntityPlayer player) { return false; }
    @Override public boolean isNonBoss() { return false; }
    @Override public void addTrackingPlayer(EntityPlayerMP player) { super.addTrackingPlayer(player); bar.addPlayer(player); }
    @Override public void removeTrackingPlayer(EntityPlayerMP player) { super.removeTrackingPlayer(player); bar.removePlayer(player); }
    @Override public boolean attackEntityAsMob(Entity target) { return windup == 0 && super.attackEntityAsMob(target); }
    @Override public boolean attackEntityFrom(DamageSource source, float amount) {
        float before = getHealth();
        boolean result = super.attackEntityFrom(source, amount);
        if (!world.isRemote && result && getHealth() < before && source.getTrueSource() instanceof EntityPlayerMP) {
            UUID id = source.getTrueSource().getUniqueID();
            contributors.put(id, contributors.getOrDefault(id, 0F) + Math.max(0, before - getHealth()));
            lastHit.put(id, world.getTotalWorldTime());
        }
        return result;
    }
    @Override public void onLivingUpdate() {
        super.onLivingUpdate();
        if (world.isRemote || !isEntityAlive()) return;
        if(followThrough>0)dataManager.set(POSE,-followThrough--);else if(windup==0)dataManager.set(POSE,0);
        if(challengeOwner!=null){
            EntityPlayerMP owner=world.getMinecraftServer().getPlayerList().getPlayerByUUID(challengeOwner);long time=world.getMinecraftServer().getWorld(0).getTotalWorldTime();
            if(owner!=null&&owner.isEntityAlive()&&owner.dimension==dimension&&getDistanceSq(owner)<=32*32)lastPresent=time;
            if(time>=challengeExpires||time-lastPresent>=600){if(owner!=null)owner.sendStatusMessage(new TextComponentString("The abandoned or expired trial dispersed."),true);setDead();return;}
        }
        bar.setPercent(getHealth() / getMaxHealth());
        bar.setName(new TextComponentString(Bosses.NAMES[kind()] + " | Phase " + phase() + (windup > 0 ? " | MOVE!" : "")));
        if (hasHome() && getDistanceSq(getHomePosition()) > 28 * 28) {
            getNavigator().tryMoveToXYZ(getHomePosition().getX() + .5, getHomePosition().getY(), getHomePosition().getZ() + .5, 1.2);
            setAttackTarget(null); windup = 0;dataManager.set(POSE,0); return;
        }
        if (windup > 0) {
            dataManager.set(POSE,windup);
            getNavigator().clearPath(); motionX = 0; motionZ = 0;
            if (windup % 5 == 0) ring();
            if (--windup == 0) detonate();
            return;
        }
        EntityLivingBase target = getAttackTarget();
        if (target != null && target.isEntityAlive() && getDistanceSq(target) < 18 * 18 && canEntityBeSeen(target) && --attackClock <= 0) beginAttack(target);
    }
    public void beginAttack(EntityLivingBase target) {
        markX = kind() == 0 ? posX : target.posX; markY = target.posY; markZ = kind() == 0 ? posZ : target.posZ;
        windup = kind() == 3 ? 50 : kind() == 1 ? 26 : 36;
        dataManager.set(POSE,windup);
        attackClock = 160 - phase() * 20;
        world.playSound(null, posX, posY, posZ, SoundEvents.BLOCK_NOTE_CHIME, getSoundCategory(), .8F, .7F);
        for (EntityPlayer player : world.getEntitiesWithinAABB(EntityPlayer.class, getEntityBoundingBox().grow(20)))
            player.sendStatusMessage(new TextComponentString(Bosses.NAMES[kind()] + ": " + (kind() == 0 ? "Leave the ring!" : "Move away from the marked spot!")), true);
        ring();
    }
    private double radius() { return kind() == 3 ? 4 : kind() == 0 ? 3.5 : kind() == 1 ? 2 : 2.75; }
    private void ring() {
        WorldServer server = (WorldServer)world;
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI / 12;
            server.spawnParticle(EnumParticleTypes.VILLAGER_ANGRY, markX + Math.cos(a) * radius(), markY + .15, markZ + Math.sin(a) * radius(), 1, 0.0, 0.0, 0.0, 0.0);
        }
    }
    public void detonate() {
        followThrough=8;dataManager.set(POSE,-8);
        AxisAlignedBB box = new AxisAlignedBB(markX-radius(), markY-2, markZ-radius(), markX+radius(), markY+3, markZ+radius());
        for (EntityPlayer player : world.getEntitiesWithinAABB(EntityPlayer.class, box)) {
            if (!player.isEntityAlive() || player.isSpectator() || player.capabilities.isCreativeMode || isOnSameTeam(player)) continue;
            if ((player.posX-markX)*(player.posX-markX)+(player.posZ-markZ)*(player.posZ-markZ) > radius()*radius()) continue;
            Vec3d center = new Vec3d(markX, markY + 1, markZ);
            if (world.rayTraceBlocks(center, player.getPositionEyes(1), false, true, false) != null) continue;
            Combat.impact(player, DamageSource.causeMobDamage(this), (kind()==3?12:8) + phase() * 2);
        }
        world.playSound(null, markX, markY, markZ, SoundEvents.ENTITY_GENERIC_EXPLODE, getSoundCategory(), .5F, 1.4F);
        ((WorldServer)world).spawnParticle(EnumParticleTypes.CLOUD, markX, markY+.4, markZ, 12, radius()/2, .2, radius()/2, .02);
    }
    @Override protected void dropEquipment(boolean recentlyHit,int looting){} // Decorative held prop is replaced by the ledger-issued encounter reward.
    @Override protected void onDeathUpdate() {
        if (!world.isRemote && dead && !rewarded) {
            RewardLedger ledger = (RewardLedger)((WorldServer)world).getMinecraftServer().getWorld(0).getMapStorage().getOrLoadData(RewardLedger.class, "vale_orders_rewards");
            if (ledger == null) { ledger = new RewardLedger(); ((WorldServer)world).getMinecraftServer().getWorld(0).getMapStorage().setData("vale_orders_rewards", ledger); }
            for (Map.Entry<UUID, Float> row : contributors.entrySet()) {
                if (row.getValue() < getMaxHealth() * .05F || world.getTotalWorldTime() - lastHit.getOrDefault(row.getKey(), 0L) > 1800) continue;
                String key = (kind() == 3 ? "storm_glaive" : kind() == 0 ? "flint_axe" : kind() == 1 ? "current_spear" : "dawn_staff");
                ledger.issue((WorldServer)world, row.getKey(), key, 1, "boss/" + getUniqueID() + "/" + row.getKey());
                EntityPlayerMP player = world.getMinecraftServer().getPlayerList().getPlayerByUUID(row.getKey());
                if (player != null) { ledger.deliver(player);Missions.bossContribution(player,kind()); player.sendMessage(new TextComponentString("Trial complete: " + key + " recorded. Full inventory rewards wait safely.")); }
            }
            rewarded = true;
        }
        super.onDeathUpdate();
    }
    @Override public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag); tag.setInteger("ValeKind", kind()); tag.setBoolean("ValeRewarded", rewarded);
        if(challengeOwner!=null)tag.setUniqueId("ValeChallengeOwner",challengeOwner);tag.setLong("ValeChallengeExpires",challengeExpires);tag.setLong("ValeLastPresent",lastPresent);
        if (hasHome()) { tag.setLong("ValeHome", getHomePosition().toLong()); tag.setBoolean("ValeHasHome", true); }
        NBTTagList list = new NBTTagList();
        for (Map.Entry<UUID, Float> row : contributors.entrySet()) { NBTTagCompound n = new NBTTagCompound(); n.setUniqueId("id", row.getKey()); n.setFloat("damage", row.getValue()); n.setLong("time", lastHit.get(row.getKey())); list.appendTag(n); }
        tag.setTag("ValeContributors", list);
    }
    @Override public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag); dataManager.set(KIND, MathHelper.clamp(tag.getInteger("ValeKind"), 0, Bosses.KEYS.length-1)); rewarded = tag.getBoolean("ValeRewarded");setSize(kind()==3?1.4F:.7F,kind()==3?3.9F:1.95F);
        challengeOwner=tag.hasUniqueId("ValeChallengeOwner")?tag.getUniqueId("ValeChallengeOwner"):null;challengeExpires=tag.getLong("ValeChallengeExpires");lastPresent=tag.getLong("ValeLastPresent");
        if (tag.getBoolean("ValeHasHome")) setHomePosAndDistance(BlockPos.fromLong(tag.getLong("ValeHome")), 24);
        contributors.clear(); lastHit.clear(); NBTTagList list = tag.getTagList("ValeContributors", 10);
        for (int i=0;i<list.tagCount();i++) { NBTTagCompound n=list.getCompoundTagAt(i); if(n.hasUniqueId("id")){UUID id=n.getUniqueId("id");contributors.put(id,n.getFloat("damage"));lastHit.put(id,n.getLong("time"));} }
        attackClock = 100; windup = 0;
    }
}
