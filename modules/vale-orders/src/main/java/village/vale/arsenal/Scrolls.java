package village.vale.arsenal;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import village.vale.ArsenalAccess;
import village.vale.Gear;
import village.vale.PlayerState;
import village.vale.RewardLedger;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.*;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Original persistent lessons and native item-use techniques. */
public final class Scrolls {
    private Scrolls() {}
    // Runtime only: neither reconnects nor copied player NBT can retain a stuck
    // reservation, and callbacks cannot recursively cast another owned lesson.
    private static final Map<UUID,CastAttempt> CASTS=new ConcurrentHashMap<>();
    public static final class Lesson {
        public final String key,title,prerequisite,kind; public final int level,cost,practice,cooldown;
        Lesson(String k,String t,int l,int c,int p,int cd,String pre,String kind){this.kind=kind;key=k;title=t;level=l;cost=c;practice=p;cooldown=cd;prerequisite=pre;}
    }
    public static final Lesson[] LESSONS=ScrollCatalog.ALL;
    /** Quartermaster integration: unknown -2, level/prerequisite locked -1, otherwise merit price. */
    public static int price(EntityPlayerMP p,String key) {
        int carrier=AbilityCarriers036.price(p,key);if(carrier!=-2)return carrier;
        for(Lesson l:LESSONS)if(key.equals("scroll_"+l.key)||key.equals("technique_"+l.key)){
            if(PlayerState.level(p)<l.level||(!l.prerequisite.isEmpty()&&!row(p,l.prerequisite).getBoolean("learned")))return -1;
            if(key.startsWith("technique_")&&!row(p,l.key).getBoolean("learned"))return -1;
            return key.startsWith("scroll_")?2+l.level:2;
        }
        for(String support:ScrollSupport.KEYS)if(support.equals(key))return PlayerState.level(p)<(support.contains("loom")||support.startsWith("puppet")||support.equals("ancestral_seal")?4:1)?-1:(support.contains("loom")||support.startsWith("puppet")||support.equals("ancestral_seal")?8:4);
        if(key.equals("sight_eye")||key.equals("void_eye"))return PlayerState.level(p)<4?-1:20;
        if(key.endsWith("_eye")||key.equals("scroll_actor_glyph"))return -1;
        return -2;
    }
    private static final List<Item> MODELS=new ArrayList<>();
    public static void register(RegistryEvent.Register<Item> event) {
        for(Lesson lesson:LESSONS){add(event,"scroll_"+lesson.key,new ScrollItem(lesson,false));add(event,"technique_"+lesson.key,new ScrollItem(lesson,true));}
        ScrollSupport.register(event);
        AbilityCarriers036.register(event);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(ScrollEvents.class);
    }
    static void add(RegistryEvent.Register<Item> event,String key,Item item) {
        if(Gear.ITEMS.containsKey(key))throw new IllegalStateException("Duplicate arsenal item: "+key);
        item.setRegistryName(new ResourceLocation("valeorders",key));item.setTranslationKey("valeorders."+key);item.setMaxStackSize(1);
        Gear.ITEMS.put(key,item);event.getRegistry().register(item);MODELS.add(item);
    }
    public static void registerEntities(Object mod) { net.minecraftforge.fml.common.registry.EntityRegistry.registerModEntity(new ResourceLocation("valeorders","scroll_actor"),ScrollActor.class,"vale_scroll_actor",120,mod,64,2,true);
        net.minecraftforge.fml.common.registry.EntityRegistry.registerModEntity(new ResourceLocation("valeorders","hand_technique"),HandTechnique.class,"vale_hand_technique",121,mod,64,1,false); }
    @SideOnly(Side.CLIENT) public static void registerRenderers() {
        ScrollActor.registerRenderer();
        net.minecraftforge.fml.client.registry.RenderingRegistry.registerEntityRenderingHandler(HandTechnique.class,village.vale.client.JutsuVfx::new);
        AbilityCarriers036.registerRenderers();
        for(Item item:MODELS)net.minecraftforge.client.model.ModelLoader.setCustomModelResourceLocation(item,0,new net.minecraft.client.renderer.block.model.ModelResourceLocation(item.getRegistryName(),"inventory"));
    }
    static NBTTagCompound state(EntityPlayerMP p) {
        NBTTagCompound root=PlayerState.data(p);if(!root.hasKey("OriginalScrolls",10))root.setTag("OriginalScrolls",new NBTTagCompound());return root.getCompoundTag("OriginalScrolls");
    }
    static NBTTagCompound row(EntityPlayerMP p,String key) {
        NBTTagCompound root=state(p);if(!root.hasKey(key,10))root.setTag(key,new NBTTagCompound());return root.getCompoundTag(key);
    }
    static boolean tell(EntityPlayerMP p,String message) {p.sendStatusMessage(new TextComponentString(message),true);return false;}
    static boolean ready(EntityPlayerMP p,ItemStack stack) {return p.isEntityAlive()&&!p.isSpectator()&&ArsenalAccess.issued(p,stack);}
    private static boolean learn(EntityPlayerMP p,ItemStack stack,Lesson lesson) {
        NBTTagCompound r=row(p,lesson.key);boolean pre=lesson.prerequisite.isEmpty()||row(p,lesson.prerequisite).getBoolean("learned");
        ScrollRules.Failure fail=ScrollRules.learn(ready(p,stack),r.getBoolean("learned"),PlayerState.level(p),lesson.level,pre);
        if(fail!=ScrollRules.Failure.NONE)return tell(p,"Lesson kept: "+fail.name().toLowerCase(Locale.ROOT)+".");
        // Durable ledger outbox first. A retry after a crash reuses this same
        // receipt, never duplicates a reusable technique. Known lessons keep item.
        String receipt=ScrollRules.receipt(p.getUniqueID().toString(),lesson.key);
        RewardLedger.get(p).issue(p,"technique_"+lesson.key,1,receipt);
        r.setBoolean("learned",true);r.setString("receipt",receipt);r.setInteger("practice",0);
        stack.shrink(1);p.inventory.markDirty();ArsenalAccess.save(p);ArsenalAccess.gesture(p);
        p.sendStatusMessage(new TextComponentString("Learned "+lesson.title+". Sneak and use its technique to practice."),true);return true;
    }
    private static boolean castProof(EntityPlayerMP p,ItemStack stack,Lesson lesson) {
        if(p==null||p.world.isRemote||p instanceof net.minecraftforge.common.util.FakePlayer
                ||stack==null||stack.isEmpty()||stack.getCount()!=1
                ||(p.getHeldItemMainhand()!=stack&&p.getHeldItemOffhand()!=stack)||!ready(p,stack))return false;
        if(stack.getItem() instanceof ScrollItem) {
            ScrollItem item=(ScrollItem)stack.getItem();return item.technique&&item.lesson==lesson;
        }
        return AbilityCarriers036.authorizedCast(p,stack,lesson.key);
    }
    private static boolean activate(EntityPlayerMP p,ItemStack stack,Lesson lesson) {
        if(CASTS.containsKey(p.getUniqueID()))return false;
        for(CastAttempt active:CASTS.values())if(active.player==p)return false;
        NBTTagCompound r=row(p,lesson.key);long now=ArsenalAccess.now(p);boolean practicing=p.isSneaking();
        int cost=ScrollRules.cost(lesson.cost,practicing,lesson.kind.equals("dragon")&&!p.isInWater());
        int required=practicing?0:lesson.practice,ticks=practicing?30:lesson.cooldown;
        ScrollRules.Failure fail=ScrollRules.cast(castProof(p,stack,lesson),r.getBoolean("learned"),PlayerState.level(p),lesson.level,r.getInteger("practice"),required,now,r.getLong("next"),PlayerState.resolve(p),cost);
        if(fail!=ScrollRules.Failure.NONE)return tell(p,"Technique unavailable: "+fail.name().toLowerCase(Locale.ROOT)+".");
        if(practicing&&r.getInteger("practice")>=60)return tell(p,"Practice complete.");
        if(p.getCooldownTracker().hasCooldown(stack.getItem()))return false;
        // Validate the deadline and reserve the entire cost before a native
        // potion, heal, spawn, teleport or damage event can call back into us.
        long next=ScrollRules.next(now,ticks);CastAttempt cast=new CastAttempt(p,stack,lesson,r,required,cost);
        if(CASTS.putIfAbsent(cast.owner,cast)!=null)return false;
        Throwable failure=null;
        try {
            cast.reserved=true;PlayerState.setResolve(p,PlayerState.resolve(p)-cost);
            if(!practicing&&!ScrollEffects.cast(p,lesson))return tell(p,"No valid effect. Resolve kept.");
            cast.applied=true;return true;
        } catch(RuntimeException|Error error) {
            failure=error;throw error;
        } finally {
            try {
                if(cast.applied)completeCast(p,cast,practicing,ticks,next);
                else cast.refund();
            } catch(RuntimeException|Error completionError) {
                if(failure==null)throw completionError;
                if(completionError!=failure)failure.addSuppressed(completionError);
            } finally {CASTS.remove(cast.owner,cast);}
        }
    }
    private static void completeCast(EntityPlayerMP p,CastAttempt cast,boolean practicing,int ticks,long next) {
        // Settlement runs once even if a native connection moved successfully
        // and then threw. Merge current state, never restore a stale snapshot.
        Lesson lesson=cast.lesson;NBTTagCompound current=state(p);
        if(current.hasKey(lesson.key,10)) {
            NBTTagCompound progress=current.getCompoundTag(lesson.key);
            if(progress.getBoolean("learned"))progress.setInteger("practice",ScrollRules.advance(progress.getInteger("practice"),1,60));
            progress.setLong("next",Math.max(progress.getLong("next"),next));
        }
        p.getCooldownTracker().setCooldown(cast.item,ticks);ArsenalAccess.gesture(p);ArsenalAccess.save(p);
        p.sendStatusMessage(new TextComponentString(lesson.title+(practicing?" practice complete.":" applied.")),true);
    }
    /** Record an observed irreversible effect before a callback exception unwinds. */
    static void recordApplied(EntityPlayerMP p) {
        for(CastAttempt cast:CASTS.values())if(cast.player==p){cast.applied=true;return;}
    }
    /** Recheck after a cancellable callback, before applying an irreversible move. */
    static boolean castStillValid(EntityPlayerMP p) {
        CastAttempt cast=CASTS.get(p.getUniqueID());return cast!=null&&cast.valid(p);
    }
    private static final class CastAttempt {
        final EntityPlayerMP player;final UUID owner;final ItemStack stack,proof;final Item item;final Lesson lesson;
        final World world;final int dimension,required,cost;final EnumHand hand;
        final NBTTagCompound account,scrolls,progress;boolean reserved,applied;
        CastAttempt(EntityPlayerMP p,ItemStack stack,Lesson lesson,NBTTagCompound progress,int required,int cost) {
            player=p;owner=p.getUniqueID();this.stack=stack;proof=stack.copy();item=stack.getItem();this.lesson=lesson;
            world=p.world;dimension=p.dimension;hand=p.getHeldItemMainhand()==stack?EnumHand.MAIN_HAND:EnumHand.OFF_HAND;
            account=PlayerState.data(p);scrolls=state(p);this.progress=progress;this.required=required;this.cost=cost;
        }
        boolean valid(EntityPlayerMP p) {
            return p==player&&owner.equals(p.getUniqueID())&&p.world==world&&p.dimension==dimension&&p.getHeldItem(hand)==stack
                    &&ItemStack.areItemStacksEqual(stack,proof)&&castProof(p,stack,lesson)
                    &&PlayerState.data(p)==account&&state(p)==scrolls&&scrolls.getCompoundTag(lesson.key)==progress
                    &&progress.getBoolean("learned")&&progress.getInteger("practice")>=required&&PlayerState.level(p)>=lesson.level;
        }
        void refund() {
            if(!reserved)return;
            int restored=village.vale.Rules.clampResolve(account.getInteger("resolve"))+cost;
            // Refund the account actually debited. Replacing that account in an
            // event must not give the replacement state an unearned top-up.
            if(PlayerState.data(player)==account)PlayerState.setResolve(player,restored);
            else account.setInteger("resolve",village.vale.Rules.clampResolve(restored));
            reserved=false;
        }
    }
    /** Original036 carrier bridge: no packet/HUD schema and no forged held-item context. */
    public static boolean castFromCarrier036(EntityPlayerMP p,ItemStack stack,String key) {
        if(!AbilityCarriers036.authorizedCast(p,stack,key))return false;
        for(Lesson lesson:LESSONS)if(lesson.key.equals(key)){Item item=stack.getItem();boolean applied=activate(p,stack,lesson);if(applied)p.getCooldownTracker().setCooldown(item,0);return applied;}
        return false;
    }
    static final class ScrollItem extends Item {
        final Lesson lesson;final boolean technique;ScrollItem(Lesson l,boolean t){lesson=l;technique=t;}
        @Override public String getItemStackDisplayName(ItemStack stack){return lesson.title+(technique?" Technique":" Lesson");}
        @SideOnly(Side.CLIENT) @Override public void addInformation(ItemStack stack,World world,List<String> lines,net.minecraft.client.util.ITooltipFlag flag){
            lines.add("Level "+lesson.level+(lesson.prerequisite.isEmpty()?"":"; prerequisite: "+lesson.prerequisite.replace('_',' ')));
            if(technique){lines.add("Sneak + use: practice (2 resolve). Use: cast.");lines.add("Requires "+lesson.practice+" practice; costs "+lesson.cost+" resolve; cooldown "+lesson.cooldown+" ticks.");if(lesson.kind.equals("teleport"))lines.add("First use marks safe ground; next use returns within 64 blocks.");if(lesson.kind.equals("dragon"))lines.add("On dry land: costs "+(lesson.cost*3)+" resolve.");if(lesson.kind.equals("ritual"))lines.add("Aim at ground with four solid anchors two blocks away.");}
            else lines.add("Use to learn once and receive your reusable technique.");
        }
        @Override public ActionResult<ItemStack> onItemRightClick(World world,EntityPlayer player,EnumHand hand){
            ItemStack stack=player.getHeldItem(hand);if(world.isRemote)return new ActionResult<>(EnumActionResult.SUCCESS,stack);
            boolean ok=stack.getItem()==this&&player instanceof EntityPlayerMP&&(technique?activate((EntityPlayerMP)player,stack,lesson):learn((EntityPlayerMP)player,stack,lesson));
            // Native use installs this return value into the hand. Preserve any
            // deliberate inventory change made by an effect/protection callback.
            return new ActionResult<>(ok?EnumActionResult.SUCCESS:EnumActionResult.FAIL,player.getHeldItem(hand));
        }
    }
}
