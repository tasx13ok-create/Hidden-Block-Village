package village.fieldhud;

import java.io.File;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

public final class ClientHud extends Gui {
    public static final Minecraft MC=Minecraft.getMinecraft();
    public static final KeyBinding SETTINGS=new KeyBinding("key.fieldhud.settings",Keyboard.KEY_NONE,"key.categories.fieldhud");
    public static final KeyBinding MODE=new KeyBinding("key.fieldhud.mode",Keyboard.KEY_NONE,"key.categories.fieldhud");
    private long ticks=0,lastBoss=-1000,lastEntry=-1000;
    private int stable=0,arrivalLeft=0;
    private String candidate="";
    private Object lastWorld;
    private Regions.Zone arrival;
    public static void start(File directory) {
        HudConfig.load(directory); Regions.load(directory);JutsuAccess.configure(directory);
        ClientRegistry.registerKeyBinding(SETTINGS); ClientRegistry.registerKeyBinding(MODE);
        JutsuInput.start();
        ClientCommandHandler.instance.registerCommand(new HudCommand());
        MinecraftForge.EVENT_BUS.register(new ClientHud());
        MinecraftForge.EVENT_BUS.register(new ShinobiHud());
        MinecraftForge.EVENT_BUS.register(new ChakraBridge());
        MinecraftForge.EVENT_BUS.register(new HudSoundPulse());
        MinecraftForge.EVENT_BUS.register(new ReadinessHud());
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END) return;
        ChakraBridge.tick();
        ValeBridge.tick();
        JutsuAccess.tick();RackAccess.tick();JutsuInput.tick();
        ReadinessHud.tick();
        ArmorHudBridge.tick();
        JutsuSignals.tick();
        LegacyHealth.sync(ShinobiHud.active()&&MC.playerController.shouldDrawHUD());
        if(MC.world!=lastWorld) {
            EyeMap.resetWorld();ReadinessHud.reset();
            ShinobiHud.resetWorld();GlassMaterial.invalidate();JutsuSignals.reset();HudSoundPulse.reset();
            lastWorld=MC.world; candidate=""; stable=0; arrivalLeft=0; lastEntry=-1000; lastBoss=-1000;
        }
        if(MC.player==null||MC.world==null) return;
        while(SETTINGS.isPressed()) MC.displayGuiScreen(new HudScreen());
        while(MODE.isPressed()) { HudConfig.mode=(HudConfig.mode+1)%3; HudConfig.save(); }
        if(MC.isGamePaused()) return;
        ticks++; if(arrivalLeft>0) arrivalLeft--;
        Regions.Zone current=Regions.at(MC);
        String id=current==null?"":current.world+":"+current.dimension+":"+current.id;
        if(!id.equals(candidate)) { candidate=id; stable=0; }
        else stable++;
        // Stay inside for a second; a shared 30-second cooldown prevents boundary spam.
        if(stable==20&&current!=null&&ticks-lastEntry>=600&&HudConfig.arrivals
            &&HudConfig.mode==0&&MC.currentScreen==null&&!MC.gameSettings.hideGUI&&ticks-lastBoss>40) {
            arrival=current; arrivalLeft=70; lastEntry=ticks;
            if(HudConfig.chime) MC.player.playSound(SoundEvents.BLOCK_NOTE_CHIME,0.15F,0.85F);
        }
    }
    @SubscribeEvent public void boss(RenderGameOverlayEvent.BossInfo event) { lastBoss=ticks; }
    public static boolean sidebar() {
        Scoreboard board=MC.world.getScoreboard();
        ScorePlayerTeam team=board.getPlayersTeam(MC.player.getName());
        int color=team==null?-1:team.getColor().getColorIndex();
        return board.getObjectiveInDisplaySlot(1)!=null || (color>=0&&board.getObjectiveInDisplaySlot(3+color)!=null);
    }
    @SubscribeEvent public void render(RenderGameOverlayEvent.Post event) {
        if(event.getType()!=RenderGameOverlayEvent.ElementType.ALL||MC.player==null||MC.world==null
            ||MC.currentScreen!=null||MC.gameSettings.hideGUI||MC.gameSettings.showDebugInfo||MC.player.isSpectator()
            ||!MC.player.isEntityAlive()||HudConfig.mode==2) return;
        int w=event.getResolution().getScaledWidth(),h=event.getResolution().getScaledHeight();
        Layout layout=new Layout(w,h,sidebar(),HudConfig.margin);
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770,771,1,0);
        try {
            Regions.Zone zone=Regions.at(MC);
            int color=zone==null?0x76C5BE:zone.color&0xFFFFFF;
            Layout.Box[] reserved=ArmorHudBridge.bounds(w,h);
            reserved=ArmorReservations.merge(reserved,JutsuRail.bounds(w,h));
            Layout.Box status=HudConfig.shinobi?ShinobiHud.layout(w,h).status:null;
            Layout.Box map=EyeMap.bounds(w,h,false);
            if(map==null)map=new Layout.Box(w-HudConfig.margin-148,44,148,160);
            Layout.Box scores=sidebar()?new Layout.Box(w-184,40,184,Math.max(1,h-114)):null;
            Layout.Box rpg=HudConfig.shinobi?ValeHud.bounds(w,h,status,map,scores,reserved):null;
            if(rpg!=null)ValeHud.card(rpg);
            Layout.Box warning=HudPlacement.place(layout.warning,w,h,HudConfig.margin,ArmorReservations.merge(reserved,status,map,scores,rpg));
            boolean warned=HudConfig.warning&&warning!=null&&renderDurability(warning);
            Layout.Box target=HudPlacement.place(layout.target,w,h,HudConfig.margin,ArmorReservations.merge(reserved,status,map,scores,rpg,warned?warning:null));
            if(HudConfig.target&&target!=null)renderTarget(target,color);
            Layout.Box location=null;
            if(HudConfig.mode==0&&HudConfig.location&&ticks-lastBoss>40&&(!HudConfig.shinobi||h>=340)) {
                String name=zone==null?MC.world.getBiome(MC.player.getPosition()).getBiomeName():zone.name;
                String detail=MC.world.provider.isSurfaceWorld()?timeWeather():"Dimension "+MC.player.dimension;
                Layout.Box preferred=HudConfig.shinobi?new Layout.Box(status.x,status.y+status.h+6,Math.min(layout.location.w,status.w),30):layout.location;
                location=HudPlacement.place(preferred,w,h,HudConfig.margin,ArmorReservations.merge(reserved,status,map,scores,rpg,warned?warning:null,HudConfig.target?target:null));
                if(location!=null)PanelSkin.location(location,name,detail,color,255);
            }
            if(HudConfig.mode==0&&arrival!=null&&arrivalLeft>0&&ticks-lastBoss>40&&!warned&&(!HudConfig.shinobi||h>=340)) {
                int alpha=(int)(255*Math.min(1.0,Math.min((70-arrivalLeft)/10.0,arrivalLeft/15.0)));
                Layout.Box enter=HudConfig.shinobi?new Layout.Box(w/2-128,h/2-20,256,40):layout.arrival;
                enter=HudPlacement.place(enter,w,h,HudConfig.margin,ArmorReservations.merge(reserved,status,map,target,scores,location,rpg));
                if(enter!=null)PanelSkin.arrival(enter,arrival.name,arrival.subtitle,arrival.color,alpha);
            }
        } finally {
            GlStateManager.color(1,1,1,1); GlStateManager.disableBlend(); GlStateManager.popMatrix();
        }
    }
    private static String timeWeather() {
        long day=MC.world.getWorldTime()%24000;
        int minutes=(int)((day+6000)%24000/1000.0*60);
        return String.format(Locale.ROOT,"%02d:%02d  %s",minutes/60,minutes%60,MC.world.isRaining()?"Rain":"Clear");
    }
    private void renderTarget(Layout.Box b,int accent) {
        RayTraceResult hit=MC.objectMouseOver;
        if(hit==null||!(hit.entityHit instanceof EntityLivingBase)) return;
        EntityLivingBase target=(EntityLivingBase)hit.entityHit;
        if(target==MC.player||!target.isEntityAlive()||target.isInvisibleToPlayer(MC.player)
            ||!MC.player.canEntityBeSeen(target)||MC.player.getDistance(target)>12) return;
        float max=target.getMaxHealth(),hp=target.getHealth();
        if(!Float.isFinite(max)||!Float.isFinite(hp)||max<=0) return;
        String values=format(hp)+" / "+format(max)+" HP";
        if(target.getAbsorptionAmount()>0) values+="  +"+format(target.getAbsorptionAmount());
        panel(b,target.getDisplayName().getUnformattedText(),values,accent,255);
        int x=b.x+7,y=b.y+b.h-6,bar=b.w-14;
        drawRect(x,y,x+bar,y+2,0xFF263438);
        drawRect(x,y,x+(int)(bar*Math.max(0,Math.min(1,hp/max))),y+2,0xFF000000|accent);
    }
    private static String format(float value) {
        return String.format(Locale.ROOT,value>=1000?"%.0f":"%.1f",value);
    }
    private boolean renderDurability(Layout.Box b) {
        ItemStack weakest=ItemStack.EMPTY; double lowest=1;
        for(ItemStack stack:MC.player.getEquipmentAndArmor()) {
            if(!stack.isEmpty()&&stack.isItemStackDamageable()&&stack.getMaxDamage()>0) {
                double remaining=(stack.getMaxDamage()-stack.getItemDamage())/(double)stack.getMaxDamage();
                if(remaining<lowest) { lowest=remaining; weakest=stack; }
            }
        }
        if(!weakest.isEmpty()&&lowest<=0.15) {
            PanelSkin.durability(b,weakest,Math.max(0,(int)(lowest*100))); return true;
        }
        return false;
    }
    public static void panel(Layout.Box b,String title,String detail,int color,int alpha) {
        if(alpha<8) return;
        PanelSkin.simple(b,title,detail,color,alpha);
    }
}
