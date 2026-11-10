package village.fieldhud;

/** One contextual card: tracked contract in Explore, selected art in Combat. */
public final class ValeHud {
    private ValeHud() {}
    public static Layout.Box bounds(int w,int h,Layout.Box status,Layout.Box map,Layout.Box scores,Layout.Box... reserved) {
        ValeSnapshotReader.State s=ValeBridge.current();
        if(s==null||status==null||HudConfig.mode==2)return null;
        if(HudConfig.mode==0&&(s.contractId.isEmpty()||s.contractGoal<=0))return null;
        if(HudConfig.mode==1&&s.artId.isEmpty())return null;
        return HudPlacement.place(new Layout.Box(status.x,status.y+status.h+6,Math.min(196,status.w),30),w,h,HudConfig.margin,ArmorReservations.merge(reserved,status,map,scores));
    }
    private static String display(String name,String id) { return name.isEmpty()?id:name; }
    public static void card(Layout.Box b) {
        ValeSnapshotReader.State s=ValeBridge.current();if(s==null)return;
        String title,detail;
        if(HudConfig.mode==0) {
            title=display(s.contractName,s.contractId);
            detail=s.contractProgress+"/"+s.contractGoal+"  Merit "+ShinobiHud.number(s.merit);
        } else {
            title=display(s.artName,s.artId);
            detail=s.cooldownRemaining>0?String.format(java.util.Locale.ROOT,"Cooldown %.1fs",s.cooldownRemaining/20.0):"Ready";
            if(!s.formId.isEmpty())detail+="  "+display(s.formName,s.formId);
            else if(!s.companionId.isEmpty())detail+="  "+display(s.companionName,s.companionId);
            else detail+="  Merit "+ShinobiHud.number(s.merit);
        }
        SealArt.Mark mark=HudConfig.mode==0?SealArt.Mark.SCROLL:SealArt.forArt(s.artId);
        PanelSkin.emblem(b,title,detail,0x80BDD6,255,mark);
    }
}
