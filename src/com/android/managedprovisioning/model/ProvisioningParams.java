/*
 * Copyright 2016, The Android Open Source Project
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

package com.android.managedprovisioning.model;

import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_LOCALE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_LOCAL_TIME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_MAIN_COLOR;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_SKIP_USER_SETUP;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_TIME_ZONE;
import static com.android.internal.util.Preconditions.checkArgument;
import static com.android.internal.util.Preconditions.checkNotNull;

import android.accounts.Account;
import android.content.ComponentName;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.PersistableBundle;
import android.support.annotation.Nullable;
import android.util.Xml;

import com.android.internal.util.FastXmlSerializer;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.StoreUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

/**
 * Provisioning parameters for Device Owner and Profile Owner provisioning.
 */
public final class ProvisioningParams implements Parcelable {
    public static final long DEFAULT_LOCAL_TIME = -1;
    public static final Integer DEFAULT_MAIN_COLOR = null;
    public static final boolean DEFAULT_STARTED_BY_TRUSTED_SOURCE = false;
    public static final boolean DEFAULT_LEAVE_ALL_SYSTEM_APPS_ENABLED = false;
    public static final boolean DEFAULT_EXTRA_PROVISIONING_SKIP_ENCRYPTION = false;
    public static final boolean DEFAULT_SKIP_USER_SETUP = true;
    // Intent extra used internally for passing data between activities and service.
    public static final String EXTRA_PROVISIONING_PARAMS = "provisioningParams";

    private static final String TAG_PROVISIONING_PARAMS = "provisioning-params";
    private static final String TAG_WIFI_INFO = "wifi-info";
    private static final String TAG_PACKAGE_DOWNLOAD_INFO = "download-info";
    private static final String TAG_STARTED_BY_TRUSTED_SOURCE = "started-by-trusted-source";
    private static final String TAG_PROVISIONING_ACTION = "provisioning-action";

    public static final Parcelable.Creator<ProvisioningParams> CREATOR
            = new Parcelable.Creator<ProvisioningParams>() {
        @Override
        public ProvisioningParams createFromParcel(Parcel in) {
            return new ProvisioningParams(in);
        }

        @Override
        public ProvisioningParams[] newArray(int size) {
            return new ProvisioningParams[size];
        }
    };

    @Nullable
    public final String timeZone;

    public final long localTime;

    @Nullable
    public final Locale locale;

    /** WiFi configuration. */
    @Nullable
    public final WifiInfo wifiInfo;

    /**
     * Package name of the device admin package.
     *
     * <p>At least one one of deviceAdminPackageName and deviceAdminComponentName should be
     * non-null.
     */
    @Deprecated
    public final String deviceAdminPackageName;

    /**
     * {@link ComponentName} of the device admin package.
     *
     * <p>At least one one of deviceAdminPackageName and deviceAdminComponentName should be
     * non-null.
     */
    public final ComponentName deviceAdminComponentName;

    /** {@link Account} that should be migrated to the managed profile. */
    @Nullable
    public final Account accountToMigrate;

    /** Provisioning action comes along with the provisioning data. */
    public final String provisioningAction;

    /**
     * The main color theme used in managed profile only.
     *
     * <p>{@code null} means the default value.
     */
    @Nullable
    public final Integer mainColor;

    /** The download information of device admin package. */
    @Nullable
    public final PackageDownloadInfo deviceAdminDownloadInfo;

    /**
     * Custom key-value pairs from enterprise mobility management which are passed to device admin
     * package after provisioning.
     *
     * <p>Note that {@link ProvisioningParams} is not immutable because this field is mutable.
     */
    @Nullable
    public final PersistableBundle adminExtrasBundle;

    /**
     * True iff provisioning flow was started by a trusted app. This includes Nfc bump and QR code.
     */
    public final boolean startedByTrustedSource;

    /** True if all system apps should be enabled after provisioning. */
    public final boolean leaveAllSystemAppsEnabled;

    /** True if device encryption should be skipped. */
    public final boolean skipEncryption;

    /** True if user setup can be skipped. */
    public final boolean skipUserSetup;

    public String inferDeviceAdminPackageName() {
        if (deviceAdminComponentName != null) {
            return deviceAdminComponentName.getPackageName();
        }
        return deviceAdminPackageName;
    }

