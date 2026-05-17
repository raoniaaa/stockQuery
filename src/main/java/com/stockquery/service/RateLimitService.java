package com.stockquery.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RateLimitService {

    private final Map<String, Long> lastRequestTime = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> requestCount = new ConcurrentHashMap<>();

    private static final long WINDOW_MS = 60_000;
    private static final int MAX_REQUESTS_PER_MINUTE = 5;
    private static final long MIN_INTERVAL_MS = 3_000;

    public boolean allowRequest(String key) {
        long now = System.currentTimeMillis();

        Long lastTime = lastRequestTime.get(key);
        if (lastTime != null && (now - lastTime) < MIN_INTERVAL_MS) {
            return false;
        }

        AtomicInteger count = requestCount.computeIfAbsent(key, k -> new AtomicInteger(0));
        Long lastWindow = lastRequestTime.get(key);

        if (lastWindow == null || (now - lastWindow) > WINDOW_MS) {
            count.set(1);
        } else {
            if (count.get() >= MAX_REQUESTS_PER_MINUTE) {
                return false;
            }
            count.incrementAndGet();
        }

        lastRequestTime.put(key, now);
        return true;
    }

    public long getWaitTime(String key) {
        Long lastTime = lastRequestTime.get(key);
        if (lastTime == null) return 0;

        long elapsed = System.currentTimeMillis() - lastTime;
        if (elapsed < MIN_INTERVAL_MS) {
            return MIN_INTERVAL_MS - elapsed;
        }
        return 0;
    }
}
