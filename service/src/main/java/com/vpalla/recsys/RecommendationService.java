package com.vpalla.recsys;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Ranks a user's contacts:
 *   score = interactionScore * hashtagScore * keywordScore
 *   interactionScore = ln(1 + 2*replies + retweets + 0.5*mentions)   (filtered by request type)
 *   hashtagScore     = 1 + ln(1 + sum over shared, non-popular hashtags of min(count_u, count_c))
 *   keywordScore     = 1 + matches of the optional phrase/hashtag filter in the contact's latest tweet
 * Ties break by larger user id for determinism.
 */
public final class RecommendationService {
    private final Repository repo;
    private final LruCache<String, Model.Response> cache;
    final AtomicLong requests = new AtomicLong(), cacheHits = new AtomicLong();

    public RecommendationService(Repository repo, int cacheSize) {
        this.repo = repo;
        this.cache = new LruCache<>(cacheSize);
    }

    public Model.Response recommend(long userId, Model.Type type, String phrase, int limit) {
        requests.incrementAndGet();
        String key = userId + "|" + type + "|" + (phrase == null ? "" : phrase.toLowerCase()) + "|" + limit;
        Model.Response hit = cache.get(key);
        if (hit != null) {
            cacheHits.incrementAndGet();
            return new Model.Response(hit.userId(), hit.type(), hit.recommendations(), true);
        }
        Map<String, Integer> myTags = repo.profile(userId).map(Model.UserProfile::hashtags).orElse(Map.of());
        List<Model.Interaction> contacts = repo.interactions(userId);
        Map<Long, Model.UserProfile> profiles = repo.profiles(contacts.stream().map(Model.Interaction::contactId).toList());
        List<Model.Recommendation> recs = new ArrayList<>();
        for (Model.Interaction i : contacts) {
            double raw = switch (type) {
                case REPLY -> 2.0 * i.replies();
                case RETWEET -> i.retweets();
                case BOTH -> 2.0 * i.replies() + i.retweets() + 0.5 * i.mentions();
            };
            if (raw <= 0) continue;
            double inter = Math.log1p(raw);
            Optional<Model.UserProfile> contact = Optional.ofNullable(profiles.get(i.contactId()));
            double tag = 1 + Math.log1p(sharedHashtags(myTags, contact.map(Model.UserProfile::hashtags).orElse(Map.of())));
            double kw = 1 + keywordMatches(i.latestTweet(), phrase);
            recs.add(new Model.Recommendation(i.contactId(), contact.map(Model.UserProfile::screenName).orElse(""),
                    round(inter * tag * kw), round(inter), round(tag), kw, i.latestTweet()));
        }
        recs.sort(Comparator.comparingDouble(Model.Recommendation::score).reversed()
                .thenComparing(Comparator.comparingLong(Model.Recommendation::userId).reversed()));
        Model.Response resp = new Model.Response(userId, type.name().toLowerCase(),
                List.copyOf(recs.subList(0, Math.min(limit, recs.size()))), false);
        cache.put(key, resp);
        return resp;
    }

    static double sharedHashtags(Map<String, Integer> a, Map<String, Integer> b) {
        double s = 0;
        for (var e : a.entrySet()) {
            if (Model.POPULAR_HASHTAGS.contains(e.getKey())) continue;
            Integer c = b.get(e.getKey());
            if (c != null) s += Math.min(e.getValue(), c);
        }
        return s;
    }

    static int keywordMatches(String text, String phrase) {
        if (text == null || phrase == null || phrase.isBlank()) return 0;
        String t = text.toLowerCase(), p = phrase.toLowerCase();
        int n = 0;
        for (int i = t.indexOf(p); i >= 0; i = t.indexOf(p, i + 1)) n++;  // overlapping matches count
        return n;
    }

    private static double round(double v) { return Math.round(v * 1e5) / 1e5; }

    public boolean healthy() { return repo.healthy(); }
}
