package narutodestinationvillages.compat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.items.ItemHandlerHelper;
import net.narutomod.PlayerTracker;
import net.narutomod.gui.GuiScrollGenjutsuGui;
import net.narutomod.item.*;

/**
 * Original native providers for the five legacy randomizer rewards (DNA, kekkei genkai, nature,
 * clan, sage). The legacy addon granted these through its old base mod's stat flags; NarutoMod
 * 1.12.2 represents the same powers as owned jutsu items, dojutsu, sage-typed senjutsu and
 * advancements, so each outcome grants exactly what NarutoMod's own distribution would.
 *
 * Token forms keep the legacy damage values: 0 is the random form, which rolls a specific token
 * (as the original did); a specific form grants its power once. Holding an owned copy refuses
 * and keeps the token. A previously awakened power whose item was lost is restored without
 * consuming the token or paying the bonus again. Legacy forms with no native counterpart are
 * never rolled; an old specific token for one re-rolls into an available form.
 */
public final class NativeRandomizers {
    public static final String DNA = "dna", KG = "kg", NATURE = "nature", CLAN = "clan", SAGE = "sage";
    private static final Random RANDOM = new Random();
    private static final Set<UUID> BUSY = new HashSet<>();
    private static boolean registered;

    // Legacy form tables (index = original damage value).
    static final String[] KG_FORMS = {"randomizer", "boil", "bone", "crystal", "dust", "explosion", "ice", "lava", "magnet", "scorch", "steel", "storm", "wood"};
    static final String[] NATURE_FORMS = {"randomizer", "wind", "water", "fire", "lightning", "earth", "yin", "yang"};
    static final String[] CLAN_FORMS = {"randomizer", "uchiha", "uzumaki", "aburame", "hatake", "hozuki", "hyuuga", "iburi", "inuzuka", "juugo", "kaguya", "kurama", "tsuchigumo", "nara", "sarutobi", "fuma", "senju", "yuki", "yamanaka", "lee", "chinoike", "namikaze", "shirogane"};
    static final String[] SAGE_FORMS = {"randomizer", "snake", "jiraiyatoad", "slug", "narutotoad", "dragon"};

    // Configured bonuses {xp, sp, jp} paid when a power is newly awakened (legacy defaults).
    static int[] kgBonus = {20, 20, 20}, natureBonus = {5, 5, 5}, clanBonus = {10, 10, 10};
    static int dnaMinimumLevel = 5, dnaBaseChance = 50, dnaChancePerLevel = 2, dnaMaxChance = 95, dnaNatureChance = 90;

    private NativeRandomizers() {}

    static void initialize() {
        if (!registered) { MinecraftForge.EVENT_BUS.register(new NativeRandomizers.Events()); registered = true; }
    }

    static void load(Configuration c) {
        kgBonus = bonus(c, "kekkei_genkai_token", kgBonus);
        natureBonus = bonus(c, "nature_token", natureBonus);
        clanBonus = bonus(c, "clan_token", clanBonus);
        dnaMinimumLevel = c.getInt("minimumNinjaLevel", "dna_identification", 5, 0, 1000, "Native ninja level needed to identify Unidentified DNA (the legacy addon used Intelligence, which NarutoMod has no equivalent for).");
        dnaBaseChance = c.getInt("baseChance", "dna_identification", 50, 0, 100, "Identification chance (%) at the minimum level. A failed identification destroys the DNA, as before.");
        dnaChancePerLevel = c.getInt("chancePerLevel", "dna_identification", 2, 0, 100, "Extra identification chance (%) per ninja level above the minimum.");
        dnaMaxChance = c.getInt("maxChance", "dna_identification", 95, 0, 100, "Highest identification chance (%).");
        dnaNatureChance = c.getInt("natureReleaseProbability", "dna_identification", 90, 0, 100, "Chance (%) that identified DNA is a nature release; otherwise it is a kekkei genkai.");
    }

