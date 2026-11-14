package net.minecraft.client.renderer;

import java.util.ArrayList;
import java.util.List;

public final class BufferBuilder {
    public boolean failBegin;
    public int failVertex=-1,beginCalls,mode;
    public boolean active;
    public final List<Vertex> vertices=new ArrayList<>();
    private double x,y,z; private int r,g,b,a;
    public static final class Vertex {
        public final double x,y,z; public final int r,g,b,a;
        Vertex(double x,double y,double z,int r,int g,int b,int a) {
            this.x=x; this.y=y; this.z=z; this.r=r; this.g=g; this.b=b; this.a=a;
        }
    }
    public void begin(int mode,Object format) {
        beginCalls++;
        if (failBegin) throw new IllegalStateException("synthetic buffer begin");
        if (active) throw new AssertionError("buffer already active");
        this.mode=mode; vertices.clear(); active=true;
    }
    public BufferBuilder pos(double x,double y,double z) { this.x=x; this.y=y; this.z=z; return this; }
    public BufferBuilder color(int r,int g,int b,int a) { this.r=r; this.g=g; this.b=b; this.a=a; return this; }
    public void endVertex() {
        if (!active) throw new AssertionError("vertex outside batch");
        if (failVertex==vertices.size()) throw new IllegalStateException("synthetic vertex upload");
        if (vertices.size()>=50000) throw new AssertionError("runaway batch exceeds recorded supported geometry");
        vertices.add(new Vertex(x,y,z,r,g,b,a));
    }
    public void reset() { active=false; failBegin=false; failVertex=-1; vertices.clear(); }
}
