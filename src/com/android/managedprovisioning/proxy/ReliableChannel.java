/*
 * Copyright 2015, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.managedprovisioning.proxy;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import com.android.managedprovisioning.ProvisionLogger;
import com.android.managedprovisioning.comm.Bluetooth.CommPacket;
import com.android.managedprovisioning.comm.Channel;
import com.android.managedprovisioning.comm.SocketWrapper;

/**
 * A {@link Channel} implementation that queues data and attempts to reconnect when IO errors occur.
 * This implementation wraps an ordinary {@code Channel} that will be opened when an active
 * connection is needed.
 *
 * <p>This channel starts out in a "shutdown" state. In this state, a connection will only be
 * attempted if there is data to be written and {@link #flush()} is called. The channel may be
 * placed in a "connected" state with {@link #createConnection()} and "shutdown" with {@link #close()}
 *
 * <p>When a {{@link #write(CommPacket)} is attempted, the data packet will be queued and written
 * out as soon as the connection is available.
 *
 * <p>When a connection fails, it may be restarted and requests will be made again.
 */
public class ReliableChannel extends Channel {

    /** Number of consecutive times to retry Bluetooth connection. */
    private static final int MAX_RETRIES = 8;

    /**
     * The amount of time to keep a socket open before closing it. This gives the programmer time
     * to process the payload before it starts getting IOExceptions.
     */
    private static final long CLOSING_DELAY = 5000;

    private boolean mReconnectNeeded = false;
    private final AtomicBoolean mIsShutdown;
    private final CommPacket mAnnouncePacket;
    private final CommPacket mEndPacket;

    /** Used to synchronize reconnecting the socket. */
    private final Object mReconnectLock = new Object();

    /** Message queue. Messages to send are added by caller and removed when they can be sent. */
    private final BlockingQueue<CommPacket> mBuffer;

    /** Handles all tasks which send packets. */
    private final ExecutorService mWriteExecutor = Executors.newSingleThreadExecutor();

    public ReliableChannel(SocketWrapper socket, CommPacket announcePacket,
            CommPacket endPacket) {
        super(socket);
        mAnnouncePacket = announcePacket;
        mEndPacket = endPacket;
        // Start off in "Shutdown" state until createConnection() is called.
        mIsShutdown = new AtomicBoolean(true);
        mBuffer = new LinkedBlockingQueue<>();
    }

    public void createConnection() throws IOException {
        // Set mIsShutdown to false. If connecting fails, mIsShutdown will be set to true
        // in retrySetupConnection().
        mIsShutdown.set(false);
        try {
            mSocket.recreate();
            onConnected();
        } catch (IOException e) {
            ProvisionLogger.logd(e);
            retrySetupConnection(e);
        }
    }

    private void retrySetupConnection(Throwable retryCause) throws IOException {
        mReconnectNeeded = true;
        synchronized (mReconnectLock) {
            retrySetupConnectionLocked(retryCause);
        }
        onConnected();
    }

    private void onConnected() throws IOException {
        // This is intentionally putting the announce packet at the end of the buffer.
        // This will cause all of our queued packets to be flushed before the programmer
        // denies us a persistent connection due to our device id.
        if (mAnnouncePacket != null) {
            write(mAnnouncePacket);
        }
        ProvisionLogger.logd("Sending device info...");
    }

    /**
     * Try to disconnect and reconnect the backing {@code Channel}.
     *
     * <p>Do not call this directly. Call {@link #retrySetupConnection(Throwable)} instead.
     * @param retryCause exception that caused this reconnect
     * @throws IOException if the reconnect failed
     */
    private void retrySetupConnectionLocked(Throwable retryCause) throws IOException {
        if (!mReconnectNeeded) return;
        boolean c = false;
        for (int retries=0; !c && retries < MAX_RETRIES; ++retries) {
            super.close();
            try {
                Thread.sleep(computeRetryTime(retries));
            } catch (InterruptedException e) {
            }
            try {
                mSocket.recreate();
                c = true;
            } catch (IOException e) {
                ProvisionLogger.logd(e);
                retryCause = e;
            }
        }
        if (!c) {
            throw new IOException(retryCause);
        }
        mReconnectNeeded = false;
    }

    /**
     * Returns the amount of time in milliseconds to wait before trying to reconnect. This time
     * is calculated based on the number of retry attempts that have been performed.
     * @param retries the number of times a reconnection has been retried
     * @return the number of milliseconds to wait before reconnecting.
     */
    private int computeRetryTime(int retries) {
        // Default increasing backoff, 1, 2, 4, 8, 16, 32, 64, 128.
        // Totaling a little over 4 mins of retries.
        return (int) Math.pow(2, retries - 1);
    }

    /**
     * Schedule a packet to be written. The packet will be written to a queue and will be written
     * when the backing {@code Channel} connection is open. This packet will be written immediately
     * if the {@code Channel} is open.
     */
    @Override
    public void write(CommPacket packet) throws IOException {
        mBuffer.add(packet);
        if (isConnected()) {
            flush();
        }
    }

    /**
     * Writes all queued packets. The write will happen on a background thread.
     */
    @Override
    public void flush() throws IOException {
        mWriteExecutor.execute(new FlushBufferTask());
    }

    /**
     * Write a packet to the {@link Channel} backing this instance.
     * @param packet data to write
     * @throws IOException if the write failed
     */
    private void unbufferedWrite(CommPacket packet) throws IOException {
        synchronized (mWriteLock) {
            try {
                super.write(packet);
            } catch (Exception e) {
                ProvisionLogger.logd(e);
                retrySetupConnection(e);
                write(packet);
            }
        }
    }

    @Override
    public synchronized CommPacket read() throws IOException {
        try {
            return super.read();
        } catch (IOException e) {
            ProvisionLogger.logd(e);
            retrySetupConnection(e);
            return read();
        }
    }

    /**
     * Close the backing {@code Channel} and set the shutdown state.
     */
    @Override
    public void close() {
        ProvisionLogger.logd("Closing reliable channel");
        mIsShutdown.set(true);
        if (mBuffer.isEmpty()) {
            super.close();
        }
    }

    /**
     * Overridden to check if this {@code Channel} is in a shutdown state. The {@code Channel}
     * backing this instance may be connected if this channel is shut down.
     * @return {@code true} if this {@code Channel} is in the shutdown state
     */
    @Override
    public boolean isConnected() {
        return !mIsShutdown.get();
    }

    /**
     * Task that runs on a background thread and writes all queued packets.
     */
    private final class FlushBufferTask implements Runnable {
        @Override
        public void run() {
            try {
                if (mBuffer.isEmpty()) {
                    return;
                }
                if (mIsShutdown.get()) {
                    ProvisionLogger.logd("Reopening connection");
                    createConnection();
                }
                CommPacket message;
                while ((message = mBuffer.poll()) != null) {
                    unbufferedWrite(message);
                }
                if (mIsShutdown.get()) {
                    unbufferedWrite(mEndPacket);
                    try {
                        Thread.sleep(CLOSING_DELAY);
                    } catch (InterruptedException e) {
                        ProvisionLogger.loge(e);
                    }
                }
            } catch (IOException ioe) {
                ProvisionLogger.loge("Failed to write all packets.", ioe);
            } catch (Throwable t) {
                ProvisionLogger.loge("Unexpected throwable.", t);
            } finally {
                if (mIsShutdown.get()) {
                    close();
                }
            }
        }
    }

    /**
     * Determine if the socket connection underlying this channel is connected.
     * @return {@code true} if this socket is connected.
     */
    protected boolean isSocketConnected() {
        return super.isConnected();
    }
}
