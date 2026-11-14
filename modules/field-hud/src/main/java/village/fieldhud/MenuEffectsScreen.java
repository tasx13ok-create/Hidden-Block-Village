package village.fieldhud;
import java.io.IOException;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
/** A child of the existing unified Look page; native GUI return and input remain intact. */
public final class MenuEffectsScreen extends GuiScreen{
 private final GuiScreen parent;private int focused;private String notice="";
 private static boolean coreChecked;private static java.lang.reflect.Method coreRead,coreWrite;
 public MenuEffectsScreen(GuiScreen parent){this.parent=parent;}
 @Override public void initGui(){buttonList.clear();int[][] rows=MenuFxRules.options(width,height);for(int i=0;i<rows.length;i++){int id=rows.length==1?3:i;int[] r=rows[i];GuiButton button=new NarutoInk.Button(id,new Layout.Box(r[0],r[1],r[2],r[3]),label(id));if(id==2)button.enabled=coreAvailable();buttonList.add(button);}focused=MenuFxRules.nextFocus(-1,1,enabledButtons());}
 private static String label(int id){
  if(id==0)return "Menu atmosphere: "+(HudConfig.menuEffects?"On":"Off");if(id==1)return "Menu clicks: "+(HudConfig.menuSounds?"On":"Off");
  if(id==2){if(!coreAvailable())return "Armor core: Unavailable";boolean value=coreEnabled();return coreAvailable()?"Armor core glow: "+(value?"On":"Off"):"Armor core: Unavailable";}return "Return to Look";
 }
 private static boolean coreAvailable(){if(!coreChecked){coreChecked=true;try{Class<?> api=Class.forName("village.vale.client.ArmorCoreFx",true,net.minecraftforge.fml.common.Loader.instance().getModClassLoader());coreRead=api.getMethod("enabled");coreWrite=api.getMethod("setEnabled",boolean.class);if(coreRead.getReturnType()!=boolean.class||coreWrite.getReturnType()!=boolean.class||!java.lang.reflect.Modifier.isStatic(coreRead.getModifiers())||!java.lang.reflect.Modifier.isStatic(coreWrite.getModifiers()))coreRead=coreWrite=null;}catch(ReflectiveOperationException|LinkageError|RuntimeException unavailable){coreRead=coreWrite=null;}}return coreRead!=null&&coreWrite!=null;}
 private static boolean coreEnabled(){if(!coreAvailable())return false;try{return Boolean.TRUE.equals(coreRead.invoke(null));}catch(ReflectiveOperationException|LinkageError|RuntimeException unavailable){coreRead=coreWrite=null;return false;}}
 private static boolean coreSet(boolean value){if(!coreAvailable())return false;try{return Boolean.TRUE.equals(coreWrite.invoke(null,value));}catch(ReflectiveOperationException|LinkageError|RuntimeException unavailable){return false;}}
 private boolean[] enabledButtons(){boolean[] enabled=new boolean[buttonList.size()];for(int i=0;i<enabled.length;i++)enabled[i]=buttonList.get(i).visible&&buttonList.get(i).enabled;return enabled;}
 @Override protected void actionPerformed(GuiButton button)throws IOException{
  if(!button.visible||!button.enabled)return;focused=buttonList.indexOf(button);notice="";if(button.id==3){mc.displayGuiScreen(parent);return;}if(button.id==0){HudConfig.menuEffects=!HudConfig.menuEffects;HudConfig.save();}else if(button.id==1){HudConfig.menuSounds=!HudConfig.menuSounds;HudConfig.save();}else if(button.id==2){if(!coreSet(!coreEnabled()))notice="Armor glow could not be saved.";}else return;for(GuiButton b:buttonList){b.displayString=label(b.id);if(b.id==2)b.enabled=coreAvailable();}
 }
 @Override protected void keyTyped(char c,int key)throws IOException{
  if(key==Keyboard.KEY_ESCAPE){mc.displayGuiScreen(parent);return;}if(key==Keyboard.KEY_TAB){focused=MenuFxRules.nextFocus(focused,isShiftKeyDown()?-1:1,enabledButtons());return;}
  if(key==Keyboard.KEY_RETURN||key==Keyboard.KEY_NUMPADENTER||key==Keyboard.KEY_SPACE){if(focused>=0&&focused<buttonList.size()){GuiButton b=buttonList.get(focused);if(b.visible&&b.enabled){b.playPressSound(mc.getSoundHandler());actionPerformed(b);}}return;}super.keyTyped(c,key);
 }
 @Override public void drawScreen(int mx,int my,float partial){drawDefaultBackground();int w=Math.max(1,Math.min(244,width-4)),x=Math.max(0,(width-w)/2),y=Math.max(0,(height-136)/2);NarutoInk.paper(x,y,w,Math.max(1,Math.min(136,height-y)));if(width>=180&&height>=140){fontRenderer.drawString("Visual effects",x+10,y+8,NarutoInk.INK,false);String help=notice.isEmpty()?"Armor glow is optional.":notice;fontRenderer.drawString(fontRenderer.trimStringToWidth(help,Math.max(1,w-20)),x+10,y+20,NarutoInk.MUTED,false);}for(int i=0;i<buttonList.size();i++)((NarutoInk.Button)buttonList.get(i)).focused=i==focused;super.drawScreen(mx,my,partial);}
 @Override public boolean doesGuiPauseGame(){return parent==null||parent.doesGuiPauseGame();}
}
