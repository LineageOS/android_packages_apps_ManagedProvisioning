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

import com.android.managedprovisioning.comm.Bluetooth.DeviceInfo;
import com.android.managedprovisioning.comm.Bluetooth.StatusUpdate;

/**
 * Receives information about connected devices.
 *
 * <p>Implementations can override {@link #onDeviceCheckin(String, DeviceInfo)} to receive a
 * call when a device connects for the first time. This {@code DeviceInfo} object will provide
 * information about the connected device.
 *
 * <p>Implementations can override {@link #onStatusUpdate(String, StatusUpdate)} to receive a
 * call when a device reports its status. The {@code StatusUpdate} object will contain the
 * current status of the device and possibly associated data.
 */
public class StatusCallback {
    /**
     * Override to receive device info when a device connects.
     * @param deviceIdentifier uniquely identifies a device
     * @param deviceInfo device info packet received from the remote device
     */
    public void onDeviceCheckin(String deviceIdentifier, DeviceInfo deviceInfo) {}

    /**
     * Override to receive device status.
     * @param deviceIdentifier uniquely identifies a device
     * @param statusUpdate status update packet received from the remote device
     */
    public void onStatusUpdate(String deviceIdentifier, StatusUpdate statusUpdate) {}
}
