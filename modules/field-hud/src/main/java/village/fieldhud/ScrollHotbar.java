package village.fieldhud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHandSide;

/** Equipment-scroll skin. Actual vanilla stacks, slot selection, bindings and attack/XP state. */
public final class ScrollHotbar extends Gui {
    private static final Minecraft MC=Minecraft.getMinecraft();
    private static double focus=-1,lastTime,changedAt=-100;
    private static int previous=-1;
    private ScrollHotbar(){}
    public static void reset(){focus=-1;previous=-1;lastTime=0;changedAt=-100;}
    public static void draw(ShinobiLayout layout,double time,float partial){
        Layout.Box b=layout.hotbar;int x=b.x,y=b.y;
        boolean motion=HudConfig.motion&&HudConfig.mode!=2;
        double route=JutsuAccess.MODEL.focus(time,motion),priority=1-route*.28;
        int selected=MC.player.inventory.currentItem;
        if(previous!=selected){previous=selected;changedAt=time;}
        if(focus<0||!motion||time<lastTime||time-lastTime>20)focus=selected;
        else focus=SkinMath.ease(focus,selected,Math.min(4,time-lastTime),.8);
        lastTime=time;
        double stamp=motion?Math.max(0,1-(time-changedAt)/7):0;

        // Parchment is restricted to borders; the item wells retain high-contrast ink.
        SkinDraw.rounded(x,y+2,b.w,30,3,0xC510161A);
        SkinDraw.rounded(x+10,y+1,b.w-20,30,2,0xE8C7B796);
        SkinDraw.rounded(x+17,y+6,b.w-34,25,1,0xF5232C30);
        SkinDraw.line(x+17,y+2.5,x+b.w-17,y+2.5,.7,0xFFE9DDC2);
        SkinDraw.line(x+17,y+30,x+b.w-17,y+30,.6,0xFF796D57);
        for(int i=0;i<9;i++){
            int sx=ScrollGeometry.slotX(b,i),sy=ScrollGeometry.slotY(b,i,HudConfig.curve);
            SkinDraw.rounded(sx,sy,19,22,1.5,0xF4141F24);
            SkinDraw.outline(sx+.25,sy+.25,18.5,21.5,1.5,.45,0x827E7969);
            SkinDraw.line(sx+3,sy+.6,sx+16,sy+.6,.6,0x78596665);
            SkinDraw.line(sx+4,sy+21,sx+15,sy+21,.5,0x706D5148);
        }
        double sx=x+22+focus*ScrollGeometry.STRIDE,sy=y+7+ScrollGeometry.curve(focus,HudConfig.curve);
        SkinDraw.rounded(sx,sy,19,22,1.5,SkinDraw.alpha(0x663B35,(int)(90*priority)));
        SkinDraw.outline(sx-.4,sy-.4,19.8,22.8,1.6,.8,SkinDraw.alpha(0xBD6E5E,(int)(245*priority)));
        ScrollChrome.corners(sx,sy,19,22,SkinDraw.alpha(0xF0DCB7,(int)(235*priority)));
        SkinDraw.line(sx+5,sy+21.1,sx+14,sy+21.1,.8,SkinDraw.alpha(0xE9BB79,(int)((150+stamp*75)*priority)));
        // The selected physical slot remains unambiguous while the outer bracket eases.
        for(int i=0;i<9;i++){
            int cx=ScrollGeometry.slotX(b,i)+9;
            SkinDraw.rounded(cx-6,y+.5,12,5.5,1,i==selected?0xFF8F4B40:0xFF303937);
            key(MC.gameSettings.keyBindsHotbar[i],cx,y+.8,i==selected?0xFFF4E3BF:0xFFC0B9A4,12);
        }
        cap(x+2,y,selected==0,route);cap(x+b.w-17,y,selected==8,route);

        RenderHelper.enableGUIStandardItemLighting();GlStateManager.enableDepth();
        for(int i=0;i<9;i++)item(MC.player.inventory.mainInventory.get(i),ScrollGeometry.itemX(b,i),ScrollGeometry.itemY(b,i,HudConfig.curve));
        RenderHelper.disableStandardItemLighting();GlStateManager.disableDepth();GlStateManager.disableLighting();
        ItemStack off=MC.player.getHeldItemOffhand();
        if(!off.isEmpty()){
            Layout.Box ob=MC.player.getPrimaryHand()==EnumHandSide.RIGHT?layout.leftHand:layout.rightHand;
            ScrollChrome.panel(ob,245);
            SkinDraw.line(ob.x+5,ob.y+7,ob.x+ob.w-5,ob.y+7,.5,0x9C776E59);
            key(MC.gameSettings.keyBindSwapHands,ob.x+ob.w/2.0,ob.y+1.2,0xFFD5C7A9,16);
            RenderHelper.enableGUIStandardItemLighting();GlStateManager.enableDepth();item(off,ob.x+4,ob.y+8);
            RenderHelper.disableStandardItemLighting();GlStateManager.disableDepth();GlStateManager.disableLighting();
        }
        if(MC.gameSettings.attackIndicator==2){
            float cooled=MC.player.getCooledAttackStrength(partial);
            if(cooled<1){
                int ax=x+b.w-5,ay=y+11;
                SkinDraw.rounded(ax-1,ay-1,4,17,1,0xFF151C20);
                double fill=15*SkinMath.ratio(cooled,1);SkinDraw.rounded(ax,ay+15-fill,2,fill,1,0xFFE7C58C);
            }
        }
        if(MC.playerController.gameIsSurvivalOrAdventure()&&!MC.player.isRidingHorse()){
            int ex=x+24,ey=y-3;
            SkinDraw.rounded(ex-1,ey-1,178,4,1,0xC0182225);
            double xp=176*SkinMath.ratio(MC.player.experience,1);
            if(xp>0)SkinDraw.rounded(ex,ey,xp,2,.8,0xFFC5B27C);
            for(int n=1;n<9;n++)SkinDraw.line(ex+n*19.55,ey,ex+n*19.55,ey+2,.45,0x5A272A25);
            if(MC.player.experienceLevel>0){
                String label=String.valueOf(MC.player.experienceLevel);int w=Math.max(16,MC.fontRenderer.getStringWidth(label)+8),cx=x+b.w/2;
                SkinDraw.rounded(cx-w/2,y-14,w,10,2,0xF21B2629);
                SkinDraw.outline(cx-w/2+.5,y-13.5,w-1,9,2,.5,0xB59B855F);
                MC.fontRenderer.drawStringWithShadow(label,cx-MC.fontRenderer.getStringWidth(label)/2,y-13,0xFFE8D5AB);
            }
        }
        GlStateManager.color(1,1,1,1);
    }
    private static void cap(int x,int y,boolean selected,double route){
        SkinDraw.rounded(x,y,15,32,2,0xFF283235);
        SkinDraw.line(x+1.5,y+3,x+1.5,y+29,.8,0xFFC5C3AA);
        SkinDraw.line(x+12.5,y+3,x+12.5,y+29,.7,0xFF101A20);
        for(int yy:new int[]{y+3,y+28}){
            SkinDraw.rounded(x+1,yy-1,13,3,1,0xFFAEAB92);
            SkinDraw.line(x+3,yy-.2,x+11,yy-.2,.5,0xFFEBDEC0);
        }
        for(int yy=7;yy<26;yy+=4){
            SkinDraw.line(x+3,y+yy,x+11,y+yy+2,1.2,0xFF713D36);
            SkinDraw.line(x+3,y+yy-.4,x+11,y+yy+1.6,.45,0xFFC17A5B);
        }
        SkinDraw.rounded(x+3,y+11,9,9,1,0xFF1A2529);
        ScrollChrome.seal(x+7.5,y+15.5,2.7,selected?0xFFE5CCA0:0xFFAFA084);
        SkinDraw.disc(x+7.5,y+25.5,.7,route>.5?0xFF8DBCB6:0xFFB89463);
    }
    private static void key(KeyBinding binding,double cx,double y,int color,int max){
        String label=GameSettings.getKeyDisplayString(binding.getKeyCode());
        if(binding.getKeyCode()==0)label="-";
        float scale=.55F;label=MC.fontRenderer.trimStringToWidth(label,(int)(max/scale));
        GlStateManager.pushMatrix();GlStateManager.translate(cx,y,0);GlStateManager.scale(scale,scale,1);
        MC.fontRenderer.drawString(label,-MC.fontRenderer.getStringWidth(label)/2,0,color);GlStateManager.popMatrix();
    }
    private static void item(ItemStack stack,int x,int y){
        if(stack.isEmpty())return;GlStateManager.color(1,1,1,1);
        MC.getRenderItem().renderItemAndEffectIntoGUI(stack,x,y);
        MC.getRenderItem().renderItemOverlays(MC.fontRenderer,stack,x,y);
    }
}
