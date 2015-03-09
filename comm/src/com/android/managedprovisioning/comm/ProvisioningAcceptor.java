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
 * Receives status updates from remote devices.
 */
public interface ProvisioningAcceptor {
    /**
     * @return {@code true} if currently accepting connections
     */
    boolean isInProgress();

    /**
     * Start listening for connections from a device with the specified identifier. A device will
     * not be allowed to connect if this method is not called with its identifier. This value is
     * user defined and should be non-null. When a device is no longer expected to connect, or
     * should be prevented from connecting in the future, {@link #stopListening(String)} should
     * be called.
     * @param deviceIdentifier expected device identifier
     */
    void listenForDevice(String deviceIdentifier);

    /**
     * Begin accepting connections.
     * @throws IOException is setting up listener fails
     */
    void startConnection() throws IOException;

    /**
     * Stop accepting connections.
     */
    void stopConnection();

    /**
     * Prevent a device with the specified identifier from connecting.
     * @param deviceIdentifier device identifier for the device that shouldn't be allowed to
     *    connect in the future.
     */
    void stopListening(String deviceIdentifier);
}
