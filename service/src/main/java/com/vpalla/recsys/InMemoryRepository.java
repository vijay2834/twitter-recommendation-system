package com.vpalla.recsys;

import java.util.*;

public final class InMemoryRepository implements Repository {
    private final Map<Long, List<Model.Interaction>> interactions = new HashMap<>();
    private final Map<Long, Model.UserProfile> profiles = new HashMap<>();

    public InMemoryRepository addInteraction(long user, Model.Interaction i) {
        interactions.computeIfAbsent(user, k -> new ArrayList<>()).add(i); return this;
    }
    public InMemoryRepository addProfile(Model.UserProfile p) { profiles.put(p.userId(), p); return this; }

    @Override public List<Model.Interaction> interactions(long userId) { return interactions.getOrDefault(userId, List.of()); }
    @Override public Optional<Model.UserProfile> profile(long userId) { return Optional.ofNullable(profiles.get(userId)); }
    @Override public boolean healthy() { return true; }
}
