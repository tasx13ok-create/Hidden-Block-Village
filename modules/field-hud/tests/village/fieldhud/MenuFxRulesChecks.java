package village.fieldhud;

/** Actual menu clock, accessibility and slot exclusion rules, independent of GL/native input. */
public final class MenuFxRulesChecks {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        for(int mode=0;mode<3;mode++)for(boolean enabled:new boolean[]{false,true})for(boolean motion:new boolean[]{false,true}){
            check(MenuFxRules.animated(enabled,motion,mode)==(enabled&&motion&&mode!=2),"animation preferences");
            for(boolean sounds:new boolean[]{false,true})check(MenuFxRules.audible(enabled,motion,sounds,mode)==(enabled&&motion&&sounds&&mode!=2),"audio preferences include motion and sound toggles");
        }
        MenuFxRules.Clock clock=new MenuFxRules.Clock();
        check(clock.step(10)==0,"first frame cannot jump");check(clock.step(10.9)==0,"same wall tick");
        check(clock.step(11)==1,"one wall tick");for(int i=0;i<100;i++)check(clock.step(11.99)==0,"high FPS cannot accelerate");
        check(clock.step(1000)==4,"pause catchup bounded");check(clock.step(2)==0,"clock reset cannot jump");
        check(clock.step(Double.NaN)==0&&clock.step(Double.POSITIVE_INFINITY)==0&&clock.step(-1)==0,"invalid clock ignored");
        check(clock.step(3)==1,"invalid samples preserve actual clock");
        for(int i=0;i<16;i++)check(clock.emit(3.1),"emission within tick budget");
        for(int i=0;i<100;i++)check(!clock.emit(3.9),"same-tick emissions bounded across events");
        check(clock.emit(4),"next tick permits particles");
        check(!clock.emit(Double.NaN)&&!clock.emit(Double.NEGATIVE_INFINITY)&&!clock.emit(-1),"bad emissions rejected");
        clock.reset();check(clock.step(20000)==0&&clock.emit(20000),"new screen resets clocks");
        int[] widths={1,12,64,179,180,220,320,480,854,1280,3840};
        int[] heights={1,18,64,109,110,139,140,180,240,480,720,2160};
        for(int w:widths)for(int h:heights){int[][] rows=MenuFxRules.options(w,h);check(rows.length==(w<180||h<140?1:4),"compact escape control");for(int[] r:rows){check(r[0]>=0&&r[1]>=0&&r[2]>0&&r[3]>0&&r[0]+r[2]<=w&&r[1]+r[3]<=h,"all options on screen");}}
        for(int maskBits=0;maskBits<16;maskBits++){boolean[] enabled=new boolean[4];for(int i=0;i<4;i++)enabled[i]=(maskBits&(1<<i))!=0;for(int from=-1;from<5;from++)for(int direction:new int[]{-1,1}){int next=MenuFxRules.nextFocus(from,direction,enabled);check(maskBits==0?next==-1:next>=0&&next<4&&enabled[next],"Tab skips unavailable controls");if(maskBits!=0){int again=next;for(int i=0;i<Integer.bitCount(maskBits);i++)again=MenuFxRules.nextFocus(again,direction,enabled);check(again==next,"Tab cycle visits only enabled controls");}}}
        check(MenuFxRules.nextFocus(-1,1,new boolean[]{true})==0&&MenuFxRules.nextFocus(0,-1,new boolean[]{true})==0,"tiny child keeps Return keyboard-accessible");
        MenuFxRules.Mask mask=new MenuFxRules.Mask(854,480,339,157,176,166);
        mask.include(315,176,16,16); // A genuine modded slot outside the standard GUI rectangle.
        for(int y=0;y<480;y+=3)for(int x=0;x<854;x+=3){boolean allowed=mask.allows(x,y,3);if(allowed){check(x-3>=0&&x+3<=854&&y-3>=0&&y+3<=480,"mote on screen");check(x+3<311||x-3>521||y+3<151||y-3>329,"mote excludes full fresh GUI/slot union");}}
        check(!mask.allows(320,180,1),"external native slot remains clear");
        check(mask.allowsRectangle(20,20,30,20),"outside button decoration permitted");
        check(!mask.allowsRectangle(300,140,250,200),"spanning rectangle cannot pass by its corners");
        check(!mask.allowsRectangle(0,0,Double.POSITIVE_INFINITY,1),"invalid rectangle rejected");
        check(!mask.allows(-1,20,0)&&!mask.allows(20,20,-1)&&!mask.allows(Double.NaN,20,1),"invalid mote bounds rejected");
        check(mask.leftMargin()<=854*.18&&mask.rightMargin()<=854*.18&&mask.bottomStart()>=329,"container gradient avoids actual slots");
        MenuFxRules.Mask invalid=new MenuFxRules.Mask(854,480,0,0,0,1);check(!invalid.allows(20,20,0)&&invalid.leftMargin()==0&&invalid.bottomStart()==480,"unknown container fails closed");
        mask.include(Double.NaN,0,16,16);check(!mask.allows(20,20,0)&&!mask.allowsRectangle(20,20,1,1),"invalid native slot disables decoration");
        System.out.println("MENU_FX_RULES_PASS checks="+checks);
    }
}
