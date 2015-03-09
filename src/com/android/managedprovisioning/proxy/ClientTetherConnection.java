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

/**
 * Interfaced used by provisioned devices to interact with remote Bluetooth connection.
 */
public interface ClientTetherConnection {
    /**
     * Start the global proxy. Network traffic will be sent over the Bluetooth connection.
     * @throws IOException if the global proxy could not be set
     */
    void startGlobalProxy() throws IOException;

    /**
     * Stop sending network data over the Bluetooth connection.
     */
    void removeGlobalProxy();

    /**
     * Send a status update to the remote device.
     * @param statusCode event or status type
     * @param data
     * @return {@code true} if the update succeeded
     */
    boolean sendStatusUpdate(int statusCode, String data);
}
