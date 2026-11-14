package village.encounters;
import java.util.Locale;
/** Original deterministic appearance for our Encounter actors, never imported NPCs. */
public final class ActorSkinRules {
 private ActorSkinRules(){}
 private static final String[] BOSS={"ash_regent_boss","garran_flint_jonin","rook_dusk_operative","lantern_sage_dawn"},MOB={"rook_dusk_operative","garran_flint_jonin","ash_regent_boss"},ALLY={"kaito_ember_genin","sayo_current_kunoichi","lantern_sage_dawn","garran_flint_jonin"};
 public static String skin(String kind,String profile){String key=profile==null||profile.length()>128?"":profile.toLowerCase(Locale.ROOT);boolean companion="companion".equals(kind),boss="boss".equals(kind);
  if(key.contains("ash")||key.contains("ember")||key.contains("hearth"))return companion?ALLY[0]:BOSS[0];
  if(key.contains("lantern")||key.contains("dawn"))return BOSS[3];
  if(key.contains("rain")||key.contains("fen")||key.contains("reed"))return boss?BOSS[2]:ALLY[1];
  if(key.contains("slate")||key.contains("flint")||key.contains("quarry")||key.contains("cairn"))return BOSS[1];
  String[] pool=boss?BOSS:companion?ALLY:MOB;return pool[Math.floorMod(key.hashCode(),pool.length)];
 }
}
