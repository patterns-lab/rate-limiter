package com.nemisolv.ratelimiter.service;

import com.nemisolv.ratelimiter.algorithms.TokenBucket;
import org.springframework.stereotype.Service;

/**
 * Service quản lý các instance của Rate Limiter.
 *
 * Trong ví dụ này, chúng ta sẽ tạo một rate limiter chung cho một API cụ thể.
 * Trong thực tế, bạn có thể dùng Map để quản lý nhiều bucket cho nhiều user/IP khác nhau.
 */
@Service
public class RateLimiterService {

    // Ví dụ: Tạo một bucket chung cho 1 API
    // - Sức chứa 10 token
    // - Tốc độ nạp: 2 token/giây
    // Điều này có nghĩa là API chịu được 2 req/s, với khả năng "burst" (tăng đột biến) lên 10 req.
    private final TokenBucket apiBucket = new TokenBucket(10, 2);

    // TODO: Trong tương lai, bạn có thể mở rộng:
    // private final ConcurrentHashMap<String, TokenBucket> bucketsPerUser = new ConcurrentHashMap<>();

    /**
     * Kiểm tra xem yêu cầu có được phép hay không.
     * @return true nếu được phép, false nếu bị rate limit.
     */
    public boolean allowApiRequest() {
        return apiBucket.tryConsume();
    }

    /**
     * (Ví dụ mở rộng trong tương lai)
     * Lấy bucket cho một user cụ thể.
     *
     * @param userId ID của user
     * @return true nếu được phép, false nếu bị rate limit.
     */
    // public boolean allowUserRequest(String userId) {
    //     // Lấy bucket của user, nếu chưa có thì tạo mới
    //     TokenBucket userBucket = bucketsPerUser.computeIfAbsent(userId, id -> {
    //         return new TokenBucket(50, 5); // 50 capacity, 5 token/giây cho mỗi user
    //     });
    //
    //     return userBucket.tryConsume();
    // }
}