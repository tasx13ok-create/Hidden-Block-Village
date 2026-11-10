package village.controls;

/** Independently authored, renderer-free board and scroll geometry. */
public final class MenuLayout {
    public static final class Box {
        public final int x,y,w,h;
        public Box(int x,int y,int w,int h){this.x=x;this.y=y;this.w=Math.max(0,w);this.h=Math.max(0,h);}
        public int right(){return x+w;}public int bottom(){return y+h;}
        public boolean contains(int px,int py){return px>=x&&px<right()&&py>=y&&py<bottom();}
        public boolean overlaps(Box b){return x<b.right()&&right()>b.x&&y<b.bottom()&&bottom()>b.y;}
    }
    public final Box board,header,tabs,nav,content,help,footer;
    public final boolean compact,sideHelp,simple;
    public final int gap=8,pad=10;
    public MenuLayout(int width,int height,boolean controls,boolean navigation){
        this(width,height,controls,navigation,466);
    }
    public MenuLayout(int width,int height,boolean controls,boolean navigation,int preferredHeight){
        simple=false;
        int w=Math.min(width,Math.max(280,Math.min(780,width-16))),h=Math.min(height,Math.max(208,Math.min(preferredHeight,height-16)));
        compact=h<310;board=new Box((width-w)/2,(height-h)/2,w,h);
        int head=compact?30:36,tab=controls?20:0;
        header=new Box(board.x+pad,board.y+5,w-pad*2,head-7);
        tabs=new Box(board.x+pad,board.y+head,w-pad*2,tab);
        footer=new Box(board.x+pad,board.bottom()-32,w-pad*2,25);
        int top=board.y+head+tab+6,bottom=footer.y-8,nw=navigation?(w<440?80:98):0;
        nav=new Box(board.x+pad,top,nw,Math.max(1,bottom-top));
        int cx=board.x+pad+(navigation?nw+gap:0),available=board.right()-pad-cx;sideHelp=available>=560;
        if(sideHelp){int hw=Math.min(178,available/3);help=new Box(board.right()-pad-hw,top,hw,bottom-top);content=new Box(cx,top,help.x-gap-cx,bottom-top);}
        else{int hh=compact?22:44;help=new Box(cx,bottom-hh,available,hh);content=new Box(cx,top,available,Math.max(1,help.y-6-top));}
    }
    /** One stable navigation row and a full-width workspace; help expands only on demand. */
    public static MenuLayout simple(int width,int height){return new MenuLayout(width,height);}
    private MenuLayout(int width,int height){
        simple=true;int w=Math.min(width,Math.max(280,Math.min(780,width-16))),h=Math.min(height,Math.max(208,Math.min(466,height-16)));
        compact=h<310;sideHelp=false;board=new Box((width-w)/2,(height-h)/2,w,h);
        int head=compact?30:36;
        header=new Box(board.x+pad,board.y+5,w-pad*2,head-7);
        tabs=new Box(board.x+pad,board.y+head+3,w-pad*2,20);
        footer=new Box(board.x+pad,board.bottom()-32,w-pad*2,25);
        int top=tabs.bottom()+8,bottom=footer.y-8;
        help=new Box(board.x+pad,bottom-22,w-pad*2,22);
        content=new Box(board.x+pad,top,w-pad*2,Math.max(1,help.y-6-top));
        nav=new Box(content.x,content.y,0,content.h);
    }
    public Box valueCell(int y,int rowHeight){int width=Math.min(148,Math.max(76,content.w*42/100));return new Box(content.right()-width-8,y+3,width,rowHeight-6);}
    public int labelWidth(){return Math.max(24,valueCell(content.y,26).x-content.x-18);}
    public Box modal(int preferredHeight){int w=Math.min(500,board.w-12),h=Math.min(preferredHeight,board.h-12);return new Box(board.x+(board.w-w)/2,board.y+(board.h-h)/2,w,h);}
    public int mouseRowHeight(){return Math.min(34,Math.max(20,(content.h-25)/4));}
    public Box[] presets(){Box area=simple?new Box(content.x,content.y+28,content.w,content.h-28):content;boolean columns=area.w>=400;int reserve=simple?0:24,gap=6,w=columns?(area.w-gap*2)/3:area.w,h=columns?area.h-reserve:Math.max(18,(area.h-reserve-gap*2)/3);Box[] out=new Box[3];for(int i=0;i<3;i++)out[i]=new Box(columns?area.x+i*(w+gap):area.x,columns?area.y:area.y+i*(h+gap),w,h);return out;}
    public Box presetAction(Box card){boolean columns=content.w>=400;return new Box(columns?card.x+8:card.right()-64,columns?card.bottom()-27:card.y+(simple?1:4),columns?card.w-16:60,columns?20:card.h-(simple?2:8));}
    public static int clampOffset(int offset,int total,int capacity){return Math.max(0,Math.min(Math.max(0,total-Math.max(1,capacity)),offset));}
    public static Box thumb(Box track,int total,int capacity,int offset){int length=total<=capacity?track.h:Math.max(12,track.h*Math.max(1,capacity)/Math.max(1,total));length=Math.min(track.h,length);return new Box(track.x,track.y+(track.h-length)*clampOffset(offset,total,capacity)/Math.max(1,total-capacity),track.w,length);}
    public static int offsetAt(Box track,int total,int capacity,int mouseY){Box thumb=thumb(track,total,capacity,0);int span=track.h-thumb.h;if(span<=0)return 0;double ratio=Math.max(0,Math.min(1,(mouseY-track.y-thumb.h/2d)/span));return clampOffset((int)Math.round(ratio*Math.max(0,total-capacity)),total,capacity);}
}
