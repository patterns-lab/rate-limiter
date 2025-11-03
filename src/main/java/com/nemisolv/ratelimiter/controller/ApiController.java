package com.nemisolv.ratelimiter.controller;

import com.nemisolv.ratelimiter.service.RateLimiterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class ApiController {

    private final RateLimiterService rateLimiterService;

    /**
     * Global rate limited endpoint.
     */
    @GetMapping("/resource")
    public ResponseEntity<Map<String, Object>> getResource() {
        if (rateLimiterService.allowApiRequest()) {
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Resource accessed successfully");
            response.put("timestamp", System.currentTimeMillis());
            response.put("status", "allowed");
            
            return ResponseEntity.ok(response);
        } else {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Rate limit exceeded");
            errorResponse.put("message", "Global rate limit exceeded. Please try again later.");
            errorResponse.put("timestamp", System.currentTimeMillis());
            errorResponse.put("status", "rate_limited");
            
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(errorResponse);
        }
    }

    /**
     * User-specific rate limited endpoint.
     */
    @GetMapping("/user-resource")
    public ResponseEntity<Map<String, Object>> getUserResource(
            @RequestParam(required = false) String userId,
            HttpServletRequest request) {
        
        // Use provided userId or extract from request (in real app, this would come from authentication)
        String effectiveUserId = userId != null ? userId : "anonymous";
        
        if (rateLimiterService.allowUserRequest(effectiveUserId)) {
            Map<String, Object> response = new HashMap<>();
            response.put("message", "User resource accessed successfully");
            response.put("userId", effectiveUserId);
            response.put("timestamp", System.currentTimeMillis());
            response.put("status", "allowed");
            
            // Add rate limit info
            RateLimiterService.TokenBucketStatus userStatus = rateLimiterService.getUserStatus(effectiveUserId);
            if (userStatus != null) {
                response.put("tokensRemaining", userStatus.getCurrentTokens());
                response.put("tokensCapacity", userStatus.getCapacity());
            }
            
            return ResponseEntity.ok(response);
        } else {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Rate limit exceeded");
            errorResponse.put("message", "User rate limit exceeded. Please try again later.");
            errorResponse.put("userId", effectiveUserId);
            errorResponse.put("timestamp", System.currentTimeMillis());
            errorResponse.put("status", "rate_limited");
            
            // Add retry info
            RateLimiterService.TokenBucketStatus userStatus = rateLimiterService.getUserStatus(effectiveUserId);
            if (userStatus != null) {
                errorResponse.put("retryAfterMs", userStatus.getTimeToNextToken());
            }
            
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(errorResponse);
        }
    }

    /**
     * IP-specific rate limited endpoint.
     */
    @GetMapping("/ip-resource")
    public ResponseEntity<Map<String, Object>> getIpResource(HttpServletRequest request) {
        String clientIp = rateLimiterService.extractClientIp(request);
        
        if (rateLimiterService.allowIpRequest(clientIp)) {
            Map<String, Object> response = new HashMap<>();
            response.put("message", "IP resource accessed successfully");
            response.put("clientIp", clientIp);
            response.put("timestamp", System.currentTimeMillis());
            response.put("status", "allowed");
            
            // Add rate limit info
            RateLimiterService.TokenBucketStatus ipStatus = rateLimiterService.getIpStatus(clientIp);
            if (ipStatus != null) {
                response.put("tokensRemaining", ipStatus.getCurrentTokens());
                response.put("tokensCapacity", ipStatus.getCapacity());
            }
            
            return ResponseEntity.ok(response);
        } else {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Rate limit exceeded");
            errorResponse.put("message", "IP rate limit exceeded. Please try again later.");
            errorResponse.put("clientIp", clientIp);
            errorResponse.put("timestamp", System.currentTimeMillis());
            errorResponse.put("status", "rate_limited");
            
            // Add retry info
            RateLimiterService.TokenBucketStatus ipStatus = rateLimiterService.getIpStatus(clientIp);
            if (ipStatus != null) {
                errorResponse.put("retryAfterMs", ipStatus.getTimeToNextToken());
            }
            
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(errorResponse);
        }
    }

    /**
     * Combined rate limiting (global + user + IP).
     */
    @GetMapping("/protected-resource")
    public ResponseEntity<Map<String, Object>> getProtectedResource(
            @RequestParam(required = false) String userId,
            HttpServletRequest request) {
        
        String effectiveUserId = userId != null ? userId : "anonymous";
        String clientIp = rateLimiterService.extractClientIp(request);
        
        // Check all rate limits
        boolean globalAllowed = rateLimiterService.allowApiRequest();
        boolean userAllowed = rateLimiterService.allowUserRequest(effectiveUserId);
        boolean ipAllowed = rateLimiterService.allowIpRequest(clientIp);
        
        if (globalAllowed && userAllowed && ipAllowed) {
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Protected resource accessed successfully");
            response.put("userId", effectiveUserId);
            response.put("clientIp", clientIp);
            response.put("timestamp", System.currentTimeMillis());
            response.put("status", "allowed");
            
            return ResponseEntity.ok(response);
        } else {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Rate limit exceeded");
            errorResponse.put("message", "Request blocked by rate limiting");
            errorResponse.put("userId", effectiveUserId);
            errorResponse.put("clientIp", clientIp);
            errorResponse.put("timestamp", System.currentTimeMillis());
            errorResponse.put("status", "rate_limited");
            
            // Add which limit was hit
            Map<String, Boolean> limits = new HashMap<>();
            limits.put("global", globalAllowed);
            limits.put("user", userAllowed);
            limits.put("ip", ipAllowed);
            errorResponse.put("limits", limits);
            
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(errorResponse);
        }
    }

    /**
     * Get rate limit status and metrics.
     */
    @GetMapping("/rate-limit-status")
    public ResponseEntity<RateLimiterService.RateLimitStatus> getRateLimitStatus() {
        RateLimiterService.RateLimitStatus status = rateLimiterService.getStatus();
        return ResponseEntity.ok(status);
    }

    /**
     * Get user-specific rate limit status.
     */
    @GetMapping("/user-rate-limit-status")
    public ResponseEntity<Map<String, Object>> getUserRateLimitStatus(
            @RequestParam(required = false) String userId) {
        
        String effectiveUserId = userId != null ? userId : "anonymous";
        RateLimiterService.TokenBucketStatus userStatus = rateLimiterService.getUserStatus(effectiveUserId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("userId", effectiveUserId);
        
        if (userStatus != null) {
            response.put("status", userStatus);
        } else {
            response.put("message", "No rate limit data found for user");
        }
        
        return ResponseEntity.ok(response);
    }

    /**
     * Get IP-specific rate limit status.
     */
    @GetMapping("/ip-rate-limit-status")
    public ResponseEntity<Map<String, Object>> getIpRateLimitStatus(HttpServletRequest request) {
        String clientIp = rateLimiterService.extractClientIp(request);
        RateLimiterService.TokenBucketStatus ipStatus = rateLimiterService.getIpStatus(clientIp);
        
        Map<String, Object> response = new HashMap<>();
        response.put("clientIp", clientIp);
        
        if (ipStatus != null) {
            response.put("status", ipStatus);
        } else {
            response.put("message", "No rate limit data found for IP");
        }
        
        return ResponseEntity.ok(response);
    }

    /**
     * Clear user rate limit data (admin endpoint).
     */
    @DeleteMapping("/user-rate-limit")
    public ResponseEntity<Map<String, String>> clearUserRateLimit(@RequestParam String userId) {
        rateLimiterService.clearUserData(userId);
        
        Map<String, String> response = new HashMap<>();
        response.put("message", "User rate limit data cleared");
        response.put("userId", userId);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Clear IP rate limit data (admin endpoint).
     */
    @DeleteMapping("/ip-rate-limit")
    public ResponseEntity<Map<String, String>> clearIpRateLimit(@RequestParam String ipAddress) {
        rateLimiterService.clearIpData(ipAddress);
        
        Map<String, String> response = new HashMap<>();
        response.put("message", "IP rate limit data cleared");
        response.put("ipAddress", ipAddress);
        
        return ResponseEntity.ok(response);
    }
}