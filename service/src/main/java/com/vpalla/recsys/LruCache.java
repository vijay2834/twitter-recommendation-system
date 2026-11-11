package com.vpalla.recsys;

import java.util.LinkedHashMap;
import java.util.Map;

/** Small thread-safe LRU cache for hot users (recommendations change only when the ETL reloads). */
public final class LruCache<K, V> {
    private final Map<K, V> map;

    public LruCache(int capacity) {
        this.map = new LinkedHashMap<>(16, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<K, V> e) { return size() > capacity; }
        };
    }
    public synchronized V get(K k) { return map.get(k); }
    public synchronized void put(K k, V v) { map.put(k, v); }
    public synchronized int size() { return map.size(); }
}
