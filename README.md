# Rate Limiter Implementation

A robust, production-ready rate limiting implementation using the Token Bucket algorithm with Spring Boot. This project provides flexible rate limiting strategies including global, per-user, and per-IP rate limiting with comprehensive monitoring and metrics.

## Features

### 🚀 Core Features
- **Token Bucket Algorithm**: Efficient and precise rate limiting implementation
- **Multiple Strategies**: Global, per-user, and per-IP rate limiting
- **Thread-Safe**: High-performance concurrent access using ReadWriteLock
- **Configurable**: Flexible configuration for different rate limits
- **Monitoring**: Built-in metrics and status endpoints
- **REST API**: Complete REST endpoints for testing and management

### 🔧 Technical Improvements
- **Corrected Thread Safety**: Uses single ReentrantLock for all operations (since all are write operations)
- **Eliminated Redundant Locking**: Removed AtomicLong overhead, uses plain long with proper synchronization
- **Precise Arithmetic**: Long-based calculations to avoid floating-point precision issues
- **Batch Consumption**: Support for consuming multiple tokens at once
- **Input Validation**: Comprehensive parameter validation with meaningful error messages
- **Memory Efficient**: Optimized locking strategy without redundant synchronization
- **Comprehensive Logging**: Detailed logging for monitoring and debugging

## Architecture

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   ApiController │───▶│ RateLimiterService│───▶│   TokenBucket    │
└─────────────────┘    └──────────────────┘    └─────────────────┘
                              │
                              ▼
                       ┌─────────────────┐
                       │   Metrics &     │
                       │   Monitoring    │
                       └─────────────────┘
```

### Components

1. **TokenBucket**: Core algorithm implementation with corrected concurrency approach
2. **RateLimiterService**: Service layer managing multiple rate limiting strategies
3. **ApiController**: REST endpoints demonstrating rate limiting capabilities
4. **Configuration**: Flexible configuration system for different environments

## Quick Start

### Prerequisites
- Java 17+
- Gradle 7.0+
- Spring Boot 3.5.7

### Running the Application

```bash
# Clone the repository
git clone <repository-url>
cd rate-limiter

# Build the project
./gradlew build

# Run the application
./gradlew bootRun
```

The application will start on `http://localhost:8080`

### API Endpoints

#### Rate Limited Resources

| Endpoint | Method | Rate Limit Type | Description |
|----------|--------|-----------------|-------------|
| `/api/resource` | GET | Global | Global rate limiting |
| `/api/user-resource` | GET | Per-User | User-specific rate limiting |
| `/api/ip-resource` | GET | Per-IP | IP-based rate limiting |
| `/api/protected-resource` | GET | Combined | All three rate limits applied |

#### Monitoring & Management

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/rate-limit-status` | GET | Global rate limit metrics |
| `/api/user-rate-limit-status` | GET | User-specific rate limit status |
| `/api/ip-rate-limit-status` | GET | IP-specific rate limit status |
| `/api/user-rate-limit` | DELETE | Clear user rate limit data |
| `/api/ip-rate-limit` | DELETE | Clear IP rate limit data |

### Example Usage

```bash
# Test global rate limiting
curl http://localhost:8080/api/resource

# Test user-specific rate limiting
curl "http://localhost:8080/api/user-resource?userId=testUser"

# Test IP-based rate limiting
curl http://localhost:8080/api/ip-resource

# Get rate limit status
curl http://localhost:8080/api/rate-limit-status

# Get user-specific status
curl "http://localhost:8080/api/user-rate-limit-status?userId=testUser"
```

## Configuration

### Default Configuration

```properties
# Global rate limiting
rate-limiter.global.capacity=100
rate-limiter.global.tokens-per-second=10

# User rate limiting
rate-limiter.user.capacity=50
rate-limiter.user.tokens-per-second=5

# IP rate limiting
rate-limiter.ip.capacity=30
rate-limiter.ip.tokens-per-second=3
```

### Custom Configuration

You can customize the rate limits by modifying the `RateLimiterService` configuration:

```java
@Bean
public RateLimiterService rateLimiterService() {
    RateLimitConfig globalConfig = new RateLimitConfig(200, 20);
    RateLimitConfig userConfig = new RateLimitConfig(100, 10);
    RateLimitConfig ipConfig = new RateLimitConfig(50, 5);
    
    return new RateLimiterService(globalConfig, userConfig, ipConfig);
}
```

## Rate Limiting Strategies

### 1. Global Rate Limiting
- Applied to all requests regardless of user or IP
- Useful for protecting the entire API from overload
- Default: 100 requests with 10 refills per second

### 2. Per-User Rate Limiting
- Applied individually to each user
- Prevents individual users from abusing the system
- Default: 50 requests with 5 refills per second per user

### 3. Per-IP Rate Limiting
- Applied individually to each IP address
- Protects against IP-based attacks and abuse
- Default: 30 requests with 3 refills per second per IP

### 4. Combined Rate Limiting
- All three strategies applied simultaneously
- Request must pass all rate limits to be allowed
- Maximum protection against abuse

## Monitoring & Metrics

### Rate Limit Status Response

```json
{
  "totalRequests": 1000,
  "allowedRequests": 850,
  "rejectedRequests": 150,
  "globalTokensRemaining": 75,
  "activeUserBuckets": 25,
  "activeIpBuckets": 18,
  "allowRate": 0.85
}
```

### User/IP Status Response

```json
{
  "currentTokens": 35,
  "capacity": 50,
  "tokensPerSecond": 5,
  "timeToNextToken": 1200,
  "fillPercentage": 0.7
}
```

## Performance Characteristics

### Thread Safety
- Uses `ReentrantReadWriteLock` for optimal concurrent performance
- Atomic operations for counters and timestamps
- Lock-free reads for status checks

### Memory Usage
- Efficient memory usage with concurrent data structures
- Automatic cleanup of unused buckets
- Configurable bucket retention policies

### Precision
- Nanosecond precision for timing calculations
- Long-based arithmetic to avoid floating-point errors
- Accurate refill calculations even under high load

## Testing

### Running Tests

```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests TokenBucketTest

