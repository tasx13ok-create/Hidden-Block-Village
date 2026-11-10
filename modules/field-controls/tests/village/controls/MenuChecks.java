package village.controls;

import java.util.*;
import village.controls.MenuLayout.Box;
import village.controls.Model.*;

/** Renderer-free invariants for the new board, input focus and combined undo. */
public final class MenuChecks {
    private static int assertions,viewports;
    private static void require(boolean ok,String why){assertions++;if(!ok)throw new AssertionError(why);}
    private static void contains(Box outer,Box inner,String why){require(inner.w>0&&inner.h>0&&inner.x>=outer.x&&inner.y>=outer.y&&inner.right()<=outer.right()&&inner.bottom()<=outer.bottom(),why);}
    private static void apart(Box a,Box b,String why){require(!a.overlaps(b),why);}
    private static void geometry(int width,int height,boolean controls,boolean nav,int preferred){
        viewports++;String size=width+"x"+height+" controls="+controls+" nav="+nav;
        MenuLayout l=new MenuLayout(width,height,controls,nav,preferred);Box screen=new Box(0,0,width,height);
        contains(screen,l.board,"Board escaped "+size);require(Math.abs(l.board.x*2+l.board.w-width)<=1&&Math.abs(l.board.y*2+l.board.h-height)<=1,"Board must stay centered "+size);
        for(Box box:new Box[]{l.header,l.content,l.help,l.footer})contains(l.board,box,"Board section escaped "+size);
        if(nav){contains(l.board,l.nav,"Navigation escaped "+size);apart(l.nav,l.content,"Navigation/content overlap "+size);apart(l.nav,l.help,"Navigation/help overlap "+size);}
        if(controls){contains(l.board,l.tabs,"Tabs escaped "+size);apart(l.tabs,l.content,"Tabs/content overlap "+size);}
        apart(l.header,l.content,"Header/content overlap "+size);apart(l.content,l.help,"Content/help overlap "+size);apart(l.content,l.footer,"Content/footer overlap "+size);apart(l.help,l.footer,"Help/footer overlap "+size);
        int rh=l.compact?26:28,top=l.content.y+25,capacity=Math.max(1,(l.content.bottom()-top)/rh),aligned=-1;
        for(int row=0;row<capacity;row++){Box cell=l.valueCell(top+row*rh,rh);contains(l.content,cell,"Option cell escaped "+size);require(cell.h>=20&&cell.w>=76,"Value target too small "+size);require(cell.x-l.content.x-18==l.labelWidth(),"Label/value gutter changed "+size);if(aligned!=-1)require(cell.x==aligned,"Value column shifted "+size);aligned=cell.x;}
        require(l.labelWidth()>=70,"Labels lost usable space "+size);
        for(int h:new int[]{196,208,286,316}){Box modal=l.modal(h);contains(l.board,modal,"Modal escaped "+size);require(modal.w>=280&&modal.h>=196,"Modal too small "+size);}
        if(controls&&!nav){
            Box[] cards=l.presets();Box defaults=new Box(l.content.right()-94,l.content.bottom()-19,94,18);
            contains(l.content,defaults,"Preset defaults escaped "+size);
            for(int i=0;i<cards.length;i++){contains(l.content,cards[i],"Preset card escaped "+size);contains(cards[i],l.presetAction(cards[i]),"Preset action escaped "+size);require(l.presetAction(cards[i]).h>=12,"Preset action label clipped "+size);apart(cards[i],defaults,"Preset overlaps defaults "+size);for(int j=0;j<i;j++)apart(cards[i],cards[j],"Preset cards overlap "+size);}
            int mr=l.mouseRowHeight();Box reset=new Box(l.content.right()-98,l.content.bottom()-19,98,18);for(int i=0;i<4;i++){Box row=new Box(l.content.x,l.content.y+i*mr,l.content.w,mr);contains(l.content,row,"Mouse row escaped "+size);apart(row,reset,"Mouse defaults overlap a row "+size);contains(row,l.valueCell(row.y,mr),"Mouse target escaped "+size);}
        }
    }
    private static void scrolling(){
        for(int total:new int[]{0,1,3,9,39,99,250})for(int cap:new int[]{1,3,7,20,300})for(int height:new int[]{1,12,31,129,300}){
            Box track=new Box(10,20,3,height);int maximum=Math.max(0,total-cap);Box first=MenuLayout.thumb(track,total,cap,-50),last=MenuLayout.thumb(track,total,cap,Integer.MAX_VALUE);
            contains(track,first,"First scroll thumb escaped");contains(track,last,"Last scroll thumb escaped");require(first.y==track.y,"First thumb not at start");require(last.bottom()==track.bottom(),"Last thumb not at end");
            require(MenuLayout.clampOffset(-1,total,cap)==0&&MenuLayout.clampOffset(Integer.MAX_VALUE,total,cap)==maximum,"Scroll clamp changed");
            require(MenuLayout.offsetAt(track,total,cap,-1000)==0,"Drag top must reach start");
            require(MenuLayout.offsetAt(track,total,cap,2000)==(height>12?maximum:0),"Drag bottom must reach end or full thumb");
            int previous=-1;for(int y=track.y-10;y<=track.bottom()+10;y++){int offset=MenuLayout.offsetAt(track,total,cap,y);require(offset>=previous&&offset>=0&&offset<=maximum,"Drag must be monotonic and bounded");previous=offset;}
        }
    }
    private static void focus(){
        MenuFocus f=new MenuFocus();f.rebuild(Arrays.asList("tab/binds","search","bind/cast","footer/done"));
        require(f.move(1).equals("tab/binds"),"Tab enters first enabled control");require(f.move(-1).equals("footer/done"),"Shift Tab wraps");
        f.set("bind/cast");f.rebuild(Arrays.asList("tab/binds","search","bind/cast","footer/done"));require(f.current().equals("bind/cast"),"Rebuild lost semantic focus");
        f.rebuild(Arrays.asList("modal/confirm","modal/cancel"),true);require(f.current().isEmpty(),"Modal retained an underlying target");f.set("modal/cancel");f.rebuild(Arrays.asList("modal/swap","modal/cancel"),true);require(f.current().equals("modal/cancel"),"Modal-to-modal focus lost");
        f.rebuild(Arrays.asList("tab/binds","search","bind/cast","footer/done"),false);require(f.current().equals("bind/cast"),"Dialog must return focus to its opener");
        f.rebuild(Arrays.asList("footer/done"));require(f.current().isEmpty(),"Disabled or hidden control remained focused");f.set("missing");require(f.current().isEmpty(),"Unavailable target accepted");require(f.move(-1).equals("footer/done"),"Reverse entry must reach last control");f.clear();require(f.current().isEmpty(),"Clear focus failed");f.rebuild(Collections.emptyList());require(f.move(1).isEmpty()&&f.move(-1).isEmpty(),"Empty focus list failed");
        f.rebuild(Arrays.asList("removed","footer/done"));f.set("removed");f.rebuild(Arrays.asList("modal/cancel"),true);f.rebuild(Arrays.asList("footer/done"),false);require(f.current().isEmpty(),"Dialog restored a removed control");
    }
    private static DraftJournal.State state(Map<String,Stroke> map,String layout,float sensitivity,boolean invert){return new DraftJournal.State(map,layout,sensitivity,invert,false,false);}
    private static void undo(){
        Map<String,Stroke> live=new LinkedHashMap<>();live.put("cast",new Stroke(19));live.put("focus",new Stroke(33));DraftJournal journal=new DraftJournal();
        DraftJournal.State initial=state(live,"Action",.5f,false);live.get("cast").code=20;DraftJournal.State rebound=state(live,"Custom",.5f,false);
        require(initial.values.get("cast").code==19,"Snapshot aliased a live stroke");require(journal.record(initial,rebound),"Binding edit not recorded");
        DraftJournal.State dragged=state(live,"Custom",.8f,false);require(journal.record(rebound,dragged),"Whole mouse gesture not recorded");DraftJournal.State toggled=state(live,"Custom",.8f,true);require(journal.record(dragged,toggled),"Mouse toggle not recorded");
        require(!journal.record(toggled,state(live,"Custom",.8f,true)),"No-op must not erase chronology");require(journal.undo().same(dragged),"First undo must restore mouse toggle");require(journal.undo().same(rebound),"Second undo must restore one whole gesture");require(journal.undo().same(initial),"Third undo must restore keys and preset name");require(!journal.available()&&journal.undo()==null,"Exhausted undo must be safe");
        for(int i=0;i<45;i++)journal.record(state(live,"Custom",i,false),state(live,"Custom",i+1,false));for(int i=44;i>=5;i--)require(journal.undo().sensitivity==i,"Bounded history order changed");require(!journal.available(),"Undo exceeded forty entries");journal.record(initial,rebound);journal.clear();require(!journal.available(),"Apply must clear undo baseline");
        live.put("late",new Stroke(45,Mod.CONTROL));require(!initial.values.containsKey("late"),"Late registry entry mutated saved state");
    }
    private static void motion(){
        require(MenuMotion.sample(0,1,-1,120)==0&&MenuMotion.sample(0,1,121,120)==1,"Motion must clamp endpoints");double previous=-1;for(int t=0;t<=120;t++){double v=MenuMotion.sample(0,1,t,120);require(v>=previous&&v>=0&&v<=1,"Motion overshot or reversed");previous=v;}
        MenuMotion m=new MenuMotion();require(m.value(true,100,true)==0,"Hover must start at current color");double middle=m.value(true,160,true);require(Math.abs(middle-.5)<1e-9,"Hover duration changed");require(Math.abs(m.value(false,160,true)-middle)<1e-9,"Reversing hover jumped");require(m.value(false,280,true)==0,"Hover out did not settle");require(m.value(true,281,false)==1&&m.value(false,282,false)==0,"Reduced motion must update immediately");
        require(MenuMotion.mix(0x10203040,0xF0E0D0C0,0)==0x10203040&&MenuMotion.mix(0x10203040,0xF0E0D0C0,1)==0xF0E0D0C0,"Color endpoints changed");require(MenuMotion.mix(0x00000000,0xFFFFFFFF,.5)==0x80808080,"Color alpha interpolation failed");
    }
    private static void dispatch(){
        MenuDispatch gate=new MenuDispatch();require(gate.rebuildNow(),"Idle rebuild must be immediate");gate.begin();require(!gate.rebuildNow()&&!gate.rebuildNow(),"Click hit list must remain stable through repeated edits");gate.begin();require(!gate.rebuildNow()&&!gate.end(),"Nested dispatch must not release the outer hit list");require(gate.end(),"Outermost dispatch must release one deferred rebuild");require(gate.rebuildNow(),"Released rebuild must run immediately");gate.begin();require(!gate.end(),"A click without an edit must not rebuild");
        List<String> buttons=new ArrayList<>(Arrays.asList("Cancel"));int actions=0;gate.begin();try{for(int i=0;i<buttons.size();i++)if(buttons.get(i).equals("Cancel")){actions++;if(gate.rebuildNow())buttons.add("Reset underlying input");}}finally{if(gate.end())buttons=Arrays.asList("Reset underlying input");}require(actions==1&&buttons.size()==1,"Closing a modal must not dispatch into its replacement list");
        boolean caught=false;try{gate.begin();require(!gate.rebuildNow(),"Exception path still defers");throw new IllegalArgumentException("fixture");}catch(IllegalArgumentException expected){caught=true;}finally{require(gate.end(),"Exception cleanup must release the pending rebuild");}require(caught&&gate.rebuildNow(),"Dispatch gate must recover after an exception");
    }
    public static void main(String[] args){
        for(int w=320;w<=1440;w+=16)for(int h:new int[]{240,241,266,300,320,360,450,540,720,900}){geometry(w,h,false,true,466);geometry(w,h,false,true,316);geometry(w,h,true,true,466);geometry(w,h,true,false,466);geometry(w,h,true,false,306);geometry(w,h,true,false,286);}
        scrolling();focus();undo();motion();dispatch();System.out.println("MENU_CHECKS_PASS assertions="+assertions+" viewports="+viewports);
    }
}
