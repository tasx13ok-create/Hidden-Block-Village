package village.fieldhud;

/** Extra menu affordances stay ABOVE the native panel, never in armor/recipe/JEI/rail cells. */
public final class InventoryThemeLayout {
    public final Layout.Box header,settings;
    public InventoryThemeLayout(int screenWidth,int left,int top,int panelWidth){
        if(top<20||left<0||panelWidth<64||left+panelWidth>screenWidth){header=settings=null;return;}
        header=new Layout.Box(left,top-19,panelWidth,16);
        settings=new Layout.Box(left+panelWidth-56,top-19,56,16);
    }
}
