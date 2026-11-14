package village.vale;

import java.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.*;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

/** Independent contract/journal system; no dependency on hosted NPC or quest files. */
public final class Missions {
    public static final class Contract {
        public final String key,title,type;public final int level,goal,xp,merit;
        Contract(String k,String t,String type,int l,int goal,int xp,int merit){key=k;title=t;this.type=type;level=l;this.goal=goal;this.xp=xp;this.merit=merit;}
    }
    public static final Map<String,Contract> ALL=new LinkedHashMap<>();
    static{
        add("field_patrol","Clear the Footpaths","kill",1,5,125,1);
        add("academy_dispatch","The Missing Dispatch: First Delivery","academyDelivery",2,1,125,1);
        add("academy_ambush","Roadside Ambush","academyCombat",2,2,175,2);
        add("lightning_trial","Lightning Armor: Trial of the Thunder Guard","lightningTrial",1,3,200,3);
        add("trail_scout","Survey the Old Road","travel",1,192,150,1);
        add("supply_run","Granary Relief","supplies",1,16,150,1);
        add("ration_cook","Camp Kitchen","cook",2,4,200,2);
        add("night_watch","Lantern Watch","night",3,8,250,2);
        add("ward_practice","Applied Fieldcraft","art",3,4,200,2);
        add("companion_bond","A Shared Watch","companion",3,90,250,2);
        add("rogue_hunt","Roadside Interdiction","rogue",4,3,300,2);
        add("basalt_trial","The Basalt Oath","boss0",5,1,400,3);
        add("reed_trial","The Reed Measure","boss1",7,1,500,3);
        add("lantern_trial","The Lantern Question","boss2",9,1,600,4);
        add("colossus_trial","Heart of the Mountain","boss3",12,1,900,6);
    }
    private static final class Death {final LivingDeathEvent event;int ticks;Death(LivingDeathEvent e){event=e;}}
    private static final List<Death> deaths=new ArrayList<>();
    private Missions(){}
    private static void add(String k,String t,String type,int l,int goal,int xp,int merit){ALL.put(k,new Contract(k,t,type,l,goal,xp,merit));}
    public static void queueDeath(LivingDeathEvent e){if(village.vale.expedition.GateRuntime.managedActor(e.getEntityLiving()))return;if(e.getSource().getTrueSource() instanceof EntityPlayerMP&&Targets.hostile(e.getEntityLiving()))deaths.add(new Death(e));}
    public static void flushDeaths(MinecraftServer server){
        Iterator<Death> it=deaths.iterator();while(it.hasNext()){
            Death d=it.next();EntityLivingBase target=d.event.getEntityLiving();
            if(d.event.isCanceled()||target.getHealth()>0){it.remove();continue;}
            if(!target.isDead&&target.deathTime==0&&++d.ticks<3)continue;
            it.remove();if(!target.isDead&&target.deathTime==0)continue;
            EntityPlayerMP p=server.getPlayerList().getPlayerByUUID(d.event.getSource().getTrueSource().getUniqueID());
            if(p!=null)credit(p,target);
        }
    }
    public static void credit(EntityPlayerMP p,EntityLivingBase target){
        if(village.vale.expedition.GateRuntime.managedActor(target))return;
        if(target.getEntityData().getBoolean("valeDeathCredited"))return;target.getEntityData().setBoolean("valeDeathCredited",true);
        int before=PlayerState.level(p);PlayerState.addXp(p,target instanceof TrialKeeper||EncounterRewards.boss(target)?150:25);
        if(PlayerState.level(p)>before){p.sendMessage(new TextComponentString("Vale level "+PlayerState.level(p)+" reached."));Progression.unlock(p);}
        NBTTagCompound state=PlayerState.data(p),dossier=state.getCompoundTag("dossier");ResourceLocation id=EntityList.getKey(target);String key=id==null?target.getClass().getSimpleName():id.toString();
        if(dossier.hasKey(key)||dossier.getSize()<128)dossier.setInteger(key,Math.min(1000000,dossier.getInteger(key)+1));state.setTag("dossier",dossier);
        Contract c=ALL.get(state.getString("mission"));if(c==null)return;
        boolean matches=c.type.equals("kill")||c.type.equals("night")&&(p.world.getWorldTime()%24000>=13000&&p.world.getWorldTime()%24000<23000)||c.type.equals("rogue")&&(target instanceof FieldRogue||EncounterRewards.mob(target))||(target instanceof TrialKeeper||EncounterRewards.boss(target))&&c.type.equals("boss"+(target instanceof TrialKeeper?((TrialKeeper)target).kind():EncounterRewards.tier(target)));
        if(c.type.equals("academyCombat")&&AcademyCampaign.ambushMatches(p,target))matches=true;
        if(c.type.equals("lightningTrial")&&LightningTrial.matches(p,target))matches=true;
        if(matches)advance(p,1);
    }
    public static boolean accept(EntityPlayerMP p,String key){
        Contract c=ALL.get(key);if(c==null)return Adventures.tell(p,"Unknown contract. /vale missions lists contracts.");NBTTagCompound s=PlayerState.data(p);
        if(village.vale.expedition.GateRuntime.hasActive(p))return Adventures.tell(p,"Finish or abandon your gate expedition with its keeper before accepting a field contract.");
        if(c.type.startsWith("academy")&&!AcademyCampaign.basicsComplete(p))return Adventures.tell(p,"Finish the six academy basics before accepting this mission.");
        if(c.type.equals("academyCombat")&&!AcademyCampaign.state(p).getBoolean("dispatchCompleted"))return Adventures.tell(p,"Complete and claim your first dispatch delivery before the ambush mission.");
        if(c.type.equals("academyCombat")&&Adventures.now(p)<s.getLong("academyAmbushReady"))return Adventures.tell(p,"The ambush yard is preparing. Retry in a minute; abandoning cannot farm new raiders.");
        if(c.type.equals("lightningTrial")&&(!LightningTrial.canAccept(p)||Adventures.now(p)<s.getLong("lightningTrialReady")))return Adventures.tell(p,"Choose a nature at Aki and wait for the trial yard to be ready; abandoning cannot farm new challengers.");
        if(!s.getString("mission").isEmpty())return Adventures.tell(p,"Finish or abandon your current contract first.");
        if(s.getString("starterOrder").isEmpty()||PlayerState.level(p)<c.level)return Adventures.tell(p,"Choose an order and reach level "+c.level+".");
        long ready=s.getCompoundTag("missionCooldowns").getLong(key);if(Adventures.now(p)<ready)return Adventures.tell(p,"That contract is cooling down for "+Rules.cooldownSeconds(Adventures.now(p),ready)+"s.");
        s.setString("mission",key);s.setInteger("missionProgress",0);s.setDouble("missionFraction",0);s.setLong("missionRun",s.getLong("missionRun")+1);if(c.type.equals("academyCombat"))s.setLong("academyAmbushReady",Adventures.now(p)+1200);if(c.type.equals("lightningTrial"))s.setLong("lightningTrialReady",Adventures.now(p)+1200);location(p,s);Adventures.save(p);if(c.type.equals("lightningTrial"))LightningTrial.acquire(p);return Adventures.tell(p,"Accepted "+c.title+". Goal "+c.goal+"; /vale mission claim when ready.",true);
    }
    public static void abandon(EntityPlayerMP p){PlayerState.data(p).removeTag("mission");PlayerState.data(p).setInteger("missionProgress",0);Adventures.save(p);Adventures.tell(p,"Contract abandoned. No reward or merit awarded.",true);}
    static void advance(EntityPlayerMP p,int amount){NBTTagCompound s=PlayerState.data(p);Contract c=ALL.get(s.getString("mission"));if(c!=null&&amount>0){s.setInteger("missionProgress",Math.min(c.goal,s.getInteger("missionProgress")+amount));HudNetwork.dirty(p);}}
    public static void art(EntityPlayerMP p){Contract c=ALL.get(PlayerState.data(p).getString("mission"));if(c!=null&&c.type.equals("art"))advance(p,1);}
    public static void crafted(EntityPlayerMP p,ItemStack result){Contract c=ALL.get(PlayerState.data(p).getString("mission"));if(c!=null&&c.type.equals("cook")&&(result.getItem() instanceof FieldItems.Meal||result.getItem() instanceof village.vale.culinary.KitchenFood))advance(p,result.getCount());}
    private static void location(EntityPlayerMP p,NBTTagCompound s){s.setDouble("missionX",p.posX);s.setDouble("missionY",p.posY);s.setDouble("missionZ",p.posZ);s.setInteger("missionDim",p.dimension);}
    public static void tick(EntityPlayerMP p){
        NBTTagCompound s=PlayerState.data(p);Contract c=ALL.get(s.getString("mission"));if(c==null)return;
        if(c.type.equals("travel")){
            double dx=p.posX-s.getDouble("missionX"),dz=p.posZ-s.getDouble("missionZ");double moved=Math.sqrt(dx*dx+dz*dz);
            if(p.dimension==s.getInteger("missionDim")&&moved<=12&&!p.capabilities.isFlying&&!p.isRiding()){
                double progress=s.getDouble("missionFraction")+moved;int whole=(int)progress;advance(p,whole);s.setDouble("missionFraction",progress-whole);
            }location(p,s);
        }
        if(c.type.equals("companion")&&s.hasUniqueId("summonId")){
            Entity e=((net.minecraft.world.WorldServer)p.world).getEntityFromUuid(s.getUniqueId("summonId"));if(e instanceof EchoCompanion&&e.isEntityAlive()&&((EchoCompanion)e).kind()!=3)advance(p,1);
        }
    }
    private static int wheat(EntityPlayerMP p){int n=0;for(ItemStack stack:p.inventory.mainInventory)if(stack.getItem()==Items.WHEAT&&!stack.hasTagCompound())n+=stack.getCount();return n;}
    public static boolean claim(EntityPlayerMP p){
        NBTTagCompound s=PlayerState.data(p);Contract c=ALL.get(s.getString("mission"));if(c==null)return Adventures.tell(p,"No active contract.");
        if(!p.isEntityAlive()||p.isSpectator())return false;
        if(c.type.equals("supplies")?wheat(p)<c.goal:s.getInteger("missionProgress")<c.goal)return Adventures.tell(p,"Contract not complete. Supply contracts consume sixteen plain wheat at claim.");
        String receipt="mission/"+p.getUniqueID()+"/"+s.getLong("missionRun");
        if(c.type.equals("supplies")){int needed=c.goal;for(ItemStack stack:p.inventory.mainInventory)if(stack.getItem()==Items.WHEAT&&!stack.hasTagCompound()){int take=Math.min(needed,stack.getCount());stack.shrink(take);needed-=take;if(needed==0)break;}}
        s.removeTag("mission");s.setInteger("missionProgress",0);s.setDouble("missionFraction",0);
        s.setInteger("merit",Math.min(1000000,s.getInteger("merit")+c.merit));s.setInteger("missionsCompleted",Math.min(1000000,s.getInteger("missionsCompleted")+1));
        NBTTagCompound cooldowns=s.getCompoundTag("missionCooldowns");cooldowns.setLong(c.key,Adventures.now(p)+(c.type.startsWith("boss")?12000:6000));s.setTag("missionCooldowns",cooldowns);
        PlayerState.addXp(p,c.xp);if(c.type.equals("academyDelivery"))AcademyCampaign.state(p).setBoolean("dispatchCompleted",true);if(c.type.equals("lightningTrial"))LightningTrial.claimed(p);village.vale.expedition.GateRuntime.contractCompleted(p,c.key,receipt);Adventures.save(p);
        RewardLedger.get(p).issue(p,"travel_ration",2,receipt);Progression.unlock(p);return Adventures.tell(p,"Completed "+c.title+": "+c.xp+" XP, "+c.merit+" merit, two queued travel rations.",true);
    }
    public static void show(EntityPlayerMP p){
        if(p.dimension==0&&AcademyCampaign.course(p).active)AcademyCampaign.show(p);
        NBTTagCompound s=PlayerState.data(p);Contract c=ALL.get(s.getString("mission"));int completed=s.getInteger("missionsCompleted");
        p.sendMessage(new TextComponentString("Field journal | "+(completed>=12?"Keeper":completed>=6?"Warden":completed>=2?"Scout":"Initiate")+" | Merit "+s.getInteger("merit")+" | Contracts "+completed));
        village.vale.expedition.GateRuntime.show(p);
        TextComponentString provisions=new TextComponentString("[Quartermaster] Spend earned merit on equipment and training tools.");provisions.getStyle().setColor(TextFormatting.AQUA).setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/vale provisions"));p.sendMessage(provisions);
        if(c!=null)p.sendMessage(new TextComponentString(c.title+" | "+(c.type.equals("supplies")?Math.min(c.goal,wheat(p)):s.getInteger("missionProgress"))+"/"+c.goal+" | /vale mission claim"));
        for(Contract row:ALL.values()){
            TextComponentString line=new TextComponentString("[Accept] "+row.title+" | Level "+row.level+" | "+objective(row)+" | "+row.merit+" merit");
            line.getStyle().setColor(PlayerState.level(p)>=row.level?TextFormatting.GREEN:TextFormatting.GRAY).setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/vale mission accept "+row.key));p.sendMessage(line);
        }
    }
    public static void dossier(EntityPlayerMP p){NBTTagCompound d=PlayerState.data(p).getCompoundTag("dossier");p.sendMessage(new TextComponentString("Enemy dossier: "+d.getSize()+" observed enemy types. Trials change at 65% and 30% health; avoid locked ground marks."));for(String key:new TreeSet<>(d.getKeySet()))p.sendMessage(new TextComponentString(key+": "+d.getInteger(key)+" defeated"));}
    public static void bossContribution(EntityPlayerMP p,int kind){Contract c=ALL.get(PlayerState.data(p).getString("mission"));if(c!=null&&c.type.equals("boss"+kind))advance(p,1);}
    private static String objective(Contract c){
        if(c.type.equals("lightningTrial"))return "Use your Lightning Armor kit to defeat three owned challengers in the far courtyard; claim to learn Guillotine Drop";
        if(c.type.equals("academyDelivery"))return "Collect the dispatch from the Mission Officer; deliver to Ren";
        if(c.type.equals("academyCombat"))return "Defeat your two mission raiders; speak to Ren to resume";
        if(c.type.startsWith("boss"))return "Defeat "+Bosses.NAMES[Integer.parseInt(c.type.substring(4))];
        if(c.type.equals("travel"))return "Travel "+c.goal+" blocks on foot";
        if(c.type.equals("supplies"))return "Deliver sixteen plain wheat";
        if(c.type.equals("cook"))return "Craft "+c.goal+" field meals";
        if(c.type.equals("art"))return "Use "+c.goal+" techniques successfully";
        if(c.type.equals("companion"))return "Watch with a companion for "+c.goal+" seconds";
        return "Defeat "+c.goal+(c.type.equals("night")?" hostiles after dark":c.type.equals("rogue")?" road patrols":" hostiles");
    }

}
