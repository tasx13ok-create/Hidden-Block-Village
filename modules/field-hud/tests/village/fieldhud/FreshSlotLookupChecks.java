package village.fieldhud;

import java.util.*;

/** Headless invocation/coordinate contract fixtures, not a rendered/native GUI acceptance. */
public final class FreshSlotLookupChecks {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static final class Cell {final int x,y,id;boolean enabled=true;Cell(int x,int y,int id){this.x=x;this.y=y;this.id=id;}}
    private static class NativeShape {
        int left=100,top=50;final List<Cell> cells=new ArrayList<>();Cell cachedHover;
        protected boolean region(int x,int y,int w,int h,int mx,int my){int rx=mx-left,ry=my-top;return rx>=x-1&&rx<x+w+1&&ry>=y-1&&ry<y+h+1;}
        // Contract observed in prepared MCP: first enabled native slot, through virtual region gate.
        private Cell getSlotAtPosition(int x,int y){for(Cell c:cells)if(region(c.x,c.y,16,16,x,y)&&c.enabled)return c;return null;}
        public Cell getSlotUnderMouse(){return cachedHover;}
    }
    private static final class HiddenRecipe extends NativeShape {boolean hidden;@Override protected boolean region(int x,int y,int w,int h,int mx,int my){return !hidden&&super.region(x,y,w,h,mx,my);}}
    private static final class SrgOnly {private String func_146975_c(int x,int y){return x+":"+y;}}
    private static final class Throws extends NativeShape {@Override protected boolean region(int x,int y,int w,int h,int mx,int my){throw new IllegalStateException("fixture input gate");}}
    public static void main(String[]args){
        FreshSlotLookup bridge=new FreshSlotLookup(NativeShape.class,"getSlotAtPosition","func_146975_c");check(bridge.available(),"MCP private method unresolved");
        NativeShape gui=new NativeShape();Cell a=new Cell(8,84,0),b=new Cell(26,84,1);gui.cells.add(a);gui.cells.add(b);gui.cachedHover=a;
        check(gui.getSlotUnderMouse()==a&&bridge.at(gui,126,134)==b,"move+release used last-drawn hover instead of current event coordinates");
        check(bridge.at(gui,107,133)==a,"native -1 left/top fringe rejected");check(bridge.at(gui,124,150)==a,"native last included pixel rejected");
        check(bridge.at(gui,106,134)==null,"left boundary expanded beyond native -1 fringe");check(bridge.at(gui,108,132)==null,"top boundary expanded beyond native -1 fringe");
        check(bridge.at(gui,108,151)==null,"native bottom-exclusive boundary accepted");check(bridge.at(gui,125,134)==b,"adjacent boundary selected old/last-rendered slot");check(bridge.at(gui,143,134)==null,"native right-exclusive boundary accepted");
        b.enabled=false;check(bridge.at(gui,126,134)==null,"disabled native slot targeted");b.enabled=true;
        Cell overlap=new Cell(8,84,2);gui.cells.add(overlap);check(bridge.at(gui,108,134)==a,"native first-slot ordering changed");a.enabled=false;check(bridge.at(gui,108,134)==overlap,"first disabled cell masked later enabled slot");a.enabled=true;
        gui.left=177;check(bridge.at(gui,108,134)==null&&bridge.at(gui,185,134)==a,"recipe-book panel shift used stale origin");
        HiddenRecipe recipe=new HiddenRecipe();recipe.cells.add(a);recipe.hidden=true;check(bridge.at(recipe,108,134)==null,"hidden narrow recipe-book panel still targeted");recipe.hidden=false;check(bridge.at(recipe,108,134)==a,"restored native region gate blocked all input");
        FreshSlotLookup srg=new FreshSlotLookup(SrgOnly.class,"getSlotAtPosition","func_146975_c");check(srg.available()&&"12:19".equals(srg.at(new SrgOnly(),12,19)),"SRG fallback lost fresh coordinates");
        check(bridge.at(new Object(),108,134)==null&&bridge.at(null,108,134)==null,"foreign/null screen did not fail closed");
        check(!new FreshSlotLookup(SrgOnly.class,"missing").available()&&new FreshSlotLookup(SrgOnly.class,"missing").at(new SrgOnly(),0,0)==null,"unsupported API did not fail closed");
        Throws throwing=new Throws();throwing.cells.add(a);check(bridge.at(throwing,108,134)==null,"native input-gate exception allowed continuation");
        System.out.println("FRESH_SLOT_LOOKUP_PASS: "+checks);
    }
}
