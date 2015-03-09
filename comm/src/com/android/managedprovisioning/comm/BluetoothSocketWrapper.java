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
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * Provides a testable wrapper around a {@code BluetoothSocket}.
 */
public class BluetoothSocketWrapper implements SocketWrapper {
    private final BluetoothAdapter mBluetoothAdapter;
    private BluetoothSocket mSocket;
    private String mMacAddress;
    private UUID mUuid;

    // Used by BluetoothServerSocket when socket exists.
    public BluetoothSocketWrapper(BluetoothAdapter adapter, BluetoothSocket socket) {
        mBluetoothAdapter = adapter;
        mSocket = socket;
    }

    // Used for clients so that a ReliableChannel can recreate the connection.
    public BluetoothSocketWrapper(BluetoothAdapter adapter, String macAddress, String uuid) {
        mBluetoothAdapter = adapter;
        mMacAddress = macAddress;
        mUuid = UUID.fromString(uuid);
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return mSocket.getInputStream();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return mSocket.getOutputStream();
    }

    @Override
    public void close() throws IOException {
        if (mSocket != null) {
            try {
                getInputStream().close();
            } catch (IOException ex) {
                ProvisionCommLogger.logw(ex);
            }
            try {
                getOutputStream().close();
            } catch (IOException ex) {
                ProvisionCommLogger.logw(ex);
            }
            try {
                mSocket.close();
            } catch (IOException ex) {
                ProvisionCommLogger.logw(ex);
            }
        }
    }

    @Override
    public boolean isConnected() {
        return mSocket != null && mSocket.isConnected();
    }

    @Override
    public void open() throws IOException {
        if (mMacAddress != null) {
            BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(mMacAddress);
            mSocket = device.createInsecureRfcommSocketToServiceRecord(mUuid);
        }
        if (mBluetoothAdapter.isDiscovering()) {
            mBluetoothAdapter.cancelDiscovery();
        }
        mSocket.connect();
    }

    @Override
    public String getIdentifier() {
        return mSocket.getRemoteDevice().getAddress();
    }

    @Override
    public void recreate() throws IOException {
        if (mMacAddress == null) {
            throw new IOException("Cannot recreate a socket with no MAC Address");
        }
        try {
            close();
        } catch (IOException e) {

        }
        open();
    }

    public void setReconnectUuid(String mBluetoothUuid) {
        mUuid = UUID.fromString(mBluetoothUuid);
    }
}
