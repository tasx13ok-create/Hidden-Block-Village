package village.vale;

import java.util.*;
import net.minecraft.entity.player.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.*;
import net.minecraftforge.common.*;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraftforge.fml.common.gameevent.*;
import net.minecraftforge.fml.common.registry.EntityRegistry;

public final class Adventures {
    public static long now(EntityPlayer p){return p.getServer().getWorld(0).getTotalWorldTime();}
    public static void save(EntityPlayerMP p){p.getServer().getWorld(0).getSaveHandler().getPlayerNBTManager().writePlayerData(p);}
    public static void gesture(EntityPlayerMP p){p.swingArm(net.minecraft.util.EnumHand.MAIN_HAND);if(!(p instanceof net.minecraftforge.common.util.FakePlayer))p.connection.sendPacket(new net.minecraft.network.play.server.SPacketAnimation(p,0));}
    public static boolean tell(EntityPlayerMP p,String message){return tell(p,message,false);}
    public static boolean tell(EntityPlayerMP p,String message,boolean result){p.sendStatusMessage(new TextComponentString(message),true);HudNetwork.dirty(p);return result;}
    public static void register(Object mod){
        EntityRegistry.registerModEntity(new ResourceLocation(ValeOrders.ID,"echo_companion"),EchoCompanion.class,"echo_companion",2,mod,64,3,true);
        EntityRegistry.registerModEntity(new ResourceLocation(ValeOrders.ID,"rune_field"),RuneField.class,"rune_field",3,mod,48,5,false);
        EntityRegistry.registerModEntity(new ResourceLocation(ValeOrders.ID,"field_projectile"),FieldProjectile.class,"field_projectile",4,mod,64,1,true);
        EntityRegistry.registerModEntity(new ResourceLocation(ValeOrders.ID,"field_rogue"),FieldRogue.class,"field_rogue",5,mod,64,3,true,0x263844,0xb99d72);
    }
    public static void spawns(){
        if(!ValeOrders.fieldRogueSpawns)return;
        java.util.List<net.minecraft.world.biome.Biome> biomes=new ArrayList<>();
        for(net.minecraft.world.biome.Biome b:net.minecraftforge.fml.common.registry.ForgeRegistries.BIOMES.getValuesCollection())
            if(!BiomeDictionary.hasType(b,BiomeDictionary.Type.NETHER)&&!BiomeDictionary.hasType(b,BiomeDictionary.Type.END)&&(BiomeDictionary.hasType(b,BiomeDictionary.Type.FOREST)||BiomeDictionary.hasType(b,BiomeDictionary.Type.HILLS)||BiomeDictionary.hasType(b,BiomeDictionary.Type.SWAMP)))biomes.add(b);
        EntityRegistry.addSpawn(FieldRogue.class,6,1,2,net.minecraft.entity.EnumCreatureType.MONSTER,biomes.toArray(new net.minecraft.world.biome.Biome[0]));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)public void death(LivingDeathEvent e){if(!e.getEntityLiving().world.isRemote)Missions.queueDeath(e);}
    @SubscribeEvent public void server(TickEvent.ServerTickEvent e){if(e.phase==TickEvent.Phase.END)Missions.flushDeaths(net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance());}
    @SubscribeEvent public void player(TickEvent.PlayerTickEvent e){
        if(e.phase!=TickEvent.Phase.END||e.player.world.isRemote||e.player.ticksExisted%20!=0||!(e.player instanceof EntityPlayerMP))return;
        EntityPlayerMP p=(EntityPlayerMP)e.player;if(!p.isEntityAlive())return;
        Forms.tick(p);Summons.tick(p);Missions.tick(p);
    }
    @SubscribeEvent public void crafted(PlayerEvent.ItemCraftedEvent e){if(!e.player.world.isRemote&&e.player instanceof EntityPlayerMP){Missions.crafted((EntityPlayerMP)e.player,e.crafting);AcademyCampaign.crafted((EntityPlayerMP)e.player,e.crafting);}}
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.player instanceof EntityPlayerMP){Forms.end((EntityPlayerMP)e.player,false);Summons.dismiss((EntityPlayerMP)e.player);}}
    @SubscribeEvent public void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.player instanceof EntityPlayerMP){Forms.end((EntityPlayerMP)e.player,false);Summons.dismiss((EntityPlayerMP)e.player);}}
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent e){if(e.player instanceof EntityPlayerMP){Forms.end((EntityPlayerMP)e.player,false);Summons.dismiss((EntityPlayerMP)e.player);Quartermaster.recover((EntityPlayerMP)e.player);}}
    @SubscribeEvent(priority=EventPriority.HIGH)public void hurt(LivingHurtEvent e){if(!e.getEntityLiving().world.isRemote&&e.getEntityLiving() instanceof EntityPlayerMP&&Forms.active((EntityPlayerMP)e.getEntityLiving())==3)e.setAmount(e.getAmount()*1.20F);}
    public static boolean trial(EntityPlayerMP p,boolean cycle){
        NBTTagCompound s=PlayerState.data(p);int kind=s.getInteger("trialKind");if(kind<0||kind>=Bosses.KEYS.length)kind=0;
        if(cycle){kind=(kind+1)%Bosses.KEYS.length;s.setInteger("trialKind",kind);return tell(p,"Trial selected: "+Bosses.NAMES[kind]+". Use again without sneaking to challenge.",true);}
        if(!p.isEntityAlive()||p.isSpectator()||p.capabilities.isCreativeMode||Combat.fullSet(p)==null||PlayerState.level(p)<new int[]{5,7,9,12}[kind])return tell(p,"Equip an issued set and reach this trial's level (5/7/9/12); challenge in survival.");
        if(s.getInteger("merit")<1||now(p)<s.getLong("trialReady"))return tell(p,"A trial costs one merit and shares a five-minute challenge cooldown.");
        if(p.world.getDifficulty()==EnumDifficulty.PEACEFUL)return tell(p,"Trials require a non-peaceful world.");
        for(TrialKeeper nearby:p.world.getEntitiesWithinAABB(TrialKeeper.class,p.getEntityBoundingBox().grow(48)))if(nearby.isEntityAlive())return tell(p,"Another trial is already nearby.");
        for(EntityPlayer other:p.world.playerEntities)if(other!=p&&other.getDistanceSq(p)<18*18)return tell(p,"Use an arena at least eighteen blocks from other players.");
        Vec3d at=p.getPositionVector().add(p.getLookVec().scale(5));TrialKeeper boss=new TrialKeeper(p.world);boss.setPosition(at.x,p.posY,at.z);boss.configure(kind);
        boss.challenge(p);
        if(!p.world.isAreaLoaded(boss.getPosition(),6)||!p.world.getWorldBorder().contains(boss.getPosition())||!p.world.getCollisionBoxes(boss,boss.getEntityBoundingBox().grow(2,0,2)).isEmpty()||p.world.containsAnyLiquid(boss.getEntityBoundingBox()))return tell(p,"Need a clear, loaded, dry arena ahead.");
        if(!p.world.spawnEntity(boss))return false;
        s.setInteger("merit",s.getInteger("merit")-1);s.setLong("trialReady",now(p)+6000);save(p);return tell(p,Bosses.NAMES[kind]+" challenged. Watch the ground marks.",true);
    }
}
