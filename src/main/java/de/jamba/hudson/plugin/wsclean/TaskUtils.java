package de.jamba.hudson.plugin.wsclean;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

public class TaskUtils {
    /**
     * Runs a task and returns its result, or throws {@link TimeoutException} if it
     * took too long. If we get interrupted while waiting, or we time out, we'll
     * cancel (interrupt) the task to tell it to quit ASAP.
     * 
     * @param timeoutInMs Max duration to allow it to run in milliseconds.
     * @param task        The task to be run.
     * @return The result of the task.
     * @throws InterruptedException If we were interrupted or if the task itself was
     *                              interrupted.
     * @throws TimeoutException     If the task did not complete in time.
     */
    @Restricted(NoExternalUse.class)
    static <T> T runWithTimeout(final ExecutorService threadpool, final long timeoutInMs, final Callable<T> task)
            throws InterruptedException, TimeoutException {
        final Future<T> futureResult = threadpool.submit(task);
        try {
            return futureResult.get(timeoutInMs, TimeUnit.MILLISECONDS);
        } catch (ExecutionException ex) {
            final Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof InterruptedException) {
                throw (InterruptedException) cause;
            }
            throw new RuntimeException(cause);
        } finally {
            futureResult.cancel(true);
        }
    }

    /**
     * Runs a task and results its result, letting it run indefinitely.
     * 
     * @param task The task to be run.
     * @return The result of the task.
     * @throws InterruptedException If we were interrupted while we were running the
     *                              task.
     */
    @Restricted(NoExternalUse.class)
    static <T> T runWithoutTimeout(final Callable<T> task) throws InterruptedException {
        try {
            return task.call();
        } catch (Exception ex) {
            if (ex instanceof RuntimeException) {
                throw (RuntimeException) ex;
            }
            if (ex instanceof InterruptedException) {
                throw (InterruptedException) ex;
            }
            throw new RuntimeException(ex);
        }
    }

    /**
     * Waits until multiple tasks are complete.
     * 
     * @param asyncTasks The tasks to be waited for.
     * @throws InterruptedException if we are interrupted while we were waiting.
     */
    @Restricted(NoExternalUse.class)
    static void waitUntilAllAreDone(Iterable<Future<?>> asyncTasks) throws InterruptedException {
        final long millisecondsToWaitLongerEachTime = 50L;
        final long maxMillisecondsToWaitBetweenPolls = 1000L;
        final long millisecondsToWaitFirstTime = 50L;
        long millisecondsToWaitThisTime = millisecondsToWaitFirstTime;
        for (Future<?> incompleteTask = getFirstIncompleteTask(
                asyncTasks); incompleteTask != null; incompleteTask = getFirstIncompleteTask(asyncTasks)) {
            try {
                incompleteTask.get(millisecondsToWaitThisTime, TimeUnit.MILLISECONDS);
                // it completed (without error) - reset timeout duration
                millisecondsToWaitThisTime = millisecondsToWaitFirstTime;
            } catch (ExecutionException e) {
                // it completed (with error) - reset timeout duration
                millisecondsToWaitThisTime = millisecondsToWaitFirstTime;
            } catch (TimeoutException e) {
                // it did not complete - increase the timeout
                millisecondsToWaitThisTime = Math.min(maxMillisecondsToWaitBetweenPolls,
                        millisecondsToWaitThisTime + millisecondsToWaitLongerEachTime);
            } catch (InterruptedException e) {
                throw e;
            }
            // Note: If we are interrupted, we let that escape uncaught
        }
    }

    private static <T extends Future<?>> T getFirstIncompleteTask(Iterable<T> asyncTasks) {
        for (final T t : asyncTasks) {
            if (!t.isDone()) {
                return t;
            }
        }
        return null;
    }
}
