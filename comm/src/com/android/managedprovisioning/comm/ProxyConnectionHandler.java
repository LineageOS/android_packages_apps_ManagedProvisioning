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


import android.os.Handler;

import com.android.managedprovisioning.comm.Bluetooth.CommPacket;
import com.android.managedprovisioning.comm.Bluetooth.DeviceInfo;
import com.android.managedprovisioning.comm.Bluetooth.NetworkData;
import com.android.managedprovisioning.comm.Bluetooth.StatusUpdate;

import java.io.IOException;
import java.util.Hashtable;
import java.util.Set;

/**
 * Handles all input from a single Channel, which may be receiving packets from multiple proxy
 * connections on the student-side tablet. Distinct proxy connections are identified by connection
 * IDs; each connection is processed by its own Connection thread, which passes packets along to the
 * appropriate server, and sends server responses back over Bluetooth to the student-side proxy
 * connection.  This is a component of the Bluetooth-mediated proxy server system.
 */
public class ProxyConnectionHandler extends ChannelHandler {
    private final Hashtable<Integer, ProxyConnection> mConnectionTable;

    /**
     * Set of device identifiers that are expected to connect. Packets without expected device
     * identifiers will be ignored and their connection attempts rejected.
     */
    private final Set<String> mExpectedConnections;

    private final StatusCallback mCallback;
    private final Handler mCallbackHandler;

    public ProxyConnectionHandler(Channel channel, Handler handler, StatusCallback callback,
            Set<String> expectedConnections) {
        super(channel);
        if (callback == null) {
            callback = new StatusCallback();
        }
        mCallback = callback;
        mCallbackHandler = handler;
        mConnectionTable = new Hashtable<Integer, ProxyConnection>();
        mExpectedConnections = expectedConnections;
    }

    private void endConnection() throws IOException {
        ProvisionCommLogger.logd("Ending bluetooth connection.");
        // Acknowledge EOC received by returning message. This writes a packet without a device Id
        try {
            mChannel.write(new PacketUtil("").createEndPacket());
        } finally {
            mChannel.close();
        }
    }

    @Override
    protected void startConnection() throws IOException {

    }

    @Override
    protected void stopConnection() {
        try {
            for (ProxyConnection connection : mConnectionTable.values()) {
                connection.shutdown();
            }
        } catch (Exception e) {
            ProvisionCommLogger.logd("Problem cleaning up connection", e);
        }
    }

    @Override
    protected void handleRequest(CommPacket packet) throws IOException {
        // Make sure device identifier is expected
        String deviceIdentifier = packet.deviceIdentifier;
        if (deviceIdentifier == null || !mExpectedConnections.contains(deviceIdentifier)) {
            ProvisionCommLogger.logd("Unexpected device: " + deviceIdentifier);
            endConnection();
            return;
        }
        // Process packet. Make sure only a single extra packet type is specified.
        if (packet.deviceInfo != null) {
            if (packet.networkData != null || packet.statusUpdate != null) {
                ProvisionCommLogger.logd("Device " + deviceIdentifier + " set multiple packets.");
                endConnection();
                return;
            }
            handleDeviceInfoPacket(deviceIdentifier, packet.deviceInfo);
        } else if (packet.networkData != null) {
            if (packet.statusUpdate != null) {
                ProvisionCommLogger.logd("Device " + deviceIdentifier + " set multiple packets.");
                endConnection();
                return;
            }
            handleNetworkDataPacket(packet.networkData);
        } else if (packet.statusUpdate != null) {
            handleStatusUpdatePacket(deviceIdentifier, packet.statusUpdate);
        }
    }

    private void handleDeviceInfoPacket(final String deviceIdentifier,
            final DeviceInfo deviceInfo) {
        mCallbackHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    mCallback.onDeviceCheckin(deviceIdentifier, deviceInfo);
                } catch (Throwable t) {
                    ProvisionCommLogger.logd("Error from callback.", t);
                }
            }
        });
    }

    private void handleNetworkDataPacket(NetworkData networkData) throws IOException {
        if (networkData.connectionId == PacketUtil.END_CONNECTION) {
            endConnection();
            return;
        }
        ProxyConnection connection = mConnectionTable.get(networkData.connectionId);
        if (connection == null) {
            ProvisionCommLogger.logv("Adding a stream for connection #" + networkData.connectionId);
            connection = new ProxyConnection(mChannel, networkData.connectionId);
            mConnectionTable.put(networkData.connectionId, connection);
            connection.start();
        }
        if (networkData.status == NetworkData.EOF) {
            ProvisionCommLogger.logv("Read EOF for conn #" + networkData.connectionId);
            connection.shutdown();
        } else {
            connection.getOutput().write(networkData.data);
            connection.getOutput().flush();
        }
    }

    private void handleStatusUpdatePacket(final String deviceIdentifier,
            final StatusUpdate statusUpdate) {
        mCallbackHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    mCallback.onStatusUpdate(deviceIdentifier, statusUpdate);
                } catch (Throwable t) {
                    ProvisionCommLogger.logd("Error from callback.", t);
                }
            }
        });
    }
}
