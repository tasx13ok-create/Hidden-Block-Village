package org.lwjgl.input;
public final class Keyboard{
 public static boolean created=true,down=true,repeat;public static int event=46,reads;
 public static boolean isCreated(){return created;}
 public static int getEventKey(){if(!created)throw new IllegalStateException("No keyboard");reads++;return event;}
 public static boolean getEventKeyState(){reads++;return down;}public static boolean isRepeatEvent(){reads++;return repeat;}
}
