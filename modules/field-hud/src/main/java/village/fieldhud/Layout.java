package village.fieldhud;

/** All positions use Minecraft scaled GUI pixels, never framebuffer pixels. */
public final class Layout {
    public static final class Box {
        public final int x,y,w,h;
        public Box(int x,int y,int w,int h) { this.x=x; this.y=y; this.w=w; this.h=h; }
        public boolean overlaps(Box b) { return x<b.x+b.w && x+w>b.x && y<b.y+b.h && y+h>b.y; }
    }
    public final Box location,target,warning,arrival;
    public Layout(int w,int h,boolean scoreboard,int margin) {
        int m=Math.max(4,Math.min(24,margin));
        // Small GUI widths collapse the location card, leaving Xaero's corner free.
        location=new Box(m,m,Math.min(174,Math.max(96,w-156-m*2)),30);
        boolean compact=h<340;
        int freeLeft=Math.max(96,w-2*m-156);
        int tw=Math.min(160,compact?freeLeft:w-2*m);
        target=new Box(scoreboard||h<340?m:w-m-tw,Math.max(68,h-112),tw,36);
        warning=new Box(m,Math.max(40,h-148),Math.min(150,h-148<204?freeLeft:w-2*m),23);
        Box center=new Box((w-Math.min(256,w-2*m))/2,Math.max(40,h/3-16),Math.min(256,w-2*m),40);
        Box map=new Box(w-m-148,44,148,160);
        arrival=compact||center.overlaps(map)?new Box(m,42,Math.min(256,freeLeft),40):center;
    }
}
