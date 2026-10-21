package dev.echo.ai;

import dev.echo.EchoConfig;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class BackendTest {
    @Test void rejectsRemoteCleartextAndEmbeddedCredentials() {
        for(String url:new String[]{"http://example.com/api","https://user:pass@example.com/api","file:///etc/passwd","http://127.0.0.1.evil.com/api"})
            assertThrows(RuntimeException.class,()->EchoConfig.endpoint(url,true));
        assertThrows(RuntimeException.class,()->EchoConfig.endpoint("https://example.com/api",false));
        assertEquals("localhost",EchoConfig.endpoint("http://localhost:8080/v1/chat/completions",false).getHost());
    }
    @Test void sendsStructuredRequestAndReadsCompatibleBackend() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/chat/completions",exchange->{
            String request=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
            var json=com.google.gson.JsonParser.parseString(request).getAsJsonObject();
            assertEquals("test-model",json.get("model").getAsString());
            assertEquals(2,json.getAsJsonArray("messages").size());
            byte[] response="{\"choices\":[{\"message\":{\"content\":\"{\\\"reply\\\":\\\"Hi\\\",\\\"steps\\\":[]}\"}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,response.length); exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try { String result=ModelBackend.complete("http://127.0.0.1:"+server.getAddress().getPort()+"/v1/chat/completions","test-model","ECHO_TEST_UNUSED",false,2,"rules","context"); assertEquals("Hi",Plan.parse(result).reply()); }
        finally { server.stop(0); }
    }
    @Test void oversizedResponseIsRejected() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{byte[] bytes=new byte[65537]; exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});server.start();
        try { assertThrows(Exception.class,()->ModelBackend.complete("http://127.0.0.1:"+server.getAddress().getPort()+"/","m","ECHO_TEST_UNUSED",false,2,"s","c")); }
        finally { server.stop(0); }
    }
    @Test void deadlineCoversAStalledResponseBody() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var release=new java.util.concurrent.CountDownLatch(1);
        server.createContext("/",exchange->{
            exchange.sendResponseHeaders(200,0); exchange.getResponseBody().write('x');exchange.getResponseBody().flush();
            try { release.await(5,java.util.concurrent.TimeUnit.SECONDS); } catch(InterruptedException ex) { Thread.currentThread().interrupt(); }
            exchange.close();
        });server.start();
        try {
            var req=java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/")).GET().build();
            assertThrows(java.util.concurrent.TimeoutException.class,()->BoundedHttp.send(java.net.http.HttpClient.newHttpClient(),req,1024,1));
        } finally { release.countDown();server.stop(0); }
    }
}
