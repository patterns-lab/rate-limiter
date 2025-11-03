package com.nemisolv.ratelimiter.algorithms;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TokenBucketTest {

    private TokenBucket tokenBucket;

    @Nested
    @DisplayName("Basic Functionality Tests")
    class BasicFunctionalityTests {

        @BeforeEach
        void setUp() {
            tokenBucket = new TokenBucket(10, 5); // 10 capacity, 5 tokens/sec
        }

        @Test
        @DisplayName("Should consume token when available")
        void shouldConsumeTokenWhenAvailable() {
            assertTrue(tokenBucket.tryConsume());
            assertEquals(9, tokenBucket.getCurrentTokens());
        }

        @Test
        @DisplayName("Should reject when no tokens available")
        void shouldRejectWhenNoTokensAvailable() {
            // Consume all tokens
            for (int i = 0; i < 10; i++) {
                tokenBucket.tryConsume();
            }

            assertFalse(tokenBucket.tryConsume());
            assertEquals(0, tokenBucket.getCurrentTokens());
        }

        @Test
        @DisplayName("Should consume multiple tokens when available")
        void shouldConsumeMultipleTokensWhenAvailable() {
            assertTrue(tokenBucket.tryConsume(5));
            assertEquals(5, tokenBucket.getCurrentTokens());
        }

        @Test
        @DisplayName("Should reject multiple tokens when insufficient")
        void shouldRejectMultipleTokensWhenInsufficient() {
            assertFalse(tokenBucket.tryConsume(15));
            assertEquals(10, tokenBucket.getCurrentTokens()); // Should remain unchanged
        }

        @Test
        @DisplayName("Should throw exception for invalid token amount")
        void shouldThrowExceptionForInvalidTokenAmount() {
            assertThrows(IllegalArgumentException.class, () -> tokenBucket.tryConsume(0));
            assertThrows(IllegalArgumentException.class, () -> tokenBucket.tryConsume(-1));
        }
    }

    @Nested
    @DisplayName("Refill Mechanism Tests")
    class RefillMechanismTests {

        @Test
        @DisplayName("Should refill tokens over time")
        void shouldRefillTokensOverTime() throws InterruptedException {
            TokenBucket bucket = new TokenBucket(10, 10); // 10 tokens/sec for faster testing
            
            // Consume all tokens
            for (int i = 0; i < 10; i++) {
                bucket.tryConsume();
            }
            assertEquals(0, bucket.getCurrentTokens());

            // Wait for refill (1 second should add 10 tokens)
            Thread.sleep(1100);
            
            assertTrue(bucket.tryConsume());
            assertTrue(bucket.getCurrentTokens() > 0);
        }

        @Test
        @DisplayName("Should not exceed capacity during refill")
        void shouldNotExceedCapacityDuringRefill() throws InterruptedException {
            TokenBucket bucket = new TokenBucket(5, 10); // High refill rate
            
            // Consume some tokens
            bucket.tryConsume(3);
            assertEquals(2, bucket.getCurrentTokens());

            // Wait for refill
            Thread.sleep(1100);
            
            // Should not exceed capacity
            assertEquals(5, bucket.getCurrentTokens());
        }

        @Test
        @DisplayName("Should handle precise refill timing")
        void shouldHandlePreciseRefillTiming() throws InterruptedException {
            TokenBucket bucket = new TokenBucket(100, 10); // 10 tokens/sec
            
            // Consume all tokens
            bucket.tryConsume(100);
            assertEquals(0, bucket.getCurrentTokens());

            // Wait 500ms - should get 5 tokens
            Thread.sleep(500);
            assertEquals(5, bucket.getCurrentTokens());

            // Wait another 500ms - should get 5 more tokens
            Thread.sleep(500);
            assertEquals(10, bucket.getCurrentTokens());
        }
    }

    @Nested
    @DisplayName("Thread Safety Tests")
    class ThreadSafetyTests {

        @Test
        @DisplayName("Should be thread-safe under concurrent access")
        void shouldBeThreadSafeUnderConcurrentAccess() throws InterruptedException {
            int threadCount = 50;
            int requestsPerThread = 20;
            TokenBucket bucket = new TokenBucket(100, 50); // High capacity and refill rate
            
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successfulConsumptions = new AtomicInteger(0);
            AtomicInteger failedConsumptions = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < requestsPerThread; j++) {
                            if (bucket.tryConsume()) {
                                successfulConsumptions.incrementAndGet();
                            } else {
                                failedConsumptions.incrementAndGet();
                            }
                            Thread.sleep(1); // Small delay to increase contention
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS));
            executor.shutdown();

            int totalConsumptions = successfulConsumptions.get() + failedConsumptions.get();
            assertEquals(threadCount * requestsPerThread, totalConsumptions);
            
            // Current tokens should be within valid bounds
            long currentTokens = bucket.getCurrentTokens();
            assertTrue(currentTokens >= 0 && currentTokens <= bucket.getCapacity());
        }

        @Test
        @DisplayName("Should handle concurrent refill and consumption")
        void shouldHandleConcurrentRefillAndConsumption() throws InterruptedException {
            TokenBucket bucket = new TokenBucket(50, 25); // Moderate capacity and refill rate
            
            ExecutorService executor = Executors.newFixedThreadPool(20);
            CountDownLatch latch = new CountDownLatch(20);
            AtomicInteger operations = new AtomicInteger(0);

            // 10 threads consuming, 10 threads checking status
            for (int i = 0; i < 20; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < 100; j++) {
                            if (threadId < 10) {
                                // Consumer threads
                                bucket.tryConsume();
                            } else {
                                // Status checking threads
                                bucket.getCurrentTokens();
                                bucket.getTimeToNextToken();
                            }
                            operations.incrementAndGet();
                            Thread.sleep(1);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(15, TimeUnit.SECONDS));
            executor.shutdown();

            assertEquals(2000, operations.get());
            
            // Bucket should still be in a valid state
            long currentTokens = bucket.getCurrentTokens();
            assertTrue(currentTokens >= 0 && currentTokens <= bucket.getCapacity());
        }
    }

    @Nested
    @DisplayName("Configuration and Validation Tests")
    class ConfigurationAndValidationTests {

        @Test
        @DisplayName("Should throw exception for invalid capacity")
        void shouldThrowExceptionForInvalidCapacity() {
            assertThrows(IllegalArgumentException.class, () -> new TokenBucket(0, 5));
            assertThrows(IllegalArgumentException.class, () -> new TokenBucket(-1, 5));
        }

        @Test
        @DisplayName("Should throw exception for invalid refill rate")
        void shouldThrowExceptionForInvalidRefillRate() {
            assertThrows(IllegalArgumentException.class, () -> new TokenBucket(10, 0));
            assertThrows(IllegalArgumentException.class, () -> new TokenBucket(10, -1));
        }

        @Test
        @DisplayName("Should create valid bucket with correct parameters")
        void shouldCreateValidBucketWithCorrectParameters() {
            TokenBucket bucket = new TokenBucket(100, 25);
            
            assertEquals(100, bucket.getCapacity());
            assertEquals(25, bucket.getTokensPerSecond());
            assertEquals(100, bucket.getCurrentTokens()); // Should start full
        }
    }

    @Nested
    @DisplayName("Monitoring and Utility Tests")
    class MonitoringAndUtilityTests {

        @Test
        @DisplayName("Should provide accurate time to next token")
        void shouldProvideAccurateTimeToNextToken() throws InterruptedException {
            TokenBucket bucket = new TokenBucket(10, 10); // 10 tokens/sec = 100ms per token
            
            // Consume all tokens
            bucket.tryConsume(10);
            
            // Should indicate time to next token
            long timeToNext = bucket.getTimeToNextToken();
            assertTrue(timeToNext > 0 && timeToNext <= 150); // Should be around 100ms
            
            // Wait and check again
            Thread.sleep(110);
            timeToNext = bucket.getTimeToNextToken();
            assertTrue(timeToNext <= 50); // Should be less now
        }

        @Test
        @DisplayName("Should return zero time to next token when tokens available")
        void shouldReturnZeroTimeToNextTokenWhenTokensAvailable() {
            TokenBucket bucket = new TokenBucket(10, 5);
            
            assertEquals(0, bucket.getTimeToNextToken());
            
            bucket.tryConsume(5);
            assertEquals(0, bucket.getTimeToNextToken()); // Still have tokens
        }

        @Test
        @DisplayName("Should provide meaningful toString representation")
        void shouldProvideMeaningfulToStringRepresentation() {
            TokenBucket bucket = new TokenBucket(50, 10);
            String toString = bucket.toString();
            
            assertTrue(toString.contains("TokenBucket"));
            assertTrue(toString.contains("capacity=50"));
            assertTrue(toString.contains("tokensPerSecond=10"));
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should handle very high refill rates")
        void shouldHandleVeryHighRefillRates() throws InterruptedException {
            TokenBucket bucket = new TokenBucket(1000, 1000); // Very high rate
            
            bucket.tryConsume(1000);
            assertEquals(0, bucket.getCurrentTokens());
            
            Thread.sleep(100); // Should refill ~100 tokens
            assertTrue(bucket.getCurrentTokens() >= 90 && bucket.getCurrentTokens() <= 110);
        }

        @Test
        @DisplayName("Should handle very low refill rates")
        void shouldHandleVeryLowRefillRates() {
            TokenBucket bucket = new TokenBucket(10, 1); // Very low rate
            
            bucket.tryConsume(10);
            assertEquals(0, bucket.getCurrentTokens());
            
            // Should not refill immediately
            assertEquals(0, bucket.getCurrentTokens());
        }

        @Test
        @DisplayName("Should handle single token bucket")
        void shouldHandleSingleTokenBucket() throws InterruptedException {
            TokenBucket bucket = new TokenBucket(1, 1);
            
            assertTrue(bucket.tryConsume());
            assertFalse(bucket.tryConsume());
            
            Thread.sleep(1100);
            assertTrue(bucket.tryConsume());
        }
    }
}