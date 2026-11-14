package village.vale.clientcompat;

/** A native toggle receives one physical key-down edge; no held-key latch is kept. */
public final class PhysicalChargeKey {
    private PhysicalChargeKey() {}
    public static boolean allow(int bound,int eventKey,boolean down,boolean repeat,boolean playContext){
        return playContext&&bound>0&&eventKey==bound&&down&&!repeat;
    }
}
