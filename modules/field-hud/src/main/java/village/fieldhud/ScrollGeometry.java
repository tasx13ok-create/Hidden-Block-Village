package village.fieldhud;

/** One coordinate source for the visible scroll, item overlays and reserved HUD bounds. */
public final class ScrollGeometry {
    public static final int WIDTH=224,HEIGHT=32,STRIDE=20;
    private ScrollGeometry(){}
    public static int slotX(Layout.Box bar,int slot){return bar.x+22+STRIDE*slot;}
    public static int slotY(Layout.Box bar,int slot,boolean curved){return bar.y+7+SkinMath.slotCurve(slot,curved);}
    public static int itemX(Layout.Box bar,int slot){return slotX(bar,slot)+2;}
    public static int itemY(Layout.Box bar,int slot,boolean curved){return slotY(bar,slot,curved)+3;}
    public static double curve(double slot,boolean curved){
        if(!curved)return 0;
        double s=Math.max(0,Math.min(8,Double.isFinite(slot)?slot:0));int a=(int)Math.floor(s),b=Math.min(8,a+1);
        return SkinMath.slotCurve(a,true)+(SkinMath.slotCurve(b,true)-SkinMath.slotCurve(a,true))*(s-a);
    }
}
