package village.fieldhud;

import java.util.ArrayList;
import java.util.List;

/** GUI-pixel placement, independent of client rendering and saved resolution. */
public final class HudPlacement {
    private HudPlacement() {}
    public static int clamp(int n,int low,int high) { return Math.max(low,Math.min(Math.max(low,high),n)); }
    public static int pixels(int normalized,int low,int high) {
        return low+(int)Math.round((Math.max(low,high)-low)*clamp(normalized,0,1000)/1000.0);
    }
    public static int normalized(int pixel,int low,int high) {
        return high<=low?0:clamp((int)Math.round((pixel-low)*1000.0/(high-low)),0,1000);
    }
    public static boolean clear(Layout.Box b,int w,int h,int margin,Layout.Box... obstacles) {
        if(b.x<margin||b.y<margin||b.x+b.w>w-margin||b.y+b.h>h-74)return false;
        for(Layout.Box o:obstacles)if(o!=null&&b.overlaps(o))return false;
        return true;
    }
    public static Layout.Box place(Layout.Box preferred,int w,int h,int margin,Layout.Box... obstacles) {
        if(clear(preferred,w,h,margin,obstacles))return preferred;
        List<Integer> xs=new ArrayList<>(),ys=new ArrayList<>();
        xs.add(preferred.x);xs.add(margin);xs.add(w-margin-preferred.w);
        ys.add(preferred.y);ys.add(margin);ys.add(h-74-preferred.h);
        for(Layout.Box o:obstacles)if(o!=null) {
            xs.add(o.x-preferred.w-6);xs.add(o.x+o.w+6);
            ys.add(o.y-preferred.h-6);ys.add(o.y+o.h+6);
        }
        Layout.Box best=null;long distance=Long.MAX_VALUE;
        for(int x:xs)for(int y:ys) {
            Layout.Box b=new Layout.Box(x,y,preferred.w,preferred.h);
            long dx=(long)x-preferred.x,dy=(long)y-preferred.y,d=dx*dx+dy*dy;
            if(d<distance&&clear(b,w,h,margin,obstacles)) { best=b;distance=d; }
        }
        return best;
    }
}
