package dev.echo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
import java.net.URI;

public final class EchoConfig {
    public static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    public boolean aiEnabled = false;
    public boolean autoSpawnOnJoin = true;
    public int hearingRadius = 32;
    public String plannerUrl = "http://127.0.0.1:8080/v1/chat/completions";
    public String plannerModel = "gemma-4-12b";
    public String plannerKeyEnv = "ECHO_PLANNER_KEY";
    public boolean kevEnabled = false;
    public String kevUrl = "http://127.0.0.1:8009/v1/systemone";
    // Kev's server chooses the checkpoint at startup; its API model alias is kev-latest.
    public String kevModel = "kev-latest";
    public String kevKeyEnv = "ECHO_KEV_KEY";
    public boolean allowRemoteBackends = false;
    public int timeoutSeconds = 45;
    public static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("echo-server.json"); }
    public EchoConfig copy() { return JSON.fromJson(JSON.toJson(this),EchoConfig.class); }
    public EchoConfig validate() {
        plannerUrl=dev.echo.setup.ConfigFiles.text(plannerUrl,"Planner URL",2048);
        kevUrl=dev.echo.setup.ConfigFiles.text(kevUrl,"Kev URL",2048);
        endpoint(plannerUrl,allowRemoteBackends); endpoint(kevUrl,allowRemoteBackends);
        plannerModel=dev.echo.setup.ConfigFiles.text(plannerModel,"Planner model",160);
        kevModel=dev.echo.setup.ConfigFiles.text(kevModel,"Kev model alias",160);
        plannerKeyEnv=dev.echo.setup.ConfigFiles.keyName(plannerKeyEnv);
        kevKeyEnv=dev.echo.setup.ConfigFiles.keyName(kevKeyEnv);
        if(timeoutSeconds<2 || timeoutSeconds>120) throw new IllegalArgumentException("Planner timeout must be 2 to 120 seconds.");
        if(hearingRadius<8 || hearingRadius>64) throw new IllegalArgumentException("Hearing radius must be 8 to 64 blocks.");
        return this;
    }
    public void save() throws java.io.IOException { validate();dev.echo.setup.ConfigFiles.write(path(),this); }
    public static EchoConfig read() throws java.io.IOException {
        EchoConfig c=JSON.fromJson(Files.readString(path()),EchoConfig.class);
        if(c==null)throw new IllegalArgumentException("Empty server configuration.");
        return c.validate();
    }
    public static EchoConfig load() {
        Path path = path();
        try {
            if (Files.exists(path)) {
                return read();
            }
            EchoConfig c = new EchoConfig(); Files.createDirectories(path.getParent()); Files.writeString(path, JSON.toJson(c)); return c;
        } catch (Exception ex) { EchoMod.LOG.warn("Echo config invalid; using offline defaults (no credentials logged)"); return new EchoConfig(); }
    }
    public static URI endpoint(String address, boolean remote) {
        URI uri;
        try { uri=URI.create(address); } catch(Exception ex) { throw new IllegalArgumentException("Enter a complete HTTP or HTTPS service URL."); }
        String h = uri.getHost();
        boolean local = "127.0.0.1".equals(h) || "localhost".equals(h) || "[::1]".equals(h) || "::1".equals(h);
        if (h == null || uri.getUserInfo() != null || uri.getFragment() != null || (!local && !remote)
                || (!"https".equals(uri.getScheme()) && !(local && "http".equals(uri.getScheme()))))
            throw new IllegalArgumentException("Use loopback HTTP or explicitly enabled remote HTTPS");
        return uri;
    }
}
