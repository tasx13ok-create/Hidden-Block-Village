package village.controls;

/** Keep a native click's hit list stable until Forge finishes dispatching it. */
public final class MenuDispatch {
    private int depth;private boolean pending;
    public void begin(){depth++;}
    public boolean rebuildNow(){if(depth>0){pending=true;return false;}return true;}
    public boolean end(){if(depth<=0)throw new IllegalStateException("No menu dispatch to finish");depth--;if(depth==0&&pending){pending=false;return true;}return false;}
}
