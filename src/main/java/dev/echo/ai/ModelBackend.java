package dev.echo.ai;

import com.google.gson.*;
import dev.echo.EchoConfig;
import java.net.http.*;
import java.time.Duration;
import java.io.*;
import java.nio.charset.StandardCharsets;

public final class ModelBackend {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    public static String complete(String url, String model, String keyEnv, boolean remote, int timeout, String system, String context) throws Exception {
        JsonObject body = new JsonObject(); body.addProperty("model", model); body.addProperty("temperature", 0.35); body.addProperty("max_tokens", 1400);
        JsonObject format = new JsonObject(); format.addProperty("type", "json_object"); body.add("response_format", format);
        JsonArray messages = new JsonArray();
        for (String[] m : new String[][]{{"system", system}, {"user", context}}) {
            JsonObject msg = new JsonObject(); msg.addProperty("role", m[0]); msg.addProperty("content", m[1]); messages.add(msg);
        }
        body.add("messages", messages);
        HttpRequest.Builder request = HttpRequest.newBuilder(EchoConfig.endpoint(url, remote)).timeout(Duration.ofSeconds(timeout))
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        String key = System.getenv(keyEnv);
        if (key != null && !key.isBlank()) request.header("Authorization", "Bearer " + key);
        HttpResponse<byte[]> response = BoundedHttp.send(HTTP, request.build(), 65536, timeout);
        if (response.statusCode() != 200) throw new IOException("Backend HTTP " + response.statusCode());
        return JsonParser.parseString(new String(response.body(), StandardCharsets.UTF_8)).getAsJsonObject()
                    .getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message").get("content").getAsString();
    }
}
