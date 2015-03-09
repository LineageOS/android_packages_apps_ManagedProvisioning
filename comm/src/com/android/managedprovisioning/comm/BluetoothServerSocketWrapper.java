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
import android.bluetooth.BluetoothServerSocket;
import android.text.TextUtils;

import java.io.IOException;
import java.util.UUID;

/**
 * Wrapper around a {@link BluetoothServerSocket}.
 */
public class BluetoothServerSocketWrapper implements ServerSocketWrapper {
    private final BluetoothAdapter mBtAdapter;
    private final UUID mUuid;
    private final String mServerName;
    private BluetoothServerSocket mServerSocket;

    /**
     * Start listening for Bluetooth connections.
     * @param serverName the name of server; used for Bluetooth Service Discovery Protocol.
     * @param uuid unique identifier for the Service Discovery Protocol record.
     * @param adapter Bluetooth adapter used for listening
     * @throws NullPointerException if either {@code uuid} or {@code adapter} are null.
     * @throws IllegalArgumentException if {@code serverName} is either {@code null} or empty.
     */
    public BluetoothServerSocketWrapper(String serverName, UUID uuid,
            BluetoothAdapter adapter) {
        if (uuid == null || adapter == null) {
            throw new NullPointerException("UUID and BluetoothAdapter cannot be null");
        }
        if (TextUtils.isEmpty(serverName)) {
            throw new IllegalArgumentException("serverName cannot be empty");
        }
        mServerName = serverName;
        mBtAdapter = adapter;
        mUuid = uuid;
    }

    @Override
    public SocketWrapper accept() throws IOException {
        return new BluetoothSocketWrapper(mBtAdapter, mServerSocket.accept());
    }

    @Override
    public void close() throws IOException {
        if (mServerSocket != null) {
            mServerSocket.close();
        }
    }

    @Override
    public void recreate() throws IOException {
        try {
            close();
        } catch (Exception e) {
            ProvisionCommLogger.loge(e);
        }
        mServerSocket = mBtAdapter.listenUsingInsecureRfcommWithServiceRecord(mServerName, mUuid);
    }
}
