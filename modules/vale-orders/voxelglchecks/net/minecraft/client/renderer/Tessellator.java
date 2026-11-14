package net.minecraft.client.renderer;

import java.util.ArrayList;
import java.util.List;

public final class Tessellator {
    private static final Tessellator ONE=new Tessellator();
    private final BufferBuilder buffer=new BufferBuilder();
    public boolean failDraw;
    public int drawCalls;
    public final List<Batch> batches=new ArrayList<>();
    public static final class Batch {
        public final int mode; public final List<BufferBuilder.Vertex> vertices;
        Batch(BufferBuilder b) { mode=b.mode; vertices=new ArrayList<>(b.vertices); }
    }
    public static Tessellator getInstance() { return ONE; }
    public BufferBuilder getBuffer() { return buffer; }
    public void draw() {
        if (!buffer.active) throw new AssertionError("draw without active buffer");
        drawCalls++; batches.add(new Batch(buffer)); buffer.active=false;
        if (failDraw) throw new IllegalStateException("synthetic tessellation draw");
    }
    public int vertexCount() { int n=0; for (Batch b:batches) n+=b.vertices.size(); return n; }
    public void reset() { failDraw=false; drawCalls=0; batches.clear(); buffer.reset(); }
}
