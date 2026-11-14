package village.vale;

import java.util.*;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import village.vale.armor.*;
import village.vale.armor.api.*;

/** Versioned SERVER API for the original provider. No clientbound snapshot is an authority token. */
public final class OriginalArmorApi {
    public static final int SCHEMA_VERSION=1,MAX_ACTIONS=10,RECOMMENDED_LOADOUT_SLOTS=6;
    public static final String API_VERSION="1.0.0",PROVIDER_ID="original_armor",MOD_ID="valeorders",IMPLEMENTATION_VERSION="0.4.1-api1-candidate";
    public static final String FLIGHT_ID="order/original_armor_auric_skimmer_flight";
    public static final int FLIGHT_TOGGLE_COOLDOWN=4;
    private OriginalArmorApi(){}

    /** Provider discovery after item registration; a DTO/library JAR alone is not an implementation. */
    public static boolean isAvailable(){
        try{for(ArmorProfile p:ArmorProfile.values())for(ArmorMesh.Slot slot:ArmorMesh.Slot.values())if(!(Item.REGISTRY.getObject(new ResourceLocation(p.item(slot))) instanceof OriginalArmorRegistry.Armor))return false;return true;}
        catch(LinkageError unavailable){return false;}
    }

    /** Server thread only. Empty means no current matching full set owned by this player. */
    public static String getFullSet(EntityPlayerMP player){requireContext(player);ArmorProfile p=OriginalArmorRegistry.full(player);return p==null?"":p.key;}
    /** The caller supplies the stack, but its NBT is not evidence without the live owner/item ledger row. */
    public static boolean ownsPiece(EntityPlayerMP player,ItemStack stack){requireContext(player);return stack!=null&&!stack.isEmpty()&&stack.getItem() instanceof OriginalArmorRegistry.Armor&&stack.hasTagCompound()&&RewardLedger.get(player).valid(player,stack);}
    public static boolean isRewardItem(String itemId){return profileForItem(itemId)!=null;}
    /** Trusted server reward integration only. Never expose this method as a client action. */
    public static ArmorActionResult grantPiece(EntityPlayerMP owner,String itemId,String receipt){
        if(!context(owner))return result(ArmorActionResult.Code.WRONG_CONTEXT,"Run rewards on the server thread.");
        if(profileForItem(itemId)==null||receipt==null||receipt.trim().isEmpty()||receipt.length()>160)return result(ArmorActionResult.Code.INVALID_REWARD,"An original armor item and bounded receipt are required.");
        try{
            boolean fresh=RewardLedger.get(owner).issue(owner,itemId.substring(MOD_ID.length()+1),1,receipt);
            return result(fresh?ArmorActionResult.Code.ACCEPTED:ArmorActionResult.Code.ALREADY_RECORDED,fresh?"Reward recorded in the existing outbox.":"The identical reward receipt was already recorded.");
        }catch(IllegalArgumentException conflict){return result(ArmorActionResult.Code.RECEIPT_CONFLICT,"Reward receipt or item was rejected.");}
    }
    public static ArmorSnapshot getSnapshot(EntityPlayerMP player){
        requireContext(player);ArmorProfile full=OriginalArmorRegistry.full(player);long serverTick=now(player),armorTick=Math.max(0,player.world.getTotalWorldTime());
        OriginalArmorRuntime.State state=OriginalArmorRuntime.STATES.get(player.getUniqueID());NBTTagCompound saved=existing(player);
        int stamina=state==null?(saved.hasKey("stamina",3)?saved.getInteger("stamina"):2000):state.stamina;
        long rest=state==null?saved.getLong("restUntil"):state.restUntil;
        boolean same=state!=null&&state.profile==full&&full!=null;
        int passiveTotal=full==null?0:BalanceEnvelope.forRole(full.role).cooldownTicks;
        List<ArmorSnapshot.Piece> pieces=new ArrayList<>();
        for(EntityEquipmentSlot slot:OriginalArmorRegistry.SLOTS){
            ItemStack stack=player.getItemStackFromSlot(slot);if(!(stack.getItem() instanceof OriginalArmorRegistry.Armor))continue;
            String serial=stack.hasTagCompound()?stack.getTagCompound().getString("valeSerial"):"";if(!serial.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))serial="";
            boolean own=ownsPiece(player,stack)&&((OriginalArmorRegistry.Armor)stack.getItem()).armorType==slot;
            pieces.add(new ArmorSnapshot.Piece(OriginalArmorRegistry.convert(slot).name().toLowerCase(Locale.ROOT),Gear.key(stack),serial,own));
        }
        List<ArmorSnapshot.Action> actions=new ArrayList<>();NBTTagCompound core=PlayerState.data(player);
        int cost=Math.max(0,Math.min(100,ValeOrders.skillCost)),skillTotal=Math.max(0,Math.min(24000,ValeOrders.skillCooldown*20));
        int skillRemaining=ticks(serverTick,core.getLong("nextSkill"),skillTotal);
        for(ArmorProfile p:ArmorProfile.values()){
            String reason=wardReason(player,p,full,cost,skillRemaining);Order ward=OriginalArmorRegistry.order(p);
            actions.add(new ArmorSnapshot.Action(wardId(p),"order/"+p.ward,p.title+" / "+ward.skill,p.ward,p.key,p.item(ArmorMesh.Slot.CHEST),cost,reason.isEmpty(),reason,skillRemaining,skillTotal,true));
        }
        ArmorProfile wings=ArmorProfile.AURIC_SKIMMER;int flightRemaining=ticks(armorTick,saved.getLong("apiFlightReady"),FLIGHT_TOGGLE_COOLDOWN);
        String flightReason=flightReason(player,full,state,flightRemaining);boolean flying=same&&state.ownsFlight&&player.capabilities.isFlying;
        actions.add(new ArmorSnapshot.Action(FLIGHT_ID,"original_armor/flight_toggle",flying?"Auric Skimmer / End stamina flight":"Auric Skimmer / Begin stamina flight",wings.ward,wings.key,wings.item(ArmorMesh.Slot.CHEST),0,flightReason.isEmpty(),flightReason,flightRemaining,FLIGHT_TOGGLE_COOLDOWN,false));
        return new ArmorSnapshot(player.getUniqueID(),player.dimension,serverTick,armorTick,full==null?"":full.key,full==null?"":full.ward,full==null?"":full.role.name().toLowerCase(Locale.ROOT),
            stamina,ticks(armorTick,rest,40),same&&state.ownsFlight,flying,same?ticks(armorTick,state.nextAssist,passiveTotal):0,passiveTotal,same?state.momentum:0,same?state.reactiveRemaining:0,same?ticks(armorTick,state.reactiveReady,80):0,same&&state.target!=null&&armorTick<=state.targetUntil,pieces,actions);
    }
    public static List<ArmorSnapshot.Action> getActiveAbilities(EntityPlayerMP player){return getSnapshot(player).actions;}
    /** The core RPC must use its authenticated sender. This accepts an ID, never a target or an owner UUID. */
    public static ArmorActionResult activate(EntityPlayerMP player,String actionId){
        if(!context(player))return result(ArmorActionResult.Code.WRONG_CONTEXT,"Run actions on the authenticated server thread.");
        if(actionId==null||actionId.length()>96)return result(ArmorActionResult.Code.INVALID_ACTION,"Unknown original armor action.");
        ArmorSnapshot snapshot=getSnapshot(player);ArmorSnapshot.Action action=snapshot.find(actionId);
        if(action==null)return result(ArmorActionResult.Code.INVALID_ACTION,"Unknown original armor action.");
        if(!action.enabled)return result(ArmorActionResult.Code.BLOCKED,action.reason);
        if(FLIGHT_ID.equals(actionId))return toggleFlight(player);
        ArmorProfile profile=ArmorProfile.byKey(action.profileKey);Order order=OriginalArmorRegistry.order(profile);
        // Shared offensive skills retain the original armor's stronger pet/friendly policy as well.
        if(order==Order.EMBER||order==Order.RIME||order==Order.STORM){EntityLivingBase target=Combat.aimedTarget(player);if(target==null||!OriginalArmorRuntime.allowed(player,target))return result(ArmorActionResult.Code.BLOCKED,"Aim at a visible permitted opponent.");}
        boolean applied=Combat.activate(player,player.getHeldItemMainhand(),order);
        return result(applied?ArmorActionResult.Code.ACCEPTED:ArmorActionResult.Code.BLOCKED,applied?"Ward skill activated through shared combat.":"The current state or target rejected the ward skill.");
    }
    private static ArmorActionResult toggleFlight(EntityPlayerMP player){
        ArmorProfile full=OriginalArmorRegistry.full(player);OriginalArmorRuntime.State s=OriginalArmorRuntime.STATES.get(player.getUniqueID());
        long clock=Math.max(0,player.world.getTotalWorldTime());NBTTagCompound data=OriginalArmorRuntime.saved(player);
        String reason=flightReason(player,full,s,ticks(clock,data.getLong("apiFlightReady"),FLIGHT_TOGGLE_COOLDOWN));if(!reason.isEmpty())return result(ArmorActionResult.Code.BLOCKED,reason);
        boolean ending=s.ownsFlight&&player.capabilities.isFlying;
        if(!ending){
            if(!s.ownsFlight){s.ownsFlight=true;player.capabilities.allowFlying=true;}
            player.capabilities.isFlying=true;s.flying=true;
        }else{player.capabilities.isFlying=false;s.flying=false;}
        s.restUntil=Math.max(s.restUntil,clock+BalanceEnvelope.REST_TICKS);data.setLong("apiFlightReady",clock+FLIGHT_TOGGLE_COOLDOWN);
        OriginalArmorRuntime.save(player,s);player.sendPlayerAbilities();OriginalArmorRuntime.sync(player,s);
        return result(ArmorActionResult.Code.ACCEPTED,ending?"Stamina flight ended.":"Stamina flight started.");
    }
    private static String wardReason(EntityPlayerMP player,ArmorProfile expected,ArmorProfile actual,int cost,int remaining){
        if(!player.isEntityAlive()||player.isSpectator())return "Available while alive in play.";
        if(actual!=expected)return "Equip your four matching issued "+expected.title+" pieces.";
        ItemStack held=player.getHeldItemMainhand();
        if(!(held.getItem() instanceof Gear.WardBlade)||((Gear.WardBlade)held.getItem()).order()!=OriginalArmorRegistry.order(expected)||!RewardLedger.get(player).valid(player,held))return "Hold your issued matching ward weapon.";
        if(remaining>0)return "The shared ward skill is cooling down.";
        if(village.vale.bridge.MoveCost.availableUnits(player)<cost)return village.vale.bridge.ResourceText.requirement(player,cost);return "";
    }
    private static String flightReason(EntityPlayerMP player,ArmorProfile full,OriginalArmorRuntime.State state,int remaining){
        if(!player.isEntityAlive()||player.isSpectator()||player.capabilities.isCreativeMode)return "Stamina flight is available in survival play.";
        if(full!=ArmorProfile.AURIC_SKIMMER)return "Equip your four matching issued Auric Skimmer pieces.";
        if(state==null||state.profile!=full)return "Armor resonance is updating; retry next tick.";
        if(!state.ownsFlight&&player.capabilities.allowFlying)return "Another system owns the flight permission.";
        if(remaining>0)return "Flight control is cooling down.";
        if(!(state.ownsFlight&&player.capabilities.isFlying)&&!BalanceEnvelope.canStart(state.stamina))return "Flight needs at least 20 stamina points.";
        return "";
    }
    public static String wardId(ArmorProfile profile){return "order/original_armor_"+profile.key+"_ward";}
    private static ArmorProfile profileForItem(String id){if(id==null||id.length()>96)return null;for(ArmorProfile p:ArmorProfile.values())for(ArmorMesh.Slot slot:ArmorMesh.Slot.values())if(p.item(slot).equals(id))return p;return null;}
    private static int ticks(long now,long ready,int maximum){if(ready<=now)return 0;return (int)Math.min(Math.max(0,maximum),ready-now);}
    private static long now(EntityPlayerMP p){return Math.max(0,p.getServer().getWorld(0).getTotalWorldTime());}
    private static NBTTagCompound existing(EntityPlayerMP p){return p.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG).getCompoundTag("ValeOrders").getCompoundTag("originalArmor");}
    private static boolean context(EntityPlayerMP p){return p!=null&&p.world!=null&&!p.world.isRemote&&p.getServer()!=null&&p.getServer().isCallingFromMinecraftThread();}
    private static void requireContext(EntityPlayerMP p){if(!context(p))throw new IllegalArgumentException("Original armor API requires the owning server thread");}
    private static ArmorActionResult result(ArmorActionResult.Code code,String text){return ArmorActionResult.of(code,text);}
}
