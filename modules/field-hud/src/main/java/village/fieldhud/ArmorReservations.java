package village.fieldhud;

/** Published hvbijuu 0.17 screen-space reservations. No rendering or state writes. */
public final class ArmorReservations {
    private static final Layout.Box[] EMPTY=new Layout.Box[0];
    private ArmorReservations() {}
    public static Layout.Box[] bounds(int w,int h,boolean armor,boolean wings,int forgeHeight) {
        if(!armor&&!wings)return EMPTY;
        Layout.Box[] boxes=new Layout.Box[(armor?1:0)+(wings?1:0)];int i=0;
        // Includes the text shadow and the documented center-91 anchor.
        if(armor)boxes[i++]=new Layout.Box(w/2-93,h-92,214,14);
        if(wings)boxes[i]=new Layout.Box((w-142)/2-2,Math.max(14,h-Math.max(116,forgeHeight+27))-2,146,23);
        return boxes;
    }
    public static Layout.Box[] merge(Layout.Box[] reserved,Layout.Box... own) {
        if(reserved.length==0)return own;
        Layout.Box[] result=new Layout.Box[reserved.length+own.length];
        System.arraycopy(reserved,0,result,0,reserved.length);System.arraycopy(own,0,result,reserved.length,own.length);
        return result;
    }
}
