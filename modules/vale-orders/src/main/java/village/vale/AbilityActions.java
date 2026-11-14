package village.vale;

import java.util.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;

/** Build from authoritative live state, then rebuild before every action. */
public final class AbilityActions {
    private static final String[] PROJECTILES={"needle_fan","snap_disc","smoke_capsule"};
    private AbilityActions(){}
    static ItemStack carried(EntityPlayerMP p,String id){for(ItemStack stack:p.inventory.mainInventory)if(!stack.isEmpty()&&Gear.key(stack).equals(id))return stack;ItemStack off=p.getHeldItemOffhand();return !off.isEmpty()&&Gear.key(off).equals(id)?off:ItemStack.EMPTY;}
    private static int count(EntityPlayerMP p,String id){int result=0;for(ItemStack stack:p.inventory.mainInventory)if(Gear.key(stack).equals(id))result+=stack.getCount();if(Gear.key(p.getHeldItemOffhand()).equals(id))result+=p.getHeldItemOffhand().getCount();return result;}
    private static String base(EntityPlayerMP p,int level,int cost,boolean armor){
        if(!p.isEntityAlive()||p.isSpectator())return "Available while alive in play.";
        if(PlayerState.data(p).getString("starterOrder").isEmpty())return "Choose an order first.";
        if(PlayerState.level(p)<level)return "Requires level "+level+".";
        if(armor&&Combat.fullSet(p)==null)return "Equip four matching issued armor pieces.";
        if(PlayerState.resolve(p)<cost)return "Needs "+cost+" resolve.";return "";
    }
    private static int remaining(long now,long ready,int total){return (int)Math.min(total,Math.max(0,ready-now));}
    private static AbilitySnapshot.Entry entry(String id,String title,String kind,String school,String icon,int level,int cost,int count,String reason,int remaining,int total){return new AbilitySnapshot.Entry(id,title,kind,school,icon,0,level,cost,count,reason.isEmpty(),reason,remaining,total,total==0?0:remaining/(float)total);}
    public static AbilitySnapshot snapshot(EntityPlayerMP p){
        NBTTagCompound state=PlayerState.data(p);long now=Math.max(0,Adventures.now(p));List<AbilitySnapshot.Entry> list=new ArrayList<>();
        boolean codex=!carried(p,"valeorders:technique_codex").isEmpty();
        for(Arts.Art art:Arts.available(p)){
            int cost=LightningKit.index(art.key)>=0?art.cost:Forms.active(p)==2?Math.max(1,art.cost*3/4):art.cost;
            String reason=base(p,art.level,cost,true);if(reason.isEmpty()&&!codex)reason="Carry your technique codex.";
            int kit=LightningKit.index(art.key);if(reason.isEmpty()&&kit>=0)reason=LightningKit.gate(p,kit);
            int own=remaining(now,state.getCompoundTag("artCooldowns").getLong(art.key),art.cooldown*20),global=remaining(now,state.getLong("artGlobal"),20);
            if(kit==5){own=0;global=0;}
            list.add(entry("art/"+art.key,art.title,"art",art.order==null?"neutral":art.order.key,"valeorders:technique_codex",art.level,cost,0,reason,Math.max(own,global),own>=global?art.cooldown*20:20));
        }
        ItemStack held=p.getHeldItemMainhand();
        if(held.getItem() instanceof Gear.WardBlade){
            Gear.WardBlade blade=(Gear.WardBlade)held.getItem();Order order=blade.order();String reason=base(p,1,ValeOrders.skillCost,true);
            if(reason.isEmpty()&&(Combat.fullSet(p)!=order||!RewardLedger.get(p).valid(p,held)))reason="Use your issued matching weapon and armor.";
            list.add(entry("order/"+order.key,order.skill,"order",order.key,Gear.key(held),1,ValeOrders.skillCost,0,reason,remaining(now,state.getLong("nextSkill"),ValeOrders.skillCooldown*20),ValeOrders.skillCooldown*20));
            reason=base(p,1,20,true);if(reason.isEmpty()&&(Combat.fullSet(p)!=order||!RewardLedger.get(p).valid(p,held)))reason="Use your issued matching weapon and armor.";
            list.add(entry("technique/"+held.getItem().getRegistryName().getPath(),Techniques.name(blade.kind),"technique",order.key,Gear.key(held),1,20,0,reason,remaining(now,state.getLong("nextTechnique"),120),120));
        }
        for(int i=0;i<3;i++){
            String id="valeorders:"+PROJECTILES[i];ItemStack source=carried(p,id);if(source.isEmpty())continue;
            float fraction=p.getCooldownTracker().getCooldown(source.getItem(),0);String reason=base(p,1,0,false);
            list.add(new AbilitySnapshot.Entry("item/"+id,source.getDisplayName(),"projectile","neutral",id,0,1,0,count(p,id),reason.isEmpty(),reason,-1,-1,fraction));
        }
        for(int i=0;i<Forms.KEYS.length;i++){
            String id="valeorders:form_"+Forms.KEYS[i];ItemStack source=carried(p,id);if(source.isEmpty())continue;boolean ending=Forms.active(p)>=0;
            String reason=ending?base(p,1,0,false):base(p,Forms.LEVELS[i],60,true);
            list.add(entry("item/"+id,source.getDisplayName()+(ending?" (end active form)":""),"form","neutral",id,Forms.LEVELS[i],ending?0:60,count(p,id),reason,ending?0:remaining(now,state.getLong("formReady"),2400),ending?0:2400));
        }
        for(int i=0;i<3;i++){
            String id="valeorders:summon_"+Summons.KEYS[i];ItemStack source=carried(p,id);if(source.isEmpty())continue;boolean ending=state.getLong("summonEnd")>now;
            String reason=ending?base(p,1,0,false):base(p,Summons.LEVELS[i],35,true);
            list.add(entry("item/"+id,source.getDisplayName()+(ending?" (dismiss companion)":""),"summon","neutral",id,Summons.LEVELS[i],ending?0:35,count(p,id),reason,ending?0:remaining(now,state.getLong("summonReady"),900),ending?0:900));
        }
        String selected=state.getString("selectedAbility");boolean found=false;for(AbilitySnapshot.Entry e:list)if(e.id.equals(selected))found=true;
        if(!found){Arts.Art art=Arts.selected(p);selected=art==null?"":"art/"+art.key;found=false;for(AbilitySnapshot.Entry e:list)if(e.id.equals(selected))found=true;if(!found)selected=list.isEmpty()?"":list.get(0).id;}
        return new AbilitySnapshot(p.getUniqueID(),p.dimension,now,selected,list);
    }
    public static boolean select(EntityPlayerMP p,String id){
        if(!AbilitySnapshot.validId(id)||!p.isEntityAlive()||p.isSpectator()||(OriginalArmorLoadoutProvider.handles(id)?loadout(p).find(id):snapshot(p).find(id))==null)return false;
        PlayerState.data(p).setString("selectedAbility",id);if(id.startsWith("art/"))PlayerState.data(p).setString("selectedArt",id.substring(4));HudNetwork.dirty(p);return true;
    }
    public static AbilityLoadoutSnapshot loadout(EntityPlayerMP p){return loadout(p,snapshot(p));}
    static AbilityLoadoutSnapshot loadout(EntityPlayerMP p,AbilitySnapshot legacy){
        List<AbilitySnapshot.Entry> list=new ArrayList<>(legacy.entries);Set<String> serials=new HashSet<>();
        for(int slot=0;slot<37;slot++){
            ItemStack stack=slot<36?p.inventory.getStackInSlot(slot):p.getHeldItemOffhand();
            if(!weaponSource(p,stack))continue;String id="weapon/"+stack.getTagCompound().getString("valeSerial");if(!serials.add(id))continue;
            Gear.WardBlade blade=(Gear.WardBlade)stack.getItem();String reason=equipGate(p),title=stack.getDisplayName();if(title.length()>96)title=title.substring(0,96);
            list.add(entry(id,title,"weapon",blade.order().key,Gear.key(stack),1,0,1,reason,0,0));
        }
        OriginalArmorLoadoutProvider.append(p,list);
        String selected=PlayerState.data(p).getString("selectedAbility");boolean present=false;for(AbilitySnapshot.Entry entry:list)if(entry.id.equals(selected))present=true;
        if(!present)selected=legacy.selectedEntryId;
        return new AbilityLoadoutSnapshot(p.getUniqueID(),p.dimension,legacy.serverTick,selected,list);
    }
    private static boolean weaponSource(EntityPlayerMP p,ItemStack stack){
        if(stack.isEmpty()||stack.getCount()!=1||!(stack.getItem() instanceof Gear.WardBlade)||!stack.hasTagCompound()||!RewardLedger.get(p).valid(p,stack)||CanonicalCatalog.blocked(stack.getItem()))return false;
        return stack.getTagCompound().getString("valeSerial").matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }
    private static String equipGate(EntityPlayerMP p){
        if(!p.isEntityAlive()||p.isSpectator())return "Available while alive in play.";
        if(p.openContainer!=p.inventoryContainer||!p.inventory.getItemStack().isEmpty())return "Close the container and finish moving items first.";
        return "";
    }
    /** Explicit equip only: no attack, target selection, resource charge or inventory item creation. */
    public static boolean equip(EntityPlayerMP p,String id){
        if(id==null||!id.matches("weapon/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))return false;
        String gate=equipGate(p);if(!gate.isEmpty())return Adventures.tell(p,gate);
        String serial=id.substring(7);int source=-1;
        for(int slot=0;slot<37;slot++){ItemStack stack=slot<36?p.inventory.getStackInSlot(slot):p.getHeldItemOffhand();if(weaponSource(p,stack)&&serial.equals(stack.getTagCompound().getString("valeSerial"))){if(source>=0)return Adventures.tell(p,"That weapon has duplicate ownership data; ask an operator to inspect it.");source=slot;}}
        if(source<0)return Adventures.tell(p,"That issued weapon is no longer carried.");
        p.stopActiveHand();
        if(source<9)p.inventory.currentItem=source;
        else{
            int target=p.inventory.currentItem;if(target<0||target>8)return false;
            ItemStack previous=p.inventory.getStackInSlot(target),chosen=source<36?p.inventory.getStackInSlot(source):p.getHeldItemOffhand();
            p.inventory.setInventorySlotContents(target,chosen);
            if(source<36)p.inventory.setInventorySlotContents(source,previous);else p.setHeldItem(net.minecraft.util.EnumHand.OFF_HAND,previous);
        }
        PlayerState.data(p).setString("selectedAbility",id);p.inventory.markDirty();
        if(!(p instanceof net.minecraftforge.common.util.FakePlayer)&&p.connection!=null){p.connection.sendPacket(new net.minecraft.network.play.server.SPacketHeldItemChange(p.inventory.currentItem));p.sendContainerToPlayer(p.inventoryContainer);}
        HudNetwork.dirty(p);return true;
    }
    public static boolean activate(EntityPlayerMP p,String id){
        if(!AbilitySnapshot.validId(id))return false;
        if(OriginalArmorLoadoutProvider.handles(id)){boolean accepted=OriginalArmorLoadoutProvider.activate(p,id);if(accepted)PlayerState.data(p).setString("selectedAbility",id);HudNetwork.dirty(p);return accepted;}
        AbilitySnapshot.Entry action=snapshot(p).find(id);
        if(action==null)return Adventures.tell(p,"That action is no longer available.");
        if(!action.enabled)return Adventures.tell(p,action.reason);
        // Choose the exact validated ID. Never use whichever art was previously selected.
        PlayerState.data(p).setString("selectedAbility",id);boolean success=false;
        if(id.startsWith("art/"))success=Arts.castFromBar(p,id.substring(4));
        else if(id.startsWith("order/")){
            ItemStack held=p.getHeldItemMainhand();if(held.getItem() instanceof Gear.WardBlade)success=Combat.activate(p,held,((Gear.WardBlade)held.getItem()).order());
        }else if(id.startsWith("technique/")){
            ItemStack held=p.getHeldItemMainhand();if(held.getItem() instanceof Gear.WardBlade)success=Techniques.activate(p,(Gear.WardBlade)held.getItem());
        }else if(id.startsWith("item/")){
            String key=id.substring(5);ItemStack source=carried(p,key);if(source.isEmpty())return false;
            for(int i=0;i<3;i++)if(key.equals("valeorders:"+PROJECTILES[i]))success=FieldItems.throwItem(p,source,i);
            for(int i=0;i<4;i++)if(key.equals("valeorders:form_"+Forms.KEYS[i]))success=Forms.activate(p,i);
            for(int i=0;i<3;i++)if(key.equals("valeorders:summon_"+Summons.KEYS[i]))success=Summons.activate(p,i);
        }
        if(success){p.inventory.markDirty();p.inventoryContainer.detectAndSendChanges();}HudNetwork.dirty(p);return success;
    }
}
