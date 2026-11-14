package village.fieldhud;

public final class NarutoMenuChecks {
    private static int assertions;
    private static void check(boolean ok,String text){assertions++;if(!ok)throw new AssertionError(text);}
    private static boolean contains(Layout.Box a,Layout.Box b){return b.x>=a.x&&b.y>=a.y&&b.x+b.w<=a.x+a.w&&b.y+b.h<=a.y+a.h;}
    private static boolean overlap(Layout.Box a,Layout.Box b){return a.x<b.x+b.w&&a.x+a.w>b.x&&a.y<b.y+b.h&&a.y+a.h>b.y;}
    public static void main(String[] args){
        for(int width:new int[]{320,426,640,854,1280,1920})for(int height:new int[]{240,270,360,480,720,1080}){
            NarutoMenuLayout m=new NarutoMenuLayout(width,height);check(m.usable,"normal Minecraft GUI rejected");
            Layout.Box screen=new Layout.Box(0,0,width,height);check(contains(screen,m.panel),"menu leaves screen");
            for(int i=0;i<8;i++){
                Layout.Box row=m.row(i),value=m.value(i);check(contains(m.panel,row)&&contains(row,value),"setting row/value outside panel");check(value.h>=14&&value.w>=48,"setting target too small");check(!overlap(row,m.help),"settings overwrite helper text");
                for(int j=0;j<i;j++)check(!overlap(row,m.row(j)),"two settings overlap");
                for(int j=0;j<6;j++)check(!overlap(row,m.tab(j)),"row overlaps tab");
            }
            for(int i=0;i<6;i++){check(contains(m.panel,m.tab(i)),"six-tab navigation outside panel");for(int j=0;j<i;j++)check(!overlap(m.tab(i),m.tab(j)),"navigation tabs overlap");}
            for(int i=0;i<4;i++){check(contains(m.panel,m.footer(i))&&!overlap(m.help,m.footer(i)),"footer overlaps help/leaves panel");for(int j=0;j<i;j++)check(!overlap(m.footer(i),m.footer(j)),"footer buttons overlap");}
            // Native panel can move when the recipe book opens: follow its real left edge.
            for(int left:new int[]{(width-176)/2,Math.max(0,(width-176)/2+77)}){
                InventoryThemeLayout inv=new InventoryThemeLayout(width,left,(height-166)/2,176);
                if(left+176<=width){check(inv.header!=null&&inv.settings!=null,"normal native panel lost its settings affordance");check(inv.settings.x+inv.settings.w==left+176,"settings failed to follow shifted native panel");check(inv.header.y+inv.header.h<(height-166)/2,"settings cover native armor/crafting slots");check(contains(screen,inv.header),"inventory header exceeds screen");}
                else check(inv.header==null,"out-of-window native panel added more overlap");
            }
        }
        check(!new NarutoMenuLayout(240,180).usable,"tiny screen did not request safe fallback");
        check(new InventoryThemeLayout(320,72,10,176).header==null,"short headroom overwrote native top slots");
        check(new InventoryThemeLayout(320,-5,37,176).header==null,"negative native panel accepted");
        check(NarutoMenuLayout.nextFocus(-1,1,3)==0&&NarutoMenuLayout.nextFocus(-1,-1,3)==2,"keyboard initial focus incorrect");
        check(NarutoMenuLayout.nextFocus(2,1,3)==0&&NarutoMenuLayout.nextFocus(0,-1,3)==2,"keyboard focus did not wrap");
        check(NarutoMenuLayout.nextFocus(0,1,0)==-1,"empty keyboard navigation did not fail safely");
        check(TrainingGrade.forLevel(0).equals("Awaiting record"),"unknown progression invented a rank");
        check(!TrainingGrade.forLevel(4).equals(TrainingGrade.forLevel(5))&&!TrainingGrade.forLevel(11).equals(TrainingGrade.forLevel(12))&&!TrainingGrade.forLevel(23).equals(TrainingGrade.forLevel(24))&&!TrainingGrade.forLevel(39).equals(TrainingGrade.forLevel(40)),"training thresholds not represented");
        for(int level=0;level<=100;level++)check(!TrainingGrade.forLevel(level).toLowerCase(java.util.Locale.ROOT).contains("kage"),"XP assigned political office");
        RebindGesture capture=new RebindGesture();capture.begin(28);check(!capture.ready(true)&&!capture.ready(true),"held Enter rebinds itself after keyboard activation");check(capture.ready(false)&&capture.ready(true),"release did not arm a fresh deliberate binding");capture.begin(-100);check(!capture.ready(true)&&capture.ready(false),"mouse opening click leaks into rebind");capture.reset();check(!capture.ready(true),"closed screen retained an armed capture");capture.begin(0);check(capture.ready(false),"programmatic action requires a nonexistent release");
        System.out.println("NARUTO_MENU_LAYOUT_PASS: "+assertions);
    }
}
