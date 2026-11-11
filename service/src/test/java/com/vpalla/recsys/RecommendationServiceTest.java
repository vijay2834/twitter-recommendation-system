package com.vpalla.recsys;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RecommendationServiceTest {
    InMemoryRepository repo;
    RecommendationService svc;

    @BeforeEach
    void setUp() {
        repo = new InMemoryRepository()
            .addProfile(new Model.UserProfile(1, "alice", "", Map.of("rust", 3, "news", 9)))
            .addProfile(new Model.UserProfile(2, "bob", "", Map.of("rust", 5)))
            .addProfile(new Model.UserProfile(3, "carol", "", Map.of("news", 20)))
            .addProfile(new Model.UserProfile(4, "dave", "", Map.of()))
            .addInteraction(1, new Model.Interaction(2, 1, 1, 0, "learning rust and more rust"))
            .addInteraction(1, new Model.Interaction(3, 1, 1, 0, "breaking news today"))
            .addInteraction(1, new Model.Interaction(4, 0, 5, 0, "hello"));
        svc = new RecommendationService(repo, 100);
    }

    @Test void sharedNicheHashtagsBoostRanking() {
        var r = svc.recommend(1, Model.Type.BOTH, null, 10).recommendations();
        assertEquals(2, r.get(0).userId(), "bob shares #rust");
        assertEquals(1.0, r.stream().filter(x -> x.userId() == 3).findFirst().orElseThrow().hashtagScore(),
                "popular #news must not count");
    }

    @Test void typeFilterExcludesContactsWithoutThatInteraction() {
        var r = svc.recommend(1, Model.Type.REPLY, null, 10).recommendations();
        assertTrue(r.stream().noneMatch(x -> x.userId() == 4), "dave only retweets");
    }

    @Test void phraseMatchesCountOverlapping() {
        assertEquals(2, RecommendationService.keywordMatches("learning rust and more rust", "rust"));
        assertEquals(3, RecommendationService.keywordMatches("aaaa", "aa"));
        var r = svc.recommend(1, Model.Type.BOTH, "rust", 10).recommendations();
        assertEquals(3.0, r.get(0).keywordScore());
    }

    @Test void secondCallIsServedFromCache() {
        assertFalse(svc.recommend(1, Model.Type.BOTH, null, 10).cached());
        assertTrue(svc.recommend(1, Model.Type.BOTH, null, 10).cached());
        assertEquals(1, svc.cacheHits.get());
    }

    @Test void lruEvicts() {
        var c = new LruCache<Integer, Integer>(2);
        c.put(1, 1); c.put(2, 2); c.get(1); c.put(3, 3);
        assertNull(c.get(2)); assertNotNull(c.get(1));
    }

    @Test void httpEndpoints() throws Exception {
        var server = App.start(svc, 0);
        int port = server.getAddress().getPort();
        var http = HttpClient.newHttpClient();
        try {
            var ok = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/recommend?user_id=1&type=retweet&limit=2")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, ok.statusCode());
            assertTrue(ok.body().contains("\"screenName\":\"dave\""));
            var bad = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/recommend?type=reply")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(400, bad.statusCode());
            var ready = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/readyz")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, ready.statusCode());
        } finally { server.stop(0); }
    }
}
