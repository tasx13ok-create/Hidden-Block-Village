package village.vale;

import java.util.Objects;
import java.util.UUID;

/** Immutable owner-only HUD contract. Values originate on the server, never inferred from client NBT. */
public final class HudSnapshot {
    public static final int SCHEMA=1;
    public final int schemaVersion=1;
    public final UUID playerId;
    public final int dimension;
    public final long serverTick;
    public final double resolve,resolveMax;
    public final int level;
    public final long merit;
    public final String selectedArtId,selectedArtName;
    public final int cooldownRemainingTicks,cooldownTotalTicks;
    public final String activeFormId,activeFormName,companionId,companionName;
    public final String contractId,contractName;
    public final int contractProgress,contractGoal;
    public final long castSequence,lastCastServerTick;
    public final String lastCastArtId;

    public HudSnapshot(UUID playerId,int dimension,long serverTick,double resolve,double resolveMax,int level,long merit,
            String selectedArtId,String selectedArtName,int cooldownRemainingTicks,int cooldownTotalTicks,
            String activeFormId,String activeFormName,String companionId,String companionName,
            String contractId,String contractName,int contractProgress,int contractGoal,
            long castSequence,long lastCastServerTick,String lastCastArtId){
        if(serverTick<0||!Double.isFinite(resolve)||!Double.isFinite(resolveMax)||resolveMax<=0||resolveMax>1000||resolve<0||resolve>resolveMax||level<1||level>20||merit<0||merit>1000000)throw new IllegalArgumentException("Invalid HUD resource/progression bounds");
        if(cooldownRemainingTicks<0||cooldownTotalTicks<0||cooldownRemainingTicks>cooldownTotalTicks||cooldownTotalTicks>24000||contractGoal<0||contractGoal>1000000||contractProgress<0||contractProgress>contractGoal||castSequence<0||lastCastServerTick<0||lastCastServerTick>serverTick)throw new IllegalArgumentException("Invalid HUD timer/progress bounds");
        this.playerId=Objects.requireNonNull(playerId,"playerId");this.dimension=dimension;this.serverTick=serverTick;this.resolve=resolve;this.resolveMax=resolveMax;this.level=level;this.merit=merit;
        this.selectedArtId=text(selectedArtId);this.selectedArtName=text(selectedArtName);this.cooldownRemainingTicks=cooldownRemainingTicks;this.cooldownTotalTicks=cooldownTotalTicks;
        this.activeFormId=text(activeFormId);this.activeFormName=text(activeFormName);this.companionId=text(companionId);this.companionName=text(companionName);
        this.contractId=text(contractId);this.contractName=text(contractName);this.contractProgress=contractProgress;this.contractGoal=contractGoal;
        this.castSequence=castSequence;this.lastCastServerTick=lastCastServerTick;this.lastCastArtId=text(lastCastArtId);
    }
    private static String text(String value){Objects.requireNonNull(value,"HUD text");if(value.length()>256)throw new IllegalArgumentException("HUD text too long");return value;}
}