    private static int[] bonus(Configuration c, String category, int[] d) {
        return new int[] {
            c.getInt("xp", category, d[0], 0, 100000, "Native battle XP granted when this token awakens a new power."),
            c.getInt("sp", category, d[1], 0, 100000, "Skill points granted when this token awakens a new power."),
            c.getInt("jp", category, d[2], 0, 100000, "Jutsu points granted when this token awakens a new power.")};
    }

    // ---- token identity ----

    static String kindForLegacyId(String lowerId) {
        switch (lowerId) {
            case "narutodestination:unidentifieddna": return DNA;
            case "narutodestination:kgrandomizer": return KG;
            case "narutodestination:naturerandomizer": return NATURE;
            case "narutodestination:clanrandomizer": return CLAN;
            case "narutodestination:sagerandomizer": return SAGE;
            default: return null;
        }
    }

    static boolean isKind(String kind) { return DNA.equals(kind) || KG.equals(kind) || NATURE.equals(kind) || CLAN.equals(kind) || SAGE.equals(kind); }

    static String[] forms(String kind) {
        switch (kind) {
            case KG: return KG_FORMS;
            case NATURE: return NATURE_FORMS;
            case CLAN: return CLAN_FORMS;
            case SAGE: return SAGE_FORMS;
            default: return new String[] {"unidentified"};
        }
    }

    static boolean validForm(String kind, int form) { return isKind(kind) && form >= 0 && form < forms(kind).length; }

    static String displayName(String kind, int form) {
        String base = DNA.equals(kind) ? "Unidentified DNA" : KG.equals(kind) ? "Kekkei Genkai Token" : NATURE.equals(kind) ? "Nature Token" : CLAN.equals(kind) ? "Clan Token" : "Sage Mode Token";
        if (DNA.equals(kind) || form == 0) return (DNA.equals(kind) ? "§5" : "§6") + base + (DNA.equals(kind) ? "" : " §e(Random)");
        return "§6" + base + " §e(" + title(kind, form) + ")";
    }

    static String[] lore(String kind, int form) {
        if (DNA.equals(kind)) return new String[] {"§7Right-click to try to identify it.", "§7Needs ninja level " + dnaMinimumLevel + ". Failure destroys it."};
        if (form == 0) return new String[] {"§7Right-click to roll a specific token."};
        return new String[] {"§7Right-click to awaken this power.", "§7Kept if you already have it."};
    }

    static String title(String kind, int form) {
        switch (kind) {
            case KG: return new String[] {"Random", "Boil Release", "Bone Release", "Crystal Release", "Dust Release", "Explosion Release", "Ice Release", "Lava Release", "Magnet Release", "Scorch Release", "Steel Release", "Storm Release", "Wood Release"}[form];
            case NATURE: return new String[] {"Random", "Wind Release", "Water Release", "Fire Release", "Lightning Release", "Earth Release", "Yin Release", "Yang Release"}[form];
            case CLAN: { String n = CLAN_FORMS[form]; return form == 0 ? "Random" : (n.equals("hyuuga") ? "Hyuga" : n.equals("juugo") ? "Jugo" : Character.toUpperCase(n.charAt(0)) + n.substring(1)) + " Clan"; }
            case SAGE: return new String[] {"Random", "Snake Sage Mode", "Jiraiya Toad Sage Mode", "Slug Sage Mode", "Naruto Toad Sage Mode", "Dragon Sage Mode"}[form];
            default: return "Unidentified";
        }
    }

    // ---- native bindings ----

