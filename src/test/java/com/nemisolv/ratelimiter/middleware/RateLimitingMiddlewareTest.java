package com.nemisolv.ratelimiter.middleware;

import com.nemisolv.ratelimiter.config.RateLimitConfigLoader;
import com.nemisolv.ratelimiter.config.RateLimitRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.UnsupportedEncodingException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitingMiddlewareTest {

    @Mock
    private RateLimitConfigLoader configLoader;

    @InjectMocks
    private RateLimitingMiddleware middleware;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setRequestURI("/api/resource");
        request.setMethod("GET");
        request.setRemoteAddr("127.0.0.1");
    }

    @Test
    void preHandle_WhenNoMatchingRule_ShouldReturnTrue() throws Exception {
        // Arrange
        when(configLoader.findMatchingRule(any(String.class), any(List.class))).thenReturn(null);

        // Act
        boolean result = middleware.preHandle(request, response, null);

        // Assert
        assertTrue(result);
        assertEquals(HttpStatus.OK.value(), response.getStatus());
    }

    @Test
    void preHandle_WhenMatchingRuleAndAllowed_ShouldReturnTrueAndSetHeaders() throws Exception {
        // Arrange
        RateLimitRule.RateLimit rateLimit = new RateLimitRule.RateLimit("minute", 10);
        RateLimitRule rule = new RateLimitRule("api", 
            new RateLimitRule.Descriptor[]{new RateLimitRule.Descriptor("path", "*")}, 
            rateLimit);
        
        when(configLoader.findMatchingRule(any(String.class), any(List.class))).thenReturn(rule);

        // Act
        boolean result = middleware.preHandle(request, response, null);

        // Assert
        assertTrue(result);
        assertEquals(HttpStatus.OK.value(), response.getStatus());
        assertEquals("10", response.getHeader("X-Ratelimit-Limit"));
        assertNotNull(response.getHeader("X-Ratelimit-Remaining"));
        assertNull(response.getHeader("X-Ratelimit-Retry-After"));
    }

    @Test
    void preHandle_WhenRateLimitExceeded_ShouldReturnFalseAndSetHeaders() throws Exception {
        // Arrange
        RateLimitRule.RateLimit rateLimit = new RateLimitRule.RateLimit("minute", 1);
        RateLimitRule rule = new RateLimitRule("api", 
            new RateLimitRule.Descriptor[]{new RateLimitRule.Descriptor("path", "*")}, 
            rateLimit);
        
        when(configLoader.findMatchingRule(any(String.class), any(List.class))).thenReturn(rule);

        // First request should be allowed
        middleware.preHandle(request, response, null);
        System.out.println(response.getHeader("X-Ratelimit-Limit"));
        System.out.println(response.getHeader("X-Ratelimit-Remaining"));
        System.out.println(response.getHeader("X-Ratelimit-Retry-After"));

        // Reset response for second request
        response = new MockHttpServletResponse();


        // Act - Second request should be rate limited
        boolean result = middleware.preHandle(request, response, null);

        // Assert
        assertFalse(result);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), response.getStatus());
        assertEquals("1", response.getHeader("X-Ratelimit-Limit"));
        assertEquals("0", response.getHeader("X-Ratelimit-Remaining"));
        assertNotNull(response.getHeader("X-Ratelimit-Retry-After"));
        
        // Check error response body
        String responseBody = response.getContentAsString();
        assertTrue(responseBody.contains("Rate limit exceeded"));
    }

    @Test
    void preHandle_WithUserIdHeader_ShouldExtractUserId() throws Exception {
        // Arrange
        request.addHeader("X-User-ID", "test-user");
        RateLimitRule.RateLimit rateLimit = new RateLimitRule.RateLimit("minute", 10);
        RateLimitRule rule = new RateLimitRule("user",
            new RateLimitRule.Descriptor[]{new RateLimitRule.Descriptor("user_id", "*")},
            rateLimit);
        
        when(configLoader.findMatchingRule(any(String.class), any(List.class))).thenReturn(rule);

        // Act
        boolean result = middleware.preHandle(request, response, null);

        // Assert
        assertTrue(result);
        assertEquals(HttpStatus.OK.value(), response.getStatus());
    }

    @Test
    void preHandle_WithXForwardedForHeader_ShouldExtractCorrectIp() throws Exception {
        // Arrange
        request.addHeader("X-Forwarded-For", "192.168.1.100, 10.0.0.1");
        RateLimitRule.RateLimit rateLimit = new RateLimitRule.RateLimit("minute", 10);
        RateLimitRule rule = new RateLimitRule("ip",
            new RateLimitRule.Descriptor[]{new RateLimitRule.Descriptor("ip_address", "*")},
            rateLimit);
        
        when(configLoader.findMatchingRule(any(String.class), any(List.class))).thenReturn(rule);

        // Act
        boolean result = middleware.preHandle(request, response, null);

        // Assert
        assertTrue(result);
        assertEquals(HttpStatus.OK.value(), response.getStatus());
    }

    @Test
    void preHandle_WithAuthLoginPath_ShouldMatchAuthRule() throws Exception {
        // Arrange
        request.setRequestURI("/api/auth/login");
        request.setMethod("POST");
        RateLimitRule.RateLimit rateLimit = new RateLimitRule.RateLimit("minute", 5);
        RateLimitRule rule = new RateLimitRule("auth",
            new RateLimitRule.Descriptor[]{new RateLimitRule.Descriptor("auth_type", "login")},
            rateLimit);
        
        when(configLoader.findMatchingRule(any(String.class), any(List.class))).thenReturn(rule);

        // Act
        boolean result = middleware.preHandle(request, response, null);

        // Assert
        assertTrue(result);
        assertEquals(HttpStatus.OK.value(), response.getStatus());
    }

    @Test
    void preHandle_WithPasswordResetPath_ShouldMatchPasswordResetRule() throws Exception {
        // Arrange
        request.setRequestURI("/api/auth/password-reset");
        request.setMethod("POST");
        RateLimitRule.RateLimit rateLimit = new RateLimitRule.RateLimit("hour", 3);
        RateLimitRule rule = new RateLimitRule("auth",
            new RateLimitRule.Descriptor[]{new RateLimitRule.Descriptor("auth_type", "password_reset")},
            rateLimit);
        
        when(configLoader.findMatchingRule(any(String.class), any(List.class))).thenReturn(rule);

        // Act
        boolean result = middleware.preHandle(request, response, null);

        // Assert
        assertTrue(result);
        assertEquals(HttpStatus.OK.value(), response.getStatus());
    }

    @Test
    void preHandle_WithMessagingEndpoint_ShouldMatchMessagingRule() throws Exception {
        // Arrange
        request.setRequestURI("/api/messaging/send");
        request.setMethod("POST");
        request.addParameter("message_type", "marketing");
        RateLimitRule.RateLimit rateLimit = new RateLimitRule.RateLimit("day", 5);
        RateLimitRule rule = new RateLimitRule("messaging",
            new RateLimitRule.Descriptor[]{new RateLimitRule.Descriptor("message_type", "marketing")},
            rateLimit);
        
        when(configLoader.findMatchingRule(any(String.class), any(List.class))).thenReturn(rule);

        // Act
        boolean result = middleware.preHandle(request, response, null);

        // Assert
        assertTrue(result);
        assertEquals(HttpStatus.OK.value(), response.getStatus());
    }
}
