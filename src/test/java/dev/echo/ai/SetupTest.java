package dev.echo.ai;

import dev.echo.*;
import dev.echo.setup.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class SetupTest {
    @TempDir Path temp;
    @Test void copiedSettingsValidateWithoutChangingPublishedValues() {
        EchoConfig current=new EchoConfig();EchoConfig next=current.copy();next.plannerModel="  model-id  ";next.validate();
        assertEquals("model-id",next.plannerModel);assertEquals("gemma-4-12b",current.plannerModel);
        next.hearingRadius=65;assertThrows(IllegalArgumentException.class,next::validate);
        next.hearingRadius=32;next.plannerKeyEnv="secret key pasted here";assertThrows(IllegalArgumentException.class,next::validate);
    }
    @Test void atomicSaveRetainsUnrelatedConfigurationFields() throws Exception {
        EchoConfig c=new EchoConfig();c.hearingRadius=48;c.kevEnabled=true;
        Path path=temp.resolve("echo-server.json");ConfigFiles.write(path,c);
        EchoConfig parsed=EchoConfig.JSON.fromJson(Files.readString(path),EchoConfig.class).validate();
        assertEquals(48,parsed.hearingRadius);assertTrue(parsed.kevEnabled);
        try(var files=Files.list(temp)){assertEquals(1,files.count());}
    }
    @Test void derivedChecksKeepProxyPrefixAndDropQueryAndCredentials() {
        assertEquals("https://example.com/proxy/v1/models",ConnectionChecks.sibling("https://example.com/proxy/v1/chat/completions?ignored=1","models",true).toString());
        assertEquals("http://127.0.0.1:8090/voice/health",ConnectionChecks.sibling("http://127.0.0.1:8090/voice/transcribe","health",false).toString());
        assertThrows(IllegalArgumentException.class,()->ConnectionChecks.sibling("https://user:secret@example.com/api","health",true));
    }
    private HttpServer fixture(String route,String body,AtomicInteger posts) throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext(route,exchange->{if(exchange.getRequestMethod().equals("POST"))posts.incrementAndGet();byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});server.start();return server;
    }
    @Test void modelListRequiresExactConfiguredAliasAndNeverInfers() throws Exception {
        AtomicInteger posts=new AtomicInteger();var s=fixture("/v1/models","{\"data\":[{\"id\":\"my-loaded-model\"}]}",posts);
        try{EchoConfig c=new EchoConfig();c.plannerUrl="http://127.0.0.1:"+s.getAddress().getPort()+"/v1/chat/completions";
            assertFalse(ConnectionChecks.models(c).ok());c.plannerModel="my-loaded-model";assertTrue(ConnectionChecks.models(c).ok());assertEquals(0,posts.get());
        }finally{s.stop(0);}
    }
    @Test void voiceHealthDistinguishesProcessFromLoadedWeightsWithoutInference() throws Exception {
        AtomicInteger posts=new AtomicInteger();var s=fixture("/health","{\"status\":\"ready\",\"parakeet_loaded\":false,\"kokoro_loaded\":false}",posts);
        try{var r=ConnectionChecks.voice("http://127.0.0.1:"+s.getAddress().getPort()+"/speech","ECHO_TEST_UNUSED",false);assertTrue(r.ok());assertTrue(r.message().contains("loaded: false"));assertEquals(0,posts.get());}finally{s.stop(0);}
    }
    @Test void plannerProbeRejectsActionsAndDoesNotExecuteThem() throws Exception {
        AtomicInteger posts=new AtomicInteger();var s=fixture("/v1/chat/completions","{\"choices\":[{\"message\":{\"content\":\"{\\\"reply\\\":\\\"Hi\\\",\\\"steps\\\":[{\\\"action\\\":\\\"follow\\\"}]}\"}}]}",posts);
        try{EchoConfig c=new EchoConfig();c.plannerUrl="http://127.0.0.1:"+s.getAddress().getPort()+"/v1/chat/completions";assertFalse(ConnectionChecks.planner(c).ok());assertEquals(1,posts.get());}finally{s.stop(0);}
    }
    @Test void malformedHealthCannotClaimReadiness() throws Exception {
        AtomicInteger posts=new AtomicInteger();var s=fixture("/health","{\"ok\":true}",posts);
        try{assertFalse(ConnectionChecks.voice("http://127.0.0.1:"+s.getAddress().getPort()+"/speech","ECHO_TEST_UNUSED",false).ok());}finally{s.stop(0);}
    }
}