    private ProvisioningParams(Builder builder) {
        timeZone = builder.mTimeZone;
        localTime = builder.mLocalTime;
        locale = builder.mLocale;

        wifiInfo = builder.mWifiInfo;

        deviceAdminComponentName = builder.mDeviceAdminComponentName;
        deviceAdminPackageName = builder.mDeviceAdminPackageName;

        deviceAdminDownloadInfo = builder.mDeviceAdminDownloadInfo;

        adminExtrasBundle = builder.mAdminExtrasBundle;

        startedByTrustedSource = builder.mStartedByTrustedSource;
        leaveAllSystemAppsEnabled = builder.mLeaveAllSystemAppsEnabled;
        skipEncryption = builder.mSkipEncryption;
        accountToMigrate = builder.mAccountToMigrate;
        provisioningAction = checkNotNull(builder.mProvisioningAction);
        mainColor = builder.mMainColor;
        skipUserSetup = builder.mSkipUserSetup;

        validateFields();
    }

    private ProvisioningParams(Parcel in) {
        timeZone = in.readString();
        localTime = in.readLong();
        locale = (Locale) in.readSerializable();

        wifiInfo = (WifiInfo) in.readParcelable(WifiInfo.class.getClassLoader());

        deviceAdminPackageName = in.readString();
        deviceAdminComponentName = (ComponentName)
                in.readParcelable(null /* use default classloader */);

        deviceAdminDownloadInfo =
                (PackageDownloadInfo) in.readParcelable(PackageDownloadInfo.class.getClassLoader());

        adminExtrasBundle = in.readParcelable(null /* use default classloader */);

        startedByTrustedSource = in.readInt() == 1;
        leaveAllSystemAppsEnabled = in.readInt() == 1;
        skipEncryption = in.readInt() == 1;
        accountToMigrate = (Account) in.readParcelable(null /* use default classloader */);
        provisioningAction = checkNotNull(in.readString());
        if (in.readInt() != 0) {
            mainColor = in.readInt();
        } else {
            mainColor = null;
        }
        skipUserSetup = in.readInt() == 1;

        validateFields();
    }

    private void validateFields() {
        checkArgument(deviceAdminPackageName != null || deviceAdminComponentName != null);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(timeZone);
        out.writeLong(localTime);
        out.writeSerializable(locale);

        out.writeParcelable(wifiInfo, 0 /* default */ );

        out.writeString(deviceAdminPackageName);
        out.writeParcelable(deviceAdminComponentName, 0 /* default */);

        out.writeParcelable(deviceAdminDownloadInfo, 0 /* default */);

        out.writeParcelable(adminExtrasBundle, 0 /* default */);

        out.writeInt(startedByTrustedSource ? 1 : 0);
        out.writeInt(leaveAllSystemAppsEnabled ? 1 : 0);
        out.writeInt(skipEncryption ? 1 : 0);
        out.writeParcelable(accountToMigrate, 0 /* default */);
        out.writeString(provisioningAction);
        if (mainColor != null) {
            out.writeInt(1);
            out.writeInt(mainColor);
        } else {
            out.writeInt(0);
        }
        out.writeInt(skipUserSetup ? 1 : 0);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ProvisioningParams that = (ProvisioningParams) o;
        return localTime == that.localTime
                && startedByTrustedSource == that.startedByTrustedSource
                && leaveAllSystemAppsEnabled == that.leaveAllSystemAppsEnabled
                && skipEncryption == that.skipEncryption
                && skipUserSetup == that.skipUserSetup
                && Objects.equals(timeZone, that.timeZone)
                && Objects.equals(locale, that.locale)
                && Objects.equals(wifiInfo, that.wifiInfo)
                && Objects.equals(deviceAdminPackageName, that.deviceAdminPackageName)
                && Objects.equals(deviceAdminComponentName, that.deviceAdminComponentName)
                && Objects.equals(accountToMigrate, that.accountToMigrate)
                && Objects.equals(provisioningAction, that.provisioningAction)
                && Objects.equals(mainColor, that.mainColor)
                && Objects.equals(deviceAdminDownloadInfo, that.deviceAdminDownloadInfo)
                && isPersistableBundleEquals(adminExtrasBundle, that.adminExtrasBundle);
    }

