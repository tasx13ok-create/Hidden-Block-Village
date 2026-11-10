package village.controls;
import village.controls.MenuLayout.Box;

public final class SimpleMenuChecks {
    private static int checks;
    private static void ok(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static void inside(Box outer,Box inner){ok(inner.w>0&&inner.h>0&&inner.x>=outer.x&&inner.y>=outer.y&&inner.right()<=outer.right()&&inner.bottom()<=outer.bottom(),"Control escaped its region");}
    public static class FakeGui{}
    public static class State{}
    public interface GuiPort{void openScreen(FakeGui screen);}
    public static class Counts{static int configs,screens,utilities,opens;}
    public static class Config{public static final Config INSTANCE=new Config();public Config(){Counts.configs++;}public State getGui(){return new State();}}
    public static class Screen extends FakeGui{public Screen(State state){Counts.screens++;}}
    public static class API{public static GuiPort getGuiUtil(){Counts.utilities++;return screen->Counts.opens++;}}
    public static class LegacyConfig{public static final LegacyConfig INSTANCE=new LegacyConfig();public FakeGui getGui(){return new FakeGui();}}
    public static void main(String[] args)throws Exception{
        for(int w=320;w<=1440;w+=16)for(int h:new int[]{240,241,266,300,360,450,540,720,900}){
            MenuLayout l=MenuLayout.simple(w,h);ok(!l.sideHelp&&l.nav.w==0,"Simple mode retained a sidebar");
            for(Box b:new Box[]{l.header,l.tabs,l.content,l.help,l.footer})inside(l.board,b);
            ok(!l.content.overlaps(l.help)&&!l.content.overlaps(l.tabs),"Workspace overlaps navigation/help");
            for(int i=0;i<5;i++){Box b=SimpleNavigation.tab(l,i);inside(l.tabs,b);ok(b.w>=50,"Main navigation too small");if(i>0)ok(!b.overlaps(SimpleNavigation.tab(l,i-1)),"Main tabs overlap");}
            inside(l.header,SimpleNavigation.essential(l));inside(l.content,SimpleNavigation.search(l));inside(l.content,SimpleNavigation.layout(l));
            ok(!SimpleNavigation.search(l).overlaps(SimpleNavigation.layout(l)),"Search/layout overlap");
            ok(!SimpleNavigation.category(l).overlaps(SimpleNavigation.filter(l)),"Category/filter overlap");
            for(Box card:l.presets()){inside(l.content,card);inside(card,l.presetAction(card));ok(l.presetAction(card).h>=14,"Preset action clipped");}
            int top=l.content.y+48,rh=l.compact?22:28;ok(l.content.bottom()-top>=rh,"Keybind list lost its first row");
        }
        ok(SimpleNavigation.groupLabel("Essentials").equals("Basics"),"Core actions confused with Essential mod");
        OptionalGuiRoute route=OptionalGuiRoute.inspect(API.class,Config.class,Screen.class,FakeGui.class);
        ok(Counts.configs==0&&Counts.screens==0&&Counts.utilities==0&&Counts.opens==0,"Discovery executed optional mod code");
        route.open();ok(Counts.configs==1&&Counts.screens==1&&Counts.utilities==1&&Counts.opens==1,"One click did not open exactly one real screen");
        OptionalGuiRoute.inspect(API.class,LegacyConfig.class,null,FakeGui.class).open();ok(Counts.opens==2,"Legacy settings API unavailable");
        boolean rejected=false;try{OptionalGuiRoute.inspect(API.class,Config.class,null,FakeGui.class);}catch(NoSuchMethodException expected){rejected=true;}ok(rejected&&Counts.opens==2,"Unsupported API was executed");
        System.out.println("SIMPLE_MENU_CHECKS_PASS assertions="+checks);
    }
}
