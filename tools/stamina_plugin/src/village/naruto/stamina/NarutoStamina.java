package village.naruto.stamina;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
/** Original food-stamina policy only; no native chakra, health, damage, XP or permission edits. */
public final class NarutoStamina extends JavaPlugin implements Listener {
 private static volatile boolean active;private BukkitTask task;
 @Override public void onEnable(){saveDefaultConfig();active=getConfig().getBoolean("enabled",true);if(!active)return;getServer().getPluginManager().registerEvents(this,this);task=getServer().getScheduler().runTaskTimer(this,()->{for(Player p:getServer().getOnlinePlayers())maintain(p);},20L,20L);getLogger().info("NARUTO_STAMINA_ENABLED");}
 private static boolean eligible(Player p){return active&&p!=null&&Bukkit.isPrimaryThread()&&p.isOnline()&&!p.isDead()&&Bukkit.getPlayer(p.getUniqueId())==p&&(p.getGameMode()==GameMode.SURVIVAL||p.getGameMode()==GameMode.ADVENTURE);}
 public static boolean maintain(Player p){if(!eligible(p))return false;p.setFoodLevel(20);p.setSaturation(20f);p.setExhaustion(0f);return true;}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void food(FoodLevelChangeEvent e){if(e.getEntity() instanceof Player&&eligible((Player)e.getEntity())&&e.getFoodLevel()<((Player)e.getEntity()).getFoodLevel())e.setCancelled(true);}
 @Override public void onDisable(){active=false;if(task!=null){task.cancel();task=null;}HandlerList.unregisterAll((Listener)this);}
}