     @Override
     public String toString() {
         StringBuilder sb = new StringBuilder();
         sb.append("timeZone: " + timeZone + "\n");
         sb.append("localTime: " + localTime + "\n");
         sb.append("locale: " + locale + "\n");
         sb.append("wifiInfo: " + wifiInfo + "\n");
         sb.append("deviceAdminPackageName: " + deviceAdminPackageName + "\n");
         sb.append("deviceAdminComponentName: " + deviceAdminComponentName + "\n");
         sb.append("accountToMigrate: " + accountToMigrate + "\n");
         sb.append("provisioningAction: " + provisioningAction + "\n");
         sb.append("mainColor: " + mainColor + "\n");
         sb.append("deviceAdminDownloadInfo: " + deviceAdminDownloadInfo + "\n");
         sb.append("adminExtrasBundle: " + adminExtrasBundle + "\n");
         sb.append("startedByTrustedSource: " + startedByTrustedSource + "\n");
         sb.append("leaveAllSystemAppsEnabled: " + leaveAllSystemAppsEnabled + "\n");
         sb.append("skipEncryption: " + skipEncryption + "\n");
         sb.append("skipUserSetup: " + skipUserSetup + "\n");
         return sb.toString();
     }

    /**
     * Compares two {@link PersistableBundle} objects are equals.
     */
    private static boolean isPersistableBundleEquals(
            PersistableBundle obj1, PersistableBundle obj2) {
        if (obj1 == obj2) {
            return true;
        }
        if (obj1 == null || obj2 == null || obj1.size() != obj2.size()) {
            return false;
        }
        Set<String> keys = obj1.keySet();
        for (String key : keys) {
            Object val1 = obj1.get(key);
            Object val2 = obj2.get(key);
            if (!isPersistableBundleSupportedValueEquals(val1, val2)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Compares two values which type is supported by {@link PersistableBundle}.
     *
     * <p>If the type isn't supported. The equality is done by {@link Object#equals(Object)}.
     */
    private static boolean isPersistableBundleSupportedValueEquals(Object val1, Object val2) {
        if (val1 == val2) {
            return true;
        } else if (val1 == null || val2 == null || !val1.getClass().equals(val2.getClass())) {
            return false;
        } else if (val1 instanceof PersistableBundle && val2 instanceof PersistableBundle) {
            return isPersistableBundleEquals((PersistableBundle) val1, (PersistableBundle) val2);
        } else if (val1 instanceof int[]) {
            return Arrays.equals((int[]) val1, (int[]) val2);
        } else if (val1 instanceof long[]) {
            return Arrays.equals((long[]) val1, (long[]) val2);
        } else if (val1 instanceof double[]) {
            return Arrays.equals((double[]) val1, (double[]) val2);
        } else if (val1 instanceof boolean[]) {
            return Arrays.equals((boolean[]) val1, (boolean[]) val2);
        } else if (val1 instanceof String[]) {
            return Arrays.equals((String[]) val1, (String[]) val2);
        } else {
            return Objects.equals(val1, val2);
        }
    }

    /**
     * Saves the ProvisioningParams to the specified file.
     */
    public void save(File file) {
        ProvisionLogger.logd("Saving ProvisioningParams to " + file);
        try (FileOutputStream stream = new FileOutputStream(file)) {
            XmlSerializer serializer = new FastXmlSerializer();
            serializer.setOutput(stream, StandardCharsets.UTF_8.name());
            serializer.startDocument(null, true);
            serializer.startTag(null, TAG_PROVISIONING_PARAMS);
            save(serializer);
            serializer.endTag(null, TAG_PROVISIONING_PARAMS);
            serializer.endDocument();
            save(serializer);
        } catch (IOException | XmlPullParserException e) {
            ProvisionLogger.loge("Caught exception while trying to save Provisioning Params to "
                    + " file " + file, e);
            file.delete();
        }
    }

    private void save(XmlSerializer serializer) throws XmlPullParserException, IOException {
        StoreUtils.writeTag(serializer, EXTRA_PROVISIONING_TIME_ZONE, timeZone);
        StoreUtils.writeTag(serializer, EXTRA_PROVISIONING_LOCAL_TIME,
                Long.toString(localTime));
        StoreUtils.writeTag(serializer, EXTRA_PROVISIONING_LOCALE,
                StoreUtils.localeToString(locale));
        if (wifiInfo != null) {
            serializer.startTag(null, TAG_WIFI_INFO);
            wifiInfo.save(serializer);
            serializer.endTag(null, TAG_WIFI_INFO);
        }
        StoreUtils.writeTag(serializer,
                EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME,
                deviceAdminPackageName);
        if (deviceAdminComponentName != null) {
            StoreUtils.writeTag(serializer,
                    EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                    deviceAdminComponentName.flattenToString());
        }
        StoreUtils.writeAccount(serializer,
                EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE,
                accountToMigrate);
        StoreUtils.writeTag(serializer, TAG_PROVISIONING_ACTION, provisioningAction);
        if (mainColor != null) {
            StoreUtils.writeTag(serializer, EXTRA_PROVISIONING_MAIN_COLOR,
                    Integer.toString(mainColor));
        }
        if (deviceAdminDownloadInfo != null) {
            serializer.startTag(null, TAG_PACKAGE_DOWNLOAD_INFO);
            deviceAdminDownloadInfo.save(serializer);
            serializer.endTag(null, TAG_PACKAGE_DOWNLOAD_INFO);
        }

        if (adminExtrasBundle != null) {
            serializer.startTag(null, EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE);
            adminExtrasBundle.saveToXml(serializer);
            serializer.endTag(null, EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE);
        }

        StoreUtils.writeTag(serializer, TAG_STARTED_BY_TRUSTED_SOURCE,
                Boolean.toString(startedByTrustedSource));
        StoreUtils.writeTag(serializer,
                EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED,
                Boolean.toString(leaveAllSystemAppsEnabled));
        StoreUtils.writeTag(serializer, EXTRA_PROVISIONING_SKIP_ENCRYPTION,
                Boolean.toString(skipEncryption));
        StoreUtils.writeTag(serializer, EXTRA_PROVISIONING_SKIP_USER_SETUP,
                Boolean.toString(skipUserSetup));
    }

    /**
     * Loads the ProvisioningParams From the specified file.
     */
    public static ProvisioningParams load(File file) {
        if (!file.exists()) {
            return null;
        }
        ProvisionLogger.logd("Loading ProvisioningParams from " + file);
        try (FileInputStream stream = new FileInputStream(file)) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(stream, null);
            return load(parser);
        } catch (IOException | XmlPullParserException e) {
            ProvisionLogger.loge("Caught exception while trying to load the provisioning params"
                    + " from file " + file, e);
            return null;
        }
    }

    private static ProvisioningParams load(XmlPullParser parser) throws XmlPullParserException,
            IOException {
        Builder builder = new Builder();
        int type;
        int outerDepth = parser.getDepth();
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
             if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                 continue;
             }
             String tag = parser.getName();
             switch (tag) {
                 case EXTRA_PROVISIONING_TIME_ZONE:
                     builder.setTimeZone(parser.getAttributeValue(null, StoreUtils.ATTR_VALUE));
                     break;
                 case EXTRA_PROVISIONING_LOCAL_TIME:
                     builder.setLocalTime(
                         Long.parseLong(parser.getAttributeValue(null, StoreUtils.ATTR_VALUE)));
                     break;
                 case EXTRA_PROVISIONING_LOCALE:
                     builder.setLocale(
                         StoreUtils.stringToLocale(parser.getAttributeValue(null,
                                 StoreUtils.ATTR_VALUE)));
                     break;
                 case TAG_WIFI_INFO:
                     builder.setWifiInfo(WifiInfo.load(parser));
                     break;
                 case EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME:
                     builder.setDeviceAdminPackageName(
                         parser.getAttributeValue(null, StoreUtils.ATTR_VALUE));
                     break;
                 case EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME:
                     builder.setDeviceAdminComponentName(ComponentName.unflattenFromString(
                            parser.getAttributeValue(null, StoreUtils.ATTR_VALUE)));
                     break;
                 case EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE:
                     builder.setAccountToMigrate(StoreUtils.readAccount(parser));
                     break;
                 case TAG_PROVISIONING_ACTION:
                     builder.setProvisioningAction(parser.getAttributeValue(null,
                            StoreUtils.ATTR_VALUE));
                     break;
                 case EXTRA_PROVISIONING_MAIN_COLOR:
                     builder.setMainColor(
                         Integer.parseInt(parser.getAttributeValue(null, StoreUtils.ATTR_VALUE)));
                     break;
                 case TAG_PACKAGE_DOWNLOAD_INFO:
                     builder.setDeviceAdminDownloadInfo(PackageDownloadInfo.load(parser));
                     break;
                 case EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE:
                     builder.setAdminExtrasBundle(PersistableBundle.restoreFromXml(parser));
                     break;
                 case TAG_STARTED_BY_TRUSTED_SOURCE:
                     builder.setStartedByTrustedSource(Boolean.parseBoolean(
                            parser.getAttributeValue(null, StoreUtils.ATTR_VALUE)));
                     break;
                 case EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED:
                     builder.setLeaveAllSystemAppsEnabled(Boolean.parseBoolean(
                            parser.getAttributeValue(null, StoreUtils.ATTR_VALUE)));
                     break;
                 case EXTRA_PROVISIONING_SKIP_ENCRYPTION:
                     builder.setSkipEncryption(Boolean.parseBoolean(parser.getAttributeValue(null,
                            StoreUtils.ATTR_VALUE)));
                     break;
                 case EXTRA_PROVISIONING_SKIP_USER_SETUP:
                     builder.setSkipUserSetup(Boolean.parseBoolean(parser.getAttributeValue(null,
                            StoreUtils.ATTR_VALUE)));
                     break;
             }
        }
        return builder.build();
    }

