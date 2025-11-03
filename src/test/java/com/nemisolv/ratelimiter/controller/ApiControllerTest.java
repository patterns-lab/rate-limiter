package com.nemisolv.ratelimiter.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemisolv.ratelimiter.service.RateLimiterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ApiController.class)
class ApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        // Reset mock behavior before each test
        when(rateLimiterService.extractClientIp(any())).thenReturn("127.0.0.1");
    }

    @Test
    @DisplayName("Should allow access to global resource when not rate limited")
    void shouldAllowAccessToGlobalResourceWhenNotRateLimited() throws Exception {
        when(rateLimiterService.allowApiRequest()).thenReturn(true);

        mockMvc.perform(get("/api/resource"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Resource accessed successfully"))
                .andExpect(jsonPath("$.status").value("allowed"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Should reject access to global resource when rate limited")
    void shouldRejectAccessToGlobalResourceWhenRateLimited() throws Exception {
        when(rateLimiterService.allowApiRequest()).thenReturn(false);

        mockMvc.perform(get("/api/resource"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("Rate limit exceeded"))
                .andExpect(jsonPath("$.status").value("rate_limited"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Should allow access to user resource when not rate limited")
    void shouldAllowAccessToUserResourceWhenNotRateLimited() throws Exception {
        when(rateLimiterService.allowUserRequest("testUser")).thenReturn(true);
        when(rateLimiterService.getUserStatus("testUser")).thenReturn(
            new RateLimiterService.TokenBucketStatus(45, 50, 5, 0)
        );

        mockMvc.perform(get("/api/user-resource")
                .param("userId", "testUser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User resource accessed successfully"))
                .andExpect(jsonPath("$.userId").value("testUser"))
                .andExpect(jsonPath("$.status").value("allowed"))
                .andExpect(jsonPath("$.tokensRemaining").value(45))
                .andExpect(jsonPath("$.tokensCapacity").value(50));
    }

    @Test
    @DisplayName("Should reject access to user resource when rate limited")
    void shouldRejectAccessToUserResourceWhenRateLimited() throws Exception {
        when(rateLimiterService.allowUserRequest("testUser")).thenReturn(false);
        when(rateLimiterService.getUserStatus("testUser")).thenReturn(
            new RateLimiterService.TokenBucketStatus(0, 50, 5, 1000)
        );

        mockMvc.perform(get("/api/user-resource")
                .param("userId", "testUser"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("Rate limit exceeded"))
                .andExpect(jsonPath("$.userId").value("testUser"))
                .andExpect(jsonPath("$.status").value("rate_limited"))
                .andExpect(jsonPath("$.retryAfterMs").value(1000));
    }

    @Test
    @DisplayName("Should handle anonymous user requests")
    void shouldHandleAnonymousUserRequests() throws Exception {
        when(rateLimiterService.allowUserRequest("anonymous")).thenReturn(true);
        when(rateLimiterService.getUserStatus("anonymous")).thenReturn(
            new RateLimiterService.TokenBucketStatus(49, 50, 5, 0)
        );

        mockMvc.perform(get("/api/user-resource"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("anonymous"))
                .andExpect(jsonPath("$.tokensRemaining").value(49));
    }

    @Test
    @DisplayName("Should allow access to IP resource when not rate limited")
    void shouldAllowAccessToIpResourceWhenNotRateLimited() throws Exception {
        when(rateLimiterService.extractClientIp(any())).thenReturn("192.168.1.100");
        when(rateLimiterService.allowIpRequest("192.168.1.100")).thenReturn(true);
        when(rateLimiterService.getIpStatus("192.168.1.100")).thenReturn(
            new RateLimiterService.TokenBucketStatus(25, 30, 3, 0)
        );

        mockMvc.perform(get("/api/ip-resource"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("IP resource accessed successfully"))
                .andExpect(jsonPath("$.clientIp").value("192.168.1.100"))
                .andExpect(jsonPath("$.status").value("allowed"))
                .andExpect(jsonPath("$.tokensRemaining").value(25))
                .andExpect(jsonPath("$.tokensCapacity").value(30));
    }

    @Test
    @DisplayName("Should reject access to IP resource when rate limited")
    void shouldRejectAccessToIpResourceWhenRateLimited() throws Exception {
        when(rateLimiterService.extractClientIp(any())).thenReturn("192.168.1.100");
        when(rateLimiterService.allowIpRequest("192.168.1.100")).thenReturn(false);
        when(rateLimiterService.getIpStatus("192.168.1.100")).thenReturn(
            new RateLimiterService.TokenBucketStatus(0, 30, 3, 500)
        );

        mockMvc.perform(get("/api/ip-resource"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("Rate limit exceeded"))
                .andExpect(jsonPath("$.clientIp").value("192.168.1.100"))
                .andExpect(jsonPath("$.status").value("rate_limited"))
                .andExpect(jsonPath("$.retryAfterMs").value(500));
    }

    @Test
    @DisplayName("Should allow access to protected resource when all limits pass")
    void shouldAllowAccessToProtectedResourceWhenAllLimitsPass() throws Exception {
        when(rateLimiterService.extractClientIp(any())).thenReturn("192.168.1.100");
        when(rateLimiterService.allowApiRequest()).thenReturn(true);
        when(rateLimiterService.allowUserRequest("testUser")).thenReturn(true);
        when(rateLimiterService.allowIpRequest("192.168.1.100")).thenReturn(true);

        mockMvc.perform(get("/api/protected-resource")
                .param("userId", "testUser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Protected resource accessed successfully"))
                .andExpect(jsonPath("$.userId").value("testUser"))
                .andExpect(jsonPath("$.clientIp").value("192.168.1.100"))
                .andExpect(jsonPath("$.status").value("allowed"));
    }

    @Test
    @DisplayName("Should reject access to protected resource when any limit fails")
    void shouldRejectAccessToProtectedResourceWhenAnyLimitFails() throws Exception {
        when(rateLimiterService.extractClientIp(any())).thenReturn("192.168.1.100");
        when(rateLimiterService.allowApiRequest()).thenReturn(true);
        when(rateLimiterService.allowUserRequest("testUser")).thenReturn(false); // User limit fails
        when(rateLimiterService.allowIpRequest("192.168.1.100")).thenReturn(true);

        mockMvc.perform(get("/api/protected-resource")
                .param("userId", "testUser"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("Rate limit exceeded"))
                .andExpect(jsonPath("$.userId").value("testUser"))
                .andExpect(jsonPath("$.clientIp").value("192.168.1.100"))
                .andExpect(jsonPath("$.status").value("rate_limited"))
                .andExpect(jsonPath("$.limits.global").value(true))
                .andExpect(jsonPath("$.limits.user").value(false))
                .andExpect(jsonPath("$.limits.ip").value(true));
    }

    @Test
    @DisplayName("Should return rate limit status")
    void shouldReturnRateLimitStatus() throws Exception {
        RateLimiterService.RateLimitStatus mockStatus = new RateLimiterService.RateLimitStatus(
            100, 80, 20, 50, 5, 3
        );
        when(rateLimiterService.getStatus()).thenReturn(mockStatus);

        mockMvc.perform(get("/api/rate-limit-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests").value(100))
                .andExpect(jsonPath("$.allowedRequests").value(80))
                .andExpect(jsonPath("$.rejectedRequests").value(20))
                .andExpect(jsonPath("$.globalTokensRemaining").value(50))
                .andExpect(jsonPath("$.activeUserBuckets").value(5))
                .andExpect(jsonPath("$.activeIpBuckets").value(3));
    }

    @Test
    @DisplayName("Should return user rate limit status")
    void shouldReturnUserRateLimitStatus() throws Exception {
        RateLimiterService.TokenBucketStatus mockUserStatus = new RateLimiterService.TokenBucketStatus(
            30, 50, 5, 200
        );
        when(rateLimiterService.getUserStatus("testUser")).thenReturn(mockUserStatus);

        mockMvc.perform(get("/api/user-rate-limit-status")
                .param("userId", "testUser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("testUser"))
                .andExpect(jsonPath("$.status.currentTokens").value(30))
                .andExpect(jsonPath("$.status.capacity").value(50))
                .andExpect(jsonPath("$.status.tokensPerSecond").value(5))
                .andExpect(jsonPath("$.status.timeToNextToken").value(200));
    }

    @Test
    @DisplayName("Should handle non-existent user status")
    void shouldHandleNonExistentUserStatus() throws Exception {
        when(rateLimiterService.getUserStatus("nonExistentUser")).thenReturn(null);

        mockMvc.perform(get("/api/user-rate-limit-status")
                .param("userId", "nonExistentUser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("nonExistentUser"))
                .andExpect(jsonPath("$.message").value("No rate limit data found for user"));
    }

    @Test
    @DisplayName("Should return IP rate limit status")
    void shouldReturnIpRateLimitStatus() throws Exception {
        when(rateLimiterService.extractClientIp(any())).thenReturn("192.168.1.100");
        RateLimiterService.TokenBucketStatus mockIpStatus = new RateLimiterService.TokenBucketStatus(
            15, 30, 3, 100
        );
        when(rateLimiterService.getIpStatus("192.168.1.100")).thenReturn(mockIpStatus);

        mockMvc.perform(get("/api/ip-rate-limit-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientIp").value("192.168.1.100"))
                .andExpect(jsonPath("$.status.currentTokens").value(15))
                .andExpect(jsonPath("$.status.capacity").value(30))
                .andExpect(jsonPath("$.status.tokensPerSecond").value(3))
                .andExpect(jsonPath("$.status.timeToNextToken").value(100));
    }

    @Test
    @DisplayName("Should clear user rate limit data")
    void shouldClearUserRateLimitData() throws Exception {
        mockMvc.perform(delete("/api/user-rate-limit")
                .param("userId", "testUser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User rate limit data cleared"))
                .andExpect(jsonPath("$.userId").value("testUser"));
    }

    @Test
    @DisplayName("Should clear IP rate limit data")
    void shouldClearIpRateLimitData() throws Exception {
        mockMvc.perform(delete("/api/ip-rate-limit")
                .param("ipAddress", "192.168.1.100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("IP rate limit data cleared"))
                .andExpect(jsonPath("$.ipAddress").value("192.168.1.100"));
    }

    @Test
    @DisplayName("Should handle missing required parameters")
    void shouldHandleMissingRequiredParameters() throws Exception {
        mockMvc.perform(delete("/api/user-rate-limit"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/ip-rate-limit"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return JSON content type")
    void shouldReturnJsonContentType() throws Exception {
        when(rateLimiterService.allowApiRequest()).thenReturn(true);

        mockMvc.perform(get("/api/resource"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }
}