    /** Native release items for each kekkei genkai form, with the nature affinities NarutoMod pairs with it. Null = no native counterpart. */
    static Item kgItem(int form) {
        switch (form) {
            case 1: return ItemFutton.block;
            case 2: return ItemShikotsumyaku.block;
            case 4: return ItemJinton.block;
            case 5: return ItemBakuton.block;
            case 6: return ItemHyoton.block;
            case 7: return ItemYooton.block;
            case 8: return ItemJiton.block;
            case 9: return ItemShakuton.block;
            case 11: return ItemRanton.block;
            case 12: return ItemMokuton.block;
            default: return null;   // 3 crystal, 10 steel: not in NarutoMod 1.12.2
        }
    }
    static Item[] kgAffinities(int form) {
        switch (form) {
            case 1: return new Item[] {ItemSuiton.block, ItemKaton.block};
            case 4: return new Item[] {ItemKaton.block, ItemDoton.block, ItemFuton.block};
            case 5: return new Item[] {ItemDoton.block, ItemRaiton.block};
            case 6: return new Item[] {ItemFuton.block, ItemSuiton.block};
            case 7: return new Item[] {ItemKaton.block, ItemDoton.block};
            case 8: return new Item[] {ItemFuton.block, ItemDoton.block};
            case 9: return new Item[] {ItemFuton.block, ItemKaton.block};
            case 11: return new Item[] {ItemRaiton.block, ItemSuiton.block};
            case 12: return new Item[] {ItemDoton.block, ItemSuiton.block};
            default: return new Item[0];
        }
    }
    static String kgAdvancement(int form) {
        switch (form) {
            case 1: return "futton_acquired";
            case 2: return "shikotsumyaku_acquired";
            case 4: return "kekkei_tota_awakened";
            case 5: return "bakuton_acquired";
            case 6: return "hyoton_acquired";
            case 7: return "yooton_acquired";
            case 8: return "jiton_acquired";
            case 9: return "shakuton_acquired";
            case 11: return "ranton_acquired";
            case 12: return "mokuton_acquired";
            default: return null;
        }
    }
    static Item natureItem(int form) {
        switch (form) {
            case 1: return ItemFuton.block;
            case 2: return ItemSuiton.block;
            case 3: return ItemKaton.block;
            case 4: return ItemRaiton.block;
            case 5: return ItemDoton.block;
            case 6: return ItemInton.block;
            case 7: return ItemYoton.block;
            default: return null;
        }
    }
    static ItemSenjutsu.Type sageType(int form) {
        switch (form) {
            case 1: return ItemSenjutsu.Type.SNAKE;
            case 2: case 4: return ItemSenjutsu.Type.TOAD;
            case 3: return ItemSenjutsu.Type.SLUG;
            default: return null;   // 5 dragon: not in NarutoMod 1.12.2
        }
    }

    /** Forms the random form may roll: only those with a native counterpart. */
    public static List<Integer> rollable(String kind) {
        List<Integer> out = new ArrayList<>();
        for (int f = 1; f < forms(kind).length; f++) {
            boolean ok = KG.equals(kind) ? kgItem(f) != null : NATURE.equals(kind) ? natureItem(f) != null : SAGE.equals(kind) ? sageType(f) != null : CLAN.equals(kind);
            if (ok) out.add(f);
        }
        return out;
    }

    // ---- redemption ----

    public static final class Events {
        @SubscribeEvent public void use(PlayerInteractEvent.RightClickItem event) {
            if (event.getWorld().isRemote || !(event.getEntityPlayer() instanceof EntityPlayerMP)) return;
            String kind = NativeRewardItems.kind(event.getEntityPlayer().getHeldItem(event.getHand()));
            if (!isKind(kind)) return;
            event.setCanceled(true);
            event.setCancellationResult(redeem((EntityPlayerMP) event.getEntityPlayer(), event.getHand()) ? EnumActionResult.SUCCESS : EnumActionResult.FAIL);
        }
    }

    /** Server-side redemption of the held randomizer token. Returns true when the stack changed. */
    public static boolean redeem(EntityPlayerMP player, EnumHand hand) {
        if (player.world.isRemote || !BUSY.add(player.getUniqueID())) return false;
        try {
            ItemStack stack = player.getHeldItem(hand);
            String kind = NativeRewardItems.kind(stack);
            if (!isKind(kind)) return false;
            int form = NativeRewardItems.formValue(stack);
            boolean consume;
            if (DNA.equals(kind)) consume = identify(player);
            else if (form == 0 || !rollable(kind).contains(form)) consume = roll(player, kind, form);
            else if (KG.equals(kind)) consume = awakenKg(player, form);
            else if (NATURE.equals(kind)) consume = awakenNature(player, form);
            else if (CLAN.equals(kind)) consume = joinClan(player, form);
            else consume = awakenSage(player, form);
            if (!consume) return false;
            stack.shrink(1);
            if (player.getHeldItem(hand) == stack && stack.isEmpty()) player.setHeldItem(hand, ItemStack.EMPTY);
            player.inventory.markDirty();
            player.inventoryContainer.detectAndSendChanges();
            return true;
        } finally {
            BUSY.remove(player.getUniqueID());
        }
    }

