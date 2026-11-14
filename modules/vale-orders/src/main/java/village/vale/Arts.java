package village.vale;

import java.util.*;
import village.vale.bridge.MoveCost;
import village.vale.nature.NatureMoves;
import net.minecraft.entity.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.util.text.*;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.EnderTeleportEvent;

/** Original, data-driven technique roster, separate from the nine weapon order skills. */
public final class Arts {
    public static final class Art {
        public final String key,title;public final Order order;public final int shape,level,cost,cooldown;
        Art(String key,String title,Order order,int shape,int level,int cost,int cooldown){this.key=key;this.title=title;this.order=order;this.shape=shape;this.level=level;this.cost=cost;this.cooldown=cooldown;}
    }
    public static final Map<String,Art> ALL=new LinkedHashMap<>();
    static{
        String[][] titles={{"Coal Lance","Furnace Fan","Cinder Seal"},{"River Needle","Undertow Sweep","Tidal Seal"},{"Airfoil","Crosswind","Gale Seal"},{"Gravel Dart","Faultline","Cairn Seal"},{"Briar Thorn","Petal Sweep","Verdant Seal"},{"Hail Shard","Winter Fan","Hoarfrost Seal"},{"Static Lance","Thunder Fan","Storm Seal"},{"Shade Needle","Gloam Sweep","Twilight Seal"},{"Sun Dart","Radiant Fan","Daybreak Seal"}};
        int n=0;for(Order o:Order.values()){for(int shape=0;shape<3;shape++){String key=o.key+new String[]{"_lance","_fan","_seal"}[shape];ALL.put(key,new Art(key,titles[n][shape],o,shape,new int[]{2,5,9}[shape],new int[]{18,24,30}[shape],new int[]{8,12,18}[shape]));}n++;}
        // Stable save IDs, distinct starter mechanics. Higher shapes retain their existing behavior.
        starter(Order.EMBER,"Fire Release: Flame Burst",18,7);
        starter(Order.CURRENT,"Water Release: Surging Wave",18,8);
        starter(Order.FLINT,"Earth Release: Earth Wall",24,12);
        starter(Order.GALE,"Wind Release: Gale Palm",16,7);
        starter(Order.STORM,"Lightning Release: Focused Strike",22,8);
        for(int i=0;i<LightningKit.KEYS.length;i++)ALL.put(LightningKit.KEYS[i],new Art(LightningKit.KEYS[i],LightningKit.TITLES[i],null,30+i,1,LightningKit.COST[i],LightningKit.COOLDOWN[i]));
        String[] keys={"binding_script","damping_script","ward_circle","phase_step","focus_sight","second_wind","gravitic_pulse","recall_mark","echo_double"};
        String[] names={"Binding Script","Damping Script","Ward Circle","Phase Step","Focus Sight","Second Wind","Gravitic Pulse","Recall Mark","Echo Double"};
        for(int i=0;i<keys.length;i++)ALL.put(keys[i],new Art(keys[i],names[i],null,10+i,new int[]{3,6,3,4,4,6,8,10,7}[i],new int[]{24,22,25,18,15,25,28,20,25}[i],new int[]{16,12,18,10,16,18,15,30,20}[i]));
    }
    private static void starter(Order o,String title,int cost,int cooldown){String key=o.key+"_lance";ALL.put(key,new Art(key,title,o,0,2,cost,cooldown));}
    private Arts(){}
    public static List<Art> available(EntityPlayerMP p){
        List<Art> list=new ArrayList<>();NBTTagCompound s=PlayerState.data(p);
        if(s.getString("starterOrder").isEmpty())return list;
        for(Art a:ALL.values()){int kit=LightningKit.index(a.key);if(kit>=0){if(LightningKit.available(p,kit))list.add(a);}else if(PlayerState.level(p)>=a.level&&(a.order==null||a.order.key.equals(s.getString("starterOrder"))||s.getBoolean("study_"+a.order.key)))list.add(a);}
        return list;
    }
    public static Art selected(EntityPlayerMP p){
        List<Art> available=available(p);String key=PlayerState.data(p).getString("selectedArt");
        for(Art art:available)if(art.key.equals(key))return art;return available.isEmpty()?null:available.get(0);
    }
    public static boolean select(EntityPlayerMP p,String key){
        for(Art a:available(p))if(a.key.equals(key)){PlayerState.data(p).setString("selectedArt",key);return Adventures.tell(p,"Selected "+a.title+". Use your codex to cast.",true);}
        return Adventures.tell(p,"That technique is locked. /vale arts lists your available study.");
    }
    public static void cycle(EntityPlayerMP p){List<Art> list=available(p);if(list.isEmpty()){Adventures.tell(p,"Techniques begin at level 2; complete missions or fight hostiles.");return;}Art current=selected(p);int next=(list.indexOf(current)+1)%list.size();select(p,list.get(next).key);}
    public static void show(EntityPlayerMP p){
        p.sendMessage(new TextComponentString("Technique academy | Level "+PlayerState.level(p)+" | Merit "+PlayerState.data(p).getInteger("merit")+" | Sneak + use codex to cycle."));
        for(Art a:available(p)){
            TextComponentString line=new TextComponentString("[Select] "+a.title+" | "+a.cost+" chakra units | "+a.cooldown+"s");
            line.getStyle().setColor(TextFormatting.AQUA).setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/vale art select "+a.key));p.sendMessage(line);
        }
        for(Order o:Order.values())if(!o.key.equals(PlayerState.data(p).getString("starterOrder"))&&!PlayerState.data(p).getBoolean("study_"+o.key)){
            TextComponentString line=new TextComponentString("[Study] "+o.title+" | Level 8 + four merit");line.getStyle().setColor(TextFormatting.GOLD).setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/vale study "+o.key));p.sendMessage(line);
        }
    }
    public static boolean study(EntityPlayerMP p,String key){
        Order found=null;for(Order o:Order.values())if(o.key.equals(key))found=o;
        if(found==null)return Adventures.tell(p,"Unknown study order.");NBTTagCompound s=PlayerState.data(p);
        if(key.equals(s.getString("starterOrder"))||s.getBoolean("study_"+key))return Adventures.tell(p,"You already know this school.");
        if(PlayerState.level(p)<8||s.getInteger("merit")<4)return Adventures.tell(p,"Additional schools require level 8 and four mission merit.");
        s.setInteger("merit",s.getInteger("merit")-4);s.setBoolean("study_"+key,true);Adventures.save(p);return Adventures.tell(p,"Study unlocked: "+found.title+".",true);
    }
    public static boolean cast(EntityPlayerMP p){
        return cast(p,selected(p),false);
    }
    public static boolean castFromBar(EntityPlayerMP p,String key){
        Art chosen=null;for(Art art:available(p))if(art.key.equals(key))chosen=art;
        if(chosen==null)return Adventures.tell(p,"That technique is locked.");
        PlayerState.data(p).setString("selectedArt",chosen.key);return cast(p,chosen,true);
    }
    private static boolean cast(EntityPlayerMP p,Art a,boolean carriedCodex){
        if(!p.isEntityAlive()||p.isSpectator())return false;
        if(carriedCodex){if(AbilityActions.carried(p,"valeorders:technique_codex").isEmpty())return Adventures.tell(p,"Carry your technique codex.");}
        else if(!Gear.key(p.getHeldItemMainhand()).equals("valeorders:technique_codex"))return Adventures.tell(p,"Hold the technique codex in your main hand.");
        if(Combat.fullSet(p)==null)return Adventures.tell(p,"Equip four matching issued armor pieces.");
        if(a==null)return Adventures.tell(p,"No technique is unlocked yet. Reach level 2.");
        if(LightningKit.index(a.key)>=0)return LightningKit.cast(p,a);
        if(NatureMoves.handles(a))return NatureMoves.cast(p,a,carriedCodex);
        NBTTagCompound s=PlayerState.data(p);long now=Adventures.now(p);NBTTagCompound cds=s.getCompoundTag("artCooldowns");
        if(now<s.getLong("artGlobal")||now<cds.getLong(a.key))return Adventures.tell(p,"This technique is cooling down.");
        int cost=Forms.active(p)==2?Math.max(1,a.cost*3/4):a.cost;
        MoveCost.Ticket payment=MoveCost.reserve(p,cost);if(payment==null)return Adventures.tell(p,"Chakra unavailable or insufficient for "+a.title+".");
        net.minecraft.world.World world=p.world;int dimension=p.dimension;ItemStack held=p.getHeldItemMainhand();
        java.util.function.BooleanSupplier legal=()->p.isEntityAlive()&&!p.isSpectator()&&p.world==world&&p.dimension==dimension&&p.getServer()!=null&&p.getServer().getPlayerList().getPlayerByUUID(p.getUniqueID())==p&&PlayerState.data(p)==s&&Combat.fullSet(p)!=null&&(carriedCodex?!AbilityActions.carried(p,"valeorders:technique_codex").isEmpty():p.getHeldItemMainhand()==held&&Gear.key(held).equals("valeorders:technique_codex"));
        long oldGlobal=s.getLong("artGlobal"),oldCooldown=cds.getLong(a.key),oldCombat=s.getLong("lastCombat"),deadline=now+a.cooldown*20L;
        cds.setLong(a.key,deadline);s.setTag("artCooldowns",cds);s.setLong("artGlobal",now+20);s.setLong("lastCombat",now);
        try{
            if(!payment.valid()||!legal.getAsBoolean())return false;
            boolean success=a.order==null?utility(p,a,payment,legal):school(p,a,payment);
            if(!success)return Adventures.tell(p,"No valid effect: aim at a visible permitted enemy or check your space/health.");
            payment.commit(); // helpers settle earlier at irreversible native contacts
            Missions.art(p);
            try{Adventures.gesture(p);HudNetwork.cast(p,a.key);p.sendStatusMessage(new TextComponentString(a.title+" | Chakra units "+MoveCost.availableUnits(p)),true);}catch(RuntimeException|LinkageError cosmeticFailure){}
            return true;
        }finally{
            if(payment.pending()&&PlayerState.data(p)==s){if(cds.getLong(a.key)==deadline)cds.setLong(a.key,oldCooldown);if(s.getLong("artGlobal")==now+20)s.setLong("artGlobal",oldGlobal);if(s.getLong("lastCombat")==now)s.setLong("lastCombat",oldCombat);}
            payment.close();
        }
    }
    public static List<EntityLivingBase> near(EntityPlayerMP p,double radius,boolean frontal,int limit){
        List<EntityLivingBase> all=p.world.getEntitiesWithinAABB(EntityLivingBase.class,p.getEntityBoundingBox().grow(radius));
        all.sort(Comparator.comparingDouble(p::getDistanceSq));List<EntityLivingBase> result=new ArrayList<>();
        for(EntityLivingBase e:all){if(e==p||!e.isEntityAlive()||p.getDistanceSq(e)>radius*radius||!OwnedSkills.allowed(p,e)||!p.canEntityBeSeen(e))continue;
            Vec3d delta=e.getPositionVector().subtract(p.getPositionVector());if(frontal&&delta.lengthSquared()>0&&p.getLookVec().dotProduct(delta.normalize())<.25)continue;
            result.add(e);if(result.size()>=limit)break;
        }return result;
    }
    private static boolean school(EntityPlayerMP p,Art a,MoveCost.Ticket payment){
        if(a.shape==2){
            if(a.order==Order.BLOOM){if(p.getHealth()>=p.getMaxHealth())return false;return mend(p,4,MobEffects.POISON,payment);}
            if(a.order==Order.DAWN)return buff(p,MobEffects.ABSORPTION,120,0,payment);
            RuneField field=new RuneField(p.world);field.configure(p,a.order.ordinal(),p.getPositionVector());boolean spawned=p.world.spawnEntity(field);if(spawned&&!field.isDead)payment.commit();return spawned&&!field.isDead;
        }
        List<EntityLivingBase> targets;
        if(a.shape==0){EntityLivingBase e=Combat.aimedTarget(p,12);targets=e==null?Collections.emptyList():Collections.singletonList(e);}
        else targets=near(p,6,true,3);
        boolean ok=false;for(EntityLivingBase e:targets){boolean contact=hit(p,e,a.order,a.shape==0?6:4,DamageSource.causePlayerDamage(p),payment);if(contact)AcademyCampaign.art(p,a.key,e);ok=contact||ok;}
        return ok;
    }
    public static boolean hit(EntityPlayerMP p,EntityLivingBase e,Order order,float damage){
        return hit(p,e,order,damage,DamageSource.causePlayerDamage(p));
    }
    public static boolean hit(EntityPlayerMP p,EntityLivingBase e,Order order,float damage,DamageSource source){return hit(p,e,order,damage,source,null);}
    private static boolean hit(EntityPlayerMP p,EntityLivingBase e,Order order,float damage,DamageSource source,MoveCost.Ticket payment){
        if(!OwnedSkills.allowed(p,e)||!Combat.impact(e,source,order==Order.STORM?damage+1:damage))return false;
        if(payment!=null)payment.commit();
        switch(order){
            case EMBER:e.setFire(3);break;
            case CURRENT:e.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS,50,0));push(p,e,.6);break;
            case GALE:push(p,e,.9);break;
            case FLINT:e.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS,60,0));break;
            case BLOOM:p.heal(1);break;
            case RIME:e.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS,60,1));break;
            case STORM:e.addPotionEffect(new PotionEffect(MobEffects.GLOWING,60,0));break;
            case DUSK:e.addPotionEffect(new PotionEffect(MobEffects.BLINDNESS,40,0));break;
            case DAWN:p.addPotionEffect(new PotionEffect(MobEffects.REGENERATION,40,0));break;
        }return true;
    }
    static void push(EntityPlayerMP p,EntityLivingBase e,double force){double x=p.posX-e.posX,z=p.posZ-e.posZ;if(x*x+z*z<.0001){x=-p.getLookVec().x;z=-p.getLookVec().z;if(x*x+z*z<.0001)z=1;}e.knockBack(p,(float)force,x,z);e.velocityChanged=true;}
    private static boolean changed(EntityPlayerMP p,net.minecraft.potion.Potion potion,PotionEffect before){PotionEffect after=p.getActivePotionEffect(potion);return after!=null&&(before==null||after.getAmplifier()>before.getAmplifier()||after.getDuration()>before.getDuration());}
    private static boolean buff(EntityPlayerMP p,net.minecraft.potion.Potion potion,int ticks,int strength,MoveCost.Ticket payment){
        PotionEffect before=p.getActivePotionEffect(potion);before=before==null?null:new PotionEffect(before);
        try{p.addPotionEffect(new PotionEffect(potion,ticks,strength));return changed(p,potion,before);}
        finally{if(changed(p,potion,before))payment.commit();}
    }
    private static boolean mend(EntityPlayerMP p,float amount,net.minecraft.potion.Potion cure,MoveCost.Ticket payment){
        float before=p.getHealth();boolean afflicted=p.isPotionActive(cure);
        try{p.heal(amount);if(p.getHealth()>before)payment.commit();p.removePotionEffect(cure);return p.getHealth()>before||afflicted&&!p.isPotionActive(cure);}
        finally{if(p.getHealth()>before||afflicted&&!p.isPotionActive(cure))payment.commit();}
    }
    private static boolean utility(EntityPlayerMP p,Art a,MoveCost.Ticket payment,java.util.function.BooleanSupplier legal){
        switch(a.shape){
            case 10:{RuneField f=new RuneField(p.world);f.configure(p,Order.RIME.ordinal(),p.getPositionVector());boolean spawned=p.world.spawnEntity(f);if(spawned&&!f.isDead)payment.commit();return spawned&&!f.isDead;}
            case 11:{EntityLivingBase e=Combat.aimedTarget(p,12);if(e==null||!Combat.impact(e,DamageSource.causePlayerDamage(p),2))return false;payment.commit();e.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS,100,1));return true;}
            case 12:PlayerState.data(p).setLong("guardUntil",p.world.getTotalWorldTime()+50);payment.commit();buff(p,MobEffects.RESISTANCE,60,0,payment);return true;
            case 13:{Vec3d d=p.getLookVec();boolean moved=teleport(p,p.posX+d.x*4,p.posY,p.posZ+d.z*4,legal,payment);if(moved)payment.commit();return moved;}
            case 14:{boolean ok=buff(p,MobEffects.NIGHT_VISION,200,0,payment);for(EntityLivingBase e:near(p,12,false,6))if(Combat.impact(e,DamageSource.causePlayerDamage(p),1)){payment.commit();e.addPotionEffect(new PotionEffect(MobEffects.GLOWING,100,0));ok=true;}return ok;}
            case 15:if(p.getHealth()>=p.getMaxHealth())return false;return mend(p,4,MobEffects.WEAKNESS,payment);
            case 16:{boolean ok=false;for(EntityLivingBase e:near(p,5,false,4))if(Combat.impact(e,DamageSource.causePlayerDamage(p),4)){payment.commit();push(p,e,1.1);ok=true;}return ok;}
            case 17:{NBTTagCompound s=PlayerState.data(p);if(!s.hasKey("recall",10)){NBTTagCompound mark=new NBTTagCompound();mark.setInteger("dim",p.dimension);mark.setDouble("x",p.posX);mark.setDouble("y",p.posY);mark.setDouble("z",p.posZ);s.setTag("recall",mark);payment.commit();return true;}NBTTagCompound m=s.getCompoundTag("recall");if(m.getInteger("dim")!=p.dimension||p.getDistanceSq(m.getDouble("x"),m.getDouble("y"),m.getDouble("z"))>32*32)return false;boolean ok=teleport(p,m.getDouble("x"),m.getDouble("y"),m.getDouble("z"),legal,payment);if(ok){payment.commit();s.removeTag("recall");}return ok;}
            case 18:return Summons.decoy(p,payment);
            default:return false;
        }
    }
    static boolean teleport(EntityPlayerMP p,double x,double y,double z){return teleport(p,x,y,z,()->true,null);}
    private static boolean teleport(EntityPlayerMP p,double x,double y,double z,java.util.function.BooleanSupplier legal,MoveCost.Ticket payment){
        if(!legal.getAsBoolean())return false;
        EnderTeleportEvent event=new EnderTeleportEvent(p,x,y,z,0);if(MinecraftForge.EVENT_BUS.post(event)||!legal.getAsBoolean())return false;
        x=event.getTargetX();y=event.getTargetY();z=event.getTargetZ();BlockPos at=new BlockPos(x,y,z);
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||p.getDistanceSq(x,y,z)>32*32)return false;
        AxisAlignedBB box=p.getEntityBoundingBox().offset(x-p.posX,y-p.posY,z-p.posZ);
        if(y<1||y>254||!p.world.isBlockLoaded(at)||!p.world.getWorldBorder().contains(at)||!p.world.getCollisionBoxes(p,box).isEmpty()||p.world.containsAnyLiquid(box)||!p.world.getBlockState(at.down()).getMaterial().isSolid())return false;
        double beforeX=p.posX,beforeY=p.posY,beforeZ=p.posZ;net.minecraft.world.World world=p.world;
        try{p.setPositionAndUpdate(x,y,z);p.fallDistance=0;return true;}
        finally{if(payment!=null&&p.world==world&&(p.posX!=beforeX||p.posY!=beforeY||p.posZ!=beforeZ))payment.commit();}
    }
}