    public final static class Builder {
        private String mTimeZone;
        private long mLocalTime = DEFAULT_LOCAL_TIME;
        private Locale mLocale;
        private WifiInfo mWifiInfo;
        private String mDeviceAdminPackageName;
        private ComponentName mDeviceAdminComponentName;
        private Account mAccountToMigrate;
        private String mProvisioningAction;
        private Integer mMainColor = DEFAULT_MAIN_COLOR;
        private PackageDownloadInfo mDeviceAdminDownloadInfo;
        private PersistableBundle mAdminExtrasBundle;
        private boolean mStartedByTrustedSource = DEFAULT_STARTED_BY_TRUSTED_SOURCE;
        private boolean mLeaveAllSystemAppsEnabled = DEFAULT_LEAVE_ALL_SYSTEM_APPS_ENABLED;
        private boolean mSkipEncryption = DEFAULT_EXTRA_PROVISIONING_SKIP_ENCRYPTION;
        private boolean mSkipUserSetup = DEFAULT_SKIP_USER_SETUP;

        public Builder setTimeZone(String timeZone) {
            mTimeZone = timeZone;
            return this;
        }

        public Builder setLocalTime(long localTime) {
            mLocalTime = localTime;
            return this;
        }

        public Builder setLocale(Locale locale) {
            mLocale = locale;
            return this;
        }

