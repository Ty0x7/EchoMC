package dev.echo.client;

import dev.echo.*;
import dev.echo.setup.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.text.Text;
import java.util.*;
import java.util.function.*;

/** Native Minecraft options, with explicit page saves and no automatic inference. */
public final class EchoSetupScreen extends Screen {
    private final Screen parent;
    private final EchoConfig server;
    private final VoiceSupport.Config voice;
    private int page;
    private String status="Services must already be running. Save each page, then test. F8 opens setup; hold V talks. No software or models are installed here.";
    private int statusColor=0xCCD5DA;
    private boolean busy;
    private int checkRevision;
    private final List<Runnable> fields=new ArrayList<>();
    private final List<String> labels=new ArrayList<>();
    private int left,panel,row;
    public EchoSetupScreen(Screen parent) {
        super(Text.literal("Echo setup"));this.parent=parent;server=EchoMod.CONFIG.copy();voice=VoiceSupport.settings();
    }
    private boolean localServer(){return client.getServer()!=null || client.getNetworkHandler()==null;}
    private ButtonWidget button(String label,int x,int y,int w,Consumer<ButtonWidget> action){
        var b=addDrawableChild(ButtonWidget.builder(Text.literal(label),action::accept).dimensions(x,y,w,20).build());
        if(label.contains("(inference)"))b.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Sends a synthetic request. Your service may load or download its configured weights. No player/world data or gameplay actions.")));
        return b;
    }
    private void field(String label,String value,int max,Consumer<String> update){
        labels.add(label);var input=new TextFieldWidget(textRenderer,left,row+10,panel,16,Text.literal(label));input.setMaxLength(max);input.setText(value);
        if(label.contains("environment"))input.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Enter only the variable name. Set its value before starting Minecraft/server. New environment values need a process restart.")));
        addDrawableChild(input);fields.add(()->update.accept(input.getText().strip()));row+=29;
    }
    private void flush(){for(Runnable update:fields)update.run();}
    private void message(String text,boolean ok){status=text;statusColor=ok?0xA8E6A3:0xFFD18C;}
    private void toggle(String label,BooleanSupplier get,Consumer<Boolean> set,int x,int y,int w){button(label+": "+(get.getAsBoolean()?"ON":"OFF"),x,y,w,b->{set.accept(!get.getAsBoolean());b.setMessage(Text.literal(label+": "+(get.getAsBoolean()?"ON":"OFF")));message("Unsaved changes. Use Save page to apply.",false);});}
    private void tab(int next){flush();page=next;checkRevision++;busy=false;status="Changes stay in this draft until you save their page.";clearAndInit();}
    @Override protected void init(){
        panel=Math.min(480,width-24);left=(width-panel)/2;row=53;fields.clear();labels.clear();
        String[] tabs={"Start","Planner","Kev","Voice","World"};int gap=3,w=(panel-4*gap)/5;
        for(int i=0;i<tabs.length;i++){int next=i;var b=button(tabs[i],left+i*(w+gap),28,w,bn->tab(next));b.active=i!=page;}
        if(page==0){
            button("1  Connect Gemma planner",left,57,panel,b->tab(1));
            button("2  Optional Kev reactions",left,82,panel,b->tab(2));
            button("3  Connect microphone and speech",left,107,panel,b->tab(3));
            button("4  Arrival and world awareness",left,132,panel,b->tab(4));
            button("Reload saved voice options",left,157,panel,b->{try{VoiceSupport.reload();var v=VoiceSupport.settings();voice.sttUrl=v.sttUrl;voice.ttsUrl=v.ttsUrl;voice.keyEnv=v.keyEnv;voice.voiceEnabled=v.voiceEnabled;voice.speechEnabled=v.speechEnabled;voice.allowRemoteBackends=v.allowRemoteBackends;message("Voice options reloaded. Server operators use /echo-config reload.",true);}catch(Exception ex){message("Could not reload voice options. Fix echo-client.json; current options stay active.",false);}});
        }else if(page!=3&&!localServer()){
            status="Multiplayer: these options belong to the server. Ask its operator to use /echo-config. Your voice options are in the Voice tab.";
        }else if(page==1){
            field("Full chat completion URL",server.plannerUrl,2048,v->server.plannerUrl=v);
            field("Exact model ID exposed by your inference server",server.plannerModel,160,v->server.plannerModel=v);
            field("Key environment variable name (never paste the key)",server.plannerKeyEnv,128,v->server.plannerKeyEnv=v);
            toggle("Planner",()->server.aiEnabled,v->server.aiEnabled=v,left,143,panel/2-2);
            toggle("Remote HTTPS",()->server.allowRemoteBackends,v->server.allowRemoteBackends=v,left+panel/2+2,143,panel/2-2);
            button("Check model list",left,167,panel/2-2,b->checkServer(0));
            button("Test reply (inference)",left+panel/2+2,167,panel/2-2,b->checkServer(1));
        }else if(page==2){
            field("Full native System One URL",server.kevUrl,2048,v->server.kevUrl=v);
            field("Kev request alias (server loads Kev-4B)",server.kevModel,160,v->server.kevModel=v);
            field("Key environment variable name",server.kevKeyEnv,128,v->server.kevKeyEnv=v);
            toggle("Kev",()->server.kevEnabled,v->server.kevEnabled=v,left,143,panel/2-2);
            toggle("Remote HTTPS",()->server.allowRemoteBackends,v->server.allowRemoteBackends=v,left+panel/2+2,143,panel/2-2);
            button("Test System One (inference)",left,167,panel,b->checkServer(2));
        }else if(page==3){
            field("Parakeet transcription URL",voice.sttUrl,2048,v->voice.sttUrl=v);
            field("Kokoro speech URL",voice.ttsUrl,2048,v->voice.ttsUrl=v);
            field("Voice key environment variable name",voice.keyEnv,128,v->voice.keyEnv=v);
            int w3=(panel-6)/3;
            toggle("Mic",()->voice.voiceEnabled,v->voice.voiceEnabled=v,left,143,w3);
            toggle("Speech",()->voice.speechEnabled,v->voice.speechEnabled=v,left+w3+3,143,w3);
            toggle("Remote",()->voice.allowRemoteBackends,v->voice.allowRemoteBackends=v,left+2*(w3+3),143,w3);
            button("Check Parakeet health",left,167,panel/2-2,b->checkVoice(false));
            button("Check Kokoro health",left+panel/2+2,167,panel/2-2,b->checkVoice(true));
        }else if(page==4){
            field("Hearing radius in blocks (8 to 64)",Integer.toString(server.hearingRadius),3,v->server.hearingRadius=number(v));
            field("Planner timeout in seconds (2 to 120)",Integer.toString(server.timeoutSeconds),3,v->server.timeoutSeconds=number(v));
            toggle("Automatic arrival on join",()->server.autoSpawnOnJoin,v->server.autoSpawnOnJoin=v,left,119,panel);
            status="Arrival changes affect future joins. Mining/build/explore permissions still use /echo allow. Combat defaults to defense.";
        }
        var save=button("Save page",left,height-24,panel/2-2,b->save());save.active=page!=0&&(page==3||localServer());
        button("Done",left+panel/2+2,height-24,panel/2-2,b->close());
    }
    private static int number(String s){try{return Integer.parseInt(s);}catch(NumberFormatException ex){return -1;}}
    private void checkServer(int kind){flush();try{var c=server.copy().validate();check(()->switch(kind){case 0->ConnectionChecks.models(c);case 1->ConnectionChecks.planner(c);default->ConnectionChecks.kev(c);});}catch(Exception ex){message("Check URLs, model IDs, environment names and Remote HTTPS option first.",false);}}
    private void checkVoice(boolean tts){flush();try{var c=EchoConfig.JSON.fromJson(EchoConfig.JSON.toJson(voice),VoiceSupport.Config.class).validate();check(()->ConnectionChecks.voice(tts?c.ttsUrl:c.sttUrl,c.keyEnv,c.allowRemoteBackends));}catch(Exception ex){message("Check both voice URLs, environment name and Remote option first.",false);}}
    private void check(ConnectionChecks.Check check){
        if(busy){message("A check is already running. Please wait.",false);return;}flush();busy=true;int revision=++checkRevision;
        message("Checking draft settings... no gameplay actions or microphone capture.",true);
        ConnectionChecks.submit(check,r->client.execute(()->{if(revision==checkRevision){busy=false;message(r.message(),r.ok());}}));
    }
    private void save(){
        flush();
        if(page==3){try{VoiceSupport.apply(voice);message("Saved. Mic: "+(voice.voiceEnabled?"ON (hold V in game)":"OFF")+"; speech: "+(voice.speechEnabled?"ON":"OFF")+". Typed chat stays available.",true);}catch(IllegalArgumentException ex){message("Check full voice URLs, Remote option and environment variable name.",false);}catch(Exception ex){message("Voice file could not be saved. Current options remain active.",false);}return;}
        if(!localServer())return;
        int savedPage=page;EchoConfig draft=server.copy();
        var integrated=client.getServer();
        Runnable persist=()->{
            try{
                var next=EchoMod.CONFIG.copy();
                if(savedPage==1){next.plannerUrl=draft.plannerUrl;next.plannerModel=draft.plannerModel;next.plannerKeyEnv=draft.plannerKeyEnv;next.aiEnabled=draft.aiEnabled;next.allowRemoteBackends=draft.allowRemoteBackends;}
                if(savedPage==2){next.kevUrl=draft.kevUrl;next.kevModel=draft.kevModel;next.kevKeyEnv=draft.kevKeyEnv;next.kevEnabled=draft.kevEnabled;next.allowRemoteBackends=draft.allowRemoteBackends;}
                if(savedPage==4){next.hearingRadius=draft.hearingRadius;next.timeoutSeconds=draft.timeoutSeconds;next.autoSpawnOnJoin=draft.autoSpawnOnJoin;}
                next.save();EchoMod.CONFIG=next;client.execute(()->message(integrated==null?"Saved for your next singleplayer world.":"Saved and applied to your singleplayer server. No restart needed.",true));
            }catch(IllegalArgumentException ex){client.execute(()->message("Not saved. Check values, ranges, full URLs and Remote HTTPS policy.",false));}
            catch(Exception ex){client.execute(()->message("Could not save the file. Existing runtime settings remain active.",false));}
        };
        if(integrated!=null)integrated.execute(persist);else persist.run();
    }
    @Override public void close(){checkRevision++;client.setScreen(parent);}
    @Override public void render(DrawContext ctx,int mx,int my,float delta){
        ctx.fill(0,0,width,height,0xF0161D1A);
        super.render(ctx,mx,my,delta);
        ctx.drawCenteredTextWithShadow(textRenderer,title,width/2,10,0xFFFFFF);
        for(int i=0;i<labels.size();i++)ctx.drawTextWithShadow(textRenderer,labels.get(i),left,53+i*29,0xD0D0D0);
        ctx.drawTextWrapped(textRenderer,Text.literal(status),left,height-62,panel,statusColor);
    }
}
