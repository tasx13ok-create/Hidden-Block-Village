package village.vale.client;
import java.lang.ref.WeakReference;import java.util.*;import village.vale.DodgeMath;
/** Exact remote REGISTER proof; independent of body/world lifetime and bounded across overlapping connections. */
public final class DodgeServerCapability {
 private static final class Entry{final WeakReference<Object> manager;Entry(Object m){manager=new WeakReference<>(m);}}
 private final List<Entry> advertised=new ArrayList<>();
 private void prune(){advertised.removeIf(e->e.manager.get()==null);}
 public synchronized void registration(Object manager,boolean receivingClient,String operation,boolean dodgeChannel){if(manager==null||!receivingClient||!dodgeChannel)return;prune();if("UNREGISTER".equals(operation)){disconnect(manager);return;}if(!"REGISTER".equals(operation))return;for(Entry e:advertised)if(e.manager.get()==manager)return;if(advertised.size()>=4)advertised.remove(0);advertised.add(new Entry(manager));}
 public synchronized void disconnect(Object manager){advertised.removeIf(e->e.manager.get()==null||e.manager.get()==manager);}
 public synchronized boolean supports(Object manager,String explicitVersion,boolean open){prune();if(manager==null||!open||explicitVersion!=null&&!DodgeMath.supports(explicitVersion))return false;for(Entry e:advertised)if(e.manager.get()==manager)return true;return false;}
 public synchronized int size(){prune();return advertised.size();}
}
