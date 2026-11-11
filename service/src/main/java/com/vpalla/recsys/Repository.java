package com.vpalla.recsys;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Storage abstraction: MySQL in production, in-memory for tests and local demos. */
public interface Repository {
    List<Model.Interaction> interactions(long userId);
    Optional<Model.UserProfile> profile(long userId);
    boolean healthy();

    /** Batch profile lookup; implementations should override to avoid N+1 queries. */
    default Map<Long, Model.UserProfile> profiles(Collection<Long> ids) {
        Map<Long, Model.UserProfile> out = new HashMap<>();
        for (long id : ids) profile(id).ifPresent(p -> out.put(id, p));
        return out;
    }
}
