package com.nemisolv.ratelimiter.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Represents a rate limiting rule configuration.
 * Based on Lyft's open-source rate-limiting component format.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitRule {
    
    /**
     * Domain for the rate limit (e.g., "messaging", "auth", "api")
     */
    private String domain;
    
    /**
     * Descriptors that define what this rule applies to
     */
    private Descriptor[] descriptors;
    
    /**
     * Rate limit configuration
     */
    @JsonProperty("rate_limit")
    private RateLimit rateLimit;
    
    /**
     * Descriptor for matching requests
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Descriptor {
        /**
         * Key to match (e.g., "message_type", "auth_type", "user_id", "ip_address")
         */
        private String key;
        
        /**
         * Value to match (e.g., "marketing", "login", or wildcard "*")
         */
        private String value;
    }
    
    /**
     * Rate limit configuration
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RateLimit {
        /**
         * Time unit for the rate limit (second, minute, hour, day)
         */
        @JsonProperty("unit")
        private String unit;
        
        /**
         * Number of requests allowed per unit time
         */
        @JsonProperty("requests_per_unit")
        private long requestsPerUnit;
        
        /**
         * Convert unit to seconds for internal processing
         */
        public long getUnitInSeconds() {
            return switch (unit.toLowerCase()) {
                case "second" -> 1L;
                case "minute" -> 60L;
                case "hour" -> 3600L;
                case "day" -> 86400L;
                default -> throw new IllegalArgumentException("Unknown time unit: " + unit);
            };
        }
        
        /**
         * Calculate tokens per second for token bucket algorithm
         */
        public long getTokensPerSecond() {
            return requestsPerUnit / getUnitInSeconds();
        }
    }
}