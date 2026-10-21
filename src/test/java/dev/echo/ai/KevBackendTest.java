package dev.echo.ai;

import dev.echo.EchoConfig;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KevBackendTest {
    private String response(String choice,double probability,double danger) {
        return "{\"model\":\"kev-latest\",\"answers\":{\"reaction\":{\"type\":\"choice\",\"choice\":\""+choice+"\",\"probabilities\":{\""+choice+"\":"+probability+"}},\"danger\":{\"type\":\"noul\",\"noul\":"+danger+"}}}";
    }
    @Test void nativeSchemaHasStateAndIndependentQuestions() {
        var req=KevBackend.request("kev-latest","health=30");
        assertFalse(req.has("messages")); assertEquals("health=30",req.get("state").getAsString());
        var questions=req.getAsJsonObject("questions"); assertEquals("choice",questions.getAsJsonObject("reaction").get("type").getAsString());
        assertEquals("noul",questions.getAsJsonObject("danger").get("type").getAsString());
    }
    @Test void confidenceAndDangerGateImmediateDecisions() {
        assertEquals(KevBackend.Decision.ENGAGE,KevBackend.parse(response("engage",0.9,0.1)));
        assertEquals(KevBackend.Decision.VANILLA,KevBackend.parse(response("engage",0.4,0.1)));
        assertEquals(KevBackend.Decision.RETREAT,KevBackend.parse(response("engage",0.9,0.9)));
        assertThrows(RuntimeException.class,()->KevBackend.parse(response("run_command",1,0)));
        assertThrows(RuntimeException.class,()->KevBackend.parse(response("engage",2,0)));
    }
    @Test void postsToNativeSystemOneEndpoint() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/systemone",exchange->{
            var body=com.google.gson.JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
            assertTrue(body.has("state")); assertTrue(body.has("questions")); assertFalse(body.has("messages"));
            byte[] bytes=response("defend",0.85,0.1).getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        }); server.start();
        try { var config=new EchoConfig(); config.kevUrl="http://127.0.0.1:"+server.getAddress().getPort()+"/v1/systemone";
            assertEquals(KevBackend.Decision.DEFEND,KevBackend.decide(config,"health=30; owner needs cover")); }
        finally { server.stop(0); }
    }
}
