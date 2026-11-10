package village.vale;

import java.util.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.*;
import net.minecraft.util.text.event.ClickEvent;

/** Mission-merit provisioning. Paid requests keep a retryable receipt before delivery. */
public final class Quartermaster {
    private Quartermaster(){}
    public static int price(EntityPlayerMP p,String key){
        int specialist=village.vale.arsenal.Arsenal.price(p,key);if(specialist!=-2)return specialist;
        int scroll=village.vale.arsenal.Scrolls.price(p,key);if(scroll!=-2)return scroll;
        int level=PlayerState.level(p);NBTTagCompound s=PlayerState.data(p);if(s.getString("starterOrder").isEmpty())return -1;
        if(key.startsWith("hvbijuu:")){
            String raw=key.substring(8);if(!OwnedArmor.rewardKey(raw)||!Item.REGISTRY.containsKey(new net.minecraft.util.ResourceLocation(key)))return -1;
            String[] beasts=OwnedArmor.beasts();for(int i=0;i<beasts.length;i++)if(raw.startsWith(beasts[i]+"_"))return level>=Math.min(20,5+i*2)?6+i:-1;return -1;
        }
        int fieldTool=OriginalFieldTools.price(key,level);if(fieldTool!=-2)return fieldTool;
        if(key.equals("focus_talisman"))return 2;
        Item item=Gear.ITEMS.get(key);if(item==null)return -1;
        if(item instanceof Gear.Ordered){Order order=((Gear.Ordered)item).order();if(!order.key.equals(s.getString("starterOrder"))&&!s.getBoolean("study_"+order.key))return -1;
            if(item instanceof OriginalFocusGear.Focus)return level>=5?8:-1;
            if(item instanceof OriginalOutfits.Garment)return 2;
            if(item instanceof OriginalArmorRegistry.Armor)return OriginalArmorRegistry.price(p,((OriginalArmorRegistry.Armor)item).profile);
            if(item instanceof Gear.WardArmor)return key.endsWith("_chestplate")?4:key.endsWith("_boots")?2:3;
            String type=key.substring(order.key.length()+1);int minimum=java.util.Arrays.asList("saber","dagger","spear","glaive","staff","axe").indexOf(type)+1;return level>=minimum?3:-1;
        }
        if(key.equals("technique_codex")||key.equals("mission_journal")||key.equals("field_manual"))return 1;
        if(key.equals("trial_writ"))return level>=5?1:-1;
        for(int i=0;i<4;i++)if(key.equals("form_"+Forms.KEYS[i]))return level>=Forms.LEVELS[i]?1:-1;
        for(int i=0;i<3;i++)if(key.equals("summon_"+Summons.KEYS[i]))return level>=Summons.LEVELS[i]?1:-1;
        return -1;
    }
    public static boolean buy(EntityPlayerMP p,String key){
        if(!p.isEntityAlive()||p.isSpectator())return false;
        if(key.startsWith(ValeOrders.ID+":"))key=key.substring(ValeOrders.ID.length()+1);
        if(!recover(p))return false;int price=price(p,key);NBTTagCompound s=PlayerState.data(p);
        if(price<0)return Adventures.tell(p,"Provision unavailable: check your level, school study and installed armor.");
        if(s.getInteger("merit")<price)return Adventures.tell(p,"This provision costs "+price+" mission merit.");
        long number=s.getLong("purchaseNumber")+1;s.setLong("purchaseNumber",number);s.setInteger("merit",s.getInteger("merit")-price);
        NBTTagCompound pending=new NBTTagCompound();pending.setString("key",key);pending.setString("receipt","quartermaster/"+p.getUniqueID()+"/"+number);s.setTag("purchasePending",pending);Adventures.save(p);
        return recover(p);
    }
    public static boolean recover(EntityPlayerMP p){
        NBTTagCompound s=PlayerState.data(p);if(!s.hasKey("purchasePending",10))return true;NBTTagCompound row=s.getCompoundTag("purchasePending");String key=row.getString("key");
        try{
            if(key.startsWith("hvbijuu:"))RewardLedger.get(p).issueOwned(p,key.substring(8),row.getString("receipt"));
            else RewardLedger.get(p).issue(p,key,1,row.getString("receipt"));
        }catch(IllegalArgumentException e){return Adventures.tell(p,"A paid provision remains queued for retry when its item is available. No further merit charged.");}
        s.removeTag("purchasePending");Adventures.save(p);return Adventures.tell(p,"Provision recorded. Inventory overflow waits in your reward queue.",true);
    }
    public static void show(EntityPlayerMP p,String page){
        p.sendMessage(new TextComponentString("Quartermaster | Merit "+PlayerState.data(p).getInteger("merit")+" | /vale provisions ward|spirit|tools|<studied order>"));
        String school=page.equals("ward")?PlayerState.data(p).getString("starterOrder"):page;
        if(page.equals("spirit")){
            for(String key:Gear.ITEMS.keySet())if(Gear.ITEMS.get(key) instanceof OriginalArmorRegistry.Armor)line(p,key,new ItemStack(Gear.ITEMS.get(key)).getDisplayName());
        }else for(String key:Gear.ITEMS.keySet()){
            
        Item item=Gear.ITEMS.get(key);
            if(page.equals("tools")?!(item instanceof Gear.Ordered)&&!(item instanceof FieldItems.Meal):item instanceof Gear.Ordered&&((Gear.Ordered)item).order().key.equals(school))line(p,key,new ItemStack(item).getDisplayName());
        }
    }
    private static void line(EntityPlayerMP p,String key,String title){int price=price(p,key);if(price<0)return;TextComponentString line=new TextComponentString("[Provision] "+title+" | "+price+" merit");line.getStyle().setColor(TextFormatting.AQUA).setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/vale provision "+key));p.sendMessage(line);}
}
