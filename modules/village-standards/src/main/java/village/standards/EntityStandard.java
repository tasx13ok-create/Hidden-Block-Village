package village.standards;

import io.netty.buffer.ByteBuf;
import javax.annotation.Nullable;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityHanging;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;
import net.minecraftforge.fml.common.network.ByteBufUtils;

/** Original five-block hanging display, including a visible recoverable unassigned state. */
public final class EntityStandard extends EntityHanging implements IEntityAdditionalSpawnData {
    private static final DataParameter<String> VARIANT = EntityDataManager.createKey(EntityStandard.class, DataSerializers.STRING);
    private StandardRecord record = StandardRecord.read(new NBTTagCompound());
    private boolean broken;

    public EntityStandard(World world) { super(world); }
    public EntityStandard(World world, BlockPos anchor, EnumFacing facing, StandardRecord record) {
        super(world, anchor);
        this.record = record;
        syncVariant();
        updateFacingWithBoundingBox(facing);
    }
    @Override protected void entityInit() { dataManager.register(VARIANT, ""); }
    public StandardVariant variant() { return StandardVariant.byKey(dataManager.get(VARIANT)); }
    private void syncVariant() { dataManager.set(VARIANT, record.variant() == null ? "" : record.variant().key); }
    public void assign(StandardVariant variant) { record.assign(variant); syncVariant(); }
    public BlockPos anchor() { return hangingPosition; }
    public EnumFacing facing() { return facingDirection == null ? EnumFacing.SOUTH : facingDirection; }
    @Override public int getWidthPixels() { return StandardGeometry.PIXELS; }
    @Override public int getHeightPixels() { return StandardGeometry.PIXELS; }
    @Override public boolean onValidSurface() {
        if (hangingPosition == null || !StandardGeometry.horizontal(facingDirection)) return false;
        if (!world.getWorldBorder().contains(getEntityBoundingBox())
                || !world.getCollisionBoxes(this, getEntityBoundingBox()).isEmpty()) return false;
        for (BlockPos backing : StandardGeometry.backingCells(hangingPosition, facingDirection)) {
            BlockPos display = backing.offset(facingDirection);
            if (display.getY() < 0 || display.getY() >= world.getHeight()
                    || !world.isBlockLoaded(backing) || !world.isBlockLoaded(display)
                    || !world.getBlockState(backing).isSideSolid(world, backing, facingDirection)) return false;
        }
        for (Entity entity : world.getEntitiesInAABBexcluding(this, getEntityBoundingBox(), null)) {
            if (entity instanceof EntityHanging && !entity.isDead) return false;
        }
        return true;
    }
    @Override public void playPlaceSound() { playSound(SoundEvents.ENTITY_PAINTING_PLACE, 1.0F, 1.0F); }
    @Override public void onBroken(@Nullable Entity breaker) {
        if (world.isRemote || broken) return;
        broken = true;
        playSound(SoundEvents.ENTITY_PAINTING_BREAK, 1.0F, 1.0F);
        // Include current vanilla hanging fields and every opaque old field in
        // the recovered item; a missing legacy variant never destroys its data.
        NBTTagCompound preserved = new NBTTagCompound();
        writeToNBT(preserved);
        if (variant() == null && world instanceof WorldServer) {
            WorldServer serverWorld = (WorldServer) world;
            if (StandardsArchive.get(serverWorld).keep(record.origin(), preserved))
                serverWorld.getMinecraftServer().getWorld(0).getMapStorage().saveAllData();
        }
        if (!world.getGameRules().getBoolean("doEntityDrops")
                || breaker instanceof EntityPlayer && ((EntityPlayer) breaker).capabilities.isCreativeMode) return;
        StandardRecord dropRecord = StandardRecord.read(preserved);
        ItemStack drop = dropRecord.drop();
        if (hasCustomName()) drop.setStackDisplayName(getCustomNameTag());
        entityDropItem(drop, 0.0F);
    }
    @Override public ItemStack getPickedResult(net.minecraft.util.math.RayTraceResult target) {
        NBTTagCompound saved = new NBTTagCompound(); writeToNBT(saved);
        ItemStack picked = StandardRecord.read(saved).drop();
        if (hasCustomName()) picked.setStackDisplayName(getCustomNameTag());
        return picked;
    }
    @Override public boolean processInitialInteract(EntityPlayer player, EnumHand hand) {
        if (variant() != null) return false;
        if (!world.isRemote) {
            String text = "This standard's saved village is unassigned. Breaking it preserves its saved data.";
            if (player.canUseCommand(2, "standards")) text += " Assign it with /standards resolve " + getUniqueID() + " <stone|sand|mist|leaf|cloud>.";
            player.sendMessage(new TextComponentString(text));
        }
        return true;
    }
    @Override public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        record.write(tag);
    }
    @Override public void readEntityFromNBT(NBTTagCompound tag) {
        record = StandardRecord.read(tag);
        super.readEntityFromNBT(tag);
        syncVariant(); broken = false;
    }
    @Override public void writeSpawnData(ByteBuf buffer) {
        buffer.writeInt(hangingPosition.getX()); buffer.writeInt(hangingPosition.getY()); buffer.writeInt(hangingPosition.getZ());
        buffer.writeByte(facing().getHorizontalIndex());
        ByteBufUtils.writeUTF8String(buffer, variant() == null ? "" : variant().key);
    }
    @Override public void readSpawnData(ByteBuf buffer) {
        hangingPosition = new BlockPos(buffer.readInt(), buffer.readInt(), buffer.readInt());
        int facing = buffer.readUnsignedByte();
        if (facing > 3) throw new IllegalArgumentException("Invalid standard facing");
        String key = ByteBufUtils.readUTF8String(buffer);
        StandardVariant selected = StandardVariant.byKey(key);
        dataManager.set(VARIANT, selected == null ? "" : selected.key);
        updateFacingWithBoundingBox(EnumFacing.byHorizontalIndex(facing));
    }
}
