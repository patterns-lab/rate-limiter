package com.nemisolv.ratelimiter.middleware;

import com.nemisolv.ratelimiter.algorithms.TokenBucket;
import com.nemisolv.ratelimiter.config.RateLimitConfigLoader;
import com.nemisolv.ratelimiter.config.RateLimitRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting middleware that intercepts requests and applies rate limiting rules.
 * Implements standard HTTP headers for rate limiting:
 * - X-Ratelimit-Remaining: The remaining number of allowed requests within the window
 * - X-Ratelimit-Limit: How many calls the client can make per time window
 * - X-Ratelimit-Retry-After: Number of seconds to wait until next request
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitingMiddleware implements HandlerInterceptor {
    
    private final RateLimitConfigLoader configLoader;
    
    // Token buckets for different rate limit keys
    private final Map<String, TokenBucket> tokenBuckets = new ConcurrentHashMap<>();
    
    // Standard rate limit headers
    private static final String HEADER_RATE_LIMIT_LIMIT = "X-Ratelimit-Limit";
    private static final String HEADER_RATE_LIMIT_REMAINING = "X-Ratelimit-Remaining";
    private static final String HEADER_RATE_LIMIT_RETRY_AFTER = "X-Ratelimit-Retry-After";
    
    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        // Extract request information for rate limiting
        List<RateLimitRule.Descriptor> descriptors = extractDescriptors(request);
        
        // Try to find matching rate limit rule for different domains
        RateLimitRule matchingRule = findMatchingRule(descriptors);
        
        if (matchingRule == null) {
            // No rate limit rule found, allow request
            return true;
        }
        
        // Generate unique key for this request type
        String rateLimitKey = generateRateLimitKey(matchingRule.getDomain(), descriptors);
        
        // Get or create token bucket for this key
        TokenBucket bucket = tokenBuckets.computeIfAbsent(rateLimitKey, k -> {
            RateLimitRule.RateLimit rateLimit = matchingRule.getRateLimit();
            long tokensPerSecond = rateLimit.getTokensPerSecond();
            long requestsPerUnit = rateLimit.getRequestsPerUnit();
            
            // Use a larger capacity to allow for bursts
            // For example, if rate is 5 requests per day, use capacity of 5 (not 1 per second)
            long capacity = Math.max(requestsPerUnit, 10); // Minimum capacity of 10
            
            // Handle case where tokens per second is less than 1
            // Use minimum of 1 token per second to avoid validation error
            long effectiveTokensPerSecond = Math.max(1, tokensPerSecond);
            if (effectiveTokensPerSecond != tokensPerSecond) {
                log.debug("Adjusting tokens per second from {} to {} for key: {}", tokensPerSecond, effectiveTokensPerSecond, rateLimitKey);
            }
            
            log.debug("Creating token bucket for key: {}, capacity: {}, tokensPerSecond: {}, requestsPerUnit: {}",
                rateLimitKey, capacity, effectiveTokensPerSecond, requestsPerUnit);
            
            return new TokenBucket(capacity, effectiveTokensPerSecond);
        });
        
        // Check if request is allowed
        boolean allowed = bucket.tryConsume();
        
        // Set rate limit headers
        setRateLimitHeaders(response, matchingRule, bucket, allowed);
        
        if (!allowed) {
            // Request is rate limited
            handleRateLimitExceeded(response, matchingRule, bucket);
            return false;
        }
        
        // Request is allowed
        log.debug("Request allowed for key: {}, tokens remaining: {}", rateLimitKey, bucket.getRefilledTokens());
        return true;
    }
    
    /**
     * Extract descriptors from the request for rate limiting
     */
    private List<RateLimitRule.Descriptor> extractDescriptors(HttpServletRequest request) {
        return List.of(
            new RateLimitRule.Descriptor("path", request.getRequestURI()),
            new RateLimitRule.Descriptor("method", request.getMethod()),
            new RateLimitRule.Descriptor("ip_address", extractClientIp(request)),
            new RateLimitRule.Descriptor("user_id", extractUserId(request)),
            new RateLimitRule.Descriptor("user_tier", extractUserTier(request)),
            new RateLimitRule.Descriptor("auth_type", extractAuthType(request)),
            new RateLimitRule.Descriptor("message_type", extractMessageType(request))
        );
    }
    
    /**
     * Find matching rate limit rule for the given descriptors
     */
    private RateLimitRule findMatchingRule(List<RateLimitRule.Descriptor> descriptors) {
        // Try different domains in order of priority
        String[] domains = {"endpoint", "auth", "user", "ip", "messaging", "api"};
        
        for (String domain : domains) {
            RateLimitRule rule = configLoader.findMatchingRule(domain, descriptors);
            if (rule != null) {
                return rule;
            }
        }
        
        return null;
    }
    
    /**
     * Generate unique key for rate limiting based on domain and descriptors
     */
    private String generateRateLimitKey(String domain, List<RateLimitRule.Descriptor> descriptors) {
        StringBuilder keyBuilder = new StringBuilder(domain);
        
        for (RateLimitRule.Descriptor desc : descriptors) {
            if (desc.getValue() != null && !desc.getValue().isEmpty()) {
                keyBuilder.append(":").append(desc.getKey()).append("=").append(desc.getValue());
            }
        }
        
        return keyBuilder.toString();
    }
    
    /**
     * Set rate limit headers in the response
     */
    private void setRateLimitHeaders(HttpServletResponse response, RateLimitRule rule, TokenBucket bucket, boolean allowed) {
        RateLimitRule.RateLimit rateLimit = rule.getRateLimit();
        
        // Set limit header
        response.setHeader(HEADER_RATE_LIMIT_LIMIT, String.valueOf(rateLimit.getRequestsPerUnit()));
        
        // Set remaining header ( NOTE THAT: use getCurrentTokens instead of getRefilledToken)
        response.setHeader(HEADER_RATE_LIMIT_REMAINING, String.valueOf(Math.max(0, bucket.getCurrentTokens())));
        
        // Set retry-after header if rate limited
        if (!allowed) {
            long retryAfterSeconds = Math.max(1, bucket.getTimeToNextToken() / 1000);
            response.setHeader(HEADER_RATE_LIMIT_RETRY_AFTER, String.valueOf(retryAfterSeconds));
        }
    }
    
    /**
     * Handle rate limit exceeded scenario
     */
    private void handleRateLimitExceeded(HttpServletResponse response, RateLimitRule rule, TokenBucket bucket) throws IOException {
        log.warn("Rate limit exceeded for domain: {}, time to next token: {}ms", 
                rule.getDomain(), bucket.getTimeToNextToken());
        
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        
        // Create error response
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "Rate limit exceeded");
        errorResponse.put("message", "Too many requests. Please try again later.");
        errorResponse.put("domain", rule.getDomain());
        errorResponse.put("retryAfterSeconds", bucket.getTimeToNextToken() / 1000);
        errorResponse.put("timestamp", System.currentTimeMillis());
        
        // Write error response
        String jsonResponse = convertToJson(errorResponse);
        response.getWriter().write(jsonResponse);
    }
    
    /**
     * Extract client IP from request
     */
    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
    
    /**
     * Extract user ID from request (in real app, this would come from authentication)
     */
    private String extractUserId(HttpServletRequest request) {
        // In a real application, this would extract from JWT token, session, etc.
        return request.getHeader("X-User-ID");
    }
    
    /**
     * Extract user tier from request
     */
    private String extractUserTier(HttpServletRequest request) {
        // In a real application, this would extract from user profile
        return request.getHeader("X-User-Tier");
    }
    
    /**
     * Extract auth type from request
     */
    private String extractAuthType(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.contains("/login")) {
            return "login";
        } else if (path.contains("/password-reset")) {
            return "password_reset";
        }
        return null;
    }
    
    /**
     * Extract message type from request
     */
    private String extractMessageType(HttpServletRequest request) {
        // In a real application, this might come from request body or parameters
        return request.getParameter("message_type");
    }
    
    /**
     * Convert map to JSON string (simple implementation)
     */
    private String convertToJson(Map<String, Object> map) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                json.append(",");
            }
            json.append("\"").append(entry.getKey()).append("\":");
            
            Object value = entry.getValue();
            if (value instanceof String) {
                json.append("\"").append(value).append("\"");
            } else {
                json.append(value);
            }
            
            first = false;
        }
        
        json.append("}");
        return json.toString();
    }
}