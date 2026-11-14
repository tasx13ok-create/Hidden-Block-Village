package village.vale.arenas;

/** Pure bounded rules, shared by the real director and executable checks. */
public final class RunRules {
 private RunRules() {}
 public static final int SCHEMA=1, MAX_PARTY=4, MAX_CELLS=4096, GRACE_TICKS=2400;
 public static boolean participant(boolean loggedIn, boolean spectator, boolean creative, boolean fake, boolean preview) {
  return loggedIn && !spectator && !fake && (preview || !creative);
 }
 public static boolean terminal(boolean eventCancelled, boolean bodyDead, boolean absent, boolean revivalVerified, int quietTicks) {
  return !eventCancelled && bodyDead && absent && revivalVerified && quietTicks>=120;
 }
 public static boolean restorable(int current,int after) { return current==after; }
 public static boolean paid(boolean preview, boolean earned, double contribution, boolean terminal, boolean receipt) {
  return !preview && earned && contribution>0 && terminal && !receipt;
 }
 public static int phase(float health,float maximum) {
  if(maximum<=0||!Float.isFinite(health)||!Float.isFinite(maximum))return 0;
  return health/maximum>0.66F?1:health/maximum>0.33F?2:3;
 }
}
