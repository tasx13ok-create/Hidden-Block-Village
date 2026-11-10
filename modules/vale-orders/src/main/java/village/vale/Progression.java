package village.vale;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Gameplay unlocks make the weapon roles available without requiring crate purchases. */
public final class Progression {
    private static final String[] WEAPONS={"dagger","spear","glaive","staff","axe"};
    public static void unlock(EntityPlayerMP player) {
        String key=PlayerState.data(player).getString("starterOrder");
        Order order=null;for(Order candidate:Order.values())if(candidate.key.equals(key))order=candidate;
        if(order==null)return;
        int level=PlayerState.level(player);
        // Headless Forge FakePlayers have no FML recipe-book network dispatcher.
        if(!(player instanceof net.minecraftforge.common.util.FakePlayer)&&PlayerState.data(player).getInteger("recipeVersion")<3){
            java.util.List<net.minecraft.item.crafting.IRecipe> recipes=new java.util.ArrayList<>();
            for(net.minecraft.item.crafting.IRecipe recipe:net.minecraftforge.fml.common.registry.ForgeRegistries.RECIPES.getValuesCollection())if(recipe.getRegistryName()!=null&&recipe.getRegistryName().getNamespace().equals(ValeOrders.ID))recipes.add(recipe);
            player.unlockRecipes(recipes);PlayerState.data(player).setInteger("recipeVersion",3);
        }
        RewardLedger ledger=RewardLedger.get(player);
        for(String tool:new String[]{"technique_codex","mission_journal"})ledger.issue(player,tool,1,"academy/"+player.getUniqueID()+"/"+tool);
        for(int i=0;i<Forms.KEYS.length;i++)if(level>=Forms.LEVELS[i])ledger.issue(player,"form_"+Forms.KEYS[i],1,"academy/"+player.getUniqueID()+"/form/"+i);
        for(int i=0;i<3;i++)if(level>=Summons.LEVELS[i])ledger.issue(player,"summon_"+Summons.KEYS[i],1,"academy/"+player.getUniqueID()+"/summon/"+i);
        if(level>=5)ledger.issue(player,"trial_writ",1,"academy/"+player.getUniqueID()+"/trial");
        for(int i=0;i<WEAPONS.length;i++)if(level>=i+2)
            RewardLedger.get(player).issue(player,key+"_"+WEAPONS[i],1,"training/"+player.getUniqueID()+"/"+WEAPONS[i]);
    }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event){if(event.player instanceof EntityPlayerMP)unlock((EntityPlayerMP)event.player);}
}
