package village.vale;

/** Physical-edge input gate: screens/focus/rebinds require release, never queued activation. */
public final class DodgeInput {
    private boolean down,blocked;
    public void reset(boolean held){down=held;blocked=held;}
    public boolean press(boolean usable,boolean physicallyDown,boolean queued,boolean conflict){
        if(!usable){down=physicallyDown;blocked=true;return false;}
        if(!physicallyDown){down=false;blocked=false;return false;}
        boolean edge=!down;down=true;if(blocked||!edge||!queued||conflict)return false;return true;
    }
}
