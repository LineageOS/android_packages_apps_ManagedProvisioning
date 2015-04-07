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

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.ActivityManager.MemoryInfo;
import android.content.Context;
import android.os.Build;
import android.util.DisplayMetrics;

import com.android.managedprovisioning.comm.Bluetooth.StatusUpdate;
import com.android.managedprovisioning.comm.Bluetooth.DeviceInfo;
import com.android.managedprovisioning.comm.Bluetooth.NetworkData;
import com.android.managedprovisioning.comm.Bluetooth.CommPacket;

import java.util.Arrays;

/**
 * Handles creation of common {@code CommPacket} protos.
 */
public class PacketUtil {
    /** A connection id value that signals to close the connection. */
    public static final int END_CONNECTION = -1;

    /** Sent as part of each message to indicate which device sent a message. */
    private final String mDeviceIdentifier;

    public PacketUtil(String deviceIdentifier) {
        mDeviceIdentifier = deviceIdentifier;
    }

    /**
     * Create a communication packet containing a status update.
     * @param statusCode the reported provisioning state
     * @param customData extra data sent with the status update
     */
    public CommPacket createStatusUpdate(int statusCode, String customData) {
        StatusUpdate statusUpdate = new StatusUpdate();
        statusUpdate.statusCode = statusCode;
        statusUpdate.customData = nullSafe(customData);
        // Create packet
        CommPacket packet = new CommPacket();
        packet.deviceIdentifier = mDeviceIdentifier;
        packet.statusUpdate = statusUpdate;
        return packet;
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    public CommPacket createDeviceInfo(Context context) {
        DeviceInfo deviceInfo = new DeviceInfo();
        deviceInfo.apiVersion = android.os.Build.VERSION.SDK_INT;
        deviceInfo.make = nullSafe(android.os.Build.MANUFACTURER);
        deviceInfo.model = nullSafe(android.os.Build.MODEL);
        deviceInfo.serial = nullSafe(android.os.Build.SERIAL);
        deviceInfo.fingerprint = nullSafe(android.os.Build.FINGERPRINT);
        // Get memory info.
        MemoryInfo mi = new MemoryInfo();
        ActivityManager activityManager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager != null) {
            activityManager.getMemoryInfo(mi);
            deviceInfo.totalMemory = mi.totalMem;
        }
        // Get screen info.
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        deviceInfo.screenWidthPx = metrics.widthPixels;
        deviceInfo.screenHeightPx = metrics.heightPixels;
        deviceInfo.screenDensity = metrics.density;
        // Create packet
        CommPacket packet = new CommPacket();
        packet.deviceIdentifier = mDeviceIdentifier;
        packet.deviceInfo = deviceInfo;
        return packet;
    }

    public CommPacket createDataPacket(int connectionId, int status,
            byte[] data, int len) {
        NetworkData networkData = new NetworkData();
        networkData.connectionId = connectionId;
        networkData.status = status;
        if (data != null) {
            networkData.data =  Arrays.copyOf(data, len);
        }
        // Create packet
        CommPacket packet = new CommPacket();
        packet.deviceIdentifier = mDeviceIdentifier;
        packet.networkData = networkData;
        return packet;
    }

    public CommPacket createEndPacket() {
        return createDataPacket(END_CONNECTION, NetworkData.EOF, null, 0);
    }

    public CommPacket createEndPacket(int connId) {
        return createDataPacket(connId, NetworkData.EOF, null, 0);
    }

    private static String nullSafe(String s) {
        return s != null ? s : "";
    }
}
