package com.nemisolv.ratelimiter.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

/**
 * Loads rate limiting configuration from YAML or JSON files.
 * Supports flexible configuration similar to Lyft's rate-limiting component.
 */
@Component
@Slf4j
public class RateLimitConfigLoader {
    
    @Value("classpath:rate-limiter-config.yml")
    private Resource configFile;

    /**
     * -- GETTER --
     *  Get all loaded rate limiting rules
     */
    @Getter
    private List<RateLimitRule> rules;
    private final ObjectMapper yamlMapper;
    private final ObjectMapper jsonMapper;
    
    public RateLimitConfigLoader() {
        this.yamlMapper = new YAMLMapper();
        this.jsonMapper = new ObjectMapper();
    }
    
    @PostConstruct
    public void loadConfiguration() {
        try {
            if (configFile.exists() && configFile.isReadable()) {
                String filename = configFile.getFilename();
                if (filename != null && filename.endsWith(".yml")) {
                    rules = Arrays.asList(yamlMapper.readValue(configFile.getInputStream(), RateLimitRule[].class));
                } else {
                    rules = Arrays.asList(jsonMapper.readValue(configFile.getInputStream(), RateLimitRule[].class));
                }
                log.info("Loaded {} rate limiting rules from {}", rules.size(), filename);
            } else {
                log.warn("Rate limit configuration file not found: {}. Using default configuration.", configFile.getFilename());
                loadDefaultConfiguration();
            }
        } catch (IOException e) {
            log.error("Failed to load rate limit configuration: {}", e.getMessage(), e);
            loadDefaultConfiguration();
        }
    }
    
    /**
     * Loads default configuration when file is not available
     */
    private void loadDefaultConfiguration() {
        rules = List.of(
            new RateLimitRule(
                "api",
                new RateLimitRule.Descriptor[]{
                    new RateLimitRule.Descriptor("path", "*")
                },
                new RateLimitRule.RateLimit("minute", 100)
            ),
            new RateLimitRule(
                "auth",
                new RateLimitRule.Descriptor[]{
                    new RateLimitRule.Descriptor("auth_type", "login")
                },
                new RateLimitRule.RateLimit("minute", 5)
            ),
            new RateLimitRule(
                "messaging",
                new RateLimitRule.Descriptor[]{
                    new RateLimitRule.Descriptor("message_type", "marketing")
                },
                new RateLimitRule.RateLimit("day", 5)
            )
        );
        log.info("Loaded default rate limiting configuration with {} rules", rules.size());
    }

    /**
     * Find matching rule for given descriptors
     */
    public RateLimitRule findMatchingRule(String domain, List<RateLimitRule.Descriptor> requestDescriptors) {
        return rules.stream()
            .filter(rule -> rule.getDomain().equals(domain))
            .filter(rule -> matchesDescriptors(rule.getDescriptors(), requestDescriptors))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Check if rule descriptors match request descriptors
     */
    private boolean matchesDescriptors(RateLimitRule.Descriptor[] ruleDescriptors, 
                                     List<RateLimitRule.Descriptor> requestDescriptors) {
        if (ruleDescriptors.length == 0) {
            return true;
        }
        
        for (RateLimitRule.Descriptor ruleDesc : ruleDescriptors) {
            boolean matched = requestDescriptors.stream()
                .anyMatch(reqDesc -> 
                    ruleDesc.getKey().equals(reqDesc.getKey()) && 
                    (ruleDesc.getValue().equals("*") || ruleDesc.getValue().equals(reqDesc.getValue()))
                );
            
            if (!matched) {
                return false;
            }
        }
        
        return true;
    }
}