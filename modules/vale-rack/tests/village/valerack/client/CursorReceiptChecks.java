package village.valerack.client;

/** Actual receipt state machine, without Minecraft or a network connection. */
public final class CursorReceiptChecks {
    private static int checks;
    private static void check(boolean ok,String text){checks++;if(!ok)throw new AssertionError(text);}
    public static void main(String[]args){
        CursorReceipt r=new CursorReceipt();
        check(!r.accepted(1,10,0,0),"empty receipt accepted");
        r.begin(1,10,0,0);check(!r.accepted(1,10,0,1),"unacknowledged request accepted");r.acknowledge(10,1,false,2);check(!r.accepted(1,10,0,3),"server rejection accepted");r.acknowledge(10,1,true,4);check(!r.accepted(1,10,0,4),"later periodic success revived rejection");
        r.begin(2,10,0,10);r.acknowledge(10,1,true,11);check(!r.accepted(2,10,0,11),"old sequence acknowledged new request");r.acknowledge(11,2,true,12);check(!r.accepted(2,10,0,12),"foreign nonce acknowledged request");r.acknowledge(10,2,true,13);check(r.accepted(2,10,0,14),"exact successful receipt rejected");check(!r.accepted(2,11,0,14)&&!r.accepted(2,10,1,14),"accepted receipt crossed nonce/window");
        r.clear();r.acknowledge(10,2,true,15);check(!r.accepted(2,10,0,16),"context clear accepted old snapshot");
        r.begin(3,10,0,20);r.acknowledge(10,3,true,50);check(!r.accepted(3,10,0,50),"ack at timeout revived continuation");r.acknowledge(10,3,true,51);check(!r.accepted(3,10,0,51),"late periodic success revived timed-out request");
        r.begin(4,10,0,60);r.acknowledge(10,4,true,61);check(r.accepted(4,10,0,89)&&!r.accepted(4,10,0,90),"accepted drag outlived hard intent deadline");r.acknowledge(10,4,true,91);check(!r.accepted(4,10,0,91),"periodic snapshot resurrected expired success");
        r.begin(5,10,0,100);r.acknowledge(10,5,true,101);r.begin(6,10,0,102);check(!r.accepted(5,10,0,103)&&!r.accepted(6,10,0,103),"new request inherited previous success");r.acknowledge(10,5,true,104);check(!r.accepted(6,10,0,104),"stale success unlocked newer request");r.acknowledge(10,6,true,105);check(r.accepted(6,10,0,106),"new exact receipt rejected");
        r.begin(7,-100,2,110);r.acknowledge(-100,7,true,111);check(r.accepted(7,-100,2,112),"valid negative nonce rejected");check(!r.accepted(7,-100,2,109),"clock rollback preserved receipt");
        r.begin(8,10,-1,120);r.acknowledge(10,8,true,121);check(!r.accepted(8,10,-1,122),"non-cursor request opened continuation");
        r.begin(0,10,0,130);r.acknowledge(10,0,true,131);check(!r.accepted(0,10,0,131),"untracked sequence accepted");
        System.out.println("CURSOR_RECEIPT_PASS: "+checks);
    }
}
