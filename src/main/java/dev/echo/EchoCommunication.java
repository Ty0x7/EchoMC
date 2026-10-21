package dev.echo;

import dev.echo.ai.Brain;
import dev.echo.world.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import java.util.*;

public final class EchoCommunication {
    private record Waiting(String text,int ticks) { }
    private static final Map<UUID,Waiting> waiting=new HashMap<>();
    public static void disconnect(ServerPlayerEntity p){waiting.remove(p.getUuid());}
    public static void stop(){waiting.clear();}
    public static void tick(net.minecraft.server.MinecraftServer server){
        for(UUID owner:List.copyOf(waiting.keySet())){
            var p=server.getPlayerManager().getPlayer(owner);if(p==null){waiting.remove(owner);continue;}
            var request=waiting.get(owner);
            if(request.ticks()%10==0 && EchoArrival.loaded(p)!=null){waiting.remove(owner);message(p,request.text());}
            else if(request.ticks()<=0){waiting.remove(owner);p.sendMessage(Text.literal("[Echo] I couldn't reconnect to my saved location. Use /echo here or /echo locate; I haven't executed your instruction."),false);}
            else waiting.put(owner,new Waiting(request.text(),request.ticks()-1));
        }
    }
    public static void message(ServerPlayerEntity player,String message) {
        if(message.isBlank() || message.length()>1000)return;
        String normalized=message.strip().toLowerCase(Locale.ROOT).replaceAll("[.!?]+$","");
        normalized=normalized.replaceFirst("^echo[, :]\\s*","");
        if(Set.of("stop","cancel").contains(normalized)) {
            EchoArrival.cancel(player);waiting.remove(player.getUuid());var active=EchoArrival.loaded(player);
            if(active!=null)active.cancel();else player.sendMessage(Text.literal("[Echo] Cancelled pending recall/message."),false);return;
        }
        if(normalized.matches("(?:please )?(?:tp|teleport)(?: over)? (?:to me|here)(?: please)?")) {EchoArrival.queue(player,true);return;}
        var echo=EchoArrival.loaded(player);
        if(echo==null) {
            var ledger=CompanionLedger.get(player.getServer());var id=ledger.companions.get(player.getUuid());
            if(id!=null && !ledger.carried.containsKey(id) && !waiting.containsKey(player.getUuid()) && EchoArrival.wake(player)) {
                waiting.put(player.getUuid(),new Waiting(message,200));player.sendMessage(Text.literal("[Echo] Reconnecting to my last known area. Your message is queued; I won't teleport unless you ask."),false);return;
            }
            player.sendMessage(Text.literal("[Echo] I'm carried, parked, or in an unloaded area. Place my cube down, or use /echo here to reconnect with me."),false);return;
        }
        long now=player.getServerWorld().getTime();if(now-echo.lastVoice<20)return;echo.lastVoice=now;
        echo.wakeForInteraction();
        if(Set.of("what did you hear","what was that sound","what can you hear").contains(normalized)) {echo.say("Recent game sounds near me: "+echo.hearing.summary(echo.getWorld().getTime()));return;}
        Brain.request(echo,player,Set.of("follow","follow me","come with me","stay","stay here","wait here").contains(normalized)?normalized:message);
    }
}
