package village.fieldhud;

/** A use gesture alone is never a successful cast. Requires server item XP and resource expenditure. */
public final class CastReceipt {
    private boolean wasUsing,armed;
    private String key="";
    private int experience;
    private double chakra,until;
    public boolean update(String current,int xp,double resource,boolean using,double time) {
        if(current==null)current="";
        if(using&&(!wasUsing||!key.equals(current))) {
            key=current;experience=xp;chakra=resource;armed=!key.isEmpty()&&xp>=0&&Double.isFinite(resource);
        }
        if(using)until=time+10;
        boolean confirmed=armed&&key.equals(current)&&time<=until&&xp>experience&&Double.isFinite(resource)&&resource<chakra-1e-6;
        if(confirmed||time>until||!key.equals(current))armed=false;
        wasUsing=using;return confirmed;
    }
    public void reset() { wasUsing=armed=false;key=""; }
}
