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

import android.app.admin.DeviceInitializerStatus;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;

import com.android.managedprovisioning.ProvisionLogger;
import com.android.managedprovisioning.comm.BluetoothSocketWrapper;
import com.android.managedprovisioning.comm.CommPacketChannel;
import com.android.managedprovisioning.comm.PacketUtil;

import java.io.IOException;

/**
 * Used to setup a communication link with the device that started device provisioning via NFC.
 */
public class BluetoothTetherClient implements ClientTetherConnection {
    private final ReliableChannel mChannel;
    private final PacketUtil mPacketUtil;
    private final TetherProxy mTetherProxy;

    public BluetoothTetherClient(Context context, BluetoothAdapter bluetoothAdapter,
            String deviceIdentifier, String bluetoothMac, String bluetoothUuid) {
        // Create communication channel
        mPacketUtil = new PacketUtil(deviceIdentifier);
        BluetoothSocketWrapper socket = new BluetoothSocketWrapper(bluetoothAdapter, bluetoothMac,
                bluetoothUuid);
        mChannel = new ReliableChannel(new CommPacketChannel(socket),
                mPacketUtil.createDeviceInfo(context), mPacketUtil.createEndPacket());
        mTetherProxy = new TetherProxy(context, mChannel, mPacketUtil);
    }

    @Override
    public boolean sendStatusUpdate(int statusCode, String data) {
        try {
            mChannel.write(mPacketUtil.createStatusUpdate(statusCode, data));
            // Errors and high priority statuses should be sent immediately.
            if ((statusCode & DeviceInitializerStatus.FLAG_STATUS_ERROR) != 0 ||
                    (statusCode & DeviceInitializerStatus.FLAG_STATUS_HIGH_PRIORITY) != 0) {
                mChannel.flush();
            }
        } catch (IOException e) {
            ProvisionLogger.loge("Failed to write status.", e);
            return false;
        }
        return true;
    }

    @Override
    public void startGlobalProxy() throws IOException {
        // Start the connection and keep it open. This connection will be used by the proxy.
        mChannel.createConnection();
        mTetherProxy.startServer();
    }

    /**
     * Calls this client's proxy and clear the global proxy.
     */
    public void clearGlobalProxy() {
        mTetherProxy.clearProxy();
    }

    @Override
    public void removeGlobalProxy() {
        ProvisionLogger.logd("Stopping proxy");
        mTetherProxy.stopServer();
        // This will close the bluetooth connection.  However it will reconnect when needed.
        mChannel.close();
        // Wait for the proxy to stop before returning.
        try {
            mTetherProxy.join(1000);
        } catch (InterruptedException e) {}
    }
}
