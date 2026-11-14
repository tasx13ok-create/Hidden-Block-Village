package village.valerack.client;

/** One short-lived cursor continuation, bound to its exact session, sequence and window. */
public final class CursorReceipt {
    public static final long TIMEOUT=30;
    private int ticket,window;
    private long nonce,started,deadline;
    private boolean awaiting,accepted;
    public void clear(){ticket=0;window=-1;nonce=started=deadline=0;awaiting=accepted=false;}
    public void begin(int next,long session,int nativeWindow,long now){
        clear();if(next<=0||nativeWindow<0||now<0)return;
        ticket=next;nonce=session;window=nativeWindow;started=now;
        deadline=now>Long.MAX_VALUE-TIMEOUT?Long.MAX_VALUE:now+TIMEOUT;awaiting=true;
    }
    public void expire(long now){if(ticket>0&&(now<started||now>=deadline))clear();}
    public void acknowledge(long session,int sequence,boolean success,long now){
        expire(now);if(!awaiting||session!=nonce||sequence!=ticket)return;
        awaiting=false;accepted=success;if(!success)clear();
    }
    public boolean accepted(int sequence,long session,int nativeWindow,long now){
        expire(now);return accepted&&sequence>0&&sequence==ticket&&session==nonce&&nativeWindow==window;
    }
}
