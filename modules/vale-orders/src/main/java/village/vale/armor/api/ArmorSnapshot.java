package village.vale.armor.api;

import java.util.*;

/** Immutable owner-only values. Neither this DTO nor an entry ID authorizes a server action. */
public final class ArmorSnapshot {
    public static final int SCHEMA=1,MAX_ACTIONS=10;
    public final int schemaVersion=SCHEMA,dimension,staminaUnits,staminaMaximumUnits,staminaUnitsPerPoint,flightRestartUnits,flightRestTicks;
    public final long serverTick,armorWorldTick;
    public final UUID playerId;
    public final String providerId,fullSetKey,wardKey,passiveRole;
    public final boolean issuedFullSet,ownsFlight,flying;
    public final int passiveCooldownRemainingTicks,passiveCooldownTotalTicks,momentum,reactivePulsesRemaining,reactiveTriggerCooldownTicks;
    public final boolean opponentEngaged;
    public final List<Piece> pieces;
    public final List<Action> actions;

    public ArmorSnapshot(UUID player,int dimension,long serverTick,long armorWorldTick,String set,String ward,String role,
                         int stamina,int rest,boolean ownsFlight,boolean flying,int passiveRemaining,int passiveTotal,
                         int momentum,int reactive,int reactiveReady,boolean engaged,List<Piece> pieces,List<Action> actions){
        playerId=Objects.requireNonNull(player,"player");this.dimension=dimension;this.serverTick=Math.max(0,serverTick);this.armorWorldTick=Math.max(0,armorWorldTick);
        providerId="original_armor";fullSetKey=text(set,32);wardKey=text(ward,24);passiveRole=text(role,24);issuedFullSet=!fullSetKey.isEmpty();
        staminaUnits=clamp(stamina,0,2000);staminaMaximumUnits=2000;staminaUnitsPerPoint=20;flightRestartUnits=400;flightRestTicks=clamp(rest,0,40);
        this.ownsFlight=ownsFlight;this.flying=flying;passiveCooldownRemainingTicks=clamp(passiveRemaining,0,24000);passiveCooldownTotalTicks=clamp(passiveTotal,0,24000);
        this.momentum=clamp(momentum,0,40);reactivePulsesRemaining=clamp(reactive,0,3);
        reactiveTriggerCooldownTicks=clamp(reactiveReady,0,80);opponentEngaged=engaged;
        if(pieces.size()>4||actions.size()>MAX_ACTIONS)throw new IllegalArgumentException("Snapshot bounds");
        this.pieces=Collections.unmodifiableList(new ArrayList<>(pieces));this.actions=Collections.unmodifiableList(new ArrayList<>(actions));
        Set<String> ids=new HashSet<>();for(Action action:this.actions)if(!ids.add(action.id))throw new IllegalArgumentException("Duplicate action");
    }
    public Action find(String id){for(Action action:actions)if(action.id.equals(id))return action;return null;}
    static int clamp(int x,int a,int b){return Math.max(a,Math.min(b,x));}
    static String text(String s,int max){Objects.requireNonNull(s,"text");if(s.length()>max)throw new IllegalArgumentException("Text bound");return s;}
    public static final class Piece{
        public final String slot,itemId,serial;
        public final boolean owned;
        public Piece(String slot,String item,String serial,boolean owned){this.slot=text(slot,8);itemId=text(item,96);this.serial=text(serial,64);this.owned=owned;}
    }
    public static final class Action{
        public final String id,canonicalActionId,title,kind,schoolKey,profileKey,iconItemId,reason;
        public final int iconMeta=0,requiredLevel=1,resolveCost,cooldownRemainingTicks,cooldownTotalTicks;
        public final boolean enabled,requiresIssuedWeapon,assignable=true;
        public final float cooldownFraction;
        public Action(String id,String canonical,String title,String ward,String profile,String icon,int cost,boolean enabled,String reason,int remaining,int total,boolean requiresWeapon){
            if(!id.matches("order/original_armor_[a-z0-9_]{1,70}")||!canonical.matches("(?:order/[a-z_]{1,24}|original_armor/flight_toggle)")||!ward.matches("[a-z_]{1,24}")||!profile.matches("[a-z_]{1,32}")||!icon.matches("valeorders:[a-z_]{1,80}"))throw new IllegalArgumentException("Action identity");
            if(cost<0||cost>100||remaining<0||remaining>total||total>24000||enabled&&!reason.isEmpty())throw new IllegalArgumentException("Action bounds");
            this.id=id;canonicalActionId=canonical;this.title=text(title,96);kind="order";schoolKey=ward;profileKey=profile;iconItemId=icon;
            resolveCost=cost;this.enabled=enabled;this.reason=text(reason,160);cooldownRemainingTicks=remaining;cooldownTotalTicks=total;cooldownFraction=total==0?0:remaining/(float)total;requiresIssuedWeapon=requiresWeapon;
        }
    }
}
