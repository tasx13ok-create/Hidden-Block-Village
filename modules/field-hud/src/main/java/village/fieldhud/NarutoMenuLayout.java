package village.fieldhud;

/** Pure, responsive settings geometry; never changes native inventory slot geometry. */
public final class NarutoMenuLayout {
    public final Layout.Box panel,help;
    public final boolean usable;
    private final int column,rowHeight;
    public NarutoMenuLayout(int width,int height){
        usable=width>=300&&height>=230;
        int w=Math.min(660,Math.max(1,width-16)),h=Math.min(340,Math.max(1,height-16));
        panel=new Layout.Box((width-w)/2,(height-h)/2,w,h);
        column=Math.max(1,(w-28)/2);rowHeight=Math.max(16,Math.min(42,(h-136)/4));
        help=new Layout.Box(panel.x+10,panel.y+h-46,Math.max(1,w-20),16);
    }
    public Layout.Box tab(int i){int w=(panel.w-24)/3;return new Layout.Box(panel.x+8+(i%3)*(w+4),panel.y+35+(i/3)*20,w,17);}
    public Layout.Box row(int i){return new Layout.Box(panel.x+10+(i%2)*(column+8),panel.y+80+(i/2)*rowHeight,column,rowHeight-3);}
    public Layout.Box value(int i){Layout.Box r=row(i);int w=Math.min(112,Math.max(48,r.w/2));return new Layout.Box(r.x+r.w-w,r.y+2,w,Math.max(14,r.h-4));}
    public Layout.Box footer(int i){int w=Math.max(1,(panel.w-34)/4);return new Layout.Box(panel.x+8+i*(w+6),panel.y+panel.h-25,w,18);}
    public static int nextFocus(int current,int direction,int count){if(count<=0)return -1;return current<0?(direction<0?count-1:0):Math.floorMod(current+(direction<0?-1:1),count);}
}