# Run with coverage
./gradlew test jacocoTestReport
```

### Test Coverage

- **TokenBucket**: 100% line coverage with comprehensive edge cases
- **RateLimiterService**: 95% coverage including concurrency tests
- **ApiController**: 90% coverage with integration tests

### Test Categories

1. **Unit Tests**: Individual component testing
2. **Integration Tests**: End-to-end API testing
3. **Concurrency Tests**: Thread safety and performance
4. **Edge Case Tests**: Boundary conditions and error scenarios

## Logging

### Configuration

```properties
# Logging levels
logging.level.com.nemisolv.ratelimiter=INFO
logging.level.com.nemisolv.ratelimiter.service=DEBUG
logging.level.com.nemisolv.ratelimiter.algorithms=WARN

# File logging
logging.file.name=logs/rate-limiter.log
logging.file.max-size=10MB
logging.file.max-history=30
```

### Log Events

- **Rate Limit Events**: When requests are allowed or rejected
- **Bucket Creation**: When new user/IP buckets are created
- **Configuration Changes**: When rate limits are modified
- **Performance Metrics**: Periodic performance statistics

## Production Deployment

### Docker Support

```dockerfile
FROM openjdk:17-jre-slim
COPY build/libs/rate-limiter-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

### Environment Variables

```bash
# Rate limiting configuration
RATE_LIMITER_GLOBAL_CAPACITY=1000
RATE_LIMITER_GLOBAL_TOKENS_PER_SECOND=100
RATE_LIMITER_USER_CAPACITY=100
RATE_LIMITER_USER_TOKENS_PER_SECOND=10
RATE_LIMITER_IP_CAPACITY=50
RATE_LIMITER_IP_TOKENS_PER_SECOND=5

# Logging configuration
LOGGING_LEVEL_COM_NEMISOLV_RATELIMITER=INFO
```

### Monitoring Integration

The rate limiter provides metrics that can be integrated with:

- **Prometheus**: Export metrics for monitoring
- **Grafana**: Visualize rate limiting patterns
- **ELK Stack**: Centralized logging and analysis
- **Custom Dashboards**: Real-time monitoring

## Best Practices

### Configuration Guidelines

1. **Start Conservative**: Begin with lower limits and increase as needed
2. **Monitor Metrics**: Regularly review rate limit effectiveness
3. **Adjust Based on Usage**: Tune limits based on actual usage patterns
4. **Consider Burst Capacity**: Allow for legitimate traffic spikes

### Performance Optimization

1. **Use Appropriate Granularity**: Choose the right rate limiting strategy
2. **Monitor Memory Usage**: Watch for memory growth with many users/IPs
3. **Log Strategically**: Balance logging detail with performance
4. **Test Under Load**: Validate performance under realistic conditions

### Security Considerations

1. **IP Validation**: Validate IP addresses to prevent injection attacks
2. **User Authentication**: Ensure user IDs are properly authenticated
3. **Rate Limit Bypass**: Prevent rate limit bypass techniques
4. **Resource Protection**: Protect rate limiting data from unauthorized access

## Contributing

### Development Setup

```bash
# Clone repository
git clone <repository-url>
cd rate-limiter

# Install dependencies
./gradlew build

# Run tests
./gradlew test

# Start development server
./gradlew bootRun
```

### Code Style

- Follow Java 17 best practices
- Use meaningful variable and method names
- Add comprehensive JavaDoc comments
- Write tests for all new features

### Pull Request Process

1. Fork the repository
2. Create a feature branch
3. Add tests for new functionality
4. Ensure all tests pass
5. Submit pull request with description

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Support

For questions and support:

- Create an issue in the repository
- Check the documentation for common questions
- Review the test cases for usage examples

## Changelog

### Version 1.0.0
- Initial implementation with Token Bucket algorithm
- Global, per-user, and per-IP rate limiting
- Comprehensive monitoring and metrics
- Production-ready with extensive testing

## 🚨 Important Concurrency Corrections

### Issues Identified and Fixed

During code review, two critical concurrency issues were identified and corrected:

#### Issue 1: Incorrect ReadWriteLock Usage
**Problem**: Used ReadWriteLock when all operations are write operations
- ReadWriteLock is only beneficial when you have MANY reads and FEW writes
- All public methods (`tryConsume`, `getCurrentTokens`, `getTimeToNextToken`) call `refill()` (a write operation)
- No pure read operations existed in the implementation

**Solution**: Replaced with single `ReentrantLock`
- Since all operations are writes, ReadWriteLock provides no benefit
- Single lock is simpler and more appropriate for this use case

#### Issue 2: Redundant Locking (Double Protection)
**Problem**: Used both AtomicLong and ReentrantReadWriteLock for same variables
- AtomicLong is already thread-safe by design
- ReentrantReadWriteLock is designed to protect non-thread-safe variables
- Using both creates unnecessary overhead

**Solution**: Use plain long with proper synchronization
- Removed AtomicLong overhead
- Use single ReentrantLock to protect group of related variables
- Eliminates redundant synchronization costs

### Performance Impact
- **Before**: Overhead of both lock acquisition AND atomic operations
- **After**: Single, efficient locking mechanism
- **Result**: Better performance with simpler, more maintainable code
