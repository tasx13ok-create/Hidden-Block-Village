package village.fieldhud;
/** Pixel-aligned settings geometry, independent of drawing and live preferences. */
public final class HudMenuLayout {
    public final Layout.Box panel,help;
    public final int rowHeight,columnWidth,valueWidth;
    public HudMenuLayout(int width,int height){
        int w=Math.min(620,Math.max(1,width-16)),h=Math.min(300,Math.max(1,height-16));
        panel=new Layout.Box((width-w)/2,(height-h)/2,w,h);
        rowHeight=Math.max(14,Math.min(24,(h-94)/8));
        columnWidth=(w-24)/2;valueWidth=Math.max(48,Math.min(90,columnWidth/3));
        help=new Layout.Box(panel.x+10,panel.y+h-60,w-20,26);
    }
    public Layout.Box cell(int index){return new Layout.Box(panel.x+8+(index%2)*(columnWidth+8),panel.y+34+(index/2)*rowHeight,columnWidth,rowHeight-2);}
    public Layout.Box value(int index){Layout.Box b=cell(index);return new Layout.Box(b.x+b.w-valueWidth,b.y,valueWidth,b.h);}
    public Layout.Box footer(int index){int w=(panel.w-40)/4;return new Layout.Box(panel.x+8+index*(w+8),panel.y+panel.h-28,w,20);}
}
