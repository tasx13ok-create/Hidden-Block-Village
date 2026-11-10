package village.vale.armor;

/** Shared half-texel-safe UV allocation inside the new eight-material 256x128 atlas. */
public final class AtlasUv {
    private AtlasUv(){}
    public static int tile(String material){
        if("base".equals(material))return 0;if("shade".equals(material))return 1;
        if("edge".equals(material))return 2;if("ink".equals(material))return 3;
        if("glow".equals(material))return 4;if("core".equals(material))return 5;
        if("fabric".equals(material))return 6;if("sole".equals(material))return 7;
        throw new IllegalArgumentException("Unknown material: "+material);
    }
    public static float[][] face(ArmorMesh.Box box,int face){
        float width=face<2?box.w:face<4?box.d:box.w;
        float height=face<4?box.h:box.d;
        int tile=tile(box.material),ox=tile%4*64,oy=tile/4*64;
        float w=Math.max(1,Math.min(59,width*4)),h=Math.max(1,Math.min(59,height*4));
        boolean rotate="glow".equals(box.material)&&width>height*2;
        float x,y;
        if("glow".equals(box.material)){
            // Always sample the continuous painted central inlay, then orient it along the long edge.
            w=Math.min(5,Math.max(1,Math.min(width,height)*4));h=Math.max(8,Math.min(55,Math.max(width,height)*4));
            x=31.5f-w*.5f;y=31.5f-h*.5f;
        }else if("core".equals(box.material)){w=Math.min(w,38);h=Math.min(h,38);x=31.5f-w/2;y=31.5f-h/2;}
        else{
            int seed=(box.id.hashCode()&0x7fffffff)%1000000+face*31;
            x=2+(seed%17)/16f*(59-w);y=2+((seed/17)%17)/16f*(59-h);
        }
        float u0=(ox+x+.5f)/256,u1=(ox+x+w+.5f)/256,v0=(oy+y+.5f)/128,v1=(oy+y+h+.5f)/128;
        // Face vertex winding is fixed by ArmorMesh; keep all sample centers inside the material tile.
        boolean verticalFirst=face==0||face==3||face==5;
        return verticalFirst!=rotate?new float[][]{{u0,v0},{u0,v1},{u1,v1},{u1,v0}}:
            new float[][]{{u0,v0},{u1,v0},{u1,v1},{u0,v1}};
    }
}
