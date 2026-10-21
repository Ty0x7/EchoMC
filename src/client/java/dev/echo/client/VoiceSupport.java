package dev.echo.client;

import com.google.gson.*;
import dev.echo.EchoConfig;
import dev.echo.net.EchoPackets;
import dev.echo.ai.BoundedHttp;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.text.Text;
import javax.sound.sampled.*;
import java.io.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Client-only audio: never opens a microphone until the user explicitly enables voice and holds V. */
public final class VoiceSupport {
    public static final class Config {
        public boolean voiceEnabled=false, speechEnabled=false, allowRemoteBackends=false;
        public String sttUrl="http://127.0.0.1:8090/transcribe", ttsUrl="http://127.0.0.1:8090/speech", keyEnv="ECHO_VOICE_KEY";
        public Config validate() {
            sttUrl=dev.echo.setup.ConfigFiles.text(sttUrl,"Parakeet URL",2048);ttsUrl=dev.echo.setup.ConfigFiles.text(ttsUrl,"Kokoro URL",2048);
            EchoConfig.endpoint(sttUrl,allowRemoteBackends);EchoConfig.endpoint(ttsUrl,allowRemoteBackends);keyEnv=dev.echo.setup.ConfigFiles.keyName(keyEnv);return this;
        }
    }
    private static volatile Config config=new Config();
    public static Config settings(){return EchoConfig.JSON.fromJson(EchoConfig.JSON.toJson(config),Config.class);}
    public static void apply(Config next) throws IOException {
        next.validate();dev.echo.setup.ConfigFiles.write(FabricLoader.getInstance().getConfigDir().resolve("echo-client.json"),next);
        config=EchoConfig.JSON.fromJson(EchoConfig.JSON.toJson(next),Config.class);held=false;speechGeneration.incrementAndGet();
    }
    public static void reload() throws IOException {
        Config next=EchoConfig.JSON.fromJson(Files.readString(FabricLoader.getInstance().getConfigDir().resolve("echo-client.json")),Config.class);
        if(next==null)throw new IllegalArgumentException("Empty voice configuration.");next.validate();config=next;held=false;speechGeneration.incrementAndGet();
    }
    private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
    private static final ThreadPoolExecutor WORK=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(4),r->{Thread t=new Thread(r,"echo-voice");t.setDaemon(true);return t;});
    private static volatile boolean held, capturing;
    private static boolean wasPressed;
    private static final AtomicInteger speechGeneration=new AtomicInteger();
    public static void load() {
        Path path=FabricLoader.getInstance().getConfigDir().resolve("echo-client.json");
        try {
            if(Files.exists(path)) reload();
            else { Files.createDirectories(path.getParent()); Files.writeString(path,EchoConfig.JSON.toJson(config)); }
            EchoConfig.endpoint(config.sttUrl,config.allowRemoteBackends); EchoConfig.endpoint(config.ttsUrl,config.allowRemoteBackends);
        } catch(Exception ex) { config=new Config(); }
    }
    public static void tick(MinecraftClient client, boolean pressed) {
        held=pressed;
        if(pressed && !wasPressed && !capturing) {
            if(!config.voiceEnabled) notice(client,"Press F8 or use /echo-setup to connect Parakeet and enable microphone commands.");
            else if(client.getNetworkHandler()!=null) {
                capturing=true; var session=client.getNetworkHandler();
                var settings=config;
                try { WORK.execute(()->record(client,session,settings)); } catch(RejectedExecutionException ex) { capturing=false; notice(client,"Voice is busy."); }
            }
        }
        wasPressed=pressed;
    }
    private static void record(MinecraftClient client, ClientPlayNetworkHandler session,Config settings) {
        AudioFormat format=new AudioFormat(16000,16,1,true,false);
        try (TargetDataLine mic=AudioSystem.getTargetDataLine(format); ByteArrayOutputStream pcm=new ByteArrayOutputStream()) {
            mic.open(format); mic.start(); notice(client,"Listening… release V to send (15-second limit).");
            byte[] buffer=new byte[1600];
            while(held && config==settings && client.getNetworkHandler()==session && pcm.size()<480000) { int n=mic.read(buffer,0,Math.min(buffer.length,480000-pcm.size())); if(n>0) pcm.write(buffer,0,n); }
            mic.stop();
            if(pcm.size()<3200 || config!=settings || client.getNetworkHandler()!=session) return;
            byte[] wav;
            try(ByteArrayOutputStream out=new ByteArrayOutputStream(); AudioInputStream audio=new AudioInputStream(new ByteArrayInputStream(pcm.toByteArray()),format,pcm.size()/2)) {
                AudioSystem.write(audio,AudioFileFormat.Type.WAVE,out); wav=out.toByteArray();
            }
            byte[] result=post(settings,settings.sttUrl,"audio/wav",wav,8192);
            String text=JsonParser.parseString(new String(result,StandardCharsets.UTF_8)).getAsJsonObject().get("text").getAsString().strip();
            if(text.isEmpty() || text.length()>1000) { notice(client,"No usable voice instruction recognized."); return; }
            client.execute(()-> {
                if(config!=settings || client.getNetworkHandler()!=session || client.player==null) return;
                if(ClientPlayNetworking.canSend(EchoPackets.Voice.ID)) { client.player.sendMessage(Text.literal("[You → Echo] "+text),false); ClientPlayNetworking.send(new EchoPackets.Voice(text)); }
            });
        } catch(Exception ex) { notice(client,"Voice unavailable. Check your microphone and Parakeet service; typed commands still work."); }
        finally { capturing=false; }
    }
    public static void speak(MinecraftClient client,String text) {
        var settings=config;
        if(!settings.speechEnabled) return;
        float gain=client.options.getSoundVolume(net.minecraft.sound.SoundCategory.MASTER)*client.options.getSoundVolume(net.minecraft.sound.SoundCategory.VOICE);
        if(gain<=0)return;
        var session=client.getNetworkHandler();
        int generation=speechGeneration.incrementAndGet();
        try { WORK.execute(()-> {
            try {
                if(held || generation!=speechGeneration.get()) return;
                JsonObject body=new JsonObject(); body.addProperty("text",text);
                byte[] wav=post(settings,settings.ttsUrl,"application/json",body.toString().getBytes(StandardCharsets.UTF_8),4_000_000);
                if(client.getNetworkHandler()!=session || held || generation!=speechGeneration.get()) return;
                try(AudioInputStream audio=AudioSystem.getAudioInputStream(new ByteArrayInputStream(wav)); SourceDataLine speaker=AudioSystem.getSourceDataLine(audio.getFormat())) {
                    if(audio.getFormat().getSampleSizeInBits()!=16 || audio.getFormat().isBigEndian() || !AudioFormat.Encoding.PCM_SIGNED.equals(audio.getFormat().getEncoding()))throw new IOException("Expected signed little-endian PCM16 WAV");
                    speaker.open(audio.getFormat()); speaker.start(); byte[] buffer=new byte[4096]; int n;
                    while((n=audio.read(buffer))!=-1 && client.getNetworkHandler()==session && !held && generation==speechGeneration.get()) {
                        for(int i=0;i+1<n;i+=2){int sample=(short)((buffer[i]&255)|(buffer[i+1]<<8));int scaled=Math.round(sample*gain);buffer[i]=(byte)scaled;buffer[i+1]=(byte)(scaled>>8);}
                        speaker.write(buffer,0,n);
                    }
                    speaker.drain(); speaker.stop();
                }
            } catch(Exception ex) { notice(client,"Kokoro speech unavailable; Echo's response is in chat."); }
        }); } catch(RejectedExecutionException ignored) { /* Drop stale speech instead of building an unbounded queue. */ }
    }
    private static byte[] post(Config settings,String url,String contentType,byte[] data,int max) throws Exception {
        var request=HttpRequest.newBuilder(EchoConfig.endpoint(url,settings.allowRemoteBackends)).timeout(Duration.ofSeconds(45))
            .header("Content-Type",contentType).POST(HttpRequest.BodyPublishers.ofByteArray(data));
        String key=System.getenv(settings.keyEnv); if(key!=null && !key.isBlank()) request.header("Authorization","Bearer "+key);
        var response=BoundedHttp.send(HTTP,request.build(),max,45);
        if(response.statusCode()!=200) throw new IOException("Voice service error"); return response.body();
    }
    private static void notice(MinecraftClient client,String message) { client.execute(()->{if(client.player!=null) client.player.sendMessage(Text.literal("[Echo voice] "+message),true);}); }
}
