package village.fieldhud;

/** Original ink, lacquer, worn metal and cord motifs, drawn at the current GUI resolution. */
public final class ScrollChrome {
    public static final int PAPER=0xFFE1D1AF,INK=0xFF1B2428,RED=0xFFB26155,BRASS=0xFFC0A779;
    private ScrollChrome(){}
    public static void panel(Layout.Box b,int opacity){
        int a=Math.max(0,Math.min(255,opacity));
        SkinDraw.rounded(b.x,b.y+1,b.w,b.h-1,4,SkinDraw.alpha(0x11191D,a));
        SkinDraw.outline(b.x+.5,b.y+.5,b.w-1,b.h-1,4,.7,SkinDraw.alpha(0xA48E6B,a*3/4));
        SkinDraw.line(b.x+7,b.y+2,b.x+b.w-7,b.y+2,.8,SkinDraw.alpha(0xE1D1AF,a*2/3));
        SkinDraw.line(b.x+3,b.y+8,b.x+3,b.y+b.h-8,.6,SkinDraw.alpha(0xB26155,a/2));
        SkinDraw.line(b.x+b.w-3,b.y+8,b.x+b.w-3,b.y+b.h-8,.6,SkinDraw.alpha(0xB26155,a/2));
        for(int xx:new int[]{b.x+5,b.x+b.w-5})for(int yy:new int[]{b.y+5,b.y+b.h-5}){
            SkinDraw.disc(xx,yy,1.2,SkinDraw.alpha(0x272D2D,a));SkinDraw.disc(xx-.2,yy-.3,.55,SkinDraw.alpha(0xC0A779,a));
        }
    }
    public static void seal(double x,double y,double r,int color){
        // An angular four-direction maker's seal; never covers an item or implies a power.
        SkinDraw.outline(x-r,y-r,r*2,r*2,1,.65,color);
        SkinDraw.line(x-r*.6,y,x,y-r*.6,.7,color);SkinDraw.line(x,y-r*.6,x+r*.6,y,.7,color);
        SkinDraw.line(x+r*.6,y,x,y+r*.6,.7,color);SkinDraw.line(x,y+r*.6,x-r*.6,y,.7,color);
    }
    public static void corners(double x,double y,double w,double h,int color){
        for(int side=0;side<2;side++)for(int edge=0;edge<2;edge++){
            double xx=x+side*w,yy=y+edge*h,dx=side==0?1:-1,dy=edge==0?1:-1;
            SkinDraw.line(xx,yy,xx+dx*3,yy,.85,color);SkinDraw.line(xx,yy,xx,yy+dy*3,.85,color);
        }
    }
}
