package dev.echo.setup;

import com.google.gson.*;
import dev.echo.EchoConfig;
import dev.echo.ai.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Explicit user diagnostics. Never records audio or sends player/world context. */
public final class ConnectionChecks {
    private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
    private static final ThreadPoolExecutor WORK=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(4),r->{Thread t=new Thread(r,"echo-setup-check");t.setDaemon(true);return t;});
    public record Result(boolean ok,String message) {}
    public interface Check { Result run() throws Exception; }
    public static void submit(Check check,Consumer<Result> callback) {
        try { WORK.execute(()->{Result r;try {r=check.run();}catch(Exception ex){r=new Result(false,"Connection failed or timed out. Start the service and check URL, model and key environment.");} callback.accept(r);}); }
        catch(RejectedExecutionException ex) { callback.accept(new Result(false,"Checks are busy. Wait a moment and retry.")); }
    }
    public static String keyStatus(String name) { return System.getenv(name)==null || System.getenv(name).isBlank()?"not set (fine for services without authentication)":"set"; }
    public static URI sibling(String url,String suffix,boolean remote) {
        URI base=EchoConfig.endpoint(url,remote);
        String path=base.getPath();
        if(path.endsWith("/chat/completions"))path=path.substring(0,path.length()-"chat/completions".length());
        else path=path.substring(0,path.lastIndexOf('/')+1);
        try { return new URI(base.getScheme(),null,base.getHost(),base.getPort(),path+suffix,null,null); }
        catch(Exception ex){throw new IllegalArgumentException("Cannot derive check URL.");}
    }
    private static HttpResponse<byte[]> get(URI uri,String keyEnv) throws Exception {
        var request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).GET();
        String key=System.getenv(keyEnv);if(key!=null&&!key.isBlank())request.header("Authorization","Bearer "+key);
        return BoundedHttp.send(HTTP,request.build(),65536,5);
    }
    private static Result error(int status) {
        return new Result(false,switch(status){case 401,403->"Authentication rejected. Set the named key variable before starting Minecraft or the server.";case 404->"Check endpoint not found. Verify the URL and service API; this does not prove inference is broken.";case 429,503->"Service is busy or loading. Wait and retry.";default->"Service returned HTTP "+status+". Verify its configuration.";});
    }
    public static Result models(EchoConfig c) throws Exception {
        c.validate();var r=get(sibling(c.plannerUrl,"models",c.allowRemoteBackends),c.plannerKeyEnv);
        if(r.statusCode()!=200)return error(r.statusCode());
        var data=JsonParser.parseString(new String(r.body(),StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("data");
        if(data==null)return new Result(false,"Service replied, but model-list JSON is incompatible. Try Test reply.");
        boolean match=false;StringBuilder ids=new StringBuilder();
        for(var value:data){String id=value.getAsJsonObject().get("id").getAsString();if(id.equals(c.plannerModel))match=true;String clean=id.replaceAll("[\\p{Cntrl}]","");if(ids.length()<150)ids.append(ids.isEmpty()?"":", ").append(clean,0,Math.min(clean.length(),60));}
        return new Result(match,match?"Model is listed. Test reply next to check Echo's JSON contract.":"Model not listed. Available: "+(ids.isEmpty()?"none; load a model in your inference service.":ids.toString().substring(0,Math.min(ids.length(),180))));
    }
    public static Result planner(EchoConfig c) throws Exception {
        c.validate();
        Plan p=Plan.parse(ModelBackend.complete(c.plannerUrl,c.plannerModel,c.plannerKeyEnv,c.allowRemoteBackends,Math.min(c.timeoutSeconds,45),
            "Return only JSON with reply and steps. No actions. Example: {\"reply\":\"Echo connected\",\"steps\":[]}","Connection check only. No player or world data."));
        return new Result(p.steps().isEmpty(),p.steps().isEmpty()?"Planner returned valid conversation JSON. No gameplay actions ran.":"Planner returned actions during a conversation-only check. No actions ran.");
    }
    public static Result kev(EchoConfig c) throws Exception {
        c.validate();KevBackend.decide(c,"Setup check only: healthy companion, no hostile threat, owner nearby. No actual Minecraft state.");
        return new Result(true,"Kev native System One response parsed. This does not measure combat accuracy.");
    }
    public static Result voice(String url,String keyEnv,boolean remote) throws Exception {
        var r=get(sibling(url,"health",remote),keyEnv);if(r.statusCode()!=200)return error(r.statusCode());
        var json=JsonParser.parseString(new String(r.body(),StandardCharsets.UTF_8)).getAsJsonObject();
        if(!json.has("status") || !"ready".equals(json.get("status").getAsString()))return new Result(false,"Health replied without ready status. Verify the voice service adapter.");
        boolean asr=json.has("parakeet_loaded")&&json.get("parakeet_loaded").getAsBoolean();
        boolean tts=json.has("kokoro_loaded")&&json.get("kokoro_loaded").getAsBoolean();
        return new Result(true,"Service ready. Parakeet loaded: "+asr+"; Kokoro loaded: "+tts+". Health does not run inference.");
    }
}
