package village.residents;

import java.util.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.*;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import village.vale.Arts;
import village.vale.Missions;
import village.vale.Quartermaster;

/** Server-authoritative, short-lived resident conversation choices. No arbitrary commands. */
public final class DialogueSessions {
    private static final int CAP=256;private static final long TTL=600;
    private static final LinkedHashMap<UUID,Session> ACTIVE=new LinkedHashMap<>();
    private static final class Session {
        final UUID actor;final World world;final ResidentRole role;final String token,page;final long created,generation;
        Session(EntityResident actor,String page){this.actor=actor.getUniqueID();world=actor.world;role=actor.interactiveRole();token=UUID.randomUUID().toString();this.page=page;created=world.getTotalWorldTime();generation=DialogueBook.generation();}
    }
    private DialogueSessions(){}
    public static void clear(){ACTIVE.clear();}
    public static void logout(UUID player){ACTIVE.remove(player);}
    public static void unload(World world){ACTIVE.values().removeIf(s->s.world==world);}
    private static boolean accessible(EntityResident actor,EntityPlayerMP player){return actor!=null&&actor.isEntityAlive()&&actor.interactiveRole()!=null&&player.world==actor.world&&player.isEntityAlive()&&!player.isSpectator()&&player.getDistanceSq(actor)<=36;}
    public static boolean open(EntityResident actor,EntityPlayerMP player){if(!accessible(actor,player))return false;return page(actor,player,DialogueBook.current().roles.get(actor.interactiveRole()).start);}
    private static boolean page(EntityResident actor,EntityPlayerMP player,String pageId){
        DialogueBook.Page page=DialogueBook.current().roles.get(actor.interactiveRole()).pages.get(pageId);if(page==null)return false;
        ACTIVE.remove(player.getUniqueID());while(ACTIVE.size()>=CAP)ACTIVE.remove(ACTIVE.keySet().iterator().next());
        Session s=new Session(actor,pageId);ACTIVE.put(player.getUniqueID(),s);
        for(String line:page.lines)player.sendMessage(new TextComponentString(actor.getCustomNameTag()+": "+line));
        for(DialogueBook.Choice choice:page.choices){TextComponentString line=new TextComponentString("["+choice.label+"]");line.getStyle().setColor(TextFormatting.AQUA).setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/residentchoose "+s.token+" "+choice.id));player.sendMessage(line);}return true;
    }
    public static boolean choose(EntityPlayerMP player,String token,String choiceId){
        Session s=ACTIVE.get(player.getUniqueID());
        if(s==null||!s.token.equals(token))return deny(player,"This conversation has ended. Speak to the resident again.");
        long now=player.world.getTotalWorldTime();
        if(player.world!=s.world||now<s.created||now-s.created>TTL||s.generation!=DialogueBook.generation()) {ACTIVE.remove(player.getUniqueID());return deny(player,"This conversation has expired. Speak to the resident again.");}
        Entity entity=((WorldServer)player.world).getEntityFromUuid(s.actor);
        if(!(entity instanceof EntityResident)||!accessible((EntityResident)entity,player)||((EntityResident)entity).interactiveRole()!=s.role){ACTIVE.remove(player.getUniqueID());return deny(player,"Move close to the resident and speak again.");}
        DialogueBook.Page page=DialogueBook.current().roles.get(s.role).pages.get(s.page);DialogueBook.Choice selected=null;
        for(DialogueBook.Choice c:page.choices)if(c.id.equals(choiceId)){selected=c;break;}
        if(selected==null)return deny(player,"That choice is not part of this conversation.");
        ACTIVE.remove(player.getUniqueID()); // Consume before dispatch so a replay never repeats a claim.
        switch(selected.action){
            case PAGE:return page((EntityResident)entity,player,selected.target);
            case ACADEMY:Arts.show(player);return true;
            case CONTRACTS:Missions.show(player);return true;
            case QUARTERMASTER:Quartermaster.show(player,"ward");return true;
            case TOOLS:Quartermaster.show(player,"tools");return true;
            case SPIRIT:Quartermaster.show(player,"spirit");return true;
            case ACCEPT_CONTRACT:return Missions.accept(player,selected.target);
            case CLAIM_CONTRACT:return Missions.claim(player);
            default:return false;
        }
    }
    private static boolean deny(EntityPlayerMP p,String reason){p.sendMessage(new TextComponentString(reason));return false;}
}
