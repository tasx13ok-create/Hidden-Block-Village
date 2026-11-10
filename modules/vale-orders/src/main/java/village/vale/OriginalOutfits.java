package village.vale;
import java.util.*;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.*;
import net.minecraft.init.*;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.EnumHelper;
/** Original freely tradable standard garments. Art/unique legacy powers are deliberately not claimed here. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=ValeOrders.ID)
public final class OriginalOutfits {
    private OriginalOutfits(){}
    public static final class Spec {public final String key,title;public final EntityEquipmentSlot slot;public final Order order;Spec(String k,String t,EntityEquipmentSlot s,Order o){key=k;title=t;slot=s;order=o;}}
    public static final Spec[] SPECS={
        new Spec("outfit_bracken_coat","Bracken Coat",EntityEquipmentSlot.CHEST,Order.EMBER),
        new Spec("outfit_copperleaf_hood","Copperleaf Hood",EntityEquipmentSlot.HEAD,Order.CURRENT),
        new Spec("outfit_duskmere_coat","Duskmere Coat",EntityEquipmentSlot.CHEST,Order.GALE),
        new Spec("outfit_cairn_coat","Cairn Coat",EntityEquipmentSlot.CHEST,Order.FLINT),
        new Spec("outfit_reedbank_hood","Reedbank Hood",EntityEquipmentSlot.HEAD,Order.BLOOM),
        new Spec("outfit_highridge_coat","Highridge Coat",EntityEquipmentSlot.CHEST,Order.RIME),
        new Spec("outfit_mosslight_hood","Mosslight Hood",EntityEquipmentSlot.HEAD,Order.STORM),
        new Spec("outfit_ironbark_coat","Ironbark Coat",EntityEquipmentSlot.CHEST,Order.DUSK),
        new Spec("outfit_sunfen_hood","Sunfen Hood",EntityEquipmentSlot.HEAD,Order.DAWN),
        new Spec("outfit_rimeglass_coat","Rimeglass Coat",EntityEquipmentSlot.CHEST,Order.EMBER),
        new Spec("outfit_ashfield_hood","Ashfield Hood",EntityEquipmentSlot.HEAD,Order.CURRENT),
        new Spec("outfit_tidebound_hood","Tidebound Hood",EntityEquipmentSlot.HEAD,Order.GALE),
        new Spec("outfit_thistle_coat","Thistle Coat",EntityEquipmentSlot.CHEST,Order.FLINT),
        new Spec("outfit_thistle_hood","Thistle Hood",EntityEquipmentSlot.HEAD,Order.FLINT),
        new Spec("outfit_stormreed_coat","Stormreed Coat",EntityEquipmentSlot.CHEST,Order.BLOOM),
        new Spec("outfit_ochre_coat","Ochre Coat",EntityEquipmentSlot.CHEST,Order.RIME),
        new Spec("outfit_ochre_hood","Ochre Hood",EntityEquipmentSlot.HEAD,Order.RIME),
        new Spec("outfit_willow_coat","Willow Coat",EntityEquipmentSlot.CHEST,Order.STORM),
        new Spec("outfit_willow_hood","Willow Hood",EntityEquipmentSlot.HEAD,Order.STORM),
        new Spec("outfit_dunestone_hood","Dunestone Hood",EntityEquipmentSlot.HEAD,Order.DUSK),
        new Spec("outfit_moonfern_hood","Moonfern Hood",EntityEquipmentSlot.HEAD,Order.DAWN),
        new Spec("outfit_kilnward_hood","Kilnward Hood",EntityEquipmentSlot.HEAD,Order.EMBER),
        new Spec("outfit_galecrest_hood","Galecrest Hood",EntityEquipmentSlot.HEAD,Order.CURRENT),
        new Spec("outfit_riverflint_hood","Riverflint Hood",EntityEquipmentSlot.HEAD,Order.GALE),
        new Spec("outfit_ambermoss_hood","Ambermoss Hood",EntityEquipmentSlot.HEAD,Order.FLINT),
        new Spec("outfit_mistroot_hood","Mistroot Hood",EntityEquipmentSlot.HEAD,Order.BLOOM),
        new Spec("outfit_pearlwood_hood","Pearlwood Hood",EntityEquipmentSlot.HEAD,Order.RIME),
        new Spec("outfit_pearlwood_trousers","Pearlwood Trousers",EntityEquipmentSlot.LEGS,Order.RIME),
        new Spec("outfit_bellstone_trousers","Bellstone Trousers",EntityEquipmentSlot.LEGS,Order.STORM),
        new Spec("outfit_cinderleaf_coat","Cinderleaf Coat",EntityEquipmentSlot.CHEST,Order.DUSK),
        new Spec("outfit_cinderleaf_hood","Cinderleaf Hood",EntityEquipmentSlot.HEAD,Order.DUSK),
        new Spec("outfit_cinderleaf_trousers","Cinderleaf Trousers",EntityEquipmentSlot.LEGS,Order.DUSK),
        new Spec("outfit_silverfen_hood","Silverfen Hood",EntityEquipmentSlot.HEAD,Order.DAWN),
        new Spec("outfit_silverfen_trousers","Silverfen Trousers",EntityEquipmentSlot.LEGS,Order.DAWN),
        new Spec("outfit_hollowreed_trousers","Hollowreed Trousers",EntityEquipmentSlot.LEGS,Order.EMBER),
        new Spec("outfit_frostpine_coat","Frostpine Coat",EntityEquipmentSlot.CHEST,Order.CURRENT),
        new Spec("outfit_frostpine_hood","Frostpine Hood",EntityEquipmentSlot.HEAD,Order.CURRENT),
        new Spec("outfit_frostpine_trousers","Frostpine Trousers",EntityEquipmentSlot.LEGS,Order.CURRENT),
        new Spec("outfit_cloudmere_coat","Cloudmere Coat",EntityEquipmentSlot.CHEST,Order.GALE),
        new Spec("outfit_cloudmere_hood","Cloudmere Hood",EntityEquipmentSlot.HEAD,Order.GALE),
        new Spec("outfit_cloudmere_trousers","Cloudmere Trousers",EntityEquipmentSlot.LEGS,Order.GALE),
        new Spec("outfit_brookstone_coat","Brookstone Coat",EntityEquipmentSlot.CHEST,Order.FLINT),
        new Spec("outfit_brookstone_hood","Brookstone Hood",EntityEquipmentSlot.HEAD,Order.FLINT),
        new Spec("outfit_brookstone_trousers","Brookstone Trousers",EntityEquipmentSlot.LEGS,Order.FLINT),
        new Spec("outfit_rosequartz_coat","Rosequartz Coat",EntityEquipmentSlot.CHEST,Order.BLOOM),
        new Spec("outfit_rosequartz_trousers","Rosequartz Trousers",EntityEquipmentSlot.LEGS,Order.BLOOM),
        new Spec("outfit_nightbloom_hood","Nightbloom Hood",EntityEquipmentSlot.HEAD,Order.RIME),
        new Spec("outfit_goldenrush_coat","Goldenrush Coat",EntityEquipmentSlot.CHEST,Order.STORM),
        new Spec("outfit_goldenrush_hood","Goldenrush Hood",EntityEquipmentSlot.HEAD,Order.STORM),
        new Spec("outfit_reedstone_coat","Reedstone Coat",EntityEquipmentSlot.CHEST,Order.DUSK),
        new Spec("outfit_reedstone_hood","Reedstone Hood",EntityEquipmentSlot.HEAD,Order.DUSK),
        new Spec("outfit_sablewood_coat","Sablewood Coat",EntityEquipmentSlot.CHEST,Order.DAWN),
        new Spec("outfit_sablewood_hood","Sablewood Hood",EntityEquipmentSlot.HEAD,Order.DAWN),
        new Spec("outfit_windglass_coat","Windglass Coat",EntityEquipmentSlot.CHEST,Order.EMBER),
        new Spec("outfit_rainbloom_coat","Rainbloom Coat",EntityEquipmentSlot.CHEST,Order.CURRENT),
        new Spec("outfit_rainbloom_hood","Rainbloom Hood",EntityEquipmentSlot.HEAD,Order.CURRENT),
        new Spec("outfit_rainbloom_trousers","Rainbloom Trousers",EntityEquipmentSlot.LEGS,Order.CURRENT),
        new Spec("outfit_brightcairn_coat","Brightcairn Coat",EntityEquipmentSlot.CHEST,Order.GALE),
        new Spec("outfit_brightcairn_trousers","Brightcairn Trousers",EntityEquipmentSlot.LEGS,Order.GALE),
        new Spec("outfit_shadowpine_coat","Shadowpine Coat",EntityEquipmentSlot.CHEST,Order.FLINT),
        new Spec("outfit_shadowpine_trousers","Shadowpine Trousers",EntityEquipmentSlot.LEGS,Order.FLINT),
        new Spec("outfit_sunstone_coat","Sunstone Coat",EntityEquipmentSlot.CHEST,Order.BLOOM),
        new Spec("outfit_sunstone_trousers","Sunstone Trousers",EntityEquipmentSlot.LEGS,Order.BLOOM),
        new Spec("outfit_coastfern_coat","Coastfern Coat",EntityEquipmentSlot.CHEST,Order.RIME),
        new Spec("outfit_coastfern_trousers","Coastfern Trousers",EntityEquipmentSlot.LEGS,Order.RIME),
        new Spec("outfit_sagewood_coat","Sagewood Coat",EntityEquipmentSlot.CHEST,Order.STORM),
        new Spec("outfit_sagewood_trousers","Sagewood Trousers",EntityEquipmentSlot.LEGS,Order.STORM),
        new Spec("outfit_redwillow_coat","Redwillow Coat",EntityEquipmentSlot.CHEST,Order.DUSK),
        new Spec("outfit_redwillow_trousers","Redwillow Trousers",EntityEquipmentSlot.LEGS,Order.DUSK),
        new Spec("outfit_wildrime_coat","Wildrime Coat",EntityEquipmentSlot.CHEST,Order.DAWN),
        new Spec("outfit_wildrime_trousers","Wildrime Trousers",EntityEquipmentSlot.LEGS,Order.DAWN),
        new Spec("outfit_deepcopper_coat","Deepcopper Coat",EntityEquipmentSlot.CHEST,Order.EMBER),
        new Spec("outfit_deepcopper_trousers","Deepcopper Trousers",EntityEquipmentSlot.LEGS,Order.EMBER),
        new Spec("outfit_violetfen_coat","Violetfen Coat",EntityEquipmentSlot.CHEST,Order.CURRENT),
        new Spec("outfit_violetfen_hood","Violetfen Hood",EntityEquipmentSlot.HEAD,Order.CURRENT),
        new Spec("outfit_violetfen_trousers","Violetfen Trousers",EntityEquipmentSlot.LEGS,Order.CURRENT),
        new Spec("outfit_saltbloom_coat","Saltbloom Coat",EntityEquipmentSlot.CHEST,Order.GALE),
        new Spec("outfit_saltbloom_hood","Saltbloom Hood",EntityEquipmentSlot.HEAD,Order.GALE),
        new Spec("outfit_saltbloom_trousers","Saltbloom Trousers",EntityEquipmentSlot.LEGS,Order.GALE),
    };
    public static void register(RegistryEvent.Register<Item> event){
        ItemArmor.ArmorMaterial material=EnumHelper.addArmorMaterial("VALE_FIELD_GARMENTS","minecraft:iron",24,new int[]{2,5,6,2},14,SoundEvents.ITEM_ARMOR_EQUIP_LEATHER,0);
        material.setRepairItem(new ItemStack(Items.LEATHER));
        for(Spec spec:SPECS)Gear.add(event,spec.key,new Garment(spec,material));
    }
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
    public static void recipes(RegistryEvent.Register<IRecipe> event){
        int index=0;
        for(Spec spec:SPECS){
            Item item=Gear.ITEMS.get(spec.key);ItemStack dye=new ItemStack(Items.DYE,1,(index/16)%16);ItemStack cloth=new ItemStack(Blocks.WOOL,1,index%16);index++;
            // A distinct dye focus outside the shaped armor pattern prevents variant recipe ambiguity.
            String[] shape=spec.slot==EntityEquipmentSlot.HEAD?new String[]{"LCL","LDL"}:spec.slot==EntityEquipmentSlot.CHEST?new String[]{"LDL","LCL","LLL"}:spec.slot==EntityEquipmentSlot.LEGS?new String[]{"LLL","LCL","LDL"}:new String[]{"L L","CDC"};
            ResourceLocation id=new ResourceLocation(ValeOrders.ID,"garment_"+spec.key);
            IRecipe recipe=new ShapedOreRecipe(id,new ItemStack(item),shape[0],shape[1],shape.length==3?shape[2]:"   ",'L',Items.LEATHER,'C',cloth,'D',dye).setRegistryName(id);
            event.getRegistry().register(recipe);
        }
    }
    public static final class Garment extends ItemArmor implements Gear.Ordered {
        public final Spec spec;Garment(Spec s,ArmorMaterial material){super(material,0,s.slot);spec=s;setCreativeTab(CreativeTabs.COMBAT);}
        @Override public Order order(){return spec.order;}
        @Override public String getItemStackDisplayName(ItemStack stack){return spec.title;}
        @Override public String getArmorTexture(ItemStack stack,Entity entity,EntityEquipmentSlot slot,String type){return "minecraft:textures/models/armor/iron_layer_"+(slot==EntityEquipmentSlot.LEGS?2:1)+".png";}
        @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
        @Override public void addInformation(ItemStack stack,net.minecraft.world.World world,List<String> lines,net.minecraft.client.util.ITooltipFlag flag){lines.add("Field garment | "+spec.order.title);lines.add("Standard armor; freely tradable. Does not grant resonance powers.");lines.add("Visual design pending: standard Minecraft armor appearance.");}
    }
}
