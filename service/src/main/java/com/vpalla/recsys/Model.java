package com.vpalla.recsys;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Data model shared by the repository, scoring and HTTP layers. */
public final class Model {
    private Model() {}

    /** Aggregated interaction between the requesting user and one contact (built by the Spark ETL). */
    public record Interaction(long contactId, int replies, int retweets, int mentions, String latestTweet) {}

    /** Per-user profile: screen name, description and hashtag counts. */
    public record UserProfile(long userId, String screenName, String description, Map<String, Integer> hashtags) {}

    /** One recommended contact with the score breakdown, so callers can explain the ranking. */
    public record Recommendation(long userId, String screenName, double score, double interactionScore,
                                 double hashtagScore, double keywordScore, String latestTweet) {}

    public record Response(long userId, String type, List<Recommendation> recommendations, boolean cached) {}

    public enum Type { REPLY, RETWEET, BOTH;
        static Type parse(String s) { return s == null ? BOTH : Type.valueOf(s.trim().toUpperCase()); }
    }

    /** Hashtags too common to signal shared interests (filtered from similarity). */
    public static final Set<String> POPULAR_HASHTAGS = Set.of("news", "love", "music", "follow", "photo");
}
