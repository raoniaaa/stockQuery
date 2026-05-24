package com.stockquery.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitService {

    private final Map<String, SlidingWindow> windows = new ConcurrentHashMap<>();

    private static final long WINDOW_MS = 60_000;
    private static final int MAX_REQUESTS_PER_MINUTE = 3;

    /** 按 IP 检查是否允许请求（每个 IP 每分钟 MAX_REQUESTS_PER_MINUTE 次） */
    public boolean allowIp(String clientIp) {
        return allowRequest("ip:" + clientIp);
    }

    /** 按 IP 获取还需等待多少毫秒 */
    public long getIpWaitTime(String clientIp) {
        return getWaitTime("ip:" + clientIp);
    }

    /** 通用限流检查。每个 key 独立计数，互不影响。 */
    public boolean allowRequest(String key) {
        long now = System.currentTimeMillis();
        SlidingWindow w = windows.computeIfAbsent(key, k -> new SlidingWindow());

        synchronized (w) {
            if ((now - w.windowStart) > WINDOW_MS) {
                w.windowStart = now;
                w.count = 1;
                return true;
            }
            if (w.count >= MAX_REQUESTS_PER_MINUTE) {
                return false;
            }
            w.count++;
            return true;
        }
    }

    public long getWaitTime(String key) {
        SlidingWindow w = windows.get(key);
        if (w == null) return 0;

        synchronized (w) {
            long now = System.currentTimeMillis();
            long elapsed = now - w.windowStart;
            if (elapsed > WINDOW_MS) return 0;
            if (w.count < MAX_REQUESTS_PER_MINUTE) return 0;
            return WINDOW_MS - elapsed + 1000;
        }
    }

    private static class SlidingWindow {
        long windowStart;
        int count;
    }
}
