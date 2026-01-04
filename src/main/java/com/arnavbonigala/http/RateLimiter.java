package com.arnavbonigala.http;

import com.arnavbonigala.config.HttpCommandsConfig;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class RateLimiter {
    private final HttpCommandsConfig config;
    private final Map<UUID, Long> lastRequestTime = new ConcurrentHashMap<>();
    private final AtomicInteger activeRequests = new AtomicInteger(0);
    
    public RateLimiter(HttpCommandsConfig config) {
        this.config = config;
    }
    
    public boolean canMakeRequest(UUID sourceId) {
        // Check cooldown
        Long lastTime = lastRequestTime.get(sourceId);
        if (lastTime != null) {
            long elapsed = (System.currentTimeMillis() - lastTime) / 1000;
            if (elapsed < config.cooldownSeconds) {
                return false;
            }
        }
        
        // Check concurrent requests
        if (activeRequests.get() >= config.maxConcurrentRequests) {
            return false;
        }
        
        return true;
    }
    
    public void startRequest(UUID sourceId) {
        lastRequestTime.put(sourceId, System.currentTimeMillis());
        activeRequests.incrementAndGet();
    }
    
    public void endRequest() {
        activeRequests.decrementAndGet();
    }
    
    public long getRemainingCooldown(UUID sourceId) {
        Long lastTime = lastRequestTime.get(sourceId);
        if (lastTime == null) return 0;
        
        long elapsed = (System.currentTimeMillis() - lastTime) / 1000;
        return Math.max(0, config.cooldownSeconds - elapsed);
    }
}

