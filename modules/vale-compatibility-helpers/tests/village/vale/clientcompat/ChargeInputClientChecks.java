package village.vale.clientcompat;
import net.minecraft.client.Minecraft;import net.minecraft.client.settings.KeyBinding;import org.lwjgl.input.Keyboard;
/** Actual client helper source against isolated API stubs, not an executed Minecraft endpoint. */
public final class ChargeInputClientChecks{
 private static int checks;private static void check(boolean v,String message){checks++;if(!v)throw new AssertionError(message);}
 private static Minecraft context(){Minecraft mc=new Minecraft();mc.world=new Object();mc.player=new Minecraft.Player();mc.player.world=mc.world;mc.connection=new Object();Minecraft.INSTANCE=mc;return mc;}
 public static void main(String[]args){
  Minecraft mc=context();KeyBinding key=new KeyBinding(46);check(ChargeInputClient.allow(key),"actual helper source accepts first matching live-world press");
  Keyboard.event=17;check(!ChargeInputClient.allow(key),"other key while charging ignored");Keyboard.event=46;Keyboard.repeat=true;check(!ChargeInputClient.allow(key),"repeat ignored");Keyboard.repeat=false;
  Keyboard.down=false;check(!ChargeInputClient.allow(key),"release does not replay native toggle");Keyboard.down=true;
  mc.currentScreen=new Object();check(!ChargeInputClient.allow(key),"inventory/chat/settings focus blocks action");mc.currentScreen=null;
  mc.connection=null;check(!ChargeInputClient.allow(key),"disconnect blocks input");mc=context();mc.player.world=new Object();check(!ChargeInputClient.allow(key),"stale body/world combination blocked");
  mc=context();mc.player=null;check(!ChargeInputClient.allow(key),"no player blocked");mc=context();mc.world=null;check(!ChargeInputClient.allow(key),"no world blocked");
  mc=context();key.code=47;Keyboard.event=47;check(ChargeInputClient.allow(key),"current rebound key works in replacement context");Keyboard.event=46;check(!ChargeInputClient.allow(key),"old key has no saved latch");
  check(!ChargeInputClient.allow(null)&&!ChargeInputClient.allow(new Object()),"missing/unsupported binding blocked");
  Keyboard.created=false;Keyboard.reads=0;check(!ChargeInputClient.allow(key)&&Keyboard.reads==0,"uninitialized keyboard is never queried");
  Keyboard.created=true;Minecraft.INSTANCE=null;check(!ChargeInputClient.allow(key),"missing client context blocked");
  context();key.code=46;check(ChargeInputClient.allow(key),"fresh physical press after lifecycle changes is not stuck");
  System.out.println("CHARGE_INPUT_CLIENT_SOURCE_CHECKS_PASS assertions="+checks+" real_client_endpoint=false");
 }
}