    /** Random form (or a legacy form with no native counterpart): replace with a specific rollable token. */
    private static boolean roll(EntityPlayerMP p, String kind, int form) {
        List<Integer> options = rollable(kind);
        if (options.isEmpty()) { say(p, "§cNo forms of this token are available."); return false; }
        int pick = options.get(RANDOM.nextInt(options.size()));
        if (form != 0) say(p, "§e" + title(kind, form) + " does not exist in this world, so the token re-rolled.");
        give(p, NativeRewardItems.create("NarutoDestination:" + legacyName(kind), pick, 1));
        say(p, "§eYou rolled §f" + title(kind, pick) + "§e! Right-click the new token to awaken it.");
        return true;
    }

    private static String legacyName(String kind) {
        switch (kind) { case KG: return "kgrandomizer"; case NATURE: return "naturerandomizer"; case CLAN: return "clanrandomizer"; case SAGE: return "sagerandomizer"; default: return "unidentifieddna"; }
    }

    private static boolean identify(EntityPlayerMP p) {
        int level = (int) Math.floor(PlayerTracker.getNinjaLevel(p));
        if (level < dnaMinimumLevel) { say(p, "§cYou need ninja level " + dnaMinimumLevel + " to identify DNA."); return false; }
        int chance = Math.min(dnaMaxChance, dnaBaseChance + dnaChancePerLevel * (level - dnaMinimumLevel));
        if (RANDOM.nextInt(100) >= chance) { say(p, "§7You failed examining the DNA and it became useless."); return true; }
        boolean nature = RANDOM.nextInt(100) < dnaNatureChance;
        String kind = nature ? NATURE : KG;
        List<Integer> options = rollable(kind);
        int pick = options.get(RANDOM.nextInt(options.size()));
        say(p, (nature ? "§a" : "§6") + "You identified the DNA as " + title(kind, pick) + " DNA.");
        give(p, NativeRewardItems.create("NarutoDestination:" + legacyName(kind), pick, 1));
        return true;
    }

    private static boolean awakenKg(EntityPlayerMP p, int form) {
        Item release = kgItem(form);
        String key = "kg_" + KG_FORMS[form];
        if (ownsJutsu(p, release)) { say(p, "§cYou already possess this Kekkei Genkai!"); return false; }
        for (Item affinity : kgAffinities(form)) if (!ownsJutsu(p, affinity)) give(p, jutsu(p, affinity, true));
        give(p, jutsu(p, release, true));
        advancement(p, kgAdvancement(form));
        if (restored(p, key, title(KG, form))) return false;
        say(p, "§eYou have awakened §f" + title(KG, form) + "§e!");
        pay(p, kgBonus);
        return true;
    }

    private static boolean awakenNature(EntityPlayerMP p, int form) {
        Item nature = natureItem(form);
        if (ownsJutsu(p, nature)) { say(p, "§cYou already possess " + title(NATURE, form) + "!"); return false; }
        give(p, jutsu(p, nature, true));
        if (restored(p, "nature_" + NATURE_FORMS[form], title(NATURE, form))) return false;
        say(p, "§eYou have awakened §f" + title(NATURE, form) + "§e!");
        pay(p, natureBonus);
        return true;
    }

