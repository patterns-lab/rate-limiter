package com.nemisolv.ratelimiter.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Slf4j
public class ApiController {

    /**
     * Global rate limited endpoint.
     * Rate limiting is now handled by middleware, not in the controller.
     */
    @GetMapping("/resource")
    public ResponseEntity<Map<String, Object>> getResource() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Resource accessed successfully");
        response.put("timestamp", System.currentTimeMillis());
        response.put("status", "allowed");
        
        return ResponseEntity.ok(response);
    }

    /**
     * User-specific endpoint.
     * Rate limiting is now handled by middleware based on user ID from headers.
     */
    @GetMapping("/user-resource")
    public ResponseEntity<Map<String, Object>> getUserResource(
            @RequestParam(required = false) String userId,
            HttpServletRequest request) {
        
        // Use provided userId or extract from request (in real app, this would come from authentication)
        String effectiveUserId = userId != null ? userId : "anonymous";
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "User resource accessed successfully");
        response.put("userId", effectiveUserId);
        response.put("timestamp", System.currentTimeMillis());
        response.put("status", "allowed");
        
        return ResponseEntity.ok(response);
    }

    /**
     * IP-specific endpoint.
     * Rate limiting is now handled by middleware based on IP address.
     */
    @GetMapping("/ip-resource")
    public ResponseEntity<Map<String, Object>> getIpResource(HttpServletRequest request) {
        String clientIp = extractClientIp(request);
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "IP resource accessed successfully");
        response.put("clientIp", clientIp);
        response.put("timestamp", System.currentTimeMillis());
        response.put("status", "allowed");
        
        return ResponseEntity.ok(response);
    }

    /**
     * Protected resource endpoint.
     * Rate limiting is now handled by middleware with multiple rules.
     */
    @GetMapping("/protected-resource")
    public ResponseEntity<Map<String, Object>> getProtectedResource(
            @RequestParam(required = false) String userId,
            HttpServletRequest request) {
        
        String effectiveUserId = userId != null ? userId : "anonymous";
        String clientIp = extractClientIp(request);
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Protected resource accessed successfully");
        response.put("userId", effectiveUserId);
        response.put("clientIp", clientIp);
        response.put("timestamp", System.currentTimeMillis());
        response.put("status", "allowed");
        
        return ResponseEntity.ok(response);
    }

    /**
     * Authentication endpoint for login.
     * Rate limiting is handled by middleware for auth_type=login.
     */
    @PostMapping("/auth/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> loginRequest) {
        String username = loginRequest.get("username");
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Login successful");
        response.put("username", username);
        response.put("timestamp", System.currentTimeMillis());
        response.put("status", "authenticated");
        
        return ResponseEntity.ok(response);
    }

    /**
     * Password reset endpoint.
     * Rate limiting is handled by middleware for auth_type=password_reset.
     */
    @PostMapping("/auth/password-reset")
    public ResponseEntity<Map<String, Object>> passwordReset(@RequestBody Map<String, String> resetRequest) {
        String email = resetRequest.get("email");
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Password reset email sent");
        response.put("email", email);
        response.put("timestamp", System.currentTimeMillis());
        response.put("status", "reset_initiated");
        
        return ResponseEntity.ok(response);
    }

    /**
     * Messaging endpoint for sending messages.
     * Rate limiting is handled by middleware based on message_type.
     */
    @PostMapping("/messaging/send")
    public ResponseEntity<Map<String, Object>> sendMessage(
            @RequestParam String messageType,
            @RequestBody Map<String, Object> message) {
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Message sent successfully");
        response.put("messageType", messageType);
        response.put("timestamp", System.currentTimeMillis());
        response.put("status", "sent");
        
        return ResponseEntity.ok(response);
    }

    /**
     * Upload endpoint.
     * Rate limiting is handled by middleware for POST /api/upload.
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadFile() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "File uploaded successfully");
        response.put("timestamp", System.currentTimeMillis());
        response.put("status", "uploaded");
        
        return ResponseEntity.ok(response);
    }

    /**
     * Search endpoint.
     * Rate limiting is handled by middleware for /api/search.
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(@RequestParam String query) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Search results");
        response.put("query", query);
        response.put("timestamp", System.currentTimeMillis());
        response.put("status", "completed");
        
        return ResponseEntity.ok(response);
    }

    /**
     * Extracts client IP from HttpServletRequest.
     * This is a helper method since we removed the dependency on RateLimiterService.
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
}