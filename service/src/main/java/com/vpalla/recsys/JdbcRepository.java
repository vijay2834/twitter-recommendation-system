package com.vpalla.recsys;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.*;

/** MySQL-backed repository using a HikariCP connection pool. Schema: db/schema.sql */
public final class JdbcRepository implements Repository, AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HikariDataSource ds;

    public JdbcRepository(String url, String user, String password, int poolSize) {
        HikariConfig c = new HikariConfig();
        c.setJdbcUrl(url); c.setUsername(user); c.setPassword(password);
        c.setMaximumPoolSize(poolSize);
        c.addDataSourceProperty("cachePrepStmts", "true");
        c.addDataSourceProperty("prepStmtCacheSize", "256");
        c.addDataSourceProperty("useServerPrepStmts", "true");
        this.ds = new HikariDataSource(c);
    }

    @Override public List<Model.Interaction> interactions(long userId) {
        String sql = "SELECT contact_id, replies, retweets, mentions, latest_tweet FROM interactions WHERE user_id = ?";
        try (Connection con = ds.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, userId);
            List<Model.Interaction> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(new Model.Interaction(rs.getLong(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getString(5)));
            }
            return out;
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    @Override public Optional<Model.UserProfile> profile(long userId) {
        String sql = "SELECT user_id, screen_name, description, hashtags_json FROM users WHERE user_id = ?";
        try (Connection con = ds.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                Map<String, Integer> tags = rs.getString(4) == null ? Map.of()
                        : JSON.readValue(rs.getString(4), new TypeReference<Map<String, Integer>>() {});
                return Optional.of(new Model.UserProfile(rs.getLong(1), rs.getString(2), rs.getString(3), tags));
            }
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    /** One round trip for all contacts' profiles (replaces one query per contact). */
    @Override public Map<Long, Model.UserProfile> profiles(Collection<Long> ids) {
        Map<Long, Model.UserProfile> out = new HashMap<>();
        if (ids.isEmpty()) return out;
        String sql = "SELECT user_id, screen_name, description, hashtags_json FROM users WHERE user_id IN ("
                + String.join(",", Collections.nCopies(ids.size(), "?")) + ")";
        try (Connection con = ds.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            int i = 1;
            for (long id : ids) ps.setLong(i++, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Integer> tags = rs.getString(4) == null ? Map.of()
                            : JSON.readValue(rs.getString(4), new TypeReference<Map<String, Integer>>() {});
                    out.put(rs.getLong(1), new Model.UserProfile(rs.getLong(1), rs.getString(2), rs.getString(3), tags));
                }
            }
            return out;
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Override public boolean healthy() {
        try (Connection con = ds.getConnection()) { return con.isValid(1); } catch (SQLException e) { return false; }
    }

    @Override public void close() { ds.close(); }
}
