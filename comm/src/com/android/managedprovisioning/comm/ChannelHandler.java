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

import com.android.managedprovisioning.comm.Bluetooth.CommPacket;

import java.io.IOException;

/**
 * A Handler is a reader thread that will loop over a thread reading
 * messages and calling a handle function.  It is designed to remove
 * the repetitive looping nature from other classes.
 */
public abstract class ChannelHandler extends Thread {

    private static final int MAX_IO_EXCEPTIONS = 10;

    protected Channel mChannel;
    private boolean mIsRunning;

    public ChannelHandler(Channel socket) {
        mChannel = socket;
        mIsRunning = true;
    }

    @Override
    public void run() {
        int exceptionCount = 0;
        try {
            startConnection();
            while (mIsRunning && mChannel.isConnected()
                    && exceptionCount < MAX_IO_EXCEPTIONS) {
                try {
                    CommPacket packet = mChannel.read();
                    handleRequest(packet);
                } catch (IOException ioe) {
                    ProvisionCommLogger.logd(ioe);
                    exceptionCount++;
                }
            }
        // Catch everything for graceful close.
        } catch (Exception e) {
            ProvisionCommLogger.loge(e);
        } finally {
            stopConnection();
        }
        if (mChannel != null) {
            mChannel.close();
        }
    }

    public void stopHandler() {
        mIsRunning = false;
    }

    /**
     * Action to take when starting connection.
     * @throws IOException if there is an issue that should prevent the connection from starting
     */
    protected abstract void startConnection() throws IOException;

    /**
     * Action to take when shutting down the connection.
     */
    protected abstract void stopConnection();

    /**
     * Handle data sent over the communication channel.
     * @param packet communication packet received
     * @throws IOException if the packet could not be processed
     */
    protected abstract void handleRequest(CommPacket packet) throws IOException;

}
