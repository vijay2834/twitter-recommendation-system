package com.vpalla.recsys;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * HTTP API (JDK HttpServer on virtual threads):
 *   GET /recommend?user_id=..&type=reply|retweet|both&phrase=..&limit=10
 *   GET /healthz   liveness          GET /readyz   readiness (DB reachable)
 *   GET /metrics   Prometheus text format
 */
public final class App {
    private static final ObjectMapper JSON = new ObjectMapper();

    public static HttpServer start(RecommendationService svc, int port) throws IOException {
        // Disable Nagle's algorithm: without this, small JSON responses stall ~40 ms on delayed ACKs.
        System.setProperty("sun.net.httpserver.nodelay", "true");
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 4096);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/recommend", ex -> {
            try {
                Map<String, String> q = query(ex);
                long uid = Long.parseLong(q.get("user_id"));
                int limit = Integer.parseInt(q.getOrDefault("limit", "10"));
                send(ex, 200, JSON.writeValueAsBytes(svc.recommend(uid, Model.Type.parse(q.get("type")), q.get("phrase"), limit)));
            } catch (NumberFormatException | NullPointerException e) {
                send(ex, 400, "{\"error\":\"user_id (integer) is required\"}".getBytes());
            } catch (IllegalArgumentException e) {
                send(ex, 400, "{\"error\":\"type must be reply, retweet or both\"}".getBytes());
            } catch (Exception e) {
                send(ex, 500, ("{\"error\":\"" + e.getClass().getSimpleName() + "\"}").getBytes());
            }
        });
        server.createContext("/healthz", ex -> send(ex, 200, "ok".getBytes()));
        server.createContext("/readyz", ex -> send(ex, svc.healthy() ? 200 : 503, (svc.healthy() ? "ready" : "db unavailable").getBytes()));
        server.createContext("/metrics", ex -> send(ex, 200, ("""
                # TYPE recsys_requests_total counter
                recsys_requests_total %d
                # TYPE recsys_cache_hits_total counter
                recsys_cache_hits_total %d
                """.formatted(svc.requests.get(), svc.cacheHits.get())).getBytes()));
        server.start();
        return server;
    }

    static Map<String, String> query(HttpExchange ex) {
        Map<String, String> out = new HashMap<>();
        String raw = ex.getRequestURI().getRawQuery();
        if (raw == null) return out;
        for (String kv : raw.split("&")) {
            int i = kv.indexOf('=');
            if (i > 0) out.put(kv.substring(0, i), URLDecoder.decode(kv.substring(i + 1), StandardCharsets.UTF_8));
        }
        return out;
    }

    static void send(HttpExchange ex, int code, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", body.length > 0 && body[0] == '{' ? "application/json" : "text/plain");
        ex.sendResponseHeaders(code, body.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(body); }
    }

    public static void main(String[] args) throws IOException {
        String url = System.getenv().getOrDefault("DB_URL", "jdbc:mysql://localhost:3306/recsys");
        int pool = Integer.parseInt(System.getenv().getOrDefault("DB_POOL_SIZE", "32"));
        var repo = new JdbcRepository(url, System.getenv().getOrDefault("DB_USER", "recsys"),
                System.getenv().getOrDefault("DB_PASSWORD", ""), pool);
        var svc = new RecommendationService(repo, Integer.parseInt(System.getenv().getOrDefault("CACHE_SIZE", "100000")));
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        start(svc, port);
        System.out.println("recsys listening on :" + port);
    }
}
