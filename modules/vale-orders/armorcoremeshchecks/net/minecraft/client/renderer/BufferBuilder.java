package net.minecraft.client.renderer;
/** Synthetic mesh recorder; no native graphics context or production registration. */
public final class BufferBuilder {
 public final java.util.List<double[]> vertices=new java.util.ArrayList<>();public boolean building;
 public void begin(int mode,Object format){if(building)throw new IllegalStateException("already building");building=true;vertices.clear();}
 public void record(double x,double y,double z,int colour,float alpha){if(!building)throw new IllegalStateException("not building");vertices.add(new double[]{x,y,z,colour,alpha});}
}
