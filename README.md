# Design Rate Limiter System

## Agenda: Implement 5 common rate limit algorithm
- [x] Token bucket
- [ ] Leaking bucket
- [ ] Fixed window counter
- [ ] Sliding window log
- [ ] Sliding window counter

---

## 1. Token Bucket (Thùng Token)

### Giới thiệu

Thuật toán Token Bucket (Thùng Token) là một trong những thuật toán rate limiting phổ biến và linh hoạt nhất.


**Cách hoạt động:**

1.  **Thùng (Bucket):** Có một thùng với sức chứa tối đa (ví dụ: 100 token).
2.  **Tốc độ nạp (Refill Rate):** Token được thêm vào thùng với một tốc độ không đổi (ví dụ: 10 token/giây).
3.  **Yêu cầu (Request):** Khi một yêu cầu đến, nó phải "tiêu thụ" 1 token từ thùng.
    * **Còn token:** Yêu cầu được chấp nhận.
    * **Hết token:** Yêu cầu bị từ chối (hoặc xếp hàng).
4.  **Giới hạn:** Số lượng token trong thùng không bao giờ vượt quá sức chứa tối đa. Nếu token được nạp vào khi thùng đã đầy, token đó sẽ bị "bỏ đi".

**Ưu điểm:**
* **Linh hoạt (Burstable):** Thuật toán này cho phép một lượng yêu cầu tăng đột biến (burst) trong thời gian ngắn (lên đến số token đang có trong thùng). Ví dụ, nếu thùng có 100 token, hệ thống có thể xử lý 100 yêu cầu cùng lúc.
* **Mượt mà (Smooth):** Sau khi xử lý "burst", hệ thống sẽ quay về tốc độ xử lý trung bình bằng với tốc độ nạp token.
* **Dễ hiểu và triển khai.**

### Cách sử dụng trong Lab này

1.  **Class chính:** `com.nemisolv.ratelimiter.algorithms.TokenBucket`
2.  **Khởi tạo:**
    ```java
    // new TokenBucket(long capacity, long tokensPerSecond)
    // Ví dụ: Sức chứa 10, tốc độ nạp 2 token/giây
    TokenBucket bucket = new TokenBucket(10, 2);
    ```
3.  **Sử dụng:**
    ```java
    if (bucket.tryConsume()) {
        // Cho phép yêu cầu
    } else {
        // Từ chối yêu cầu (Rate Limit)
    }
    ```
4.  **API Thử nghiệm:**
    * Start ứng dụng Spring Boot.
    * Truy cập: `http://localhost:8080/api/resource`
    * Bạn sẽ thấy rằng bạn có thể gọi API này 10 lần liên tiếp (burst), sau đó bạn sẽ bị giới hạn ở tốc độ 2 yêu cầu/giây.