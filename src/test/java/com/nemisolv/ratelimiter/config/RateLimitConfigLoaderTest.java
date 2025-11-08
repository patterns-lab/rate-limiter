package com.nemisolv.ratelimiter.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class RateLimitConfigLoaderTest {

    private RateLimitConfigLoader configLoader;

    @BeforeEach
    void setUp() {
        configLoader = new RateLimitConfigLoader();
        configLoader.loadConfiguration();
    }

    @Test
    void loadConfiguration_WhenFileExists_ShouldLoadRules() {
        // Act
        List<RateLimitRule> rules = configLoader.getRules();

        // Assert
        assertNotNull(rules);
        assertFalse(rules.isEmpty());
        
        // Check that default rules are loaded when file doesn't exist
        assertTrue(rules.size() >= 3);
        
        // Verify default API rule
        RateLimitRule apiRule = rules.stream()
            .filter(rule -> "api".equals(rule.getDomain()))
            .findFirst()
            .orElse(null);
        
        assertNotNull(apiRule);
        assertEquals("api", apiRule.getDomain());
        assertEquals("minute", apiRule.getRateLimit().getUnit());
        assertEquals(100, apiRule.getRateLimit().getRequestsPerUnit());
    }

    @Test
    void findMatchingRule_WithMatchingDescriptors_ShouldReturnCorrectRule() {
        // Arrange
        List<RateLimitRule.Descriptor> descriptors = List.of(
            new RateLimitRule.Descriptor("auth_type", "login")
        );

        // Act
        RateLimitRule rule = configLoader.findMatchingRule("auth", descriptors);

        // Assert
        assertNotNull(rule);
        assertEquals("auth", rule.getDomain());
        assertEquals("login", rule.getDescriptors()[0].getValue());
        assertEquals("minute", rule.getRateLimit().getUnit());
        assertEquals(5, rule.getRateLimit().getRequestsPerUnit());
    }

    @Test
    void findMatchingRule_WithNonMatchingDescriptors_ShouldReturnNull() {
        // Arrange
        List<RateLimitRule.Descriptor> descriptors = List.of(
            new RateLimitRule.Descriptor("auth_type", "nonexistent")
        );

        // Act
        RateLimitRule rule = configLoader.findMatchingRule("auth", descriptors);

        // Assert
        assertNull(rule);
    }

    @Test
    void findMatchingRule_WithWildcardDescriptor_ShouldReturnRule() {
        // Arrange
        List<RateLimitRule.Descriptor> descriptors = List.of(
            new RateLimitRule.Descriptor("path", "/api/some/path")
        );

        // Act
        RateLimitRule rule = configLoader.findMatchingRule("api", descriptors);

        // Assert
        assertNotNull(rule);
        assertEquals("api", rule.getDomain());
        assertEquals("*", rule.getDescriptors()[0].getValue());
    }

    @Test
    void rateLimitGetUnitInSeconds_ShouldReturnCorrectValues() {
        // Test all time units
        assertEquals(1L, new RateLimitRule.RateLimit("second", 1).getUnitInSeconds());
        assertEquals(60L, new RateLimitRule.RateLimit("minute", 1).getUnitInSeconds());
        assertEquals(3600L, new RateLimitRule.RateLimit("hour", 1).getUnitInSeconds());
        assertEquals(86400L, new RateLimitRule.RateLimit("day", 1).getUnitInSeconds());
    }

    @Test
    void rateLimitGetTokensPerSecond_ShouldReturnCorrectValues() {
        // Test tokens per second calculation
        assertEquals(1.0, new RateLimitRule.RateLimit("second", 1).getTokensPerSecond());
        assertEquals(0.1, new RateLimitRule.RateLimit("minute", 6).getTokensPerSecond());
        assertEquals(1.0/3600, new RateLimitRule.RateLimit("hour", 1).getTokensPerSecond());
        assertEquals(5.0/86400, new RateLimitRule.RateLimit("day", 5).getTokensPerSecond());
    }

    @Test
    void rateLimitInvalidUnit_ShouldThrowException() {
        // Test invalid time unit
        assertThrows(IllegalArgumentException.class, () -> 
            new RateLimitRule.RateLimit("invalid", 1).getUnitInSeconds());
    }
}