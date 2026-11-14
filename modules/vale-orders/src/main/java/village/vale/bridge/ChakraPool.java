package village.vale.bridge;

/**
 * One live chakra pathway (absolute double units). Implemented by the reflective NarutoMod
 * adapter and by test fakes. Methods mirror the observed public API: consume(double,boolean)
 * is an absolute delta whose boolean is over-cap policy (not a simulation) and which refuses a
 * result <= 0; clear() is the only exact-zero route.
 */
public interface ChakraPool {
    double amount() throws Exception;
    double max() throws Exception;
    /** Absolute delta (positive spends, negative refunds). Returns whether the amount changed. */
    boolean consume(double delta, boolean allowOverCap) throws Exception;
    void clear() throws Exception;
    /** Identity of the backing pathway object (reacquired per action; compared before settling). */
    Object identity();
}
