package net.minecraft.client.renderer;
public final class Tessellator {
 public static final Tessellator INSTANCE=new Tessellator();public final BufferBuilder buffer=new BufferBuilder();public int draws;
 public static Tessellator getInstance(){return INSTANCE;}public BufferBuilder getBuffer(){return buffer;}
 public void draw(){if(!buffer.building)throw new IllegalStateException("not building");buffer.building=false;draws++;}
}
