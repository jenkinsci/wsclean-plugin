package de.jamba.hudson.plugin.wsclean;

import static de.jamba.hudson.plugin.wsclean.TaskUtils.runWithTimeout;
import static de.jamba.hudson.plugin.wsclean.TaskUtils.runWithoutTimeout;
import static de.jamba.hudson.plugin.wsclean.TaskUtils.waitUntilAllAreDone;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.hamcrest.Matchers.*;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

import org.junit.Test;

import com.google.common.collect.Lists;

public class TaskUtilsTest {
    @Test
    public void runWithoutTimeoutGivenTaskThatReturnsThenResultsResult() throws Exception {
        // Given
        final Integer expected = 123;
        final Callable<Integer> task = mkTaskThatReturns(expected);
        // When
        final Integer actual = runWithoutTimeout(task);
        // Then
        assertThat(actual, equalTo(expected));
    }

    @Test
    public void runWithoutTimeoutGivenTaskThatThrowsRTEThenThrowsThatRTE() throws Exception {
        // Given
        final RuntimeException expected = new RuntimeException("expected");
        final Callable<Integer> task = mkTaskThatThrows(expected);
        // When
        try {
            runWithoutTimeout(task);
            fail("Expected " + expected);
        } catch (RuntimeException actual) {
            // Then
            assertThat(actual, equalTo(expected));
        }
    }

    @Test
    public void runWithoutTimeoutGivenTaskThatsInterruptedThenThrowsIE() throws Exception {
        // Given
        final InterruptedException expected = new InterruptedException("expected");
        final Callable<Integer> task = mkTaskThatThrows(expected);
        // When
        try {
            runWithoutTimeout(task);
            fail("Expected " + expected);
        } catch (InterruptedException actual) {
            // Then
            assertThat(actual, equalTo(expected));
        }
    }

    @Test
    public void runWithoutTimeoutGivenTaskThatThrowsAnythingElseThenWrapsIt() throws Exception {
        // Given
        final Exception expected = new Exception("expected-to-be-wrapped");
        final Callable<Integer> task = mkTaskThatThrows(expected);
        // When
        try {
            runWithoutTimeout(task);
            fail("Expected " + expected);
        } catch (RuntimeException ex) {
            // Then
            final Throwable actual = ex.getCause();
            assertThat(actual, equalTo(expected));
        }
    }

    @Test
    public void runWithTimeoutGivenTaskThatReturnsThenResultsResultImmediately() throws Exception {
        // Given
        final ExecutorService threadpool = Executors.newSingleThreadExecutor();
        final long timeoutInMs = 60000L;
        final long maxExpectedExecutionDurationInMs = 100L;
        final Integer expected = 123;
        final Callable<Integer> task = mkTaskThatReturns(expected);
        // When
        final long msBeforeRun = System.currentTimeMillis();
        final Integer actual = runWithTimeout(threadpool, timeoutInMs, task);
        final long msAfterRun = System.currentTimeMillis();
        // Then
        assertThat(actual, equalTo(expected));
        final long actualDuration = msAfterRun - msBeforeRun;
        assertThat(actualDuration, lessThan(maxExpectedExecutionDurationInMs));
    }

    @Test
    public void runWithTimeoutGivenTaskThatTakesTooLongToReturnThenThrowsTimeout() throws Exception {
        // Given
        final ExecutorService threadpool = Executors.newSingleThreadExecutor();
        final long timeoutInMs = 50L;
        final long maxExpectedExecutionDurationInMs = timeoutInMs + 100L;
        final Callable<Integer> task = mkTaskThatReturnsAfterDelay(123, timeoutInMs + 1000L);
        // When
        final long msBeforeRun = System.currentTimeMillis();
        try {
            runWithTimeout(threadpool, timeoutInMs, task);
            fail("Expecting timeout");
        } catch (TimeoutException expected) {
            // expected
        }
        final long msAfterRun = System.currentTimeMillis();
        // Then
        final long actualDuration = msAfterRun - msBeforeRun;
        assertThat(actualDuration, lessThan(maxExpectedExecutionDurationInMs));
        final boolean thisThreadWasInterrupted = Thread.currentThread().isInterrupted();
        assertThat(thisThreadWasInterrupted, is(false));
    }

    @Test
    public void runWithTimeoutGivenTaskThatThrowsRTEThenThrowsThatRTE() throws Exception {
        // Given
        final ExecutorService threadpool = Executors.newSingleThreadExecutor();
        final long timeoutInMs = 60000L;
        final RuntimeException expected = new RuntimeException("expected");
        final Callable<Integer> task = mkTaskThatThrows(expected);
        // When
        try {
            runWithTimeout(threadpool, timeoutInMs, task);
            fail("Expected " + expected);
        } catch (RuntimeException actual) {
            // Then
            assertThat(actual, equalTo(expected));
        }
    }

