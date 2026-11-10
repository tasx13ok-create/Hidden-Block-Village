package village.fieldhud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.text.ChatType;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Mirrors unique order/weapon readiness before replacing the duplicate summary. */
public final class ReadinessHud {
    private static final Minecraft MC=Minecraft.getMinecraft();
    private static ReadinessLine.State latest;
    private static Object owner,world;
    private static int receivedAt,protectedUntil;
    private static final ReadyResponse RESPONSE=new ReadyResponse();
    private static boolean visible() {
        return ShinobiHud.active()&&MC.currentScreen==null&&MC.playerController.shouldDrawHUD()&&HudConfig.mode!=2;
    }
    static void reset() { latest=null;owner=world=null;receivedAt=protectedUntil=0;RESPONSE.reset(); }
    static void suspend(int ticks){if(MC.player!=null)protectedUntil=Math.max(protectedUntil,MC.player.ticksExisted+Math.max(0,Math.min(65,ticks)));}
    static void tick() {
        if(owner!=null&&(owner!=MC.player||world!=MC.world))reset();
        if(!visible()||ValeBridge.current()==null){latest=null;RESPONSE.reset();}
        if(!HudConfig.motion)RESPONSE.silence();
    }
    private static ReadinessLayout layout(ScaledResolution sr,ReadinessLine.State s) {
        return new ReadinessLayout(sr.getScaledWidth(),HudConfig.margin,MC.fontRenderer.getStringWidth(s.skillLabel()),MC.fontRenderer.getStringWidth(s.weaponLabel()));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void message(ClientChatReceivedEvent event) {
        if(event.getType()!=ChatType.GAME_INFO||event.getMessage()==null||MC.player==null||MC.world==null)return;
        if(owner!=MC.player||world!=MC.world)reset();
        owner=MC.player;world=MC.world;
        ReadinessLine.State parsed=ReadinessLine.parse(event.getMessage().getUnformattedText());
        if(parsed==null) {
            // Leave the original message visible for its normal lifetime. The
            // glass chips must not cover a boss warning, cast result or error.
            protectedUntil=MC.player.ticksExisted+65;return;
        }
        ScaledResolution sr=new ScaledResolution(MC);
        if(!visible()||!ValeBridge.hasFreshLocal()||!layout(sr,parsed).fits) {
            latest=null;RESPONSE.reset();return;
        }
        RESPONSE.accept(parsed.order,parsed.skillState.equals("ready"),parsed.weaponState.equals("ready"),MC.player.ticksExisted,HudConfig.motion);
        latest=parsed;receivedAt=MC.player.ticksExisted;event.setCanceled(true);
    }
    static void draw(ScaledResolution sr,double time) {
        if(!visible()||ValeBridge.current()==null||owner!=MC.player||world!=MC.world||latest==null)return;
        ReadinessLayout layout=layout(sr,latest);if(!layout.fits){latest=null;RESPONSE.reset();return;}
        if(time<receivedAt||time-receivedAt>60||time<protectedUntil)return;
        int x=(sr.getScaledWidth()-layout.width)/2,y=sr.getScaledHeight()-70;
        chip(x,y,layout.firstWidth,latest.skillLabel(),latest.skillState,SealArt.forOrder(latest.order),layout.art,HudConfig.motion?RESPONSE.skill(time):0);
        chip(x+layout.firstWidth+6,y,layout.secondWidth,latest.weaponLabel(),latest.weaponState,SealArt.Mark.WEAPON,layout.art,HudConfig.motion?RESPONSE.weapon(time):0);
    }
    private static void chip(int x,int y,int width,String label,String value,SealArt.Mark mark,boolean art,double response) {
        GlassMaterial.panel(x,y,width,16,4,0xA6C9D5,224);
        if(art)SealDraw.draw(mark,x+2,y,255,response);
        if(response>0) {
            boolean alpha=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_ALPHA_TEST);
            net.minecraft.client.renderer.GlStateManager.disableAlpha();
            SkinDraw.outline(x+.5,y+.5,width-1,15,4,.7,SkinDraw.alpha(mark.tone,(int)(response*100)));
            if(alpha)net.minecraft.client.renderer.GlStateManager.enableAlpha();
        }
        MC.fontRenderer.drawStringWithShadow(label,x+(art?22:6),y+4,value.equals("ready")?0xFFBDCFD4:0xFFE3C580);
    }
}
