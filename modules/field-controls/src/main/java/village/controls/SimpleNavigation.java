package village.controls;

import village.controls.MenuLayout.Box;

/** Shared information architecture and hitboxes for every settings page. */
public final class SimpleNavigation {
    public static final String[] PAGES={"Controls","Gameplay","Video","Audio","Interface"};
    public static final String[] LABELS={"Controls","Game","Video","Sound","HUD"};
    private SimpleNavigation(){}
    public static Box tab(MenuLayout l,int index){int x=l.tabs.x+index*l.tabs.w/PAGES.length,right=l.tabs.x+(index+1)*l.tabs.w/PAGES.length;return new Box(x,l.tabs.y,right-x-2,l.tabs.h);}
    public static Box essential(MenuLayout l){return new Box(l.header.right()-76,l.header.y+(l.header.h-20)/2,76,20);}
    public static Box search(MenuLayout l){return new Box(l.content.x,l.content.y,l.content.w-layoutWidth(l)-6,21);}
    public static Box layout(MenuLayout l){return new Box(l.content.right()-layoutWidth(l),l.content.y,layoutWidth(l),21);}
    public static Box category(MenuLayout l){return new Box(l.content.x,l.content.y+25,Math.min(168,Math.max(112,l.content.w/3)),19);}
    public static Box filter(MenuLayout l){return new Box(l.content.right()-88,l.content.y+25,88,19);}
    private static int layoutWidth(MenuLayout l){return l.content.w<400?100:122;}
    public static String groupLabel(String group){return group.equals("Essentials")?"Basics":group;}
    public static boolean isMainPage(String page){for(String p:PAGES)if(p.equals(page))return true;return false;}
}
