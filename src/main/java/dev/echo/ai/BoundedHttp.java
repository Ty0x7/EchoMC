package dev.echo.ai;

import java.io.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

/** Bounds bytes during receipt and deadlines through the entire body, including slow/stalled streams. */
public final class BoundedHttp {
    public static HttpResponse<byte[]> send(HttpClient client, HttpRequest request, int maxBytes, int seconds) throws Exception {
        var future=client.sendAsync(request,info->new Body(maxBytes));
        try { return future.get(seconds,TimeUnit.SECONDS); }
        catch(TimeoutException | InterruptedException ex) { future.cancel(true); if(ex instanceof InterruptedException) Thread.currentThread().interrupt(); throw ex; }
    }
    private static final class Body implements HttpResponse.BodySubscriber<byte[]> {
        private final int max;
        private final ByteArrayOutputStream data=new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result=new CompletableFuture<>();
        private Flow.Subscription subscription;
        Body(int max) { this.max=max; }
        public CompletionStage<byte[]> getBody() { return result; }
        public void onSubscribe(Flow.Subscription s) { subscription=s; s.request(1); }
        public void onNext(List<ByteBuffer> chunks) {
            for(ByteBuffer buffer:chunks) {
                if(buffer.remaining()>max-data.size()) { subscription.cancel();result.completeExceptionally(new IOException("HTTP response exceeds limit"));return; }
                byte[] bytes=new byte[buffer.remaining()];buffer.get(bytes);data.writeBytes(bytes);
            }
            subscription.request(1);
        }
        public void onError(Throwable error) { result.completeExceptionally(error); }
        public void onComplete() { result.complete(data.toByteArray()); }
    }
}
