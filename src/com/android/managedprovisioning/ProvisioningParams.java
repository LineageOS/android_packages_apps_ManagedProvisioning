/*
 * Copyright 2014, The Android Open Source Project
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

package com.android.managedprovisioning;

import java.util.Locale;

/**
 * Provisioning Parameters for DeviceOwner Provisioning
 */
public class ProvisioningParams {
    public static String mTimeZone;
    public static Long mLocalTime;
    public static Locale mLocale;

    public static String mWifiSsid;
    public static boolean mWifiHidden = false;
    public static String mWifiSecurityType;
    public static String mWifiPassword;
    public static String mWifiProxyHost;
    public static int mWifiProxyPort = 0;
    public static String mWifiProxyBypassHosts;
    public static String mWifiPacUrl;

    public static String mDeviceAdminPackageName; // Package name of the device admin package.
    public static String mOwner; // Human readable name of the institution that owns this device.

    public static String mDownloadLocation; // Url where the device admin .apk is downloaded from.
    public static byte[] mHash; // Hash of the .apk file (see {@link DownloadPackageTask).
}
