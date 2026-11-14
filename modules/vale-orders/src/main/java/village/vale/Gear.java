package village.vale;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.common.util.EnumHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@Mod.EventBusSubscriber(modid = ValeOrders.ID)
public final class Gear {
    public static final Map<String, Item> ITEMS = new LinkedHashMap<>();
    public interface Ordered { Order order(); }
    private static final String[] WEAPONS = {"saber", "spear", "dagger", "glaive", "staff", "axe"};
    private static final float[] DAMAGE = {6, 7, 4, 8, 5, 9};
    private static final float[] SPEED = {1.6F, 1.3F, 2.0F, 1.0F, 1.8F, 0.9F};
    private Gear() {}

    @SubscribeEvent
    public static void register(RegistryEvent.Register<Item> event) {
        for (Order order : Order.values()) {
            ItemArmor.ArmorMaterial armor = EnumHelper.addArmorMaterial("VALE_" + order.name(),
                ValeOrders.ID + ":" + order.key, 40, new int[] {3, 6, 8, 3}, 12,
                SoundEvents.ITEM_ARMOR_EQUIP_IRON, 2.0F);
            armor.setRepairItem(new ItemStack(net.minecraft.init.Items.IRON_INGOT));
            for (EntityEquipmentSlot slot : new EntityEquipmentSlot[] {EntityEquipmentSlot.HEAD,
                    EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET}) {
                String part = slot == EntityEquipmentSlot.HEAD ? "helmet" : slot == EntityEquipmentSlot.CHEST ?
                    "chestplate" : slot == EntityEquipmentSlot.LEGS ? "leggings" : "boots";
                add(event, order.key + "_" + part, new WardArmor(order, armor, slot));
            }
            for (int i = 0; i < WEAPONS.length; i++) {
                Item.ToolMaterial material = EnumHelper.addToolMaterial("VALE_" + order.name() + "_" + i,
                    2, 512, 6.0F, DAMAGE[i] - 3.0F, 12);
                material.setRepairItem(new ItemStack(net.minecraft.init.Items.IRON_INGOT));
                add(event, order.key + "_" + WEAPONS[i], new WardBlade(order, material, SPEED[i], WEAPONS[i]));
            }
        }
        add(event, "field_manual", new Item() {
            @Override public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
                if (!world.isRemote) {
                    player.sendMessage(new net.minecraft.util.text.TextComponentString(
                        "Vale Orders: equip four matching issued armor pieces and right-click a matching issued weapon. " +
                        "Sneak + right-click uses your weapon technique. Skills use resolve. " +
                        "Start with /vale join <order>. Levels 2-6 unlock your dagger, spear, glaive, staff and axe. Use /vale status or /vale catalog."));
                    player.sendMessage(new net.minecraft.util.text.TextComponentString("Your technique codex casts learned arts; sneak + use cycles them. /vale arts and /vale missions open study and contracts. Forms and companions unlock at higher levels. Craft recovery meals; use your trial writ in a clear arena."));
                    for (Order order : Order.values())
                        player.sendMessage(new net.minecraft.util.text.TextComponentString(order.title + ": " + order.skill));
                }
                return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
            }
        }.setCreativeTab(CreativeTabs.MISC).setMaxStackSize(1));
        FieldItems.register(event);
        OriginalOutfits.register(event);
        OriginalMilestones.register(event);
        OriginalFocusGear.register(event);
        OriginalFieldTools.register(event);
        village.vale.culinary.Culinary.registerItems(event);
        village.vale.arsenal.Arsenal.register(event);
        village.vale.arsenal.Scrolls.register(event);
        OriginalArmorRegistry.register(event);
    }

    static void add(RegistryEvent.Register<Item> event, String key, Item item) {
        item.setRegistryName(new ResourceLocation(ValeOrders.ID, key));
        item.setTranslationKey(ValeOrders.ID + "." + key);
        ITEMS.put(key, item);
        event.getRegistry().register(item);
    }

    public static String key(ItemStack stack) {
        ResourceLocation id = stack.getItem().getRegistryName();
        return id == null ? "" : id.toString();
    }

    public static final class WardArmor extends ItemArmor implements Ordered {
        private final Order school;
        WardArmor(Order order, ArmorMaterial material, EntityEquipmentSlot slot) {
            super(material, 0, slot);
            school = order; setCreativeTab(CreativeTabs.COMBAT);
        }
        @Override public Order order() { return school; }
        @Override public String getArmorTexture(ItemStack stack, Entity entity, EntityEquipmentSlot slot, String type) {
            boolean lightning = school == Order.STORM && entity instanceof net.minecraft.entity.EntityLivingBase && LightningKit.EFFECT != null && ((net.minecraft.entity.EntityLivingBase)entity).isPotionActive(LightningKit.EFFECT);
            return ArmorSkinPaths.texture(school.key,slot == EntityEquipmentSlot.LEGS,lightning);
        }
        @SideOnly(Side.CLIENT)
        @Override public void addInformation(ItemStack stack, @Nullable World world, java.util.List<String> lines, ITooltipFlag flag) {
            lines.add(school.title);
            lines.add("Four issued matching pieces unlock " + school.skill + ".");
            lines.add("Right-click a matching issued weapon to activate.");
            lines.add("Reward-bound powers; ordinary armor still protects.");
        }
    }

    public static final class WardBlade extends ItemSword implements Ordered {
        private final Order school;
        private final float swingSpeed;
        public final String kind;
        WardBlade(Order order, ToolMaterial material, float speed, String kind) {
            super(material); school = order; swingSpeed = speed; this.kind = kind;
            setCreativeTab(CreativeTabs.COMBAT);
        }
        @Override public Order order() { return school; }
        @Override public com.google.common.collect.Multimap<String, net.minecraft.entity.ai.attributes.AttributeModifier>
                getItemAttributeModifiers(EntityEquipmentSlot slot) {
            com.google.common.collect.Multimap<String, net.minecraft.entity.ai.attributes.AttributeModifier> map =
                super.getItemAttributeModifiers(slot);
            if (slot == EntityEquipmentSlot.MAINHAND) {
                map.removeAll(net.minecraft.entity.SharedMonsterAttributes.ATTACK_SPEED.getName());
                map.put(net.minecraft.entity.SharedMonsterAttributes.ATTACK_SPEED.getName(),
                    new net.minecraft.entity.ai.attributes.AttributeModifier(ATTACK_SPEED_MODIFIER,
                        "Weapon modifier", swingSpeed - 4.0D, 0));
            }
            return map;
        }
        @Override public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
            if(hand!=EnumHand.MAIN_HAND)return new ActionResult<>(EnumActionResult.PASS,player.getHeldItem(hand));
            ItemStack offhand=player.getHeldItemOffhand();
            if(!player.isSneaking()&&!offhand.isEmpty()&&offhand.getItem().isShield(offhand,player))return new ActionResult<>(EnumActionResult.PASS,player.getHeldItem(hand));
            if (!world.isRemote && hand == EnumHand.MAIN_HAND) {
                boolean cast=player.isSneaking()?Techniques.activate(player,this):Combat.activate(player,player.getHeldItem(hand),school);
                if(cast&&player instanceof net.minecraft.entity.player.EntityPlayerMP)Adventures.gesture((net.minecraft.entity.player.EntityPlayerMP)player);
            }
            return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
        }
        @SideOnly(Side.CLIENT)
        @Override public void addInformation(ItemStack stack, @Nullable World world, java.util.List<String> lines, ITooltipFlag flag) {
            lines.add(school.title + " / " + school.skill);
            lines.add("Right-click with the issued matching armor set.");
            lines.add("Sneak + right-click: " + Techniques.name(kind) + " (20 resolve, 6s).");
            lines.add("Skill powers require a server-issued weapon.");
            lines.add("An offhand shield takes normal right-click priority. Empty it to use the order skill.");
        }
    }
}
