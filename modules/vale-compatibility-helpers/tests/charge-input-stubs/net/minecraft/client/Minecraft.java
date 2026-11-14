package net.minecraft.client;
/** Isolated source-level input test fixture; never packaged. */
public final class Minecraft {
 public static Minecraft INSTANCE=new Minecraft();public Object currentScreen,world,connection;public Player player;
 public static Minecraft getMinecraft(){return INSTANCE;}public Object getConnection(){return connection;}
 public static final class Player{public Object world;}
}
