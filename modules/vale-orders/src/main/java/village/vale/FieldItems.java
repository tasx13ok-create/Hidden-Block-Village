package village.vale;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.*;
import net.minecraft.init.*;
import net.minecraft.item.*;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.*;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.event.RegistryEvent;

/** Ordinary Minecraft items. All decisions and costs run on the logical server. */
public final class FieldItems {
    public static final String[] FOODS={"trail_biscuit","herb_broth","sunrice_bowl","river_skewer","root_stew","cloud_tea","dried_berries","smoked_mushrooms","orchard_flatbread","seared_fillet","spice_curry","travel_ration"};
    public static final int[] HUNGER={4,6,8,6,8,2,3,5,6,7,10,8};
    public static final float[] SATURATION={.4F,.6F,.75F,.6F,.8F,.2F,.3F,.45F,.7F,.7F,.8F,.9F};
    private FieldItems(){}
    public static void register(RegistryEvent.Register<Item> event){
        tool(event,"technique_codex",0,0);tool(event,"mission_journal",1,0);
        for(int i=0;i<Forms.KEYS.length;i++)tool(event,"form_"+Forms.KEYS[i],2,i);
        for(int i=0;i<3;i++)tool(event,"summon_"+Summons.KEYS[i],3,i);
        tool(event,"trial_writ",4,0);
        for(int i=0;i<3;i++)tool(event,new String[]{"needle_fan","snap_disc","smoke_capsule"}[i],5,i);
        for(int i=0;i<FOODS.length;i++)Gear.add(event,FOODS[i],new Meal(i));
    }
    private static void tool(RegistryEvent.Register<Item> e,String key,final int type,final int kind){
        Gear.add(e,key,new Item(){
            @Override @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
            public void addInformation(ItemStack stack,World world,java.util.List<String> lines,net.minecraft.client.util.ITooltipFlag flag){
                if(type==0){lines.add("Use: cast selected art. Sneak + use: cycle learned arts.");lines.add("/vale arts opens clickable techniques and school study.");}
                if(type==1){lines.add("Use: open contracts, progress and mission merit.");lines.add("Complete contracts, then /vale mission claim.");}
                if(type==2){lines.add("Level "+Forms.LEVELS[kind]+" | Matching issued armor required.");lines.add("60 resolve + 3/sec | Up to 20s | 120s cooldown.");lines.add("Use again to end early.");}
                if(type==3){lines.add("Level "+Summons.LEVELS[kind]+" | Matching issued armor required.");lines.add("35 resolve + 1/sec | Up to 60s | 45s cooldown.");lines.add("Use again to dismiss. Friendly NPCs are protected.");}
                if(type==4){lines.add("Use: start selected trial. Sneak + use: cycle trials.");lines.add("1 mission merit | 5 minute cooldown | Clear open arena.");}
                if(type==5){lines.add(kind==0?"Use: throw a three-needle fan.":kind==1?"Use: throw a spinning impact disc.":"Use: throw a weakening smoke capsule.");lines.add("Consumes one. Friendly NPCs are protected.");}
            }
            @Override public ActionResult<ItemStack> onItemRightClick(World w,EntityPlayer p,EnumHand hand){
                ItemStack stack=p.getHeldItem(hand);boolean ok=true;
                if(!w.isRemote&&p instanceof EntityPlayerMP){
                    EntityPlayerMP player=(EntityPlayerMP)p;
                    if(type==0){if(p.isSneaking())Arts.cycle(player);else ok=Arts.cast(player);}
                    else if(type==1)Missions.show(player);
                    else if(type==2)ok=Forms.activate(player,kind);
                    else if(type==3)ok=Summons.activate(player,kind);
                    else if(type==4)ok=Adventures.trial(player,p.isSneaking());
                    else ok=throwItem(player,stack,kind);
                }
                return new ActionResult<>(ok?EnumActionResult.SUCCESS:EnumActionResult.FAIL,stack);
            }
        }.setCreativeTab(CreativeTabs.COMBAT).setMaxStackSize(type==5?16:1));
    }
    public static boolean throwItem(EntityPlayerMP p,ItemStack stack,int kind){
        if(!p.isEntityAlive()||p.isSpectator()||p.getCooldownTracker().hasCooldown(stack.getItem()))return false;
        if(PlayerState.data(p).getString("starterOrder").isEmpty())return Adventures.tell(p,"Choose an order with /vale join first.");
        boolean spawned=false;int amount=kind==0?3:1;
        for(int i=0;i<amount;i++){FieldProjectile bolt=new FieldProjectile(p.world,p,kind);bolt.shoot(p,p.rotationPitch,p.rotationYaw+(kind==0?(i-1)*8:0),0,1.5F,.5F);spawned=p.world.spawnEntity(bolt)||spawned;}
        if(!spawned)return false;
        p.getCooldownTracker().setCooldown(stack.getItem(),kind==2?40:12);
        p.world.playSound(null,p.posX,p.posY,p.posZ,SoundEvents.ENTITY_SNOWBALL_THROW,SoundCategory.PLAYERS,.5F,1);
        if(!p.capabilities.isCreativeMode)stack.shrink(1);Adventures.gesture(p);return true;
    }
    public static final class Meal extends ItemFood {
        public final int kind;
        Meal(int kind){super(HUNGER[kind],SATURATION[kind],false);this.kind=kind;setMaxStackSize(16);setCreativeTab(CreativeTabs.FOOD);}
        public boolean bowl(){return kind==1||kind==2||kind==4||kind==10;}
        @Override @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
        public void addInformation(ItemStack stack,World world,java.util.List<String> lines,net.minecraft.client.util.ITooltipFlag flag){
            lines.add("Restores "+HUNGER[kind]+" hunger points.");
            String[] bonus={"","Heals one heart; clears weakness.","Restores 12 resolve.","Speed I for 6s.","Resistance I for 6s.","Clears slowness and mining fatigue.","","","Heals half a heart.","Night vision for 15s.","Strength I for 6s.",""};
            if(!bonus[kind].isEmpty()){lines.add(bonus[kind]);lines.add("Recovery bonuses share a 30s cooldown.");}
            if(bowl())lines.add("Returns your bowl.");else if(kind==5)lines.add("Drink; returns your glass bottle.");
        }
        @Override public EnumAction getItemUseAction(ItemStack stack){return kind==5?EnumAction.DRINK:EnumAction.EAT;}
        @Override protected void onFoodEaten(ItemStack stack,World w,EntityPlayer p){
            super.onFoodEaten(stack,w,p);if(w.isRemote)return;
            long now=Adventures.now(p);net.minecraft.nbt.NBTTagCompound s=PlayerState.data(p);
            if(now<s.getLong("mealReady"))return;
            boolean bonus=true;
            switch(kind){
                case 1:p.heal(2);p.removePotionEffect(MobEffects.WEAKNESS);break;
                case 2:bonus=p instanceof EntityPlayerMP&&village.vale.bridge.MoveCost.restore((EntityPlayerMP)p,12)>0;break;
                case 3:p.addPotionEffect(new PotionEffect(MobEffects.SPEED,120,0));break;
                case 4:p.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE,120,0));break;
                case 5:p.removePotionEffect(MobEffects.SLOWNESS);p.removePotionEffect(MobEffects.MINING_FATIGUE);break;
                case 8:p.heal(1);break;
                case 9:p.addPotionEffect(new PotionEffect(MobEffects.NIGHT_VISION,300,0));break;
                case 10:p.addPotionEffect(new PotionEffect(MobEffects.STRENGTH,120,0));break;
                default:bonus=false;
            }
            if(bonus){s.setLong("mealReady",now+600);p.sendStatusMessage(new TextComponentString("Meal bonus active. Recovery meals share a 30s bonus cooldown."),true);}
        }
        @Override public ItemStack onItemUseFinish(ItemStack stack,World w,EntityLivingBase eater){
            ItemStack result=super.onItemUseFinish(stack,w,eater);
            if((!bowl()&&kind!=5)||!(eater instanceof EntityPlayer)||((EntityPlayer)eater).capabilities.isCreativeMode)return result;
            ItemStack bowl=new ItemStack(kind==5?Items.GLASS_BOTTLE:Items.BOWL);
            if(result.isEmpty())return bowl;
            if(!w.isRemote&&!((EntityPlayer)eater).inventory.addItemStackToInventory(bowl))((EntityPlayer)eater).dropItem(bowl,false);
            return result;
        }
    }
}
