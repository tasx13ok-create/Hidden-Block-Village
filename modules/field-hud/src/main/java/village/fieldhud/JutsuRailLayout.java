package village.fieldhud;

/** Six square icon slots below the real minimap; no label/header/footer space. */
public final class JutsuRailLayout {
    public final Layout.Box box;
    public final int visible,slotHeight,header,footer;
    public final boolean compact;
    public JutsuRailLayout(int width,int height,int margin,int count,Layout.Box map,Layout.Box... reserved) {
        int m=Math.max(4,Math.min(24,margin));
        int y=map==null?Math.min(116,Math.max(m,height/3)):map.y+map.h+6;
        int chosenRow=18;Layout.Box fit=null;
        for(int row:new int[]{28,26,24,22,20,18}){
            int middle=map==null?width-m-row:map.x+(map.w-row)/2;
            int right=map==null?width-m-row:map.x+map.w-row;
            int left=map==null?width-m-row:map.x;
            for(int preferred:new int[]{middle,right,left,width-m-row,m}){
                int x=Math.max(m,Math.min(width-m-row,preferred));
                Layout.Box candidate=new Layout.Box(x,y,row,JutsuLoadout.SIZE*row);
                if(clear(candidate,width,height,m,reserved)){fit=candidate;chosenRow=row;break;}
            }
            if(fit!=null)break;
        }
        visible=fit==null?0:JutsuLoadout.SIZE;box=fit;slotHeight=chosenRow;header=footer=0;compact=slotHeight<=22;
    }
    public int slotY(int row){return box.y+header+row*slotHeight;}
    public static boolean clear(Layout.Box box,int width,int height,int margin,Layout.Box... reserved) {
        if(box.x<margin||box.y<margin||box.x+box.w>width-margin||box.y+box.h>height-margin)return false;
        for(Layout.Box obstacle:reserved)if(obstacle!=null&&box.overlaps(obstacle))return false;
        return true;
    }
}