    @Test
    public void runWithTimeoutGivenTaskThatsInterruptedThenThrowsIE() throws Exception {
        // Given
        final ExecutorService threadpool = Executors.newSingleThreadExecutor();
        final long timeoutInMs = 60000L;
        final InterruptedException expected = new InterruptedException("expected");
        final Callable<Integer> task = mkTaskThatThrows(expected);
        // When
        try {
            runWithTimeout(threadpool, timeoutInMs, task);
            fail("Expected " + expected);
        } catch (InterruptedException actual) {
            // Then
            assertThat(actual, equalTo(expected));
        }
    }

    @Test
    public void runWithTimeoutGivenTaskThatThrowsAnythingElseThenWrapsIt() throws Exception {
        // Given
        final ExecutorService threadpool = Executors.newSingleThreadExecutor();
        final long timeoutInMs = 60000L;
        final Exception expected = new Exception("expected-to-be-wrapped");
        final Callable<Integer> task = mkTaskThatThrows(expected);
        // When
        try {
            runWithTimeout(threadpool, timeoutInMs, task);
            fail("Expected " + expected);
        } catch (RuntimeException ex) {
            // Then
            final Throwable actual = ex.getCause();
            assertThat(actual, equalTo(expected));
        }
    }

    @Test
    public void waitUntilAllAreDoneGivenNothingThenReturnsImmediately() throws Exception {
        // Given
        final long maxExpectedExecutionDurationInMs = 100L;
        final Iterable<Future<?>> tasks = Collections.emptyList();
        // When
        final long msBeforeRun = System.currentTimeMillis();
        waitUntilAllAreDone(tasks);
        final long msAfterRun = System.currentTimeMillis();
        // Then
        final long actualDuration = msAfterRun - msBeforeRun;
        assertThat(actualDuration, lessThan(maxExpectedExecutionDurationInMs));
    }

    @Test
    public void waitUntilAllAreDoneGivenWorkingTasksThenReturnsImmediatelyAfterLastCompletes() throws Exception {
        // Given
        final ExecutorService threadpool = Executors.newFixedThreadPool(4);
        final long minExpectedExecutionDurationInMs = 105L;
        final long maxExpectedExecutionDurationInMs = minExpectedExecutionDurationInMs + 100L;
        final Callable<Integer> task10 = mkTaskThatThrows(new Exception("should-be-swallowed"));
        final Callable<Integer> task30 = mkTaskThatReturnsAfterDelay(123, 50L);
        final Callable<Integer> task50 = mkTaskThatReturnsAfterDelay(123, 40L);
        final Callable<Integer> task70 = mkTaskThatReturnsAfterDelay(123, minExpectedExecutionDurationInMs);
        final List<Future<?>> tasks = Lists.newArrayList();
        tasks.add(threadpool.submit(task10));
        tasks.add(threadpool.submit(task30));
        tasks.add(threadpool.submit(task50));
        final long msBeforeRun = System.currentTimeMillis();
        tasks.add(threadpool.submit(task70));
        // When
        waitUntilAllAreDone(tasks);
        final long msAfterRun = System.currentTimeMillis();
        // Then
        final long actualDuration = msAfterRun - msBeforeRun;
        assertThat(actualDuration, lessThan(maxExpectedExecutionDurationInMs));
        assertThat(actualDuration, greaterThanOrEqualTo(minExpectedExecutionDurationInMs));
    }

    private static <T> Callable<T> mkTaskThatReturns(final T returnValue) {
        return new Callable<T>() {
            @Override
            public T call() throws Exception {
                return returnValue;
            }
        };
    }

    private static <T> Callable<T> mkTaskThatReturnsAfterDelay(final T returnValue, final long delayInMs) {
        return new Callable<T>() {
            @Override
            public T call() throws Exception {
                final long tsBefore = System.currentTimeMillis();
                while (true) {
                    final long tsNow = System.currentTimeMillis();
                    final long delaySoFar = tsNow - tsBefore;
                    final long delayRemaining = delayInMs - delaySoFar;
                    if (delayRemaining <= 0L) {
                        break;
                    }
                    Thread.sleep(delayRemaining);
                }
                return returnValue;
            }
        };
    }

    private static <T> Callable<T> mkTaskThatThrows(final Exception thrown) {
        return new Callable<T>() {
            @Override
            public T call() throws Exception {
                throw thrown;
            }
        };
    }
}
