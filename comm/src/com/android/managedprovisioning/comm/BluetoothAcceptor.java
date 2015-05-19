/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.managedprovisioning.comm;

import android.bluetooth.BluetoothAdapter;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * An Acceptor is a thread that will loop over the ServerSocketWrapper
 * and accept connections.  It will use the handler factory to spin up
 * a Handler for each of those connections.
 */
public class BluetoothAcceptor extends Thread implements ProvisioningAcceptor {
    /**
     * Number of consecutive Bluetooth IOExceptions allowed before trying to recreate the
     * Bluetooth server socket.
     */
    private static final int IO_EXCEPTION_RECREATE = 5;

    private final ServerSocketWrapper mServerSocket;

    private final ChannelFactory mChannelFactory;

    private volatile boolean mIsRunning;
    private int mConsecutiveFails;
    private boolean mDoRecreate;

    /** User defined callback. */
    private final StatusCallback mCallback;

    /** Main thread handler. Used to post callback events. */
    private final Handler mHandler;

    /**
     * Synchronized set of device ids expected to connect.
     * @see #listenForDevice(String)
     * @see #stopListening(String)
     */
    private final Set<String> mExpectedDevices;

    /**
     * Create a new instance that communicates over Bluetooth. The {@link #startConnection()}
     * method must be called before communication can begin. Defaults to using a
     * {@link CommPacketChannelFactory}.
     * @param adapter Bluetooth adapter used to establish a connection
     * @param serviceName name of the created Bluetooth service, used for discovery
     * @param uuid unique identifier of the created Bluetooth service, used for discovery
     * @param callback callback that receives information about connected devices
     */
    public BluetoothAcceptor(BluetoothAdapter adapter, String serviceName, UUID uuid,
            StatusCallback callback) {
        this(adapter, serviceName, uuid, callback, new CommPacketChannelFactory());
    }

    /**
     * Create a new instance that communicates over Bluetooth. The {@link #startConnection()}
     * method must be called before communication can begin.
     * @param adapter Bluetooth adapter used to establish a connection
     * @param serviceName name of the created Bluetooth service, used for discovery
     * @param uuid unique identifier of the created Bluetooth service, used for discovery
     * @param callback callback that receives information about connected devices
     * @param channelFactory factory to create Channels for each new connection
     */
    public BluetoothAcceptor(BluetoothAdapter adapter, String serviceName, UUID uuid,
            StatusCallback callback, ChannelFactory channelFactory) {
        mExpectedDevices = Collections.synchronizedSet(new HashSet<String>());
        // Setup callback
        mCallback = callback;
        mHandler = new Handler(Looper.getMainLooper());
        // Create socket
        mServerSocket = new BluetoothServerSocketWrapper(serviceName, uuid, adapter);
        mChannelFactory = channelFactory;
    }

    @Override
    public void run() {
        mIsRunning = true;
        try {
            while (mIsRunning) {
                try {
                    SocketWrapper socket = mServerSocket.accept();
                    handleConnection(socket);
                    mConsecutiveFails = 0;
                } catch (IOException e) {
                    if (mIsRunning) {
                        ProvisionCommLogger.logd(e);
                    }
                    ++mConsecutiveFails;
                    if (mIsRunning && (mConsecutiveFails > IO_EXCEPTION_RECREATE || mDoRecreate)) {
                        mDoRecreate = false;
                        try {
                            mServerSocket.recreate();
                        } catch (IOException e1) {
                            ProvisionCommLogger.loge("Problem recreating server socket", e1);
                        }
                    }
                }
            }
        } finally {
            close();
        }
    }

    @Override
    public boolean isInProgress() {
        return mIsRunning;
    }

    private void close() {
        if (mServerSocket != null) {
            try {
                ProvisionCommLogger.logd("Closing acceptor acceptance task");
                mIsRunning = false;
                mServerSocket.close();
            } catch (Exception e) {
                ProvisionCommLogger.logd(e);
            }
        }
    }

    @Override
    public synchronized void startConnection() throws IOException {
        mServerSocket.recreate();
        if (!mIsRunning) {
            start();
            mIsRunning = true;
        }
    }

    @Override
    public void stopConnection() {
        close();
    }

    @Override
    public void listenForDevice(String deviceIdentifier) {
        mExpectedDevices.add(deviceIdentifier);
    }

    @Override
    public void stopListening(String deviceIdentifier) {
        mExpectedDevices.remove(deviceIdentifier);
    }

    /**
     * Handle Bluetooth socket connection on a new thread.
     * @param socket the Bluetooth connection
     */
    private void handleConnection(SocketWrapper socket) {
        new ProxyConnectionHandler(mChannelFactory.newInstance(socket), mHandler, mCallback,
                Collections.unmodifiableSet(mExpectedDevices)).start();
    }
}
