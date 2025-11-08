package com.nemisolv.ratelimiter.service;

import com.nemisolv.ratelimiter.algorithms.TokenBucket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Enhanced Rate Limiter Service supporting multiple rate limiting strategies.
 *
 * <p>This service provides:
 * - Global API rate limiting
 * - Per-user rate limiting
 * - Per-IP rate limiting
 * - Configurable rate limits
 * - Monitoring and metrics
 */
@Service
@Slf4j
public class RateLimiterService {

    // Global API rate limiter
    private final TokenBucket globalApiBucket;
    
    // Per-user rate limiters
    private final ConcurrentHashMap<String, TokenBucket> userBuckets = new ConcurrentHashMap<>();
    
    // Per-IP rate limiters
    private final ConcurrentHashMap<String, TokenBucket> ipBuckets = new ConcurrentHashMap<>();
    
    // Metrics
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong allowedRequests = new AtomicLong(0);
    private final AtomicLong rejectedRequests = new AtomicLong(0);

    // Configuration
    private final RateLimitConfig globalConfig;
    private final RateLimitConfig userConfig;
    private final RateLimitConfig ipConfig;

    /**
         * Configuration class for rate limits.
         */
        public record RateLimitConfig(long capacity, long tokensPerSecond) {}

    /**
     * Initializes the RateLimiterService with default configurations.
     */
    public RateLimiterService() {
        // Default configurations
        this.globalConfig = new RateLimitConfig(100, 10);  // 100 capacity, 10 tokens/sec
        this.userConfig = new RateLimitConfig(50, 5);      // 50 capacity, 5 tokens/sec per user
        this.ipConfig = new RateLimitConfig(30, 3);        // 30 capacity, 3 tokens/sec per IP
        
        this.globalApiBucket = new TokenBucket(globalConfig.capacity(), globalConfig.tokensPerSecond());
    }

    /**
     * Initializes the RateLimiterService with custom configurations.
     */
    public RateLimiterService(RateLimitConfig globalConfig, RateLimitConfig userConfig, RateLimitConfig ipConfig) {
        this.globalConfig = globalConfig;
        this.userConfig = userConfig;
        this.ipConfig = ipConfig;
        this.globalApiBucket = new TokenBucket(globalConfig.capacity(), globalConfig.tokensPerSecond());
    }

    /**
     * Checks if a global API request is allowed.
     *
     * @return true if request is allowed, false if rate limited
     */
    public boolean allowApiRequest() {
        totalRequests.incrementAndGet();
        
        boolean allowed = globalApiBucket.tryConsume();
        if (allowed) {
            allowedRequests.incrementAndGet();
            log.debug("Global API request allowed. Tokens remaining: {}", globalApiBucket.getRefilledTokens());
        } else {
            rejectedRequests.incrementAndGet();
            log.warn("Global API request rate limited. Tokens remaining: {}, Time to next token: {}ms",
                    globalApiBucket.getRefilledTokens(), globalApiBucket.getTimeToNextToken());
        }
        
        return allowed;
    }

    /**
     * Checks if a user-specific request is allowed.
     *
     * @param userId Unique identifier for the user
     * @return true if request is allowed, false if rate limited
     */
    public boolean allowUserRequest(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            log.debug("User request rejected: invalid userId");
            return false;
        }

        totalRequests.incrementAndGet();
        
        TokenBucket userBucket = userBuckets.computeIfAbsent(userId, id -> {
            log.debug("Created new token bucket for user: {}", id);
            return new TokenBucket(userConfig.capacity(), userConfig.tokensPerSecond());
        });

        boolean allowed = userBucket.tryConsume();
        if (allowed) {
            allowedRequests.incrementAndGet();
            log.debug("User request allowed for userId: {}. Tokens remaining: {}",
                    userId, userBucket.getRefilledTokens());
        } else {
            rejectedRequests.incrementAndGet();
            log.warn("User request rate limited for userId: {}. Tokens remaining: {}, Time to next token: {}ms",
                    userId, userBucket.getRefilledTokens(), userBucket.getTimeToNextToken());
        }
        
