package com.netflexity.anypoint.mcp.audit;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
public class AuditService {

    private static final int MAX_ENTRIES = 1000;

    private final Deque<AuditEntry> ring = new ArrayDeque<>(MAX_ENTRIES);
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public void record(String apiKey, String tool, String environment,
                       String resource, String action, String result, long durationMs) {
        AuditEntry entry = AuditEntry.builder()
                .timestamp(Instant.now())
                .apiKey(mask(apiKey))
                .tool(tool)
                .environment(environment)
                .resource(resource)
                .action(action)
                .result(result)
                .durationMs(durationMs)
                .build();

        lock.writeLock().lock();
        try {
            if (ring.size() >= MAX_ENTRIES) ring.pollFirst();
            ring.addLast(entry);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<AuditEntry> getRecent(int limit) {
        lock.readLock().lock();
        try {
            return ring.stream()
                    .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                    .limit(Math.min(limit, MAX_ENTRIES))
                    .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    private String mask(String key) {
        if (key == null || key.length() < 8) return "***";
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }
}
