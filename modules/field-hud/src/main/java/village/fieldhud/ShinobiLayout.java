package village.fieldhud;

public final class ShinobiLayout {
    public final Layout.Box status,hotbar,leftHand,rightHand;
    public final boolean compact;
    public final boolean statusConflict;
    public ShinobiLayout(int width,int height,int margin) {
        this(width,height,margin,false,-1,-1,false);
    }
    public ShinobiLayout(int width,int height,int margin,boolean chakra,int positionX,int positionY,boolean slim) {
        this(width,height,margin,chakra?1:0,positionX,positionY,slim);
    }
    public ShinobiLayout(int width,int height,int margin,int resourceRows,int positionX,int positionY,boolean slim) {
        this(width,height,margin,resourceRows,positionX,positionY,slim,new Layout.Box[0]);
    }
    public ShinobiLayout(int width,int height,int margin,int resourceRows,int positionX,int positionY,boolean slim,Layout.Box... reserved) {
        int m=Math.max(4,Math.min(24,margin));
        compact=height<340;
        int sw=Math.min(slim?196:238,Math.max(96,width-2*m-156)),sh=84+18*Math.max(0,Math.min(2,resourceRows));
        int x=positionX<0?m:HudPlacement.pixels(positionX,m,width-sw-m);
        int y=positionY<0?m:HudPlacement.pixels(positionY,m,height-74-sh);
        Layout.Box preferred=new Layout.Box(x,y,sw,sh),placed=preferred;
        if(reserved.length>0) {
            placed=HudPlacement.place(preferred,width,height,m,reserved);
            if(placed==null)placed=HudPlacement.place(preferred,width,height,4,reserved);
        }
        status=placed==null?preferred:placed;
        statusConflict=reserved.length>0&&!HudPlacement.clear(status,width,height,4,reserved);
        hotbar=new Layout.Box(width/2-ScrollGeometry.WIDTH/2,height-36,ScrollGeometry.WIDTH,ScrollGeometry.HEIGHT);
        leftHand=new Layout.Box(hotbar.x-28,hotbar.y+3,24,28);
        rightHand=new Layout.Box(hotbar.x+hotbar.w+4,hotbar.y+3,24,28);
    }
}
