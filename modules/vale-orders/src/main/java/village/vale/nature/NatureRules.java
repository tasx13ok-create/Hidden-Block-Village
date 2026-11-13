package village.vale.nature;

/** Small deterministic geometry/lease policy, independent of a running Minecraft world. */
public final class NatureRules {
    public static final int COVER_LIFE=100, MAX_OWNER_BLOCKS=6, MAX_WORLD_BLOCKS=768;
    private NatureRules(){}
    public static boolean cone(double dx,double dy,double dz,double lx,double ly,double lz,double range,double cosine){
        double distance=dx*dx+dy*dy+dz*dz,look=lx*lx+ly*ly+lz*lz;
        if(!Double.isFinite(distance)||!Double.isFinite(look)||distance<.0001||look<.0001||distance>range*range)return false;
        return (dx*lx+dy*ly+dz*lz)/Math.sqrt(distance*look)>=cosine;
    }
    /** Three columns, two rows; the center sits two blocks in front of the feet. */
    public static int[][] cover(int x,int y,int z,int fx,int fz){
        if(Math.abs(fx)+Math.abs(fz)!=1)throw new IllegalArgumentException("Horizontal cardinal direction required");
        int[][] result=new int[6][3];int n=0;
        for(int lateral=-1;lateral<=1;lateral++)for(int height=0;height<2;height++)
            result[n++]=new int[]{x+fx*2-fz*lateral,y+height,z+fz*2+fx*lateral};
        return result;
    }
    public static boolean mayRestore(boolean loaded,boolean tile,int current,int expected){return loaded&&!tile&&current==expected;}
    public static boolean roomFor(int worldBlocks,int ownedBlocks,int wanted){return wanted>0&&wanted<=MAX_OWNER_BLOCKS&&worldBlocks>=0&&ownedBlocks>=0&&worldBlocks+wanted<=MAX_WORLD_BLOCKS&&ownedBlocks+wanted<=MAX_OWNER_BLOCKS;}
}
