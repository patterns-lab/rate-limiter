# API Gateway with Rate Limiting

A production-ready API Gateway with flexible rate limiting implementation using Token Bucket algorithm. This project demonstrates a clean architecture with middleware-based rate limiting, following industry best practices similar to Lyft's open-source rate-limiting component.

## 🚀 Features

### Core Features
- **Middleware-based Rate Limiting**: Clean separation of concerns with interceptors
- **Flexible Configuration**: YAML/JSON-based rate limiting rules
- **Multiple Rate Limiting Strategies**: Global, per-user, per-IP, endpoint-specific, and domain-based
- **Standard HTTP Headers**: X-Ratelimit-Limit, X-Ratelimit-Remaining, X-Ratelimit-Retry-After
- **API Gateway Pattern**: Centralized request routing and rate limiting
- **Token Bucket Algorithm**: Efficient and precise rate limiting implementation
- **Thread-Safe**: High-performance concurrent access using proper synchronization
- **Comprehensive Monitoring**: Built-in metrics and status endpoints

### Technical Improvements
- **Clean Architecture**: Middleware pattern for separation of concerns
- **Configuration-Driven**: External YAML configuration for rate limiting rules
- **Industry Standards**: Follows Lyft's rate-limiting component format
- **Proper HTTP Headers**: Standard rate limiting headers for client-side handling
- **Flexible Descriptors**: Support for multiple matching criteria (path, user, IP, etc.)
- **Scalable Design**: Easy to add new rate limiting rules without code changes

## Architecture

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Client        │───▶│  Rate Limiting   │───▶│   ApiController │
│                 │    │    Middleware    │    │                 │
└─────────────────┘    └──────────────────┘    └─────────────────┘
                              │
                              ▼
                       ┌──────────────────┐
                       │  Configuration   │
                       │     Loader       │
                       └──────────────────┘
                              │
                              ▼
                       ┌──────────────────┐
                       │   Token Bucket  │
                       │   Algorithm     │
                       └──────────────────┘
```

### Components

1. **RateLimitingMiddleware**: Interceptor that applies rate limiting rules before reaching controllers
2. **RateLimitConfigLoader**: Loads and manages rate limiting configuration from YAML/JSON
3. **RateLimitRule**: Configuration model following Lyft's rate-limiting format
4. **TokenBucket**: Core algorithm implementation for rate limiting
5. **ApiController**: Clean business logic without rate limiting concerns
6. **WebMvcConfig**: Spring configuration for middleware registration

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
| `/api/resource` | GET | Global API | Global rate limiting (100/minute) |
| `/api/user-resource` | GET | Per-User | User-specific rate limiting (30/minute) |
| `/api/ip-resource` | GET | Per-IP | IP-based rate limiting (60/minute) |
| `/api/protected-resource` | GET | Combined | Multiple rate limits applied |
| `/api/auth/login` | POST | Auth Type | Login rate limiting (5/minute) |
| `/api/auth/password-reset` | POST | Auth Type | Password reset (3/hour) |
| `/api/messaging/send` | POST | Message Type | Marketing messages (5/day) |
| `/api/upload` | POST | Endpoint Specific | Upload endpoint (10/minute) |
| `/api/search` | GET | Endpoint Specific | Search endpoint (30/minute) |

#### Example Usage

```bash
# Test global rate limiting
curl -i http://localhost:8080/api/resource

# Test user-specific rate limiting with headers
curl -i -H "X-User-ID: testuser" http://localhost:8080/api/user-resource

# Test premium user rate limiting
curl -i -H "X-User-Tier: premium" http://localhost:8080/api/user-resource

# Test authentication rate limiting
curl -i -X POST -H "Content-Type: application/json" \
  -d '{"username":"test","password":"pass"}' \
  http://localhost:8080/api/auth/login

# Test messaging rate limiting
curl -i -X POST -H "Content-Type: application/json" \
  -d '{"to":"user@example.com","content":"Hello"}' \
  "http://localhost:8080/api/messaging/send?message_type=marketing"

# Test upload rate limiting
curl -i -X POST http://localhost:8080/api/upload

# Test search rate limiting
curl -i "http://localhost:8080/api/search?q=test"
```

## Configuration

### Rate Limiting Rules

The rate limiting rules are defined in `src/main/resources/rate-limiter-config.yml`:

```yaml
# Global API rate limiting
- domain: api
  descriptors:
    - key: path
      value: "*"
  rate_limit:
    unit: minute
    requests_per_unit: 100

# Authentication rate limiting
- domain: auth
  descriptors:
    - key: auth_type
      value: login
  rate_limit:
    unit: minute
    requests_per_unit: 5

# Marketing messages rate limiting
- domain: messaging
  descriptors:
    - key: message_type
      value: marketing
  rate_limit:
    unit: day
    requests_per_unit: 5

# Premium user rate limiting
- domain: user
  descriptors:
    - key: user_tier
      value: premium
  rate_limit:
    unit: minute
    requests_per_unit: 100
