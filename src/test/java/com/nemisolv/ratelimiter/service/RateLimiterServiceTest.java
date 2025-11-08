package com.nemisolv.ratelimiter.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterServiceTest {

    private RateLimiterService rateLimiterService;
    
    @Mock
    private MockHttpServletRequest mockRequest;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        rateLimiterService = new RateLimiterService();
    }

    @Nested
    @DisplayName("Global Rate Limiting Tests")
    class GlobalRateLimitingTests {

        @Test
        @DisplayName("Should allow requests within global limit")
        void shouldAllowRequestsWithinGlobalLimit() {
            // Should allow initial requests up to capacity
            for (int i = 0; i < 100; i++) { // Default global capacity is 100
                assertTrue(rateLimiterService.allowApiRequest());
            }
        }

        @Test
        @DisplayName("Should reject requests exceeding global limit")
        void shouldRejectRequestsExceedingGlobalLimit() {
            // Consume all tokens
            for (int i = 0; i < 100; i++) {
                rateLimiterService.allowApiRequest();
            }

            // Next request should be rejected
            assertFalse(rateLimiterService.allowApiRequest());
        }

        @Test
        @DisplayName("Should track global metrics correctly")
        void shouldTrackGlobalMetricsCorrectly() {
            // Make some requests
            for (int i = 0; i < 50; i++) {
                rateLimiterService.allowApiRequest();
            }

            RateLimiterService.RateLimitStatus status = rateLimiterService.getStatus();
            assertEquals(50, status.getTotalRequests());
            assertEquals(50, status.getAllowedRequests());
            assertEquals(0, status.getRejectedRequests());
        }
    }

    @Nested
    @DisplayName("User Rate Limiting Tests")
    class UserRateLimitingTests {

        @Test
        @DisplayName("Should allow requests within user limit")
        void shouldAllowRequestsWithinUserLimit() {
            String userId = "testUser";
            
            // Should allow requests up to user capacity (default 50)
            for (int i = 0; i < 50; i++) {
                assertTrue(rateLimiterService.allowUserRequest(userId));
            }
        }

        @Test
        @DisplayName("Should reject requests exceeding user limit")
        void shouldRejectRequestsExceedingUserLimit() {
            String userId = "testUser";
            
            // Consume all user tokens
            for (int i = 0; i < 50; i++) {
                rateLimiterService.allowUserRequest(userId);
            }

            // Next request should be rejected
            assertFalse(rateLimiterService.allowUserRequest(userId));
        }

        @Test
        @DisplayName("Should handle multiple users independently")
        void shouldHandleMultipleUsersIndependently() {
            String user1 = "user1";
            String user2 = "user2";
            
            // Consume all tokens for user1
            for (int i = 0; i < 50; i++) {
                assertTrue(rateLimiterService.allowUserRequest(user1));
            }
            
            // user1 should be rate limited
            assertFalse(rateLimiterService.allowUserRequest(user1));
            
            // user2 should still have full capacity
            for (int i = 0; i < 50; i++) {
                assertTrue(rateLimiterService.allowUserRequest(user2));
            }
        }

        @Test
        @DisplayName("Should reject invalid user IDs")
        void shouldRejectInvalidUserIds() {
            assertFalse(rateLimiterService.allowUserRequest(null));
            assertFalse(rateLimiterService.allowUserRequest(""));
            assertFalse(rateLimiterService.allowUserRequest("   "));
        }

        @Test
        @DisplayName("Should provide user status correctly")
        void shouldProvideUserStatusCorrectly() {
            String userId = "testUser";
            
            // Make some requests
            for (int i = 0; i < 10; i++) {
                rateLimiterService.allowUserRequest(userId);
            }

            RateLimiterService.TokenBucketStatus status = rateLimiterService.getUserStatus(userId);
            assertNotNull(status);
            assertEquals(40, status.getCurrentTokens()); // 50 - 10 = 40
            assertEquals(50, status.getCapacity());
            assertEquals(5, status.getTokensPerSecond()); // Default user rate
        }

        @Test
        @DisplayName("Should return null for non-existent user status")
        void shouldReturnNullForNonExistentUserStatus() {
            RateLimiterService.TokenBucketStatus status = rateLimiterService.getUserStatus("nonExistentUser");
            assertNull(status);
        }
    }

    @Nested
    @DisplayName("IP Rate Limiting Tests")
    class IpRateLimitingTests {

        @Test
        @DisplayName("Should allow requests within IP limit")
        void shouldAllowRequestsWithinIpLimit() {
            String ipAddress = "192.168.1.1";
            
            // Should allow requests up to IP capacity (default 30)
            for (int i = 0; i < 30; i++) {
                assertTrue(rateLimiterService.allowIpRequest(ipAddress));
            }
        }

        @Test
        @DisplayName("Should reject requests exceeding IP limit")
        void shouldRejectRequestsExceedingIpLimit() {
            String ipAddress = "192.168.1.1";
            
            // Consume all IP tokens
            for (int i = 0; i < 30; i++) {
                rateLimiterService.allowIpRequest(ipAddress);
            }

            // Next request should be rejected
            assertFalse(rateLimiterService.allowIpRequest(ipAddress));
        }

        @Test
        @DisplayName("Should handle multiple IPs independently")
        void shouldHandleMultipleIpsIndependently() {
            String ip1 = "192.168.1.1";
            String ip2 = "192.168.1.2";
            
            // Consume all tokens for ip1
            for (int i = 0; i < 30; i++) {
                assertTrue(rateLimiterService.allowIpRequest(ip1));
            }
            
            // ip1 should be rate limited
            assertFalse(rateLimiterService.allowIpRequest(ip1));
            
            // ip2 should still have full capacity
            for (int i = 0; i < 30; i++) {
                assertTrue(rateLimiterService.allowIpRequest(ip2));
            }
        }

        @Test
        @DisplayName("Should reject invalid IP addresses")
        void shouldRejectInvalidIpAddresses() {
            assertFalse(rateLimiterService.allowIpRequest(null));
            assertFalse(rateLimiterService.allowIpRequest(""));
            assertFalse(rateLimiterService.allowIpRequest("   "));
        }
    }

    @Nested
    @DisplayName("IP Extraction Tests")
    class IpExtractionTests {

        @Test
        @DisplayName("Should extract IP from X-Forwarded-For header")
        void shouldExtractIpFromXForwardedForHeader() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Forwarded-For", "203.0.113.1, 192.168.1.1");
            
            String ip = rateLimiterService.extractClientIp(request);
            assertEquals("203.0.113.1", ip);
        }

        @Test
        @DisplayName("Should extract IP from X-Real-IP header")
        void shouldExtractIpFromXRealIpHeader() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Real-IP", "203.0.113.2");
            
            String ip = rateLimiterService.extractClientIp(request);
            assertEquals("203.0.113.2", ip);
        }

        @Test
        @DisplayName("Should fall back to remote address when headers not present")
        void shouldFallBackToRemoteAddressWhenHeadersNotPresent() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("192.168.1.100");
            
            String ip = rateLimiterService.extractClientIp(request);
            assertEquals("192.168.1.100", ip);
        }

        @Test
        @DisplayName("Should handle unknown X-Forwarded-For values")
        void shouldHandleUnknownXForwardedForValues() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Forwarded-For", "unknown");
            request.setRemoteAddr("192.168.1.100");
            
            String ip = rateLimiterService.extractClientIp(request);
            assertEquals("192.168.1.100", ip);
        }
    }

    @Nested
    @DisplayName("Metrics and Monitoring Tests")
    class MetricsAndMonitoringTests {

        @Test
        @DisplayName("Should track overall metrics correctly")
        void shouldTrackOverallMetricsCorrectly() {
            // Make some requests that will be allowed and rejected
            for (int i = 0; i < 150; i++) { // Exceeds global capacity of 100
                rateLimiterService.allowApiRequest();
            }

            RateLimiterService.RateLimitStatus status = rateLimiterService.getStatus();
            assertEquals(150, status.getTotalRequests());
            assertEquals(100, status.getAllowedRequests());
            assertEquals(50, status.getRejectedRequests());
            assertEquals(0.67, status.getAllowRate(), 0.01); // ~100/150
        }

        @Test
        @DisplayName("Should track active bucket counts")
        void shouldTrackActiveBucketCounts() {
            // Create users and IPs to generate buckets
            for (int i = 0; i < 5; i++) {
                rateLimiterService.allowUserRequest("user" + i);
                rateLimiterService.allowIpRequest("192.168.1." + i);
            }

            RateLimiterService.RateLimitStatus status = rateLimiterService.getStatus();
            assertEquals(5, status.getActiveUserBuckets());
            assertEquals(5, status.getActiveIpBuckets());
        }

        @Test
        @DisplayName("Should provide global tokens remaining")
        void shouldProvideGlobalTokensRemaining() {
            // Consume some global tokens
            for (int i = 0; i < 20; i++) {
                rateLimiterService.allowApiRequest();
            }

            RateLimiterService.RateLimitStatus status = rateLimiterService.getStatus();
            assertEquals(80, status.getGlobalTokensRemaining()); // 100 - 20 = 80
        }
    }

    @Nested
    @DisplayName("Data Management Tests")
    class DataManagementTests {

        @Test
        @DisplayName("Should clear user data correctly")
        void shouldClearUserDataCorrectly() {
            String userId = "testUser";
            
            // Create user bucket by making requests
            rateLimiterService.allowUserRequest(userId);
            assertNotNull(rateLimiterService.getUserStatus(userId));
            
            // Clear user data
            rateLimiterService.clearUserData(userId);
            assertNull(rateLimiterService.getUserStatus(userId));
        }

        @Test
        @DisplayName("Should clear IP data correctly")
        void shouldClearIpDataCorrectly() {
            String ipAddress = "192.168.1.1";
            
            // Create IP bucket by making requests
            rateLimiterService.allowIpRequest(ipAddress);
            assertNotNull(rateLimiterService.getIpStatus(ipAddress));
            
            // Clear IP data
            rateLimiterService.clearIpData(ipAddress);
            assertNull(rateLimiterService.getIpStatus(ipAddress));
        }
    }

    @Nested
    @DisplayName("Custom Configuration Tests")
    class CustomConfigurationTests {

        @Test
        @DisplayName("Should use custom configurations")
        void shouldUseCustomConfigurations() {
            RateLimiterService.RateLimitConfig globalConfig = 
                new RateLimiterService.RateLimitConfig(50, 5);
            RateLimiterService.RateLimitConfig userConfig = 
                new RateLimiterService.RateLimitConfig(25, 2);
            RateLimiterService.RateLimitConfig ipConfig = 
                new RateLimiterService.RateLimitConfig(15, 1);
            
            RateLimiterService customService = 
                new RateLimiterService(globalConfig, userConfig, ipConfig);
            
            // Test global limits
            for (int i = 0; i < 50; i++) {
                assertTrue(customService.allowApiRequest());
            }
            assertFalse(customService.allowApiRequest());
            
            // Test user limits
            for (int i = 0; i < 25; i++) {
                assertTrue(customService.allowUserRequest("testUser"));
            }
            assertFalse(customService.allowUserRequest("testUser"));
            
            // Test IP limits
            for (int i = 0; i < 15; i++) {
                assertTrue(customService.allowIpRequest("192.168.1.1"));
            }
            assertFalse(customService.allowIpRequest("192.168.1.1"));
        }
    }

    @Nested
    @DisplayName("RateLimitConfig Tests")
    class RateLimitConfigTests {

        @Test
        @DisplayName("Should create config with correct values")
        void shouldCreateConfigWithCorrectValues() {
            RateLimiterService.RateLimitConfig config = 
                new RateLimiterService.RateLimitConfig(100, 10);
            
            assertEquals(100, config.capacity());
            assertEquals(10, config.tokensPerSecond());
        }
    }

    @Nested
    @DisplayName("TokenBucketStatus Tests")
    class TokenBucketStatusTests {

        @Test
        @DisplayName("Should calculate fill percentage correctly")
        void shouldCalculateFillPercentageCorrectly() {
            RateLimiterService.TokenBucketStatus status = 
                new RateLimiterService.TokenBucketStatus(50, 100, 10, 1000);
            
            assertEquals(0.5, status.getFillPercentage());
        }

        @Test
        @DisplayName("Should handle zero capacity gracefully")
        void shouldHandleZeroCapacityGracefully() {
            RateLimiterService.TokenBucketStatus status = 
                new RateLimiterService.TokenBucketStatus(0, 0, 0, 0);
            
            assertEquals(0.0, status.getFillPercentage());
        }
    }
}