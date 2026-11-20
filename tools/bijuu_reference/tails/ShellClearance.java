package village.bijuu;

/** Clearance in model pixels around the original UV-mapped cuboid. */
public final class ShellClearance {
    public static float amount(int width,int height) {
        if(width==8 && height==8)return .68f;
        if(width==8 && height==12)return .44f;
        if(width==4 && height==12)return .68f;
        if(width==4 && height==7)return .42f;
        if(width==4 && height==5)return .52f;
        return .16f;
    }
}
