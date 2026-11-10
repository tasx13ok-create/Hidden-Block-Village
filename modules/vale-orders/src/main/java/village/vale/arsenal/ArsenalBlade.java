package village.vale.arsenal;

import com.google.common.collect.Multimap;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.ActionResult;
import net.minecraft.item.EnumAction;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
import net.minecraftforge.common.util.EnumHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import village.vale.Gear;
import village.vale.Order;

public final class ArsenalBlade extends ItemSword implements CraftedArsenalWeapon {
    public final ArsenalSpec spec;
    ArsenalBlade(ArsenalSpec spec) {
        super(EnumHelper.addToolMaterial("VALE_SPECIALIST_"+spec.name(),2,640,5,spec.damage-3,10));
        this.spec=spec;setNoRepair();setCreativeTab(CreativeTabs.COMBAT);setMaxStackSize(1);
        addPropertyOverride(new net.minecraft.util.ResourceLocation("valeorders","charged"),(stack,world,user)->
            user!=null&&user.isHandActive()&&user.getActiveItemStack()==stack
                &&user.getItemInUseMaxCount()>=Math.max(6,spec.charge)?1f:0f);
    }
    @Override public Order order() { return spec.order; }
    @Override public Multimap<String,AttributeModifier> getAttributeModifiers(EntityEquipmentSlot slot,ItemStack stack) {
        Multimap<String,AttributeModifier> map=super.getAttributeModifiers(slot,stack);
        if(slot==EntityEquipmentSlot.MAINHAND) {
            map.removeAll(SharedMonsterAttributes.ATTACK_SPEED.getName());
            map.put(SharedMonsterAttributes.ATTACK_SPEED.getName(),new AttributeModifier(ATTACK_SPEED_MODIFIER,"Specialist tempo",spec.speed-4,0));
            if(spec.role==ArsenalSpec.Role.RESTORE) {
                map.removeAll(SharedMonsterAttributes.ATTACK_DAMAGE.getName());
                map.put(SharedMonsterAttributes.ATTACK_DAMAGE.getName(),new AttributeModifier(ATTACK_DAMAGE_MODIFIER,"Tempered edge",
                        ArsenalMath.restoredDamage(stack.getItemDamage(),stack.getMaxDamage(),spec.damage),0));
            }
        }
        return map;
    }
    @Override public ActionResult<ItemStack> onItemRightClick(World world,EntityPlayer player,EnumHand hand) {
        ItemStack stack=player.getHeldItem(hand);
        if(hand!=EnumHand.MAIN_HAND)return new ActionResult<>(EnumActionResult.PASS,stack);
        ItemStack off=player.getHeldItemOffhand();
        if(!player.isSneaking()&&!off.isEmpty()&&off.getItem().isShield(off,player))return new ActionResult<>(EnumActionResult.PASS,stack);
        if(spec.role==ArsenalSpec.Role.TALONS) {
            boolean accepted=world.isRemote||player instanceof EntityPlayerMP&&ClawCombat.use((EntityPlayerMP)player,stack);
            return new ActionResult<>(accepted?EnumActionResult.SUCCESS:EnumActionResult.FAIL,stack);
        }
        if(!world.isRemote&&player instanceof EntityPlayerMP) {
            EntityPlayerMP owner=(EntityPlayerMP)player;
            if(spec.role==ArsenalSpec.Role.RETURN&&ArsenalLogic.recall(owner,stack))return new ActionResult<>(EnumActionResult.SUCCESS,stack);
            if(!ArsenalLogic.ready(owner,stack,spec,true))return new ActionResult<>(EnumActionResult.FAIL,stack);
            if(spec.role==ArsenalSpec.Role.MARK&&owner.isSneaking()) {
                ArsenalLogic.travel(owner,stack,spec);
                return new ActionResult<>(EnumActionResult.SUCCESS,stack);
            }
        }
        player.setActiveHand(hand);
        return new ActionResult<>(EnumActionResult.SUCCESS,stack);
    }
    @Override public int getMaxItemUseDuration(ItemStack stack) { return 72000; }
    @Override public EnumAction getItemUseAction(ItemStack stack) { return spec.role==ArsenalSpec.Role.TALONS?EnumAction.NONE:spec.shield()?EnumAction.BLOCK:EnumAction.BOW; }
    @Override public boolean isShield(ItemStack stack,@Nullable EntityLivingBase entity) { return spec.shield(); }
    @Override public void onPlayerStoppedUsing(ItemStack stack,World world,EntityLivingBase user,int remaining) {
        if(spec.role!=ArsenalSpec.Role.TALONS&&!world.isRemote&&user instanceof EntityPlayerMP)ArsenalLogic.release((EntityPlayerMP)user,stack,spec,getMaxItemUseDuration(stack)-remaining);
    }
    @Override public void onUsingTick(ItemStack stack,EntityLivingBase user,int remaining) {
        if(!user.world.isRemote&&user instanceof EntityPlayerMP) {
            int ticks=getMaxItemUseDuration(stack)-remaining;
            if(ticks>0&&ticks<=40&&ticks%8==0&&ArsenalLogic.ready((EntityPlayerMP)user,stack,spec,false))
                ArsenalLogic.chargeCue((EntityPlayerMP)user,spec,ticks);
        }
    }
    @Override public boolean onLeftClickEntity(ItemStack stack,EntityPlayer player,Entity target) {
        if(!player.world.isRemote&&player instanceof EntityPlayerMP) {
            EntityPlayerMP owner=(EntityPlayerMP)player;
            if(spec.role==ArsenalSpec.Role.RETURN&&ArsenalLogic.inFlight(owner,stack))return true;
            if(spec.role==ArsenalSpec.Role.TALONS&&ClawCombat.attack(owner,stack,target))return true;
            if(target instanceof EntityLivingBase)ArsenalLogic.beginSwing(owner,stack,(EntityLivingBase)target);
        }
        return false;
    }
    @Override public boolean hitEntity(ItemStack stack,EntityLivingBase target,EntityLivingBase attacker) {
        if(!attacker.world.isRemote&&attacker instanceof EntityPlayerMP)ArsenalLogic.meleeHit((EntityPlayerMP)attacker,stack,target);
        return super.hitEntity(stack,target,attacker);
    }
    @Override public boolean canDisableShield(ItemStack stack,ItemStack shield,EntityLivingBase target,EntityLivingBase attacker) {
        return spec.role==ArsenalSpec.Role.BREAK;
    }
    @SideOnly(Side.CLIENT) @Override public void addInformation(ItemStack stack,@Nullable World world,List<String> lines,ITooltipFlag flag) {
        lines.add(spec.order.title+" specialist");
        if(spec.role==ArsenalSpec.Role.TALONS) {
            lines.add("Use or middle-click: lock a target within 5 blocks.");
            lines.add("Use or attack again within 5s: lunge after a 1s dodge window.");
            lines.add("Rear lock blinds for 5s; rear strikes deal +10% damage.");
            lines.add("Every 5 landed hits adds a 17s bleed stack (maximum 3).");
            lines.add("Sneak + use cancels. Switching weapons ends pending effects.");
        } else lines.add("Hold use, then release: "+ArsenalLogic.description(spec.role));
        lines.add(spec.cost+" resolve / "+(spec.cooldown/20f)+"s recovery");
        lines.add("Powers require issued matching armor and this issued weapon.");
        if(spec.role==ArsenalSpec.Role.MARK)lines.add("Sneak + use: travel to your recent waymark.");
        if(spec.role==ArsenalSpec.Role.RETURN)lines.add("Use again to recall. One physical weapon; no duplicate drops.");
        if(spec.role==ArsenalSpec.Role.EXTEND||spec.role==ArsenalSpec.Role.SONIC)lines.add("Sneak while releasing for the alternate technique.");
    }
}

