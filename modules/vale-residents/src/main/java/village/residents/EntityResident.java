package village.residents;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

/** Independent entity; no CustomNPCs/Naruto classes or saved identifiers. */
public final class EntityResident extends EntityCreature {
    private static final DataParameter<String> ROLE = EntityDataManager.createKey(EntityResident.class, DataSerializers.STRING);
    private ResidentIdentity identity = ResidentIdentity.empty();
    private final InteractionWindow interactions = new InteractionWindow();

    public EntityResident(World world) {
        super(world);
        setSize(0.6F, 1.8F);
        enablePersistence();
        experienceValue = 0;
    }

    @Override protected void entityInit() {
        super.entityInit();
        dataManager.register(ROLE, "");
    }

    @Override protected void initEntityAI() {
        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(1, new EntityAIWatchClosest(this, EntityPlayer.class, 8.0F));
        tasks.addTask(2, new EntityAILookIdle(this));
    }

    @Override protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(20.0D);
        getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.0D);
    }

    public ResidentRole role() { return ResidentRole.fromId(dataManager.get(ROLE)); }
    public ResidentRole interactiveRole() { return identity.interactiveRole(); }

    public void configure(ResidentRole role) {
        identity = ResidentIdentity.forRole(role);
        dataManager.set(ROLE, role.id);
        setCustomNameTag(role.title);
        setAlwaysRenderNameTag(true);
        enablePersistence();
    }

    @Override public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        identity = ResidentIdentity.readEntity(tag);
        ResidentRole role = identity.displayRole();
        dataManager.set(ROLE, role == null ? "" : role.id);
        enablePersistence();
    }

    @Override public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        identity.writeEntity(tag);
    }

    @Override protected boolean canDespawn() { return false; }
    @Override protected boolean canDropLoot() { return false; }
    @Override public boolean canBePushed() { return false; }
    @Override public void applyEntityCollision(Entity other) { }
    @Override public boolean canBeLeashedTo(EntityPlayer player) { return false; }
    @Override public boolean canBeAttackedWithItem() { return false; }

    @Override public boolean attackEntityFrom(DamageSource source, float amount) {
        return source == DamageSource.OUT_OF_WORLD && super.attackEntityFrom(source, amount);
    }

    @Override public void onLivingUpdate() {
        motionX = 0;
        motionZ = 0;
        super.onLivingUpdate();
    }

    @Override public boolean processInteract(EntityPlayer player, EnumHand hand) {
        if (world.isRemote || hand != EnumHand.MAIN_HAND) return true;
        if (!(player instanceof EntityPlayerMP) || player.world != world || player.isSpectator()
            || !player.isEntityAlive() || player.getDistanceSq(this) > 36.0D) return true;
        if (!interactions.allow(player.getUniqueID(), world.getTotalWorldTime())) return true;
        ResidentRole role = identity.interactiveRole();
        if (role == null) {
            player.sendMessage(new TextComponentString("This resident has an unsupported saved role. Its data has been preserved; ask an operator to inspect it."));
            return true;
        }
        if (!village.vale.expedition.GateRuntime.open((EntityPlayerMP) player, this))
            DialogueSessions.open(this, (EntityPlayerMP) player);
        return true;
    }
}
