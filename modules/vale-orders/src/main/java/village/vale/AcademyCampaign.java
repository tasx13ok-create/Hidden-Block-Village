package village.vale;

import java.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.util.text.*;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.world.WorldServer;

/** Playable introductory course. No public command can advance an objective. */
public final class AcademyCampaign {
    private static final Map<UUID,Session> SESSIONS=new LinkedHashMap<>();
    private static final int[] XP={25,25,50,50,50,100}, MERIT={0,0,1,1,1,2};
    public static final String ACTOR_OWNER="valeAcademyOwner", ACTOR_KIND="valeAcademyKind";
    private static final class Session {
        final UUID guide; final int stage; final long created; final String token=UUID.randomUUID().toString(),role; final Set<String> actions=new HashSet<>();
        Session(Entity actor,String role,int stage){guide=actor.getUniqueID();this.role=role;this.stage=stage;created=actor.world.getTotalWorldTime();}
    }
    private AcademyCampaign() {}
    public static NBTTagCompound state(EntityPlayerMP p){
        NBTTagCompound root=PlayerState.data(p);if(!root.hasKey("Academy",10))root.setTag("Academy",AcademyProgress.fresh());return root.getCompoundTag("Academy");
    }
    public static AcademyCourse course(EntityPlayerMP p){return AcademyCourse.get(p.getServerWorld());}
    public static boolean eligible(EntityPlayerMP p){return !p.world.isRemote&&p.dimension==0&&p.isEntityAlive()&&!p.isSpectator()&&!p.capabilities.isCreativeMode&&AcademyProgress.readable(state(p))&&course(p).active;}
    public static boolean basicsComplete(EntityPlayerMP p){return AcademyProgress.readable(state(p))&&state(p).getInteger("stage")==6;}
    public static void tell(EntityPlayerMP p,String text){p.sendMessage(new TextComponentString(text));}
    private static void button(EntityPlayerMP p,String label,String command){TextComponentString text=new TextComponentString("["+label+"]");text.getStyle().setColor(TextFormatting.AQUA).setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,command));p.sendMessage(text);}
    public static void show(EntityPlayerMP p){
        AcademyCourse c=course(p);if(!c.active){tell(p,"An operator has not placed an academy course in this world.");return;}
        NBTTagCompound s=state(p);if(!AcademyProgress.readable(s)){tell(p,"Academy progress uses an unsupported schema; it has been preserved.");return;}
        int stage=s.getInteger("stage");tell(p,"Academy | "+AcademyProgress.TITLES[stage]+" | completed "+stage+"/6 basics");
        if(stage==0)tell(p,"Meet the Dispatch Keeper at "+c.at(-24,0)+" and right-click.");
        if(stage==1)tell(p,"Meet Aki at "+c.at(-16,0)+". Choose a foundation nature; existing ward choices are preserved.");
        if(stage==2)tell(p,"Walk blue markers in order: "+c.at(-20,12)+", "+c.at(-12,24)+", "+c.at(0,24)+". Progress "+s.getInteger("objective")+"/3, then return to Aki.");
        if(stage==3)tell(p,"At the sandstone ring, evade a marked strike; equip your issued set and sneak-use its saber to guard; learn Reed Escape, practice twice, then activate it before a strike. Defense flags "+s.getInteger("objective")+"/7. Return to Aki when all three pass.");
        if(stage==4)tell(p,"Equip your issued set and hold the technique codex. Choose your first nature technique in /vale arts. Demonstrate it three times at your construct; Earth demonstrates accepted cover placement. Return to Aki. "+s.getInteger("objective")+"/3.");
        if(stage==5)tell(p,"Speak to Jun at "+c.at(16,0)+" for a one-time ingredient kit. Craft bread + cooked beef + carrot into travel rations at the kitchen, then give Jun one. Farm ingredients if your kit was lost.");
        if(stage==6){tell(p,"Your introductory academy course is complete. Speak to Ren at "+c.at(24,32)+" for the dispatch/ambush missions, or the Mission Officer at "+c.at(24,0)+" for contracts. The later graduation story is separate content.");button(p,"Field missions","/vale missions");}
    }
    public static void open(EntityPlayerMP p,Entity actor){
        AcademyCourse c=course(p);String role=c.role(actor);if(role==null||actor.world!=p.world||p.getDistanceSq(actor)>36)return;
        if(!eligible(p)){tell(p,"Academy progress requires a living survival/adventure player in the placed course. Creative inspection cannot earn rewards.");return;}
        NBTTagCompound s=state(p);Session session=new Session(actor,role,s.getInteger("stage"));
        SESSIONS.remove(p.getUniqueID());while(SESSIONS.size()>=256)SESSIONS.remove(SESSIONS.keySet().iterator().next());SESSIONS.put(p.getUniqueID(),session);
        show(p);int stage=s.getInteger("stage");
        if(role.equals("arrival")&&stage==0)choice(p,session,"enroll","Receive my academy journal");
        if(role.equals("mentor")){
            if(stage==1)for(String nature:new String[]{"fire","water","earth","wind","lightning"})choice(p,session,nature,"Train "+nature);
            if(stage>=2){choice(p,session,"powertrial","Try Lightning Chakra Armor and its combat trial");choice(p,session,"powerclaim","Claim my completed power trial");}
            if(stage==2||stage==3||stage==4)choice(p,session,"claim","Review my completed exercise");
            if(stage==3||stage==4)choice(p,session,"practice","Prepare my personal training construct");
        }
        if(role.equals("supplies")&&stage==5){choice(p,session,"kit","Collect my ingredient kit");choice(p,session,"ration","Deliver a crafted travel ration");}
        if(role.equals("contracts")&&stage==6){choice(p,session,"contracts","Review field contracts");choice(p,session,"missionclaim","Claim my completed contract");}
        if(role.equals("courier")&&stage==6){
            choice(p,session,"dispatch","Accept / resume The Missing Dispatch");choice(p,session,"deliver","Deliver the dispatch");
            choice(p,session,"ambush","Accept / resume Roadside Ambush");choice(p,session,"missionclaim","Claim completed mission");
        }
    }
    private static void choice(EntityPlayerMP p,Session s,String action,String label){s.actions.add(action);button(p,label,"/valeacademy choose "+s.token+" "+action);}
    public static boolean choose(EntityPlayerMP p,String token,String action){
        Session session=SESSIONS.get(p.getUniqueID());long now=p.world.getTotalWorldTime();
        if(session==null||!session.token.equals(token)||!session.actions.contains(action)||!eligible(p)||state(p).getInteger("stage")!=session.stage||now<session.created||now-session.created>600)return false;
        Entity actor=p.getServerWorld().getEntityFromUuid(session.guide);AcademyCourse c=course(p);
        if(actor==null||!actor.isEntityAlive()||p.getDistanceSq(actor)>36||!session.role.equals(c.role(actor)))return false;
        SESSIONS.remove(p.getUniqueID());
        if(action.equals("enroll")){complete(p,0);}
        else if(Arrays.asList("fire","water","earth","wind","lightning").contains(action)){
            String[] natures={"fire","water","earth","wind","lightning"},orders={"ember","current","flint","gale","storm"};
            String order=orders[Arrays.asList(natures).indexOf(action)];String existing=PlayerState.data(p).getString("starterOrder");
            if(!existing.isEmpty())order=existing;
            Order chosen=null;for(Order candidate:Order.values())if(candidate.key.equals(order))chosen=candidate;
            if(chosen==null||!Starter.choose(p,chosen))return false;
            state(p).setString("nature",order);complete(p,1);tell(p,"Equip the issued armor and saber from your inventory. Your existing ward identity, if any, was preserved.");
        }else if(action.equals("powertrial")){return LightningTrial.begin(p);}
        else if(action.equals("powerclaim")){return Missions.claim(p);}
        else if(action.equals("practice")){prepareTarget(p);}
        else if(action.equals("claim")){
            if(!AcademyProgress.ready(state(p))){tell(p,"Complete the observed exercise first. Repeated clicks do not grant progress.");return false;}
            complete(p,session.stage);
        }else if(action.equals("kit")){kit(p);tell(p,"One bread, cooked beef and carrot are delivered or queued. Use the kitchen crafting table; the kit cannot be claimed twice.");}
        else if(action.equals("ration")){
            if(!AcademyProgress.ready(state(p)))return false;ItemStack ration=ItemStack.EMPTY;
            for(ItemStack stack:p.inventory.mainInventory)if(!stack.isEmpty()&&stack.getItem()==Gear.ITEMS.get("travel_ration")){ration=stack;break;}
            if(ration.isEmpty()){tell(p,"Bring one travel ration you crafted.");return false;}
            ration.shrink(1);p.inventory.markDirty();complete(p,5);
        }else if(action.equals("contracts")){Missions.show(p);return true;}
        else if(action.equals("missionclaim")){return Missions.claim(p);}
        else if(action.equals("dispatch")){
            if(!"academy_dispatch".equals(PlayerState.data(p).getString("mission"))&&!Missions.accept(p,"academy_dispatch"))return false;
            tell(p,"Collect the sealed dispatch at the Mission Officer, then walk it back to Ren. Your journal records pickup and delivery; no physical parcel can be duplicated.");
        }else if(action.equals("deliver")){
            if(!"academy_dispatch".equals(PlayerState.data(p).getString("mission"))||!state(p).getBoolean("dispatchCollected")){tell(p,"Collect this run's dispatch from the Mission Officer first.");return false;}
            if(state(p).getLong("dispatchRun")!=PlayerState.data(p).getLong("missionRun"))return false;
            Missions.advance(p,1);Adventures.save(p);tell(p,"Dispatch delivered. Claim this mission's reward from Ren or the Mission Officer.");
        }else if(action.equals("ambush")){
            if(!"academy_ambush".equals(PlayerState.data(p).getString("mission"))&&!Missions.accept(p,"academy_ambush"))return false;
            if(!prepareAmbush(p))return false;
        }
        settle(p);show(p);return true;
    }
    public static boolean complete(EntityPlayerMP p,int expected){
        NBTTagCompound s=state(p);if(!AcademyProgress.advance(s,expected))return false;
        PlayerState.addXp(p,XP[expected]);NBTTagCompound account=PlayerState.data(p);account.setInteger("merit",Math.min(1000000,account.getInteger("merit")+MERIT[expected]));
        // Stage, XP and merit live in the same player record and are committed once.
        Adventures.save(p);settle(p);Progression.unlock(p);tell(p,"Academy checkpoint complete: "+AcademyProgress.TITLES[expected]);return true;
    }
    public static void settle(EntityPlayerMP p){
        NBTTagCompound s=state(p);if(!AcademyProgress.readable(s)||s.getInteger("stage")==0)return;
        RewardLedger ledger=RewardLedger.get(p);String base="academy/"+p.getUniqueID()+"/";
        ledger.issue(p,"mission_journal",1,base+"mission_journal");ledger.issue(p,"travel_ration",2,base+"welcome_rations");
        if(s.getInteger("stage")>=3){
            NBTTagCompound learned=PlayerState.data(p).getCompoundTag("OriginalScrolls").getCompoundTag("reed_escape");
            if(!learned.getBoolean("learned"))ledger.issue(p,"scroll_reed_escape",1,base+"reed_escape");
        }
        if(s.getInteger("stage")>=6)ledger.issue(p,"travel_ration",2,base+"basics_rations");
    }
    private static void kit(EntityPlayerMP p){
        for(String item:new String[]{"bread","cooked_beef","carrot"})RewardLedger.get(p).issueAcademySupply(p,item,"academy/"+p.getUniqueID()+"/ingredient/"+item);
    }
    public static void crafted(EntityPlayerMP p,ItemStack stack){
        if(eligible(p)&&state(p).getInteger("stage")==5&&stack.getItem()==Gear.ITEMS.get("travel_ration")){
            state(p).setBoolean("craftedRation",true);Adventures.save(p);tell(p,"Ration crafted. Return to Jun with one ration.");
        }
    }
    public static EntityIronGolem target(EntityPlayerMP p){
        NBTTagCompound s=state(p);if(!s.hasUniqueId("target"))return null;Entity e=p.getServerWorld().getEntityFromUuid(s.getUniqueId("target"));return e instanceof EntityIronGolem&&e.isEntityAlive()?(EntityIronGolem)e:null;
    }
    public static boolean prepareTarget(EntityPlayerMP p){
        AcademyCourse c=course(p);if(!eligible(p)||(state(p).getInteger("stage")!=3&&state(p).getInteger("stage")!=4))return false;
        EntityIronGolem existing=target(p);if(existing!=null){tell(p,"Your construct is ready at "+c.at(0,8)+". The floor ring is the practice area.");return true;}
        if(!p.world.isAreaLoaded(c.at(0,8),8)){tell(p,"Load the practice ring before preparing a construct.");return false;}
        AcademyProgress.forgetActor(state(p),"target");
        if(!p.world.getEntitiesWithinAABB(EntityIronGolem.class,new AxisAlignedBB(c.at(0,8)).grow(8)).isEmpty()){tell(p,"The practice ring is occupied. Learners take turns; wait for its current owner to finish.");return false;}
        EntityIronGolem dummy=new EntityIronGolem(p.world);dummy.setPosition(c.at(0,8).getX()+.5,c.at(0,8).getY(),c.at(0,8).getZ()+.5);dummy.setNoAI(true);dummy.setPlayerCreated(true);dummy.enablePersistence();
        dummy.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(200);dummy.setHealth(200);dummy.setCustomNameTag(p.getName()+" - Academy Construct");dummy.setAlwaysRenderNameTag(true);
        dummy.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        dummy.getEntityData().setUniqueId(ACTOR_OWNER,p.getUniqueID());dummy.getEntityData().setString(ACTOR_KIND,"training");dummy.getEntityData().setInteger(Targets.KEY,1);
        if(!p.world.getCollisionBoxes(dummy,dummy.getEntityBoundingBox()).isEmpty()||!p.world.spawnEntity(dummy))return false;
        state(p).setUniqueId("target",dummy.getUniqueID());state(p).setLong("targetSeen",p.world.getTotalWorldTime());Adventures.save(p);return true;
    }
    public static void art(EntityPlayerMP p,String key,EntityLivingBase contact){
        if(!eligible(p)||!course(p).near(p,0,8,9)||target(p)!=contact||!key.equals(PlayerState.data(p).getString("starterOrder")+"_lance"))return;
        if(AcademyProgress.art(state(p))){Adventures.save(p);tell(p,"Academy art contact: "+state(p).getInteger("objective")+"/3.");}
    }
    /** Called only after an actual protected, nonempty Earth wall placement. */
    public static void defensiveArt(EntityPlayerMP p,String key,BlockPos center){
        if(!eligible(p)||state(p).getInteger("stage")!=4||!"flint_lance".equals(key)||!"flint".equals(PlayerState.data(p).getString("starterOrder"))||target(p)==null||!course(p).near(p,0,8,9)||center==null||center.distanceSq(course(p).at(0,8))>100)return;
        if(AcademyProgress.art(state(p))){Adventures.save(p);tell(p,"Academy Earth cover verified: "+state(p).getInteger("objective")+"/3.");}
    }
    public static void tick(EntityPlayerMP p){
        if(!eligible(p))return;AcademyCourse c=course(p);NBTTagCompound s=state(p);int stage=s.getInteger("stage");long now=p.world.getTotalWorldTime();
        if(stage==2){
            boolean recent=s.getInteger("sampleDim")==p.dimension&&now>s.getLong("sampleTick")&&now-s.getLong("sampleTick")<=4;
            double dx=p.posX-s.getDouble("sampleX"),dy=p.posY-s.getDouble("sampleY"),dz=p.posZ-s.getDouble("sampleZ");
            double moved=dx*dx+dy*dy+dz*dz;
            boolean walking=recent&&moved>.0001&&moved<=9&&!p.capabilities.isFlying&&!p.isRiding();
            int[][] points={{-20,12},{-12,24},{0,24}};int marker=s.getInteger("objective");
            if(marker<3&&c.near(p,points[marker][0],points[marker][1],2)&&AcademyProgress.marker(s,marker,walking)){Adventures.save(p);tell(p,"Route marker "+(marker+1)+"/3.");}
            s.setLong("sampleTick",now);s.setInteger("sampleDim",p.dimension);s.setDouble("sampleX",p.posX);s.setDouble("sampleY",p.posY);s.setDouble("sampleZ",p.posZ);
        }
        if(!"academy_ambush".equals(PlayerState.data(p).getString("mission")))removeAmbush(p);
        EntityIronGolem dummy=target(p);if(dummy!=null){
            village.vale.arsenal.ScrollEvents.beforeHealthReset(dummy);dummy.extinguish();dummy.setHealth(dummy.getMaxHealth());
            if(c.near(p,0,8,9))s.setLong("targetSeen",now);
            if(now-s.getLong("targetSeen")>600){removeTarget(p);tell(p,"Your unused practice construct was cleared. Ask Aki to prepare another.");return;}
            if(stage!=3&&stage!=4){removeTarget(p);}
            else if(stage==3&&c.near(p,0,8,9)){
                long strike=s.getLong("strikeAt");
                if(strike==0){s.setLong("strikeAt",now+40);s.setDouble("strikeX",p.posX);s.setDouble("strikeZ",p.posZ);tell(p,"Construct: marked strike in two seconds. Move clear, guard, or use an activated Reed Escape.");}
                else if(now>=strike){
                    s.removeTag("strikeAt");double x=p.posX-s.getDouble("strikeX"),z=p.posZ-s.getDouble("strikeZ");
                    if(x*x+z*z>=4){if(AcademyProgress.defense(s,1))tell(p,"Spacing verified.");}
                    else if(p.getHealth()>3){
                        NBTTagCompound scrolls=PlayerState.data(p).getCompoundTag("OriginalScrolls");boolean replacement=scrolls.getBoolean("replacement"),guard=PlayerState.data(p).getLong("guardUntil")>Adventures.now(p);
                        Vec3d before=p.getPositionVector();boolean hit=Combat.impact(p,DamageSource.causeMobDamage(dummy),Math.min(2,p.getHealth()-2));
                        if(guard&&hit&&PlayerState.data(p).getLong("guardUntil")==0&&AcademyProgress.defense(s,2))tell(p,"Guard verified.");
                        if(replacement&&!scrolls.getBoolean("replacement")&&p.getPositionVector().squareDistanceTo(before)>0&&AcademyProgress.defense(s,4))tell(p,"Substitution verified.");
                    }
                    Adventures.save(p);
                }
            }else s.removeTag("strikeAt");
        }
    }
    public static void removeTarget(EntityPlayerMP p){EntityIronGolem target=target(p);if(target!=null){target.setDead();p.world.removeEntity(target);}AcademyProgress.forgetActor(state(p),"target");state(p).removeTag("strikeAt");}
    public static void logout(EntityPlayerMP p){SESSIONS.remove(p.getUniqueID());removeTarget(p);removeAmbush(p);Adventures.save(p);}
    public static void clearSession(EntityPlayerMP p){
        SESSIONS.remove(p.getUniqueID());WorldServer world=p.getServer().getWorld(0);
        for(String key:new String[]{"target","ambush0","ambush1"})if(state(p).hasUniqueId(key)){
            Entity actor=world.getEntityFromUuid(state(p).getUniqueId(key));
            if(actor!=null&&actor.getEntityData().hasUniqueId(ACTOR_OWNER)&&actor.getEntityData().getUniqueId(ACTOR_OWNER).equals(p.getUniqueID())){actor.setDead();world.removeEntity(actor);}
            AcademyProgress.forgetActor(state(p),key);
        }
        state(p).removeTag("strikeAt");Adventures.save(p);
    }
    public static void pickupDispatch(EntityPlayerMP p,Entity guide){
        if(!eligible(p)||!basicsComplete(p)||!"contracts".equals(course(p).role(guide))||p.getDistanceSq(guide)>36||!"academy_dispatch".equals(PlayerState.data(p).getString("mission")))return;
        state(p).setBoolean("dispatchCollected",true);state(p).setLong("dispatchRun",PlayerState.data(p).getLong("missionRun"));Adventures.save(p);tell(p,"Sealed dispatch recorded. Walk it to Ren in the far courtyard.");
    }
    private static boolean prepareAmbush(EntityPlayerMP p){
        if(p.world.getDifficulty()==net.minecraft.world.EnumDifficulty.PEACEFUL){tell(p,"The ambush requires Easy, Normal or Hard difficulty.");return false;}
        AcademyCourse c=course(p);if(!c.near(p,24,32,8))return false;
        if(!p.world.isAreaLoaded(c.at(12,34),8)||!p.world.isAreaLoaded(c.at(17,34),8)){tell(p,"Wait for the ambush yard to load.");return false;}
        NBTTagCompound s=state(p);long run=PlayerState.data(p).getLong("missionRun");
        if(s.getLong("ambushRun")!=run){removeAmbush(p);s.setLong("ambushRun",run);}
        int remaining=2-PlayerState.data(p).getInteger("missionProgress"),live=0;
        for(int i=0;i<2;i++){String slot="ambush"+i;if(s.hasUniqueId(slot)){Entity existing=p.getServerWorld().getEntityFromUuid(s.getUniqueId(slot));if(existing!=null&&existing.isEntityAlive())live++;else AcademyProgress.forgetActor(s,slot);}}
        for(int i=0;i<2&&live<remaining;i++){
            String slot="ambush"+i;if(s.hasUniqueId(slot))continue;
            FieldRogue rogue=new FieldRogue(p.world);BlockPos at=c.at(12+i*5,34);rogue.setPosition(at.getX()+.5,at.getY(),at.getZ()+.5);rogue.configure(i);rogue.enablePersistence();
            rogue.setHomePosAndDistance(at,16);
            rogue.getEntityData().setUniqueId(ACTOR_OWNER,p.getUniqueID());rogue.getEntityData().setString(ACTOR_KIND,"ambush");rogue.getEntityData().setLong("academyRun",run);
            if(!p.world.isAreaLoaded(at,3)||!p.world.getCollisionBoxes(rogue,rogue.getEntityBoundingBox()).isEmpty()||!p.world.spawnEntity(rogue)){tell(p,"The ambush yard is occupied. Retry after clearing it.");Adventures.save(p);return false;}
            s.setUniqueId(slot,rogue.getUniqueID());live++;
        }
        Adventures.save(p);tell(p,"Roadside Ambush is active in the far yard. Defeat your two raiders, then claim at Ren. Leaving the server clears your actors and keeps earned mission progress.");return true;
    }
    private static void removeAmbush(EntityPlayerMP p){for(int i=0;i<2;i++){String key="ambush"+i;if(state(p).hasUniqueId(key)){Entity e=p.getServerWorld().getEntityFromUuid(state(p).getUniqueId(key));if(e!=null){e.setDead();p.world.removeEntity(e);}AcademyProgress.forgetActor(state(p),key);}}}
    public static boolean ambushMatches(EntityPlayerMP p,EntityLivingBase target){return target.getEntityData().hasUniqueId(ACTOR_OWNER)&&target.getEntityData().getUniqueId(ACTOR_OWNER).equals(p.getUniqueID())&&"ambush".equals(target.getEntityData().getString(ACTOR_KIND))&&target.getEntityData().getLong("academyRun")==PlayerState.data(p).getLong("missionRun");}
}
