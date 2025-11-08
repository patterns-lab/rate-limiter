package com.nemisolv.ratelimiter.config;

import com.nemisolv.ratelimiter.middleware.RateLimitingMiddleware;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC configuration for registering interceptors including rate limiting middleware.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {
    
    private final RateLimitingMiddleware rateLimitingMiddleware;
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Register rate limiting middleware for all API endpoints
        registry.addInterceptor(rateLimitingMiddleware)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/rate-limit-status", "/api/user-rate-limit-status", "/api/ip-rate-limit-status");
    }
}