    private static boolean awakenSage(EntityPlayerMP p, int form) {
        ItemSenjutsu.Type type = sageType(form);
        ItemSenjutsu.RangedItem senjutsu = (ItemSenjutsu.RangedItem) ItemSenjutsu.block;
        ItemStack owned = ownedStack(p, ItemSenjutsu.block);
        if (owned != null && senjutsu.getSageType(owned) == type) { say(p, "§cYou already possess " + title(SAGE, form) + "!"); return false; }
        if (owned != null) {
            senjutsu.setSageType(owned, type);   // the legacy token switched sage version
            p.inventoryContainer.detectAndSendChanges();
        } else {
            ItemStack stack = jutsu(p, ItemSenjutsu.block, false);
            senjutsu.setSageType(stack, type);
            give(p, stack);
        }
        say(p, "§eYou have unlocked §d" + title(SAGE, form) + "§e!");
        return true;
    }

    /** Clan identity plus that clan's native signature power (granted once per clan). */
    private static boolean joinClan(EntityPlayerMP p, int form) {
        NBTTagCompound data = ExtendedPlayer.get(p).data();
        String name = title(CLAN, form);
        boolean first = !data.getBoolean("clan_" + CLAN_FORMS[form]);
        if (data.getInteger("legacyClan") == form) { say(p, "§cYou are already part of the " + name + "!"); return false; }
        data.setInteger("legacyClan", form);
        data.setString("legacyClanName", name);
        say(p, "§eYou are now part of the §c" + name + "§e!");
        if (!first) return true;
        data.setBoolean("clan_" + CLAN_FORMS[form], true);
        clanPower(p, form);
        pay(p, clanBonus);
        return true;
    }

    private static void clanPower(EntityPlayerMP p, int form) {
        switch (CLAN_FORMS[form]) {
            case "uchiha": dojutsu(p, ItemSharingan.helmet, "sharinganopened", "the Sharingan"); GuiScrollGenjutsuGui.giveGenjutsu(p); break;
            case "hyuuga": dojutsu(p, ItemByakugan.helmet, "byakuganopened", "the Byakugan"); break;
            case "kaguya": power(p, ItemShikotsumyaku.block, new Item[0], "shikotsumyaku_acquired", "Bone Release"); break;
            case "senju": power(p, ItemMokuton.block, kgAffinities(12), "mokuton_acquired", "Wood Release"); break;
            case "yuki": power(p, ItemHyoton.block, kgAffinities(6), "hyoton_acquired", "Ice Release"); break;
            case "hatake": power(p, ItemRaiton.block, new Item[0], null, "Lightning Release"); break;
            case "hozuki": power(p, ItemSuiton.block, new Item[0], null, "Water Release"); break;
            case "sarutobi": power(p, ItemKaton.block, new Item[0], null, "Fire Release"); break;
            case "tsuchigumo": power(p, ItemDoton.block, new Item[0], null, "Earth Release"); break;
            case "uzumaki": item(p, ItemScrollSealingChains.block, "a Sealing Chains scroll"); break;
            case "aburame": item(p, ItemScrollKikaichuSphere.block, "a Kikaichu Sphere scroll"); break;
            case "nara": item(p, ItemScrollShadowImitation.block, "a Shadow Imitation scroll"); break;
            case "yamanaka": item(p, ItemScrollMindTransfer.block, "a Mind Transfer scroll"); break;
            case "kurama": case "chinoike": item(p, ItemScrollGenjutsu.block, "a Genjutsu scroll"); break;
            case "namikaze": item(p, ItemScrollHiraishin.block, "a Flying Thunder God scroll"); break;
            case "iburi": item(p, ItemScrollHidingInAsh.block, "a Hiding in Ash scroll"); break;
            case "juugo": item(p, ItemScrollEnhancedStrength.block, "an Enhanced Strength scroll"); break;
            case "shirogane": item(p, ItemScrollPuppet.block, "a Puppet scroll"); break;
            case "lee": item(p, ItemEightGates.block, "the Eight Gates"); break;
            case "fuma": item(p, ItemFumaShuriken.block, "a Fuma Shuriken"); break;
            case "inuzuka": item(p, ItemClaw.block, "Inuzuka claws"); break;
            default: break;
        }
    }

    private static void power(EntityPlayerMP p, Item release, Item[] affinities, String adv, String label) {
        for (Item a : affinities) if (!ownsJutsu(p, a)) give(p, jutsu(p, a, true));
        if (!ownsJutsu(p, release)) give(p, jutsu(p, release, true));
        advancement(p, adv);
        say(p, "§aYou have awakened " + label + "!");
    }

