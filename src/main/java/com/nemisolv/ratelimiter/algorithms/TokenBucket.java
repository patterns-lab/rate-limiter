package com.nemisolv.ratelimiter.algorithms;

import java.util.concurrent.TimeUnit;

/**
 * Triển khai thuật toán Rate Limiting: Token Bucket.
 *
 * <p>Thuật toán này sử dụng một "thùng" chứa "token".
 * Token được thêm vào thùng với một tốc độ không đổi (refillRate).
 * Thùng có một sức chứa tối đa (capacity).
 *
 * <p>Mỗi yêu cầu đến sẽ cố gắng lấy 1 token:
 * - Nếu còn token, yêu cầu được chấp nhận.
 * - Nếu hết token, yêu cầu bị từ chối.
 *
 * <p>Lớp này được thiết kế để an toàn khi chạy đa luồng (thread-safe)
 * bằng cách sử dụng "synchronized" cho phương thức then chốt.
 */
public class TokenBucket {

    /**
     * Sức chứa tối đa của thùng.
     */
    private final long capacity;

    /**
     * Số lượng token được thêm vào thùng MỖI GIÂY.
     */
    private final long tokensPerSecond;

    /**
     * Số lượng token hiện tại trong thùng.
     */
    private long currentTokens;

    /**
     * Mốc thời gian (nano giây) lần cuối cùng token được nạp lại.
     */
    private long lastRefillNanos;

    /**
     * Khởi tạo một Token Bucket mới.
     *
     * @param capacity Sức chứa tối đa (ví dụ: 100).
     * @param tokensPerSecond Tốc độ nạp token (ví dụ: 10 token/giây).
     */
    public TokenBucket(long capacity, long tokensPerSecond) {
        this.capacity = capacity;
        this.tokensPerSecond = tokensPerSecond;

        // Khởi tạo thùng đầy token
        this.currentTokens = capacity;
        this.lastRefillNanos = System.nanoTime();
    }

    /**
     * Cố gắng tiêu thụ một token từ thùng.
     *
     * <p>Đây là phương thức "synchronized" để đảm bảo an toàn luồng.
     *
     * @return true nếu tiêu thụ thành công (cho phép yêu cầu),
     * false nếu hết token (từ chối yêu cầu).
     */
    public synchronized boolean tryConsume() {
        // 1. Nạp lại token trước khi kiểm tra
        refill();

        // 2. Kiểm tra xem có đủ token để tiêu thụ không
        if (currentTokens > 0) {
            // Tiêu thụ 1 token
            currentTokens--;
            return true;
        }

        // Hết token
        return false;
    }

    /**
     * Phương thức nội bộ để nạp lại token dựa trên thời gian đã trôi qua.
     */
    private void refill() {
        long now = System.nanoTime();
        
        // Thời gian trôi qua (tính bằng giây) kể từ lần nạp cuối
        double secondsSinceLastRefill = (double) (now - lastRefillNanos) / TimeUnit.SECONDS.toNanos(1);

        // Tính số token cần thêm
        // (chuyển sang (long) để lấy phần nguyên)
        long tokensToAdd = (long) (secondsSinceLastRefill * tokensPerSecond);

        if (tokensToAdd > 0) {
            // Nạp token mới, nhưng không vượt quá sức chứa
            this.currentTokens = Math.min(capacity, currentTokens + tokensToAdd);
            
            // Cập nhật mốc thời gian nạp
            this.lastRefillNanos = now;
        }
    }
}