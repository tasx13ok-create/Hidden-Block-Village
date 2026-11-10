package village.fieldhud;

import java.util.Arrays;
import java.util.Comparator;
import net.minecraft.client.gui.ScaledResolution;

/** Registration is sorted once; rendering walks a stable array without sorting/allocation. */
public final class HudLayers {
    public interface Component { void render(Frame frame); }
    public static final class Frame {
        public ScaledResolution resolution;
        public ShinobiLayout layout;
        public ChakraBridge.Snapshot chakra;
        public double time;
        public float partial;
    }
    private static final class Entry {
        final int depth;final Component component;
        Entry(int depth,Component component) { this.depth=depth;this.component=component; }
    }
    private Entry[] entries=new Entry[0];
    public void register(int depth,Component component) {
        if(component==null)throw new IllegalArgumentException("Missing HUD component");
        Entry[] next=Arrays.copyOf(entries,entries.length+1);next[next.length-1]=new Entry(depth,component);
        Arrays.sort(next,Comparator.comparingInt(e->e.depth));entries=next;
    }
    public void render(Frame frame) { for(Entry entry:entries)entry.component.render(frame); }
}