```

### Rule Matching

Rate limiting rules are matched based on:
1. **Domain**: Category of the rule (api, auth, user, ip, messaging, endpoint)
2. **Descriptors**: Key-value pairs that define what the rule applies to
3. **Priority**: Rules are checked in order of domain priority

### Supported Descriptors

| Key | Source | Example |
|-----|--------|---------|
| `path` | Request URI | `/api/resource` |
| `method` | HTTP Method | `GET`, `POST` |
| `ip_address` | Client IP | `192.168.1.100` |
| `user_id` | X-User-ID header | `user123` |
| `user_tier` | X-User-Tier header | `premium` |
| `auth_type` | Path pattern | `login`, `password_reset` |
| `message_type` | Request parameter | `marketing`, `transactional` |

### Time Units

Supported time units for rate limits:
- `second`: Requests per second
- `minute`: Requests per minute
- `hour`: Requests per hour
- `day`: Requests per day

## Rate Limiting Headers

The middleware adds standard HTTP headers to responses:

### X-Ratelimit-Limit
```
X-Ratelimit-Limit: 100
```
Indicates how many calls the client can make per time window.

### X-Ratelimit-Remaining
```
X-Ratelimit-Remaining: 75
```
The remaining number of allowed requests within the window.

### X-Ratelimit-Retry-After
```
X-Ratelimit-Retry-After: 45
```
Number of seconds to wait until you can make a request again without being throttled.
Only included when rate limited (HTTP 429).

## Rate Limit Exceeded Response

When a request is rate limited, the API returns:

```json
{
  "error": "Rate limit exceeded",
  "message": "Too many requests. Please try again later.",
  "domain": "auth",
  "retryAfterSeconds": 45,
  "timestamp": 1634567890123
}
```

With HTTP status code `429 Too Many Requests`.

## Rate Limiting Strategies

### 1. Domain-Based Rate Limiting

Rules are organized by domains for better organization:

- **api**: General API rate limiting
- **auth**: Authentication-related endpoints
- **user**: User-specific rate limiting
- **ip**: IP-based rate limiting
- **messaging**: Message sending limits
- **endpoint**: Specific endpoint limits

### 2. Descriptor Matching

Flexible matching with wildcards and specific values:

```yaml
# Match all paths
- key: path
  value: "*"

# Match specific path
- key: path
  value: "/api/upload"

# Match specific user tier
- key: user_tier
  value: "premium"
```

### 3. Multiple Descriptors

Rules can have multiple descriptors for precise matching:

```yaml
# Match POST requests to /api/upload only
- domain: endpoint
  descriptors:
    - key: method
      value: POST
    - key: path
      value: "/api/upload"
  rate_limit:
    unit: minute
    requests_per_unit: 10
```

## Testing

### Running Tests

```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests RateLimitingMiddlewareTest

# Run with coverage
./gradlew test jacocoTestReport
```

### Test Coverage

- **RateLimitingMiddleware**: Comprehensive middleware testing
- **RateLimitConfigLoader**: Configuration loading and matching
- **RateLimitRule**: Rule validation and calculations
- **TokenBucket**: Core algorithm testing

### Test Categories

1. **Unit Tests**: Individual component testing
2. **Integration Tests**: End-to-end API testing with middleware
3. **Configuration Tests**: Rule loading and matching
4. **Edge Case Tests**: Boundary conditions and error scenarios

## Performance Characteristics

### Thread Safety
- Uses `ReentrantLock` for optimal concurrent performance
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
# Configuration file location
RATE_LIMITER_CONFIG_PATH=/app/config/rate-limiter-config.yml

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
2. **Use Appropriate Domains**: Organize rules logically by domain
3. **Specific Over General**: More specific rules take precedence
4. **Monitor Metrics**: Regularly review rate limit effectiveness

### Performance Optimization

1. **Use Appropriate Granularity**: Choose the right rate limiting strategy
2. **Monitor Memory Usage**: Watch for memory growth with many users/IPs
3. **Test Under Load**: Validate performance under realistic conditions
4. **Optimize Rule Order**: Put most frequently matched rules first

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
- Follow middleware pattern for cross-cutting concerns

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

### Version 2.0.0
- **NEW**: Middleware-based architecture for clean separation of concerns
- **NEW**: YAML/JSON configuration support for flexible rate limiting rules
- **NEW**: Standard HTTP headers (X-Ratelimit-*) for client-side handling
- **NEW**: API Gateway pattern with centralized rate limiting
- **NEW**: Support for multiple descriptor matching criteria
- **IMPROVED**: Removed rate limiting logic from controllers
- **IMPROVED**: Configuration-driven rate limiting without code changes
- **IMPROVED**: Better error handling and response format

### Version 1.0.0
- Initial implementation with Token Bucket algorithm
- Global, per-user, and per-IP rate limiting
- Comprehensive monitoring and metrics
- Production-ready with extensive testing

## 🚨 Architecture Improvements

### From Controller-Based to Middleware-Based

#### Previous Architecture Issues
1. **Mixed Concerns**: Rate limiting logic mixed with business logic in controllers
2. **Code Duplication**: Similar rate limiting code repeated across endpoints
3. **Hard to Maintain**: Changes to rate limiting required controller modifications
4. **Inconsistent Headers**: Different endpoints might return different headers

#### New Architecture Benefits
1. **Clean Separation**: Rate limiting completely separated from business logic
2. **Centralized Configuration**: All rate limiting rules in one configuration file
3. **Standard Headers**: Consistent HTTP headers across all endpoints
4. **Easy Maintenance**: Add new rules without touching controller code
5. **Industry Standards**: Follows established patterns from Lyft and other systems

### Key Improvements

1. **Middleware Pattern**: Clean interceptor-based approach
2. **Configuration-Driven**: External YAML configuration for all rules
3. **Standard Headers**: Proper HTTP headers for rate limiting
4. **Flexible Matching**: Support for multiple descriptor criteria
5. **API Gateway**: Centralized request processing and rate limiting