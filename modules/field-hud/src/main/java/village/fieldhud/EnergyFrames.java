package village.fieldhud;

/** Eight authored 16-pixel crest frames. All coordinates are scaled GUI pixels. */
public final class EnergyFrames {
    private static final int[][] CREST={
        {0,0,1,1,2,3,2,1,0,0,1,2,1,1,0,0},
        {0,0,0,1,1,2,3,2,1,0,0,1,2,1,1,0},
        {0,0,0,0,1,2,2,3,2,1,0,0,1,2,1,1},
        {1,0,0,0,0,1,2,3,3,2,1,0,0,1,2,1},
        {1,1,0,0,0,0,1,2,2,2,1,0,0,0,1,2},
        {1,1,1,0,0,0,0,1,2,1,1,0,0,0,0,1},
        {0,1,1,1,0,0,0,0,1,1,0,0,0,0,0,0},
        {0,0,1,1,1,0,0,0,0,1,1,0,0,0,0,0}
    };
    private EnergyFrames() {}
    public static int frame(double ticks) { return ((int)Math.floor(ticks/2)%8+8)%8; }
    public static int crest(int frame,int column) { return CREST[(frame%8+8)%8][(column%16+16)%16]; }
    public static int left(int height,int row) { return Math.max(0,height-row-1); }
    public static int right(int width,int row) { return Math.max(0,width-row); }
    public static boolean fragment(int frame,int column,int row) {
        return frame>=4&&row<2&&((column+frame*3)%19==0)&&row==(frame%2);
    }
}
