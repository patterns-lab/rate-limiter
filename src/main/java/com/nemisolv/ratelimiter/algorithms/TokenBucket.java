package com.nemisolv.ratelimiter.algorithms;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Corrected Token Bucket implementation for rate limiting.
 *
 * <p>This algorithm uses a "bucket" containing "tokens".
 * Tokens are added to the bucket at a constant rate (refillRate).
 * The bucket has a maximum capacity.
 *
 * <p>Each incoming request tries to consume tokens:
 * - If enough tokens are available, the request is allowed.
 * - If insufficient tokens, the request is rejected.
 *
 * <p>This implementation uses proper synchronization:
 * - Single ReentrantLock for all operations (since all are write operations)
 * - Plain long variables (no AtomicLong needed)
 * - Atomic operations protected by the lock
 */
public class TokenBucket {

    /**
     * Maximum capacity of the bucket.
     */
    private final long capacity;

    /**
     * Number of tokens added to the bucket per second.
     */
    private final long tokensPerSecond;

    /**
     * Current number of tokens in the bucket.
     */
    private long currentTokens;

    /**
     * Last refill timestamp in nanoseconds.
     */
    private long lastRefillNanos;

    /**
     * Single lock for all operations (since all are write operations).
     */
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * Creates a new Token Bucket with validation.
     *
     * @param capacity Maximum capacity (must be > 0)
     * @param tokensPerSecond Refill rate (must be > 0)
     * @throws IllegalArgumentException if parameters are invalid
     */
    public TokenBucket(long capacity, long tokensPerSecond) {
        validateParameters(capacity, tokensPerSecond);
        
        this.capacity = capacity;
        this.tokensPerSecond = tokensPerSecond;
        this.currentTokens = capacity;
        this.lastRefillNanos = System.nanoTime();
    }

    /**
     * Attempts to consume a single token from the bucket.
     *
     * @return true if consumption successful (request allowed),
     *         false if insufficient tokens (request rejected)
     */
    public boolean tryConsume() {
        return tryConsume(1);
    }

    /**
     * Attempts to consume multiple tokens from the bucket.
     *
     * @param tokens Number of tokens to consume (must be > 0)
     * @return true if consumption successful, false otherwise
     * @throws IllegalArgumentException if tokens <= 0
     */
    public boolean tryConsume(long tokens) {
        if (tokens <= 0) {
            throw new IllegalArgumentException("Tokens to consume must be positive");
        }

        lock.lock();
        try {
            refill();
            
            if (currentTokens >= tokens) {
                currentTokens -= tokens;
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Gets the current number of tokens in the bucket.
     *
     * @return Current token count
     */
    public long getCurrentTokens() {
        lock.lock();
        try {
            refill();
            return currentTokens;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Gets the bucket capacity.
     *
     * @return Maximum capacity
     */
    public long getCapacity() {
        return capacity;
    }

    /**
     * Gets the refill rate in tokens per second.
     *
     * @return Tokens per second
     */
    public long getTokensPerSecond() {
        return tokensPerSecond;
    }

    /**
     * Estimates time until next token is available.
     *
     * @return Estimated milliseconds until next token, or 0 if tokens available
     */
    public long getTimeToNextToken() {
        lock.lock();
        try {
            refill();
            if (currentTokens > 0) {
                return 0;
            }
            
            long now = System.nanoTime();
            long nanosSinceRefill = now - lastRefillNanos;
            
            // Time needed for one token at current rate
            long nanosPerToken = TimeUnit.SECONDS.toNanos(1) / tokensPerSecond;
            long nanosUntilNextToken = nanosPerToken - (nanosSinceRefill % nanosPerToken);
            
            return TimeUnit.NANOSECONDS.toMillis(nanosUntilNextToken);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Refills tokens based on elapsed time using precise arithmetic.
     */
    private void refill() {
        long now = System.nanoTime();
        long nanosSinceLastRefill = now - lastRefillNanos;
        
        // Calculate nanoseconds per token
        long nanosPerToken = TimeUnit.SECONDS.toNanos(1) / tokensPerSecond;
        
        if (nanosSinceLastRefill < nanosPerToken) {
            // Not enough time has passed for even one token
            return;
        }

        // Calculate tokens to add using precise arithmetic
        long tokensToAdd = nanosSinceLastRefill / nanosPerToken;

        if (tokensToAdd > 0) {
            // IMPORTANT: Update last refill time by CONSUMING the exact time used
            // This prevents losing time precision and ensures accurate refill timing
            long nanosUsed = tokensToAdd * nanosPerToken;
            lastRefillNanos += nanosUsed;
            
            // Add tokens, but don't exceed capacity
            currentTokens = Math.min(capacity, currentTokens + tokensToAdd);
        }
    }

    /**
     * Validates constructor parameters.
     */
    private void validateParameters(long capacity, long tokensPerSecond) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        if (tokensPerSecond <= 0) {
            throw new IllegalArgumentException("Tokens per second must be positive");
        }
    }

    @Override
    public String toString() {
        return String.format("TokenBucket{capacity=%d, tokensPerSecond=%d, currentTokens=%d}",
                capacity, tokensPerSecond, getCurrentTokens());
    }
}