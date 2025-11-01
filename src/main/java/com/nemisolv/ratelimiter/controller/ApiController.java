package com.nemisolv.ratelimiter.controller;

import com.nemisolv.ratelimiter.service.RateLimiterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

    private final RateLimiterService rateLimiterService;

    /**
     * Một API mẫu bị áp dụng rate limiting.
     */
    @GetMapping("/resource")
    public ResponseEntity<String> getResource() {
        
        // Hỏi service xem yêu cầu có được phép không
        if (rateLimiterService.allowApiRequest()) {
            
            // Được phép: Trả về 200 OK
            return ResponseEntity.ok("Resource accessed successfully. (Tokens remaining may be decreasing)");
        
        } else {
            
            // Bị từ chối: Trả về 429 TOO_MANY_REQUESTS
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Rate limit exceeded. Please try again later.");
        }
    }
}