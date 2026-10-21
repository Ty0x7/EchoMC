package dev.echo.client;

import dev.echo.EchoMod;
import dev.echo.entity.EchoEntity;
import dev.echo.net.EchoPackets;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.render.entity.model.*;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public final class EchoClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        EntityModelLayerRegistry.registerModelLayer(EchoCubeModel.LAYER,EchoCubeModel::data);
        EntityRendererRegistry.register(EchoMod.COMPANION, Renderer::new);
        VoiceSupport.load();
        KeyBinding setup=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.echo.setup",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_F8,"key.categories.echo"));
        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((dispatcher,access)->dispatcher.register(
            net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("echo-setup").executes(ctx->{ctx.getSource().getClient().execute(()->ctx.getSource().getClient().setScreen(new EchoSetupScreen(null)));return 1;})));
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client,screen,w,h)->{
            if(screen instanceof net.minecraft.client.gui.screen.GameMenuScreen || screen instanceof net.minecraft.client.gui.screen.TitleScreen)
                net.fabricmc.fabric.api.client.screen.v1.Screens.getButtons(screen).add(net.minecraft.client.gui.widget.ButtonWidget.builder(net.minecraft.text.Text.literal("Echo setup"),b->client.setScreen(new EchoSetupScreen(screen))).dimensions(8,8,96,20).build());
        });
        KeyBinding talk = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.echo.voice", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.echo"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while(setup.wasPressed())if(!(client.currentScreen instanceof EchoSetupScreen))client.setScreen(new EchoSetupScreen(client.currentScreen));
            VoiceSupport.tick(client, talk.isPressed() && client.currentScreen == null && client.player != null);
        });
        ClientPlayNetworking.registerGlobalReceiver(EchoPackets.Speech.ID, (payload, context) -> context.client().execute(()->VoiceSupport.speak(context.client(),payload.text())));
    }
    private static final class Renderer extends MobEntityRenderer<EchoEntity, EchoCubeModel> {
        Renderer(EntityRendererFactory.Context ctx) {
            super(ctx, new EchoCubeModel(ctx.getPart(EchoCubeModel.LAYER)), 0.14f);
        }
        @Override public Identifier getTexture(EchoEntity entity) {
            boolean closed=entity.companionPose()==dev.echo.entity.CompanionIdle.Pose.SLEEP
                || (entity.companionPose()==dev.echo.entity.CompanionIdle.Pose.WAKE && entity.poseTime(0)<6)
                || (entity.age+entity.getId()*13)%100<4;
            return Identifier.of("echo", "textures/entity/"+(closed?"echo_blink.png":"echo.png"));
        }
    }
}
