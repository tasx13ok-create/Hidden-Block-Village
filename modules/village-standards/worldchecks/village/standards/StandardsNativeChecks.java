package village.standards;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Explicit disposable-world helper; excluded from every production artifact. */
@Mod(modid = "valestandardschecks", name = "Private Village Standards Checks", version = "0.3.0",
     acceptableRemoteVersions = "*", dependencies = "required-after:hvdungeonpictures@[0.3.0]")
public final class StandardsNativeChecks {
    private static int assertions;
    private static void check(boolean value, String why) { assertions++; if (!value) throw new AssertionError(why); }
    public static int run(EntityPlayerMP caller) throws Exception {
        if (!Boolean.getBoolean("valestandards.nativecheck")
                || !"vale-recovery-test".equals(caller.getServer().getFolderName())
                || !new File(".vale-recovery-test").isFile()) throw new IllegalStateException("Standards checks require the marked disposable recovery world and explicit flag");
        for (EntityPlayerMP online : caller.getServer().getPlayerList().getPlayers())
            if (online != caller) throw new IllegalStateException("Standards checks found another connected player");
        assertions = 0;
        WorldServer world = caller.getServerWorld();
        PlacementPlayer player = new PlacementPlayer(world);
        Map<BlockPos, IBlockState> original = new LinkedHashMap<>();
        List<Entity> fixtureEntities = new ArrayList<>();
        String drops = world.getGameRules().getString("doEntityDrops");
        world.getGameRules().setOrCreateGameRule("doEntityDrops", "true");
        try {
            check(EntityList.getClass(new ResourceLocation(StandardsMod.ID, "dungeon_picture")) == EntityStandard.class, "Registered display entity key is absent");
            for (StandardVariant variant : StandardVariant.values())
                check(Item.REGISTRY.getObject(new ResourceLocation(StandardsMod.ID, variant.itemKey())) == StandardsMod.item(variant), "Historic standard item ID changed");
            check(StandardsMod.item(null) != null, "Unassigned recovery item is missing");
            int count = 0;
            for (EnumFacing facing : EnumFacing.HORIZONTALS) for (StandardVariant variant : StandardVariant.values()) {
                BlockPos center = new BlockPos(600 + (count % 5) * 12, 100, 600 + (count / 5) * 12); count++;
                buildWall(world, center, facing, original);
                player.setPosition(center.getX() + facing.getXOffset() * 3, center.getY(), center.getZ() + facing.getZOffset() * 3);
                ItemStack held = new ItemStack(StandardsMod.item(variant), 2);
                held.setStackDisplayName("Synthetic " + variant.key + " standard");
                NBTTagCompound tag = held.getTagCompound(); tag.setString("valeSerial", "standard-fixture-" + count);
                player.setHeldItem(EnumHand.MAIN_HAND, held);
                EnumActionResult placed = held.getItem().onItemUse(player, world, center, EnumHand.MAIN_HAND, facing, .5F, .5F, .5F);
                check(placed == EnumActionResult.SUCCESS && held.getCount() == 1, "Valid native placement must consume exactly one item");
                List<EntityStandard> pictures = world.getEntitiesWithinAABB(EntityStandard.class, new AxisAlignedBB(center).grow(4));
                check(pictures.size() == 1, "Native placement must create exactly one standard");
                EntityStandard picture = pictures.get(0); fixtureEntities.add(picture);
                check(picture.variant() == variant && picture.facing() == facing && picture.anchor().equals(center.offset(facing)), "Variant/facing/anchor changed at placement");
                AxisAlignedBB box = picture.getEntityBoundingBox();
                check(Math.abs((box.maxY - box.minY) - 5) < 1e-8, "Picture must occupy exactly five blocks vertically");
                check(Math.abs((facing.getAxis() == EnumFacing.Axis.Z ? box.maxX - box.minX : box.maxZ - box.minZ) - 5) < 1e-8, "Picture must occupy exactly five blocks across the wall");
                check(Math.abs((facing.getAxis() == EnumFacing.Axis.Z ? box.maxZ - box.minZ : box.maxX - box.minX) - .0625) < 1e-8, "Picture thickness must be exactly one sixteenth block");
                check(held.getItem().onItemUse(player, world, center, EnumHand.MAIN_HAND, facing, .5F, .5F, .5F) == EnumActionResult.FAIL
                    && held.getCount() == 1, "Overlapping native hanging entities must not consume another item");
                NBTTagCompound saved = picture.writeToNBT(new NBTTagCompound());
                saved.setString("UnrecognizedLegacyField", "kept-through-save-and-drop");
                // This fixture reloads the same UUID in the same tick. Ordinary
                // removal only schedules cleanup and would reject that reload.
                picture.setDead(); world.removeEntityDangerously(picture);
                EntityStandard restored = new EntityStandard(world); restored.readFromNBT(saved);
                check(restored.variant() == variant && restored.facing() == facing && restored.anchor().equals(center.offset(facing)), "Native entity NBT round-trip changed display identity");
                check(restored.onValidSurface() && world.spawnEntity(restored), "Restored standard must retain its valid wall bounds");
                fixtureEntities.add(restored);
                ByteBuf packet = Unpooled.buffer();
                try {
                    restored.writeSpawnData(packet);
                    EntityStandard tracked = new EntityStandard(world); tracked.readSpawnData(packet);
                    check(tracked.variant() == variant && tracked.facing() == facing && tracked.anchor().equals(restored.anchor()), "Spawn data lost variant or wall transform");
                    check(tracked.getEntityBoundingBox().equals(restored.getEntityBoundingBox()), "Tracked client bounding box differs from server");
                } finally { packet.release(); }
                int before = dropCount(world, center);
                check(restored.attackEntityFrom(DamageSource.causePlayerDamage(player), 1), "Native hanging attack must break a normal display");
                restored.onBroken(player);
                List<EntityItem> dropped = world.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(center).grow(5));
                check(dropCount(world, center) == before + 1, "Breaking/repeated callback must produce exactly one item");
                EntityItem item = dropped.get(dropped.size() - 1); fixtureEntities.add(item);
                ItemStack recovered = item.getItem();
                check(recovered.getItem() == StandardsMod.item(variant) && recovered.getCount() == 1, "Broken standard must retain its village item");
                check(recovered.getDisplayName().equals(held.getDisplayName())
                    && recovered.getTagCompound().getString("valeSerial").equals(tag.getString("valeSerial")), "Custom name or reward serial was lost");
                check(recovered.getSubCompound(StandardRecord.HISTORY).getString("UnrecognizedLegacyField").equals("kept-through-save-and-drop"), "Opaque saved metadata was lost on drop");
                world.removeEntityDangerously(item);
            }
            failureCases(world, player, original, fixtureEntities);
            unresolvedRecovery(world, player, original, fixtureEntities);
            System.out.println("VALE_STANDARDS_NATIVE_PASS: " + assertions + " assertions, all five pictures/four facings, placement/drop/NBT/cancellation/recovery");
            return assertions;
        } finally {
            for (Entity entity : fixtureEntities) { entity.setDead(); world.removeEntityDangerously(entity); }
            for (Map.Entry<BlockPos, IBlockState> entry : original.entrySet()) world.setBlockState(entry.getKey(), entry.getValue(), 2);
            world.getGameRules().setOrCreateGameRule("doEntityDrops", drops);
        }
    }
    private static void failureCases(WorldServer world, PlacementPlayer player, Map<BlockPos, IBlockState> original, List<Entity> entities) {
        BlockPos center = new BlockPos(680, 100, 600); EnumFacing face = EnumFacing.SOUTH;
        buildWall(world, center, face, original);
        ItemStack held = new ItemStack(StandardsMod.item(StandardVariant.LEAF), 3);
        player.setHeldItem(EnumHand.MAIN_HAND, held);
        check(held.getItem().onItemUse(player, world, center, EnumHand.MAIN_HAND, EnumFacing.UP, 0,0,0) == EnumActionResult.FAIL && held.getCount() == 3,
            "Floor/ceiling placement cannot consume a standard");
        BlockPos corner = center.up(2).east(2);
        world.setBlockState(corner, Blocks.AIR.getDefaultState(), 2);
        check(held.getItem().onItemUse(player, world, center, EnumHand.MAIN_HAND, face, 0,0,0) == EnumActionResult.FAIL && held.getCount() == 3,
            "Missing outer backing block cannot consume a standard");
        world.setBlockState(corner, Blocks.STONE.getDefaultState(), 2);
        player.denied = corner;
        check(held.getItem().onItemUse(player, world, center, EnumHand.MAIN_HAND, face, 0,0,0) == EnumActionResult.FAIL && held.getCount() == 3,
            "Permission denial on an outer block cannot consume a standard");
        player.denied = null;
        CancelPlacement cancel = new CancelPlacement(); MinecraftForge.EVENT_BUS.register(cancel);
        try {
            check(held.getItem().onItemUse(player, world, center, EnumHand.MAIN_HAND, face, 0,0,0) == EnumActionResult.FAIL
                && held.getCount() == 3 && cancel.cancelled == 1, "Cancelled native spawn must preserve the held item");
        } finally { MinecraftForge.EVENT_BUS.unregister(cancel); }
        player.capabilities.isCreativeMode = true;
        check(held.getItem().onItemUse(player, world, center, EnumHand.MAIN_HAND, face, 0,0,0) == EnumActionResult.SUCCESS && held.getCount() == 3,
            "Creative placement must retain the inventory item");
        EntityStandard picture = world.getEntitiesWithinAABB(EntityStandard.class, new AxisAlignedBB(center).grow(4)).get(0); entities.add(picture);
        int before = dropCount(world, center); picture.attackEntityFrom(DamageSource.causePlayerDamage(player), 1);
        check(dropCount(world, center) == before, "Creative removal must not manufacture a dropped item");
        player.capabilities.isCreativeMode = false;
        check(held.getItem().onItemUse(player, world, center, EnumHand.MAIN_HAND, face, 0,0,0) == EnumActionResult.SUCCESS, "Replaced wall must remain usable");
        picture = world.getEntitiesWithinAABB(EntityStandard.class, new AxisAlignedBB(center).grow(4)).stream().filter(p -> !p.isDead).findFirst().get(); entities.add(picture);
        world.setBlockState(corner, Blocks.AIR.getDefaultState(), 2);
        for (int tick = 0; tick <= 101; tick++) picture.onUpdate();
        check(picture.isDead && dropCount(world, center) == before + 1, "Removing any backing block must release one standard");
        entities.addAll(world.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(center).grow(5)));
    }
    private static void unresolvedRecovery(WorldServer world, PlacementPlayer player, Map<BlockPos, IBlockState> original, List<Entity> entities) {
        BlockPos center = new BlockPos(700, 100, 600); EnumFacing face = EnumFacing.NORTH;
        buildWall(world, center, face, original);
        NBTTagCompound unknown = new NBTTagCompound(); unknown.setString("OldVillageCode", "unmapped-reference-value");
        unknown.setInteger("TileX", center.getX()); unknown.setInteger("TileY", center.getY()); unknown.setInteger("TileZ", center.getZ() - 1);
        unknown.setByte("Facing", (byte) face.getHorizontalIndex());
        UUID origin = UUID.randomUUID(); unknown.setUniqueId("UUID", origin);
        EntityStandard unassigned = new EntityStandard(world); unassigned.readFromNBT(unknown);
        check(unassigned.variant() == null && unassigned.onValidSurface() && world.spawnEntity(unassigned), "Unknown legacy data must become an explicit valid unassigned panel");
        entities.add(unassigned);
        int archiveBefore = StandardsArchive.get(world).size();
        unassigned.attackEntityFrom(DamageSource.causePlayerDamage(player), 1);
        List<EntityItem> drops = world.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(center).grow(5));
        check(unassigned.isDead && drops.size() == 1 && drops.get(0).getItem().getItem() == StandardsMod.item(null), "Unassigned panel must be breakable into its recovery item");
        ItemStack recovered = drops.get(0).getItem().copy(); entities.add(drops.get(0)); world.removeEntityDangerously(drops.get(0));
        check(StandardsArchive.get(world).size() == archiveBefore + 1
            && StandardsArchive.get(world).copy(origin).getString("OldVillageCode").equals("unmapped-reference-value"), "Opaque legacy data must also persist before its item can be lost");
        int largest = recovered.getTagCompound().toString().length();
        for (int cycle = 0; cycle < 6; cycle++) {
            player.setHeldItem(EnumHand.MAIN_HAND, recovered.copy());
            check(StandardsMod.item(null).onItemUse(player, world, center, EnumHand.MAIN_HAND, face, 0,0,0) == EnumActionResult.SUCCESS, "Recovered unassigned data must be placeable again");
            EntityStandard panel = world.getEntitiesWithinAABB(EntityStandard.class, new AxisAlignedBB(center).grow(4)).stream().filter(p -> !p.isDead).findFirst().get(); entities.add(panel);
            panel.attackEntityFrom(DamageSource.causePlayerDamage(player), 1);
            List<EntityItem> items = world.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(center).grow(5));
            EntityItem dropped = items.stream().filter(e -> !e.isDead).findFirst().get(); entities.add(dropped);
            recovered = dropped.getItem().copy(); world.removeEntityDangerously(dropped);
            check(recovered.getSubCompound(StandardRecord.HISTORY).getString("OldVillageCode").equals("unmapped-reference-value"), "Repeated recovery must retain original custom metadata");
            check(recovered.getTagCompound().toString().length() <= largest + 600, "Repeated placement must not recursively grow saved history");
            check(StandardsArchive.get(world).size() == archiveBefore + 1, "Repeated placement must not duplicate archive records");
        }
    }
    private static int dropCount(WorldServer world, BlockPos center) {
        int count = 0;
        for (EntityItem item : world.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(center).grow(5)))
            if (!item.isDead && item.getItem().getItem() instanceof ItemStandard) count += item.getItem().getCount();
        return count;
    }
    private static void buildWall(WorldServer world, BlockPos center, EnumFacing facing, Map<BlockPos, IBlockState> original) {
        for (BlockPos back : StandardGeometry.backingCells(center.offset(facing), facing)) {
            for (int distance = 0; distance <= 2; distance++) {
                BlockPos pos = back.offset(facing, distance);
                world.getChunk(pos);
                original.putIfAbsent(pos, world.getBlockState(pos));
                world.setBlockState(pos, distance == 0 ? Blocks.STONE.getDefaultState() : Blocks.AIR.getDefaultState(), 2);
            }
        }
    }
    public static final class PlacementPlayer extends FakePlayer {
        BlockPos denied;
        PlacementPlayer(WorldServer world) { super(world, new GameProfile(UUID.fromString("eab3c159-9715-4abb-8c6a-b76e62890234"), "StandardsFixture")); }
        @Override public boolean canPlayerEdit(BlockPos pos, EnumFacing side, ItemStack stack) { return denied == null || !denied.equals(pos); }
    }
    public static final class CancelPlacement {
        int cancelled;
        @SubscribeEvent public void cancel(EntityJoinWorldEvent event) {
            if (event.getEntity() instanceof EntityStandard) { cancelled++; event.setCanceled(true); }
        }
    }
}
