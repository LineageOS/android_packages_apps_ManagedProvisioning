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

import java.io.IOException;

/**
 * Provides an abstraction layer that wraps a BluetoothServerSocket.
 */
public interface ServerSocketWrapper {

    /**
     * Restart the underlying connection.
     * @throws IOException if the connection could not be reestablished.
     */
    void recreate() throws IOException;

    /**
     * Listen for a Bluetooth connection. This method will block until connected.
     * @return the connection
     * @throws IOException if there was an error while connecting.
     */
    SocketWrapper accept() throws IOException;

    /**
     * Stop listening for incoming connections.
     * @throws IOException if there was an error while closing the connection.
     */
    void close() throws IOException;
}
