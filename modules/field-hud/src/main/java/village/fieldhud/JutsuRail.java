package village.fieldhud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** Six native storage icons when installed; legacy shortcuts only when storage is absent. */
public final class JutsuRail extends Gui {
    private static final Minecraft MC=Minecraft.getMinecraft();
    private JutsuRail(){}
    public static boolean visible(){return HudConfig.jutsuRail&&JutsuInput.controlsCompatible()&&ShinobiHud.active()&&HudConfig.mode!=2&&MC.currentScreen==null;}
    public static JutsuRailLayout layout(int width,int height){
        ShinobiLayout base=ShinobiHud.layout(width,height);Layout.Box map=EyeMap.bounds(width,height,true);
        Layout.Box scores=ClientHud.sidebar()?new Layout.Box(width-184,40,184,Math.max(1,height-114)):null;
        return new JutsuRailLayout(width,height,HudConfig.margin,JutsuLoadout.SIZE,map,
            ArmorReservations.merge(ArmorHudBridge.bounds(width,height),base.status,base.hotbar,base.leftHand,base.rightHand,scores));
    }
    public static Layout.Box bounds(int width,int height){return visible()&&(RackAccess.loaded()||JutsuAccess.MODEL.initialized())?layout(width,height).box:null;}
    // Error feedback uses the normal action bar through JutsuInput, rather than a persistent footer.
    public static void hint(String value){}
    public static void draw(HudLayers.Frame frame){
        if(RackAccess.loaded()){if(visible())RackRailDraw.draw(frame);return;}
        if(!visible()||!JutsuAccess.MODEL.initialized())return;
        JutsuRailLayout rail=layout(frame.resolution.getScaledWidth(),frame.resolution.getScaledHeight());if(rail.box==null)return;
        Layout.Box b=rail.box;double time=frame.time;JutsuRailModel model=JutsuAccess.MODEL;
        boolean motion=HudConfig.motion&&HudConfig.mode!=2,fresh=JutsuAccess.fresh();
        double focus=model.focus(time,motion),elapsed=model.elapsed(time);
        boolean alphaTest=GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        try{
            for(int row=0;row<JutsuLoadout.SIZE;row++){
                int y=rail.slotY(row),cx=b.x+b.w/2,cy=y+rail.slotHeight/2;
                int iconX=cx-8,iconY=cy-8;boolean selected=row==model.index();JutsuEntry entry=model.display(row);
                boolean enabled=fresh&&entry!=null&&entry.enabled;double ready=entry==null?0:model.ready(entry.id,time,motion);
                SkinDraw.rounded(b.x+.5,y+1.5,b.w-1,rail.slotHeight-2,2,0xA8071014);
                SkinDraw.rounded(b.x+1,y+1,b.w-2,rail.slotHeight-3,2,selected?0xF22D3330:0xE51B2428);
                SkinDraw.outline(b.x+1.2,y+1.2,b.w-2.4,rail.slotHeight-3.4,2,.55,enabled?0xA08F907B:0x70748182);
                SkinDraw.line(b.x+4,y+1.8,b.x+b.w-4,y+1.8,.55,selected?0xBFCEBA92:0x545A7276);
                if(entry==null){
                    SkinDraw.outline(cx-3,cy-3,6,6,1,.6,0x666C7E84);
                    SkinDraw.line(cx-1,cy,cx+1,cy,.6,0x667F8C91);
                    continue;
                }
                icon(entry,iconX,iconY,fresh?255:115,ready);
                double fraction=entry.fraction(elapsed);
                if(fraction>0){
                    SkinDraw.rounded(iconX,iconY,16,16*fraction,1,0xB9091218);
                    SkinDraw.arc(cx,cy,8.3,.9,-Math.PI/2,-Math.PI/2+Math.PI*2*fraction,0xD9D9BE85);
                }
                if(entry.itemCount>1)quantity(entry.itemCount,iconX+15.5,iconY+11);
                if(!enabled){
                    SkinDraw.line(iconX+3,iconY+13,iconX+13,iconY+3,.85,0xB9C3A298);
                    if(!fresh)SkinDraw.disc(b.x+b.w-3,y+3,.75,0xFF9D9380);
                }
                if(ready>0&&fresh){
                    GlStateManager.disableAlpha();SkinDraw.outline(b.x+1,y+1,b.w-2,rail.slotHeight-3,2,.8,SkinDraw.alpha(0x9DDDDC,(int)(ready*200)));
                }
            }
            double selectedY=rail.slotY(0)+model.selectedRow(JutsuLoadout.SIZE,time,motion)*rail.slotHeight;
            GlStateManager.disableAlpha();
            int tone=model.focused()?0x9DDDDC:0xD3B582;
            ScrollChrome.corners(b.x+.7,selectedY+.7,b.w-1.4,rail.slotHeight-2,SkinDraw.alpha(tone,fresh?170+(int)(focus*70):95));
            if(focus>0)SkinDraw.outline(b.x+.7,selectedY+.7,b.w-1.4,rail.slotHeight-2,2,.6,SkinDraw.alpha(0x9DDDDC,(int)(focus*110)));
        }finally{
            if(alphaTest)GlStateManager.enableAlpha();else GlStateManager.disableAlpha();GlStateManager.color(1,1,1,1);
        }
    }
    private static void quantity(int count,double right,double y){
        String value=count>99?"99+":String.valueOf(count);
        GlStateManager.pushMatrix();GlStateManager.translate(right,y,0);GlStateManager.scale(.55F,.55F,1);
        MC.fontRenderer.drawStringWithShadow(value,-MC.fontRenderer.getStringWidth(value),0,0xFFF0E5D1);GlStateManager.popMatrix();
    }
    /** Original seals for arts; actual registered models for carried equipment and tools. */
    public static void icon(JutsuEntry entry,int x,int y,int alpha,double ready){
        boolean nativeItem=entry.kind.equals("weapon")||entry.kind.equals("projectile")||entry.kind.equals("form")||entry.kind.equals("summon");
        Item item=null;try{if(nativeItem&&!entry.iconItemId.isEmpty()){ResourceLocation id=new ResourceLocation(entry.iconItemId);if(Item.REGISTRY.containsKey(id))item=Item.REGISTRY.getObject(id);}}catch(RuntimeException ignored){}
        if(item!=null&&nativeItem){
            ItemStack visual=new ItemStack(item,1,entry.iconMeta);
            // Read-only visual lookup preserves this exact weapon's model, glint and name.
            // It supplies no gameplay permission or action payload.
            if(entry.kind.equals("weapon")&&MC.player!=null){
                String serial=entry.id.substring("weapon/".length());
                for(ItemStack carried:MC.player.inventory.mainInventory)if(matches(carried,serial)){visual=carried;break;}
                for(ItemStack carried:MC.player.inventory.offHandInventory)if(matches(carried,serial)){visual=carried;break;}
            }
            RenderHelper.enableGUIStandardItemLighting();GlStateManager.enableDepth();GlStateManager.color(1,1,1,alpha/255f);
            MC.getRenderItem().renderItemAndEffectIntoGUI(visual,x,y);
            RenderHelper.disableStandardItemLighting();GlStateManager.disableDepth();GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.disableLighting();
        }else SealDraw.draw(entry.kind.equals("technique")||entry.kind.equals("weapon")?SealArt.Mark.WEAPON:SealArt.forAbility(entry.id,entry.school),x,y,alpha,ready);
    }
    private static boolean matches(ItemStack stack,String serial){return !stack.isEmpty()&&stack.hasTagCompound()&&serial.equals(stack.getTagCompound().getString("valeSerial"));}
}
