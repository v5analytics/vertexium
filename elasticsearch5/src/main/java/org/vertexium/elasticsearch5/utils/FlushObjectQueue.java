package org.vertexium.elasticsearch5.utils;

import org.elasticsearch.action.ActionRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.vertexium.VertexiumException;
import org.vertexium.util.VertexiumLogger;
import org.vertexium.util.VertexiumLoggerFactory;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class FlushObjectQueue {
    private static final VertexiumLogger LOGGER = VertexiumLoggerFactory.getLogger(FlushObjectQueue.class);
    private static final int MAX_RETRIES = 10;
    private ReadWriteLock flushLock = new ReentrantReadWriteLock();
    private Queue<FlushObject> queue = new ConcurrentLinkedQueue<>();

    public void flush() {
        flushLock.writeLock().lock();
        try {
            int sleep = 0;
            while (!queue.isEmpty()) {
                FlushObject flushObject = queue.remove();
                try {
                    flushObject.getFuture().get(30, TimeUnit.MINUTES);
                    sleep = 0;
                } catch (Exception ex) {
                    sleep += 10;
                    String message = String.format("Could not write %s", flushObject);
                    if (flushObject.retryCount >= MAX_RETRIES) {
                        throw new VertexiumException(message, ex);
                    }
                    String logMessage = String.format("%s: %s (retrying: %d/%d)", message, ex.getMessage(), flushObject.retryCount + 1, MAX_RETRIES);
                    if (flushObject.retryCount > 0) { // don't log warn the first time
                        LOGGER.warn("%s", logMessage);
                    } else {
                        LOGGER.debug("%s", logMessage);
                    }

                    requeueFlushObject(flushObject, sleep);
                }
            }
        } finally {
            flushLock.writeLock().unlock();
        }
    }

    private void requeueFlushObject(FlushObject flushObject, int additionalTimeToSleep) {
        try {
            Thread.sleep(Math.max(0, flushObject.getNextRetryTime() - System.currentTimeMillis()));
        } catch (InterruptedException ex) {
            throw new VertexiumException("failed to sleep", ex);
        }
        long timeToWait = Math.min(
                ((flushObject.getRetryCount() + 1) * 10) + additionalTimeToSleep,
                1 * 60 * 1000
        );
        long nextRetryTime = System.currentTimeMillis() + timeToWait;
        queue.add(new FlushObject(
                flushObject.getElementId(),
                flushObject.getExtendedDataRowId(),
                flushObject.getActionRequestBuilder(),
                flushObject.getActionRequestBuilder().execute(),
                flushObject.getRetryCount() + 1,
                nextRetryTime
        ));
    }

    public void add(String elementId, String rowId, UpdateRequestBuilder updateRequestBuilder, Future future) {
        flushLock.readLock().lock();
        try {
            queue.add(new FlushObject(elementId, rowId, updateRequestBuilder, future));
        } finally {
            flushLock.readLock().unlock();
        }
    }

    public boolean containsElementId(String elementId) {
        flushLock.readLock().lock();
        try {
            return queue.stream().anyMatch(flushObject -> flushObject.getElementId().equals(elementId));
        } finally {
            flushLock.readLock().unlock();
        }
    }

    public static class FlushObject {
        private final String elementId;
        private final String extendedDataRowId;
        private final ActionRequestBuilder actionRequestBuilder;
        private final Future future;
        private final int retryCount;
        private final long nextRetryTime;

        FlushObject(
                String elementId,
                String extendedDataRowId,
                UpdateRequestBuilder updateRequestBuilder,
                Future future
        ) {
            this(elementId, extendedDataRowId, updateRequestBuilder, future, 0, 0);
        }

        FlushObject(
                String elementId,
                String extendedDataRowId,
                ActionRequestBuilder actionRequestBuilder,
                Future future,
                int retryCount,
                long nextRetryTime
        ) {
            this.elementId = elementId;
            this.extendedDataRowId = extendedDataRowId;
            this.actionRequestBuilder = actionRequestBuilder;
            this.future = future;
            this.retryCount = retryCount;
            this.nextRetryTime = nextRetryTime;
        }

        @Override
        public String toString() {
            if (extendedDataRowId == null) {
                return String.format("Element \"%s\"", elementId);
            } else {
                return String.format("Extended data row \"%s\":\"%s\"", elementId, extendedDataRowId);
            }
        }

        public String getElementId() {
            return elementId;
        }

        public String getExtendedDataRowId() {
            return extendedDataRowId;
        }

        public ActionRequestBuilder getActionRequestBuilder() {
            return actionRequestBuilder;
        }

        public Future getFuture() {
            return future;
        }

        public int getRetryCount() {
            return retryCount;
        }

        public long getNextRetryTime() {
            return nextRetryTime;
        }
    }
}