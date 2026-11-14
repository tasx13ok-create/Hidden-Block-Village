package village.encounters;
public final class ActorSkinRulesChecks {
 private static int checks;private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 public static void main(String[] args){
  check(ActorSkinRules.skin("companion","hearth_guide").equals("kaito_ember_genin"),"own ember ally");check(ActorSkinRules.skin("boss","rain_warden").equals("rook_dusk_operative"),"own rain boss");check(ActorSkinRules.skin("mob","reed_scout").equals("sayo_current_kunoichi"),"own current actor");check(ActorSkinRules.skin("mob","LANTERN").equals("lantern_sage_dawn"),"stable locale-independent key");check(ActorSkinRules.skin("boss","quarry_guard").equals("garran_flint_jonin"),"stone mapping");
  for(String kind:new String[]{"mob","boss","companion",null,"foreign"})for(int i=0;i<2000;i++){String key="unknown_"+i,name=ActorSkinRules.skin(kind,key);check(name.matches("(?:ash_regent_boss|garran_flint_jonin|rook_dusk_operative|lantern_sage_dawn|kaito_ember_genin|sayo_current_kunoichi)"),"only original own textures");check(name.equals(ActorSkinRules.skin(kind,key)),"stable per profile");}
  check(ActorSkinRules.skin("mob",null).equals(ActorSkinRules.skin("mob","")),"null fallback");check(ActorSkinRules.skin("mob",new String(new char[129])).equals(ActorSkinRules.skin("mob","")),"bounded profile lookup");System.out.println("ACTOR_SKIN_RULES_PASS checks="+checks);
 }
}
