package village.fieldhud;

/** Saved visual identity only. Ownership, costs and timers always come from a fresh snapshot. */
public final class JutsuSlot {
    public final String id,title,iconItemId,kind,school;
    public final int iconMeta;
    private final JutsuEntry unavailable;
    public JutsuSlot(String id,String title,String iconItemId,int iconMeta,String kind,String school) {
        if(id==null)throw new IllegalArgumentException("Missing slot ID");
        if(id.isEmpty()){this.id="";this.title="Empty";this.iconItemId="";this.iconMeta=0;this.kind="art";this.school="";unavailable=null;return;}
        JutsuEntry safe=new JutsuEntry(id,title,iconItemId,iconMeta,kind,school,0,"",0,0,0,0,0,0,false,"Not carried or unavailable");
        if(!kind.matches("art|order|technique|projectile|form|summon|weapon"))throw new IllegalArgumentException("Invalid saved slot kind");
        this.id=safe.id;this.title=safe.name;this.iconItemId=safe.iconItemId;this.iconMeta=safe.iconMeta;this.kind=safe.kind;this.school=safe.school;
        unavailable=safe;
    }
    public static JutsuSlot empty(){return new JutsuSlot("","Empty","",0,"art","");}
    public static JutsuSlot from(JutsuEntry entry){return new JutsuSlot(entry.id,entry.name,entry.iconItemId,entry.iconMeta,entry.kind,entry.school);}
    public boolean emptySlot(){return id.isEmpty();}
    public JutsuEntry unavailable(){return unavailable;}
}