        return allowed;
    }

    /**
     * Checks if an IP-specific request is allowed.
     *
     * @param ipAddress IP address of the client
     * @return true if request is allowed, false if rate limited
     */
    public boolean allowIpRequest(String ipAddress) {
        if (ipAddress == null || ipAddress.trim().isEmpty()) {
            log.debug("IP request rejected: invalid ipAddress");
            return false;
        }

        totalRequests.incrementAndGet();
        
        TokenBucket ipBucket = ipBuckets.computeIfAbsent(ipAddress, ip -> {
            log.debug("Created new token bucket for IP: {}", ip);
            return new TokenBucket(ipConfig.capacity(), ipConfig.tokensPerSecond());
        });

        boolean allowed = ipBucket.tryConsume();
        if (allowed) {
            allowedRequests.incrementAndGet();
            log.debug("IP request allowed for ipAddress: {}. Tokens remaining: {}",
                    ipAddress, ipBucket.getRefilledTokens());
        } else {
            rejectedRequests.incrementAndGet();
            log.warn("IP request rate limited for ipAddress: {}. Tokens remaining: {}, Time to next token: {}ms",
                    ipAddress, ipBucket.getRefilledTokens(), ipBucket.getTimeToNextToken());
        }
        
        return allowed;
    }

    /**
     * Extracts client IP from HttpServletRequest.
     *
     * @param request HTTP request
     * @return Client IP address
     */
    public String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            String ip = xForwardedFor.split(",")[0].trim();
            log.trace("Extracted IP from X-Forwarded-For: {}", ip);
            return ip;
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
            log.trace("Extracted IP from X-Real-IP: {}", xRealIp);
            return xRealIp;
        }

        String remoteAddr = request.getRemoteAddr();
        log.trace("Using remote address as IP: {}", remoteAddr);
        return remoteAddr;
    }

    /**
     * Gets rate limit status for monitoring.
     *
     * @return RateLimitStatus with current metrics
     */
    public RateLimitStatus getStatus() {
        return new RateLimitStatus(
            totalRequests.get(),
            allowedRequests.get(),
            rejectedRequests.get(),
            globalApiBucket.getRefilledTokens(),
            userBuckets.size(),
            ipBuckets.size()
        );
    }

    /**
     * Gets user-specific rate limit status.
     *
     * @param userId User identifier
     * @return TokenBucket status for the user, or null if user not found
     */
    public TokenBucketStatus getUserStatus(String userId) {
        TokenBucket bucket = userBuckets.get(userId);
        if (bucket == null) {
            return null;
        }
        return new TokenBucketStatus(
            bucket.getRefilledTokens(),
            bucket.getCapacity(),
            bucket.getTokensPerSecond(),
            bucket.getTimeToNextToken()
        );
    }

    /**
     * Gets IP-specific rate limit status.
     *
     * @param ipAddress IP address
     * @return TokenBucket status for the IP, or null if IP not found
     */
    public TokenBucketStatus getIpStatus(String ipAddress) {
        TokenBucket bucket = ipBuckets.get(ipAddress);
        if (bucket == null) {
            return null;
        }
        return new TokenBucketStatus(
            bucket.getRefilledTokens(),
            bucket.getCapacity(),
            bucket.getTokensPerSecond(),
            bucket.getTimeToNextToken()
        );
    }

    /**
     * Clears rate limit data for a specific user.
     *
     * @param userId User identifier to clear
     */
    public void clearUserData(String userId) {
        TokenBucket removed = userBuckets.remove(userId);
        if (removed != null) {
            log.info("Cleared rate limit data for user: {}", userId);
        } else {
            log.debug("No rate limit data found to clear for user: {}", userId);
        }
    }

    /**
     * Clears rate limit data for a specific IP.
     *
     * @param ipAddress IP address to clear
     */
    public void clearIpData(String ipAddress) {
        TokenBucket removed = ipBuckets.remove(ipAddress);
        if (removed != null) {
            log.info("Cleared rate limit data for IP: {}", ipAddress);
        } else {
            log.debug("No rate limit data found to clear for IP: {}", ipAddress);
        }
    }

    /**
     * Rate limit status metrics.
     */
    public static class RateLimitStatus {
        private final long totalRequests;
        private final long allowedRequests;
        private final long rejectedRequests;
        private final long globalTokensRemaining;
        private final int activeUserBuckets;
        private final int activeIpBuckets;

        public RateLimitStatus(long totalRequests, long allowedRequests, long rejectedRequests,
                              long globalTokensRemaining, int activeUserBuckets, int activeIpBuckets) {
            this.totalRequests = totalRequests;
            this.allowedRequests = allowedRequests;
            this.rejectedRequests = rejectedRequests;
            this.globalTokensRemaining = globalTokensRemaining;
            this.activeUserBuckets = activeUserBuckets;
            this.activeIpBuckets = activeIpBuckets;
        }

        // Getters
        public long getTotalRequests() { return totalRequests; }
        public long getAllowedRequests() { return allowedRequests; }
        public long getRejectedRequests() { return rejectedRequests; }
        public long getGlobalTokensRemaining() { return globalTokensRemaining; }
        public int getActiveUserBuckets() { return activeUserBuckets; }
        public int getActiveIpBuckets() { return activeIpBuckets; }
        
        public double getAllowRate() {
            return totalRequests > 0 ? (double) allowedRequests / totalRequests : 0.0;
        }
    }

    /**
     * Token bucket status for individual users/IPs.
     */
    public static class TokenBucketStatus {
        private final long currentTokens;
        private final long capacity;
        private final long tokensPerSecond;
        private final long timeToNextToken;

        public TokenBucketStatus(long currentTokens, long capacity, long tokensPerSecond, long timeToNextToken) {
            this.currentTokens = currentTokens;
            this.capacity = capacity;
            this.tokensPerSecond = tokensPerSecond;
            this.timeToNextToken = timeToNextToken;
        }

        // Getters
        public long getCurrentTokens() { return currentTokens; }
        public long getCapacity() { return capacity; }
        public long getTokensPerSecond() { return tokensPerSecond; }
        public long getTimeToNextToken() { return timeToNextToken; }
        
        public double getFillPercentage() {
            return capacity > 0 ? (double) currentTokens / capacity : 0.0;
        }
    }
}