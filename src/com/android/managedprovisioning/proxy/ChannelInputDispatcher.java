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

import android.util.ArrayMap;

import com.android.managedprovisioning.comm.Bluetooth;
import com.android.managedprovisioning.comm.Channel;
import com.android.managedprovisioning.comm.ChannelHandler;
import com.android.managedprovisioning.comm.PacketUtil;
import com.android.managedprovisioning.comm.ProvisionCommLogger;
import com.android.managedprovisioning.comm.Bluetooth.CommPacket;
import com.android.managedprovisioning.comm.Bluetooth.NetworkData;

import java.io.IOException;
import java.io.OutputStream;
import java.net.SocketException;

/**
 * Reads network request data sent over a {@link Channel}. This implementation will can only
 * process packets that include {@code NetworkData}. Network data received over Bluetooth will
 * be written to the {@link OutputStream} corresponding to the data's connection Id.
 */
public class ChannelInputDispatcher extends ChannelHandler {
    /** Map from connection Ids to corresponding output stream. */
    private ArrayMap<Integer, OutputStream> mOutputStreamTable;

    ChannelInputDispatcher(Channel channel) {
        super(channel);
        mOutputStreamTable = new ArrayMap<Integer, OutputStream>();
    }

    synchronized void addStream(int connectionId, OutputStream output) {
        mOutputStreamTable.put(connectionId, output);
    }

    synchronized void removeStream(int connectionId) {
        mOutputStreamTable.remove(connectionId);
    }

    synchronized boolean containsKey(int connectionId) {
        return mOutputStreamTable.containsKey(connectionId);
    }

    synchronized OutputStream getStream(int connectionId) {
        return mOutputStreamTable.get(connectionId);
    }

    @Override
    protected void startConnection() throws IOException {

    }

    @Override
    protected void stopConnection() {

    }

    /**
     * Read network data from Bluetooth and write that data to the corresponding connection output
     * stream.
     * @param packet {@inheritDoc}
     */
    @Override
    protected void handleRequest(CommPacket packet) throws IOException {
        NetworkData networkData = packet.networkData;
        if (networkData == null) {
            ProvisionCommLogger.loge("Received packet without network data.");
            return;
        }

        int connectionId = networkData.connectionId;

        if (connectionId == PacketUtil.END_CONNECTION) {
            ProvisionCommLogger.logw(
                    "END_CONNECTION read from Bluetooth. Shutting down dispatcher");
            stopHandler();
            // Keep the channel around for status updates.
            mChannel = null;
            return;
        }

        if (!containsKey(connectionId)) {
            ProvisionCommLogger.logw("No stream found for connection #" + connectionId + " of type "
                    + networkData.status);
            return;
        }
        OutputStream output = getStream(connectionId);
        if(networkData.status == Bluetooth.NetworkData.EOF) {
            try {
                output.close();
            } catch (IOException ex) {
                ProvisionCommLogger.logw(ex);
            }
            removeStream(connectionId);
            return;
        }
        // Write network data to output stream
        byte[] data = networkData.data;
        try {
            output.write(data);
            output.flush();
        } catch (SocketException e) {
            ProvisionCommLogger.logd(e);
            removeStream(connectionId);
        }
    }

    /**
     * Removes records of all connections.
     */
    public void clearConnections() {
        mOutputStreamTable.clear();
    }
}