        public Builder setWifiInfo(WifiInfo wifiInfo) {
            mWifiInfo = wifiInfo;
            return this;
        }

        @Deprecated
        public Builder setDeviceAdminPackageName(String deviceAdminPackageName) {
            mDeviceAdminPackageName = deviceAdminPackageName;
            return this;
        }

        public Builder setDeviceAdminComponentName(ComponentName deviceAdminComponentName) {
            mDeviceAdminComponentName = deviceAdminComponentName;
            return this;
        }

        public Builder setAccountToMigrate(Account accountToMigrate) {
            mAccountToMigrate = accountToMigrate;
            return this;
        }

        public Builder setProvisioningAction(String provisioningAction) {
            mProvisioningAction = provisioningAction;
            return this;
        }

        public Builder setMainColor(Integer mainColor) {
            mMainColor = mainColor;
            return this;
        }

        public Builder setDeviceAdminDownloadInfo(PackageDownloadInfo deviceAdminDownloadInfo) {
            mDeviceAdminDownloadInfo = deviceAdminDownloadInfo;
            return this;
        }

        public Builder setAdminExtrasBundle(PersistableBundle adminExtrasBundle) {
            mAdminExtrasBundle = adminExtrasBundle;
            return this;
        }

        public Builder setStartedByTrustedSource(boolean startedByTrustedSource) {
            mStartedByTrustedSource = startedByTrustedSource;
            return this;
        }

        public Builder setLeaveAllSystemAppsEnabled(boolean leaveAllSystemAppsEnabled) {
            mLeaveAllSystemAppsEnabled = leaveAllSystemAppsEnabled;
            return this;
        }

        public Builder setSkipEncryption(boolean skipEncryption) {
            mSkipEncryption = skipEncryption;
            return this;
        }

        public Builder setSkipUserSetup(boolean skipUserSetup) {
            mSkipUserSetup = skipUserSetup;
            return this;
        }

        public ProvisioningParams build() {
            return new ProvisioningParams(this);
        }

        public static Builder builder() {
            return new Builder();
        }
    }
}