    private static void dojutsu(EntityPlayerMP p, Item helmet, String adv, String label) {
        if (ownedStack(p, helmet) == null) {
            ItemStack stack = new ItemStack(helmet, 1);
            ((ItemDojutsu.Base) helmet).setOwner(stack, p);
            give(p, stack);
        }
        advancement(p, adv);
        say(p, "§aYou have awakened " + label + "!");
    }

    private static void item(EntityPlayerMP p, Item item, String label) {
        give(p, new ItemStack(item, 1));
        say(p, "§aYou received " + label + ".");
    }

    // ---- helpers ----

    /** True once a power was awakened before; its item was re-issued above without a new bonus. */
    private static boolean restored(EntityPlayerMP p, String key, String label) {
        NBTTagCompound data = ExtendedPlayer.get(p).data();
        if (data.getBoolean(key)) { say(p, "§eRestored your " + label + ". The token was kept."); return true; }
        data.setBoolean(key, true);
        return false;
    }

    private static ItemStack jutsu(EntityPlayerMP p, Item item, boolean affinity) {
        ItemStack stack = new ItemStack(item, 1);
        if (item instanceof ItemJutsu.Base) {
            ((ItemJutsu.Base) item).setOwner(stack, p);
            if (affinity) ((ItemJutsu.Base) item).setIsAffinity(stack, true);
        }
        return stack;
    }

    /**
     * The player's own copy of a native power item. Dojutsu are checked in every inventory, armor and
     * offhand slot with the public owner test; jutsu items use NarutoMod's own owner lookup.
     */
    public static ItemStack ownedStack(EntityPlayer p, Item item) {
        if (item == null) return null;
        if (!(item instanceof ItemDojutsu.Base)) return ItemJutsu.getOwnerMatchingItemstack(p, item);
        for (int i = 0; i < p.inventory.getSizeInventory(); i++) {
            ItemStack s = p.inventory.getStackInSlot(i);
            if (!s.isEmpty() && s.getItem() == item && ((ItemDojutsu.Base) item).isOwner(s, p)) return s;
        }
        return null;
    }

    static boolean ownsJutsu(EntityPlayer p, Item item) { return ownedStack(p, item) != null; }

    private static void give(EntityPlayerMP p, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        ItemHandlerHelper.giveItemToPlayer(p, stack);
        p.inventoryContainer.detectAndSendChanges();
    }

    private static void advancement(EntityPlayerMP p, String id) {
        if (id == null || p.getServer() == null) return;
        Advancement adv = p.getServer().getAdvancementManager().getAdvancement(new ResourceLocation("narutomod", id));
        if (adv == null) return;
        AdvancementProgress progress = p.getAdvancements().getProgress(adv);
        if (progress.isDone()) return;
        for (String criterion : progress.getRemaningCriteria()) p.getAdvancements().grantCriterion(adv, criterion);
        p.world.playSound(null, p.posX, p.posY, p.posZ, net.minecraft.init.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1f, 1f);
    }

    private static void pay(EntityPlayerMP p, int[] bonus) {
        StringBuilder m = new StringBuilder();
        if (bonus[0] > 0) { PlayerTracker.addBattleXp(p, bonus[0]); m.append(bonus[0]).append(" XP"); }
        if (bonus[1] > 0 && NativeTraining.credit(p, "skill", bonus[1])) m.append(m.length() > 0 ? ", " : "").append(bonus[1]).append(" SP");
        if (bonus[2] > 0 && NativeTraining.credit(p, "jutsu", bonus[2])) m.append(m.length() > 0 ? ", " : "").append(bonus[2]).append(" JP");
        if (m.length() > 0) say(p, "§aYou received " + m + ".");
    }

    private static void say(EntityPlayerMP p, String text) { p.sendMessage(new TextComponentString(text)); }

    /** Every token kind, for the operator command. */
    static List<String> kinds() { return Arrays.asList(DNA, KG, NATURE, CLAN, SAGE); }
}
