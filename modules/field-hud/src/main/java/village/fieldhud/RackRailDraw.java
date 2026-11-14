package village.fieldhud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

/** Paints only defensive copies of the real six stored stacks. No gameplay requests. */
public final class RackRailDraw {
    private static final Minecraft MC=Minecraft.getMinecraft();
    private RackRailDraw(){}
    public static void draw(HudLayers.Frame frame){
        try(AnimeDraw.Scope renderState=new AnimeDraw.Scope()){
        JutsuRailLayout rail=JutsuRail.layout(frame.resolution.getScaledWidth(),frame.resolution.getScaledHeight());if(rail.box==null)return;
        Layout.Box b=rail.box;RackRailModel model=RackAccess.MODEL;boolean fresh=RackAccess.ready(),motion=HudConfig.motion&&HudConfig.mode!=2;
        HotbarFx.railFrame(frame.time,motion,true);
        double focus=model.focus(frame.time,motion);boolean alphaTest=GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        try{
            for(int row=0;row<RackRailModel.SIZE;row++){
                int y=rail.slotY(row),cx=b.x+b.w/2,cy=y+rail.slotHeight/2;boolean selected=row==model.index();
                ItemStack stack=RackAccess.renderStack(row); // retained cached physical snapshot; never copy or mutate
                double cooldown=stack.isEmpty()||MC.player==null?0:MC.player.getCooldownTracker().getCooldown(stack.getItem(),frame.partial);
                cooldown=Double.isFinite(cooldown)?Math.max(0,Math.min(1,cooldown)):0;
                String id=stack.isEmpty()?null:String.valueOf(stack.getItem().getRegistryName())+"/"+stack.getMetadata();
                double[] flash=HotbarFx.railState(row,id,fresh&&!stack.isEmpty()&&cooldown<=0,frame.time,selected);
                HotbarFx.pushRow(row,selected,focus,frame.time,motion);
                try{
                SkinDraw.rounded(b.x+.5,y+1.5,b.w-1,rail.slotHeight-2,2,0xA8071014);
                SkinDraw.rounded(b.x+1,y+1,b.w-2,rail.slotHeight-3,2,selected?0xF22D3330:0xE51B2428);
                SkinDraw.outline(b.x+1.2,y+1.2,b.w-2.4,rail.slotHeight-3.4,2,.55,fresh?0xA08F907B:0x70748182);
                SkinDraw.line(b.x+4,y+1.8,b.x+b.w-4,y+1.8,.55,selected?0xBFCEBA92:0x545A7276);
                HotbarFx.rackUnder(b.x+1,y+1,b.w-2,rail.slotHeight-3,stack,selected,fresh,frame.time,row);
                if(stack.isEmpty()){
                    SkinDraw.outline(cx-3,cy-3,6,6,1,.6,0x666C7E84);SkinDraw.line(cx-1,cy,cx+1,cy,.6,0x667F8C91);
                }else item(stack,cx-8,cy-8,frame.partial);
                if(cooldown>0)HotbarFx.perimeter(b.x+1,y+1,b.w-2,rail.slotHeight-3,1-cooldown,0xE6D9BE85);
                if(flash[0]>0)SkinDraw.outline(b.x,y,b.w,rail.slotHeight-1,2,.8,SkinDraw.alpha(0x9DDDDC,(int)(200*flash[0])));
                if(flash[1]>0)SkinDraw.outline(b.x,y,b.w,rail.slotHeight-1,2,1,SkinDraw.alpha(0xFFFFFF,(int)(230*flash[1])));
                if(HotbarFx.justReady(row,frame.time))HotbarFx.sparkle(cx,cy,0xFF9DDDDC,8);
                if(selected){HotbarFx.railBurst(cx,cy,rail.slotHeight,flash[1],HotbarFx.element(id));HotbarFx.tint(b.x+1,y+1,b.w-2,rail.slotHeight-3,frame.time);}
                if(!fresh)SkinDraw.disc(b.x+b.w-3,y+3,.75,0xFF9D9380);
                }finally{HotbarFx.pop();}
            }
            double selectedY=rail.slotY(0)+model.selectedRow(frame.time,motion)*rail.slotHeight;
            GlStateManager.disableAlpha();int tone=model.focused()?0x9DDDDC:0xD3B582;
            GlStateManager.pushMatrix();try{GlStateManager.translate(FableHudMath.snap(HotbarFx.rowOut(model.index())),0,0);
            ScrollChrome.corners(b.x+.7,selectedY+.7,b.w-1.4,rail.slotHeight-2,SkinDraw.alpha(tone,fresh?170+(int)(focus*70):95));
            if(focus>0)SkinDraw.outline(b.x+.7,selectedY+.7,b.w-1.4,rail.slotHeight-2,2,.6,SkinDraw.alpha(0x9DDDDC,(int)(focus*110)));
            }finally{GlStateManager.popMatrix();}
            HotbarFx.weather(b.x,b.y,b.w,b.h,frame.time);HotbarFx.particles();
        }finally{
            RenderHelper.disableStandardItemLighting();GlStateManager.disableLighting();GlStateManager.disableDepth();GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);
            if(alphaTest)GlStateManager.enableAlpha();else GlStateManager.disableAlpha();GlStateManager.color(1,1,1,1);
        }
        }
    }
    private static void item(ItemStack stack,int x,int y,float partial){
        RenderHelper.enableGUIStandardItemLighting();GlStateManager.enableDepth();GlStateManager.color(1,1,1,1);
        MC.getRenderItem().renderItemAndEffectIntoGUI(stack,x,y);MC.getRenderItem().renderItemOverlays(MC.fontRenderer,stack,x,y);
        RenderHelper.disableStandardItemLighting();GlStateManager.disableDepth();GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.disableLighting();
        // Only the actual synchronized vanilla item cooldown is available here.
        double cooldown=MC.player==null?0:MC.player.getCooldownTracker().getCooldown(stack.getItem(),partial);
        if(Double.isFinite(cooldown)&&cooldown>0){cooldown=Math.min(1,cooldown);SkinDraw.rounded(x,y,16,16*cooldown,1,0xB9091218);SkinDraw.arc(x+8,y+8,8.3,.9,-Math.PI/2,-Math.PI/2+Math.PI*2*cooldown,0xD9D9BE85);}
    }
}
