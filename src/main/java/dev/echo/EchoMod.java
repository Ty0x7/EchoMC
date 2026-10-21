package dev.echo;

import dev.echo.ai.Brain;
import dev.echo.entity.EchoEntity;
import dev.echo.net.EchoPackets;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import dev.echo.world.EchoArrival;
import net.minecraft.entity.*;
import net.minecraft.registry.*;
import net.minecraft.util.Identifier;
import org.slf4j.*;

public final class EchoMod implements ModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("echo");
    public static volatile EchoConfig CONFIG;
    public static final net.minecraft.item.Item CUBE = Registry.register(Registries.ITEM, Identifier.of("echo", "cube"),
        new dev.echo.item.EchoCubeItem(new net.minecraft.item.Item.Settings().maxCount(1).fireproof()));
    public static final EntityType<EchoEntity> COMPANION = Registry.register(Registries.ENTITY_TYPE, Identifier.of("echo", "companion"),
        EntityType.Builder.create(EchoEntity::new, SpawnGroup.CREATURE).dimensions(0.3125f,0.3125f).maxTrackingRange(10).build("echo:companion"));
    @Override public void onInitialize() {
        CONFIG=EchoConfig.load(); FabricDefaultAttributeRegistry.register(COMPANION,EchoEntity.attributes());
        ServerEntityEvents.ENTITY_LOAD.register((entity,world)->{if(entity instanceof EchoEntity e)EchoArrival.bind(e);});
        PayloadTypeRegistry.playC2S().register(EchoPackets.Voice.ID,EchoPackets.Voice.CODEC);
        PayloadTypeRegistry.playS2C().register(EchoPackets.Speech.ID,EchoPackets.Speech.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(EchoPackets.Voice.ID,(payload,context)->context.server().execute(()-> {
            EchoCommunication.message(context.player(),payload.text());
        }));
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->{EchoCommands.register(dispatcher);dev.echo.setup.SetupCommands.register(dispatcher);});
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->EchoArrival.queue(handler.player,false));
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{EchoCommunication.disconnect(handler.player);EchoArrival.park(handler.player);});
        ServerTickEvents.END_SERVER_TICK.register(server->{EchoArrival.tick(server);EchoCommunication.tick(server);});
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{EchoArrival.stop(server);EchoCommunication.stop();Brain.shutdown();});
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message,player,params)->{
            String content=message.getContent().getString();
            if(content.toLowerCase(java.util.Locale.ROOT).startsWith("@echo ")) {
                String instruction=content.substring(6).strip();player.sendMessage(net.minecraft.text.Text.literal("[You → Echo] "+instruction),false);EchoCommunication.message(player,instruction);return false;
            }
            return true;
        });
        LOG.info("Echo companion initialized; planner enabled={}",CONFIG.aiEnabled);
    }
}
