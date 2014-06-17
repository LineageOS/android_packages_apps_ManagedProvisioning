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

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Locale;

/**
 * Provisioning Parameters for DeviceOwner Provisioning
 */
public class ProvisioningParams implements Parcelable {
    public static String mTimeZone;
    public static long mLocalTime = -1;
    public static Locale mLocale;

    public static String mWifiSsid;
    public static boolean mWifiHidden = false;
    public static String mWifiSecurityType;
    public static String mWifiPassword;
    public static String mWifiProxyHost;
    public static int mWifiProxyPort = 0;
    public static String mWifiProxyBypassHosts;

    public static String mDeviceAdminPackageName; // Package name of the device admin package.
    public static String mOwner; // Human readable name of the institution that owns this device.

    public static String mDownloadLocation; // Url where the device admin .apk is downloaded from.
    public static byte[] mHash = new byte[0]; // Hash of the .apk file.

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(mTimeZone);
        out.writeLong(mLocalTime);
        out.writeSerializable(mLocale);
        out.writeString(mWifiSsid);
        out.writeInt(mWifiHidden ? 1 : 0);
        out.writeString(mWifiSecurityType);
        out.writeString(mWifiPassword);
        out.writeString(mWifiProxyHost);
        out.writeInt(mWifiProxyPort);
        out.writeString(mWifiProxyBypassHosts);
        out.writeString(mDeviceAdminPackageName);
        out.writeString(mOwner);
        out.writeString(mDownloadLocation);
        out.writeByteArray(mHash);
    }

    public static final Parcelable.Creator<ProvisioningParams> CREATOR
        = new Parcelable.Creator<ProvisioningParams>() {
        @Override
        public ProvisioningParams createFromParcel(Parcel in) {
            ProvisioningParams params = new ProvisioningParams();
            params.mTimeZone = in.readString();
            params.mLocalTime = in.readLong();
            params.mLocale = (Locale) in.readSerializable();
            params.mWifiSsid = in.readString();
            params.mWifiHidden = in.readInt()==1;
            params.mWifiSecurityType = in.readString();
            params.mWifiPassword = in.readString();
            params.mWifiProxyHost = in.readString();
            params.mWifiProxyPort = in.readInt();
            params.mWifiProxyBypassHosts = in.readString();
            params.mDeviceAdminPackageName = in.readString();
            params.mOwner = in.readString();
            params.mDownloadLocation = in.readString();
            in.readByteArray(params.mHash);
            return params;
        }

        @Override
        public ProvisioningParams[] newArray(int size) {
            return new ProvisioningParams[size];
        }
    };
}
