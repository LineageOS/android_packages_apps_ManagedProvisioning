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

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_USER;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_MINIMUM_VERSION_CODE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_COOKIE_HEADER;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_LOCALE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_LOCAL_TIME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_MAIN_COLOR;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_SKIP_USER_SETUP;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_TIME_ZONE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_HIDDEN;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_PAC_URL;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_PASSWORD;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_PROXY_BYPASS;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_PROXY_HOST;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_PROXY_PORT;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_SECURITY_TYPE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_SSID;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_LOGO_URI;
import static android.app.admin.DevicePolicyManager.MIME_TYPE_PROVISIONING_NFC;
import static java.nio.charset.StandardCharsets.UTF_8;

import android.accounts.Account;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.graphics.Color;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.text.TextUtils;

import com.android.managedprovisioning.common.Globals;
import com.android.managedprovisioning.common.IllegalProvisioningArgumentException;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.PackageDownloadInfo;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.model.WifiInfo;

import java.io.IOException;
import java.io.StringReader;
import java.util.IllformedLocaleException;
import java.util.Locale;
import java.util.Properties;

/**
 * This class can initialize a {@link ProvisioningParams} object from an intent.
 * A {@link ProvisioningParams} object stores various parameters both for the device owner
 * provisioning and profile owner provisioning.
 * There are two kinds of intents that can be parsed it into {@link ProvisioningParams}:
 *
 * <p>
 * Intent was received via Nfc.
 * The intent contains the extra {@link NfcAdapter.EXTRA_NDEF_MESSAGES}, which indicates that
 * provisioning was started via Nfc bump. This extra contains an NDEF message, which contains an
 * NfcRecord with mime type {@link MIME_TYPE_PROVISIONING_NFC}. This record stores a serialized
 * properties object, which contains the serialized extra's described in the next option.
 * A typical use case would be a programmer application that sends an Nfc bump to start Nfc
 * provisioning from a programmer device.
 *
 * <p>
 * Intent was received directly.
 * The intent contains the extra {@link #EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME} or
 * {@link #EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME} (which is deprecated and supported for
 * legacy reasons only), and may contain {@link #EXTRA_PROVISIONING_TIME_ZONE},
 * {@link #EXTRA_PROVISIONING_LOCAL_TIME}, and {@link #EXTRA_PROVISIONING_LOCALE}. A download
 * location for the device admin may be specified in
 * {@link #EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION}, together with an optional
 * {@link #EXTRA_PROVISIONING_DEVICE_ADMIN_MINIMUM_VERSION_CODE}, an optional
 * http cookie header {@link #EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_COOKIE_HEADER}, and
 * the SHA-256 hash of the target file {@link #EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM} or
 * the SHA-256 hash of any signature of the android package in the target file
 * {@link #EXTRA_PROVISIONING_DEVICE_ADMIN_CERTIFICATE_CHECKSUM}.
 * Additional information to send through to the device manager and admin may be specified in
 * {@link #EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE}.
 * The optional boolean {@link #EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED} indicates whether
 * system apps should not be disabled. The optional boolean
 * {@link #EXTRA_PROVISIONING_SKIP_ENCRYPTION} specifies whether the device should be encrypted.
 * Furthermore a wifi network may be specified in {@link #EXTRA_PROVISIONING_WIFI_SSID}, and if
 * applicable {@link #EXTRA_PROVISIONING_WIFI_HIDDEN},
 * {@link #EXTRA_PROVISIONING_WIFI_SECURITY_TYPE}, {@link #EXTRA_PROVISIONING_WIFI_PASSWORD},
 * {@link #EXTRA_PROVISIONING_WIFI_PROXY_HOST}, {@link #EXTRA_PROVISIONING_WIFI_PROXY_PORT},
 * {@link #EXTRA_PROVISIONING_WIFI_PROXY_BYPASS},
 * The optional parcelable account {@link #EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE} specifies the
 * account that has to be migrated from primary user to managed user in case of
 * profile owner provisioning.
 * A typical use case would be the {@link BootReminder} sending the intent after device encryption
 * and reboot.
 *
 * <p>
 * Furthermore this class can construct the bundle of extras for the second kind of intent given a
 * {@link ProvisioningParams}, and it keeps track of the types of the extras in the
 * DEVICE_OWNER_x_EXTRAS and PROFILE_OWNER_x_EXTRAS, with x the appropriate type.
 */
public class MessageParser {
    private static final String EXTRA_PROVISIONING_STARTED_BY_TRUSTED_SOURCE  =
            "com.android.managedprovisioning.extra.started_by_trusted_source";
    private static final String EXTRA_PROVISIONING_DEVICE_ADMIN_SUPPORT_SHA1_PACKAGE_CHECKSUM =
            "com.android.managedprovisioning.extra.device_admin_support_sha1_package_checksum";
    public static final String EXTRA_PROVISIONING_ACTION =
            "com.android.managedprovisioning.extra.provisioning_action";

    private final Utils mUtils = new Utils();

    /**
     * Converts {@link ProvisioningParams} to {@link Intent}.
     *
     * <p/>One of the use cases is to store {@link ProvisioningParams} before device-encryption
     * takes place. After device encryption is completed, the managed provisioning is resumed by
     * sending this intent.
     */
    public Intent getIntentFromProvisioningParams(ProvisioningParams params) {
        Intent intent = new Intent(Globals.ACTION_RESUME_PROVISIONING);
        intent.putExtra(EXTRA_PROVISIONING_ACTION, params.provisioningAction);
        intent.putExtra(EXTRA_PROVISIONING_TIME_ZONE, params.timeZone);
        intent.putExtra(EXTRA_PROVISIONING_LOCALE, localeToString(params.locale));
        intent.putExtra(EXTRA_PROVISIONING_WIFI_SSID, params.wifiInfo.ssid);
        intent.putExtra(EXTRA_PROVISIONING_WIFI_SECURITY_TYPE, params.wifiInfo.securityType);
        intent.putExtra(EXTRA_PROVISIONING_WIFI_PASSWORD, params.wifiInfo.password);
        intent.putExtra(EXTRA_PROVISIONING_WIFI_PROXY_HOST, params.wifiInfo.proxyHost);
        intent.putExtra(EXTRA_PROVISIONING_WIFI_PROXY_BYPASS, params.wifiInfo.proxyBypassHosts);
        intent.putExtra(EXTRA_PROVISIONING_WIFI_PAC_URL, params.wifiInfo.pacUrl);
        intent.putExtra(EXTRA_PROVISIONING_WIFI_PROXY_PORT, params.wifiInfo.proxyPort);
        intent.putExtra(EXTRA_PROVISIONING_WIFI_HIDDEN, params.wifiInfo.hidden);
        intent.putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME,
                params.deviceAdminPackageName);
        intent.putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                params.deviceAdminComponentName);
        intent.putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_MINIMUM_VERSION_CODE,
                params.deviceAdminDownloadInfo.minVersion);
        intent.putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION,
                params.deviceAdminDownloadInfo.location);
        intent.putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_COOKIE_HEADER,
                params.deviceAdminDownloadInfo.cookieHeader);
        intent.putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM,
                mUtils.byteArrayToString(params.deviceAdminDownloadInfo.packageChecksum));
        intent.putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_SUPPORT_SHA1_PACKAGE_CHECKSUM,
                params.deviceAdminDownloadInfo.packageChecksumSupportsSha1);
        intent.putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM,
                mUtils.byteArrayToString(params.deviceAdminDownloadInfo.signatureChecksum));
        intent.putExtra(EXTRA_PROVISIONING_LOCAL_TIME, params.localTime);
        intent.putExtra(EXTRA_PROVISIONING_STARTED_BY_TRUSTED_SOURCE,
                params.startedByTrustedSource);
        intent.putExtra(EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED,
                params.leaveAllSystemAppsEnabled);
        intent.putExtra(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, params.adminExtrasBundle);
        intent.putExtra(EXTRA_PROVISIONING_SKIP_ENCRYPTION, params.skipEncryption);
        intent.putExtra(EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE, params.accountToMigrate);
        if (params.mainColor != null) {
            intent.putExtra(EXTRA_PROVISIONING_MAIN_COLOR, params.mainColor);
        }
        intent.putExtra(EXTRA_PROVISIONING_SKIP_USER_SETUP, params.skipUserSetup);
        return intent;
    }

    // Begin region for Properties provisioning data parsing.

    public ProvisioningParams parseNfcIntent(Intent nfcIntent)
            throws IllegalProvisioningArgumentException {
        ProvisionLogger.logi("Processing Nfc Payload.");
        NdefRecord firstRecord = mUtils.firstNdefRecord(nfcIntent);
        if (firstRecord != null) {
            try {
                Properties props = new Properties();
                props.load(new StringReader(new String(firstRecord.getPayload(), UTF_8)));

                // For parsing non-string parameters.
                String s = null;

                ProvisioningParams.Builder builder = ProvisioningParams.Builder.builder()
                        .setStartedByTrustedSource(true)
                        .setProvisioningAction(mUtils.mapIntentToDpmAction(nfcIntent))
                        .setDeviceAdminPackageName(props.getProperty(
                                EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME));
                if ((s = props.getProperty(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME))
                        != null) {
                    builder.setDeviceAdminComponentName(ComponentName.unflattenFromString(s));
                }

                // Parse time zone, locale and local time.
                builder.setTimeZone(props.getProperty(EXTRA_PROVISIONING_TIME_ZONE))
                        .setLocale(stringToLocale(props.getProperty(EXTRA_PROVISIONING_LOCALE)));
                if ((s = props.getProperty(EXTRA_PROVISIONING_LOCAL_TIME)) != null) {
                    builder.setLocalTime(Long.parseLong(s));
                }

                // Parse WiFi configuration.
                builder.setWifiInfo(parseWifiInfoFromProperties(props))
                        // Parse device admin package download info.
                        .setDeviceAdminDownloadInfo(parsePackageDownloadInfoFromProperties(props))
                        // Parse EMM customized key-value pairs.
                        // Note: EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE property contains a
                        // Properties object serialized into String. See Properties.store() and
                        // Properties.load() for more details. The property value is optional.
                        .setAdminExtrasBundle(deserializeExtrasBundle(props,
                                EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE));
                if ((s = props.getProperty(EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED))
                        != null) {
                    builder.setLeaveAllSystemAppsEnabled(Boolean.parseBoolean(s));
                }
                if ((s = props.getProperty(EXTRA_PROVISIONING_SKIP_ENCRYPTION)) != null) {
                    builder.setSkipEncryption(Boolean.parseBoolean(s));
                }
                if ((s = props.getProperty(EXTRA_PROVISIONING_SKIP_USER_SETUP)) != null) {
                    builder.setSkipUserSetup(Boolean.parseBoolean(s));
                }

                ProvisionLogger.logi("End processing Nfc Payload.");
                return builder.build();
            } catch (IOException e) {
                throw new IllegalProvisioningArgumentException("Couldn't load payload", e);
            } catch (NumberFormatException e) {
                throw new IllegalProvisioningArgumentException("Incorrect numberformat.", e);
            } catch (IllformedLocaleException e) {
                throw new IllegalProvisioningArgumentException("Invalid locale.", e);
            } catch (IllegalArgumentException e) {
                throw new IllegalProvisioningArgumentException("Invalid parameter found!", e);
            } catch (NullPointerException e) {
                throw new IllegalProvisioningArgumentException(
                        "Compulsory parameter not found!", e);
            }
        }
        throw new IllegalProvisioningArgumentException(
                "Intent does not contain NfcRecord with the correct MIME type.");
    }

    /**
     * Parses Wifi configuration from an {@link Properties} and returns the result in
     * {@link WifiInfo}.
     */
    private WifiInfo parseWifiInfoFromProperties(Properties props) {
        WifiInfo.Builder builder = WifiInfo.Builder.builder()
                .setSsid(props.getProperty(EXTRA_PROVISIONING_WIFI_SSID))
                .setSecurityType(props.getProperty(EXTRA_PROVISIONING_WIFI_SECURITY_TYPE))
                .setPassword(props.getProperty(EXTRA_PROVISIONING_WIFI_PASSWORD))
                .setProxyHost(props.getProperty(EXTRA_PROVISIONING_WIFI_PROXY_HOST))
                .setProxyBypassHosts(props.getProperty(EXTRA_PROVISIONING_WIFI_PROXY_BYPASS))
                .setPacUrl(props.getProperty(EXTRA_PROVISIONING_WIFI_PAC_URL));
        // For parsing non-string parameters.
        String s = null;
        if ((s = props.getProperty(EXTRA_PROVISIONING_WIFI_PROXY_PORT)) != null) {
            builder.setProxyPort(Integer.parseInt(s));
        }
        if ((s = props.getProperty(EXTRA_PROVISIONING_WIFI_HIDDEN)) != null) {
            builder.setHidden(Boolean.parseBoolean(s));
        }

        return builder.build();
    }

    /**
     * Parses device admin package download info from an {@link Properties} and returns the result
     * in {@link PackageDownloadInfo}.
     */
    private PackageDownloadInfo parsePackageDownloadInfoFromProperties(Properties props) {
        PackageDownloadInfo.Builder builder = PackageDownloadInfo.Builder.builder()
                .setLocation(props.getProperty(
                        EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION))
                .setCookieHeader(props.getProperty(
                        EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_COOKIE_HEADER));
        // For parsing non-string parameters.
        String s = null;
        if ((s = props.getProperty(EXTRA_PROVISIONING_DEVICE_ADMIN_MINIMUM_VERSION_CODE)) != null) {
            builder.setMinVersion(Integer.parseInt(s));
        }
        if ((s = props.getProperty(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM)) != null) {
            // Still support SHA-1 for device admin package hash if we are provisioned by a Nfc
            // programmer.
            // TODO: remove once SHA-1 is fully deprecated.
            builder.setPackageChecksum(mUtils.stringToByteArray(s))
                    .setPackageChecksumSupportsSha1(true);
        }
        if ((s = props.getProperty(EXTRA_PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM))
                != null) {
            builder.setSignatureChecksum(mUtils.stringToByteArray(s));
        }
        return builder.build();
    }

    /**
     * Get a {@link PersistableBundle} from a String property in a Properties object.
     * @param props the source of the extra
     * @param extraName key into the Properties object
     * @return the bundle or {@code null} if there was no property with the given name
     * @throws IOException if there was an error parsing the propery
     */
    private PersistableBundle deserializeExtrasBundle(Properties props, String extraName)
            throws IOException {
        PersistableBundle extrasBundle = null;
        String serializedExtras = props.getProperty(extraName);
        if (serializedExtras != null) {
            Properties extrasProp = new Properties();
            extrasProp.load(new StringReader(serializedExtras));
            extrasBundle = new PersistableBundle(extrasProp.size());
            for (String propName : extrasProp.stringPropertyNames()) {
                extrasBundle.putString(propName, extrasProp.getProperty(propName));
            }
        }
        return extrasBundle;
    }
    // End region for Properties provisioning data parsing.

    // Begin region for bundle extras provisioning data parsing.

    public ProvisioningParams parseMinimalistNonNfcIntent(
            Intent intent, Context context, boolean isSelfOriginated)
            throws IllegalProvisioningArgumentException {
        ProvisionLogger.logi("Processing mininalist non-nfc intent.");
        return parseMinimalistNonNfcIntentInternal(intent, context, isSelfOriginated).build();
    }

    /**
     * Parses minimal supported set of parameters from bundle extras of a provisioning intent.
     *
     * <p>Here is the list of supported parameters.
     * <ul>
     *     <li>{@link EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME}</li>
     *     <li>
     *         {@link EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME} only in
     *         {@link ACTION_PROVISION_MANAGED_PROFILE}.
     *     </li>
     *     <li>{@link EXTRA_PROVISIONING_LOGO_URI}</li>
     *     <li>{@link EXTRA_PROVISIONING_MAIN_COLOR}</li>
     *     <li>
     *         {@link EXTRA_PROVISIONING_SKIP_USER_SETUP} only in
     *         {@link ACTION_PROVISION_MANAGED_USER} and {@link ACTION_PROVISION_MANAGED_DEVICE}.
     *     </li>
     *     <li>{@link EXTRA_PROVISIONING_SKIP_ENCRYPTION}</li>
     *     <li>{@link EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED}</li>
     *     <li>{@link EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE}</li>
     *     <li>{@link EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE}</li>
     * </ul>
     */
    private ProvisioningParams.Builder parseMinimalistNonNfcIntentInternal(
            Intent intent, Context context, boolean isSelfOriginated)
            throws IllegalProvisioningArgumentException {

        try {
            String provisioningAction = isSelfOriginated
                    ? intent.getStringExtra(EXTRA_PROVISIONING_ACTION)
                    : mUtils.mapIntentToDpmAction(intent);

            // Parse device admin package name and component name.
            ComponentName deviceAdminComponentName = (ComponentName) intent.getParcelableExtra(
                    EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME);
            // Device admin package name is deprecated. It is only supported in Profile Owner
            // provisioning and NFC provisioning.
            String deviceAdminPackageName = null;
            if (ACTION_PROVISION_MANAGED_PROFILE.equals(provisioningAction)) {
                deviceAdminPackageName = intent.getStringExtra(
                        EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME);
                // For profile owner, the device admin package should be installed. Verify the
                // device admin package.
                deviceAdminComponentName = mUtils.findDeviceAdmin(
                        deviceAdminPackageName, deviceAdminComponentName, context);
                deviceAdminPackageName = deviceAdminComponentName.getPackageName();
            }

            // Parse main color
            Integer mainColor = ProvisioningParams.DEFAULT_MAIN_COLOR;
            if (intent.hasExtra(EXTRA_PROVISIONING_MAIN_COLOR)) {
                mainColor = intent.getIntExtra(EXTRA_PROVISIONING_MAIN_COLOR, 0 /* not used */);
            }

            // Parse skip user setup in ACTION_PROVISION_MANAGED_USER and
            // ACTION_PROVISION_MANAGED_DEVICE only.
            boolean skipUserSetup = ProvisioningParams.DEFAULT_SKIP_USER_SETUP;
            if (provisioningAction.equals(ACTION_PROVISION_MANAGED_USER)
                    || provisioningAction.equals(ACTION_PROVISION_MANAGED_DEVICE)) {
                skipUserSetup = intent.getBooleanExtra(EXTRA_PROVISIONING_SKIP_USER_SETUP,
                        ProvisioningParams.DEFAULT_SKIP_USER_SETUP);
            }

            parseOrganizationLogoUrlFromExtras(context, intent, isSelfOriginated);

            return ProvisioningParams.Builder.builder()
                    .setProvisioningAction(provisioningAction)
                    .setDeviceAdminComponentName(deviceAdminComponentName)
                    .setDeviceAdminPackageName(deviceAdminPackageName)
                    .setSkipEncryption(intent.getBooleanExtra(EXTRA_PROVISIONING_SKIP_ENCRYPTION,
                            ProvisioningParams.DEFAULT_EXTRA_PROVISIONING_SKIP_ENCRYPTION))
                    .setLeaveAllSystemAppsEnabled(intent.getBooleanExtra(
                            EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED,
                            ProvisioningParams.DEFAULT_LEAVE_ALL_SYSTEM_APPS_ENABLED))
                    .setAccountToMigrate((Account) intent.getParcelableExtra(
                            EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE))
                    .setAdminExtrasBundle((PersistableBundle) intent.getParcelableExtra(
                            EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE))
                    .setMainColor(mainColor)
                    .setSkipUserSetup(skipUserSetup);
        } catch (ClassCastException e) {
            throw new IllegalProvisioningArgumentException("Extra "
                    + EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE
                    + " must be of type PersistableBundle.", e);
        } catch (IllegalArgumentException e) {
            throw new IllegalProvisioningArgumentException("Invalid parameter found!", e);
        } catch (NullPointerException e) {
            throw new IllegalProvisioningArgumentException("Compulsory parameter not found!", e);
        }
    }

    /**
     * Parses an intent and return a corresponding {@link ProvisioningParams} object.
     *
     * @param intent intent to be parsed.
     * @param isSelfOriginated whether the intent is sent by this app. A self-originated intent can
     *                         contain internal extras which are not part of the public API. These
     *                         extras often control sensitive aspects of Managed Provisioning such
     *                         as whether deprecated SHA-1 is supported, or whether Managed
     *                         Provisioning was started from NFC / QR code (hence no user consent
     *                         dialog).
     */
    public ProvisioningParams parseNonNfcIntent(
            Intent intent, Context context, boolean isSelfOriginated)
            throws IllegalProvisioningArgumentException {
        try {
            ProvisionLogger.logi("Processing non-nfc intent.");
            return parseMinimalistNonNfcIntentInternal(intent, context, isSelfOriginated)
                    // Parse time zone, local time and locale.
                    .setTimeZone(intent.getStringExtra(EXTRA_PROVISIONING_TIME_ZONE))
                    .setLocalTime(intent.getLongExtra(EXTRA_PROVISIONING_LOCAL_TIME,
                            ProvisioningParams.DEFAULT_LOCAL_TIME))
                    .setLocale(stringToLocale(intent.getStringExtra(EXTRA_PROVISIONING_LOCALE)))
                    // Parse WiFi configuration.
                    .setWifiInfo(parseWifiInfoFromExtras(intent))
                    // Parse device admin package download info.
                    .setDeviceAdminDownloadInfo(parsePackageDownloadInfoFromExtras(
                            intent, isSelfOriginated))
                    // Cases where startedByTrustedSource can be true are
                    // 1. We are reloading a stored provisioning intent, either Nfc bump or
                    //    PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE, after encryption reboot,
                    //    which is a self-originated intent.
                    // 2. the intent is from a trusted source, for example QR provisioning.
                    .setStartedByTrustedSource(isSelfOriginated
                            ? intent.getBooleanExtra(EXTRA_PROVISIONING_STARTED_BY_TRUSTED_SOURCE,
                            ProvisioningParams.DEFAULT_STARTED_BY_TRUSTED_SOURCE)
                            : ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE.equals(
                                    intent.getAction()))
                    .setAccountToMigrate((Account) intent.getParcelableExtra(
                            EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE))
                    .build();
        }  catch (IllegalArgumentException e) {
            throw new IllegalProvisioningArgumentException("Invalid parameter found!", e);
        } catch (NullPointerException e) {
            throw new IllegalProvisioningArgumentException("Compulsory parameter not found!", e);
        }
    }

    /**
     * Parses Wifi configuration from an Intent and returns the result in {@link WifiInfo}.
     */
    private WifiInfo parseWifiInfoFromExtras(Intent intent) {
        return WifiInfo.Builder.builder()
                .setSsid(intent.getStringExtra(EXTRA_PROVISIONING_WIFI_SSID))
                .setSecurityType(intent.getStringExtra(EXTRA_PROVISIONING_WIFI_SECURITY_TYPE))
                .setPassword(intent.getStringExtra(EXTRA_PROVISIONING_WIFI_PASSWORD))
                .setProxyHost(intent.getStringExtra(EXTRA_PROVISIONING_WIFI_PROXY_HOST))
                .setProxyBypassHosts(intent.getStringExtra(EXTRA_PROVISIONING_WIFI_PROXY_BYPASS))
                .setPacUrl(intent.getStringExtra(EXTRA_PROVISIONING_WIFI_PAC_URL))
                .setProxyPort(intent.getIntExtra(EXTRA_PROVISIONING_WIFI_PROXY_PORT,
                        WifiInfo.DEFAULT_WIFI_PROXY_PORT))
                .setHidden(intent.getBooleanExtra(EXTRA_PROVISIONING_WIFI_HIDDEN,
                        WifiInfo.DEFAULT_WIFI_HIDDEN))
                .build();
    }

    /**
     * Parses device admin package download info configuration from an Intent and returns the result
     * in {@link PackageDownloadInfo}.
     */
    private PackageDownloadInfo parsePackageDownloadInfoFromExtras(
            Intent intent, boolean isSelfOriginated) {
        PackageDownloadInfo.Builder downloadInfoBuilder = PackageDownloadInfo.Builder.builder()
                .setMinVersion(intent.getIntExtra(
                        EXTRA_PROVISIONING_DEVICE_ADMIN_MINIMUM_VERSION_CODE,
                        PackageDownloadInfo.DEFAULT_MINIMUM_VERSION))
                .setLocation(intent.getStringExtra(
                        EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION))
                .setCookieHeader(intent.getStringExtra(
                        EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_COOKIE_HEADER));
        String packageHash =
                intent.getStringExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM);
        if (packageHash != null) {
            downloadInfoBuilder.setPackageChecksum(mUtils.stringToByteArray(packageHash));
            // If we are restarted after an encryption reboot, use stored (isSelfOriginated) value
            // for this.
            if (isSelfOriginated) {
                downloadInfoBuilder.setPackageChecksumSupportsSha1(
                        intent.getBooleanExtra(
                                EXTRA_PROVISIONING_DEVICE_ADMIN_SUPPORT_SHA1_PACKAGE_CHECKSUM,
                                false));
            }
        }
        String sigHash = intent.getStringExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM);
        if (sigHash != null) {
            downloadInfoBuilder.setSignatureChecksum(mUtils.stringToByteArray(sigHash));
        }
        return downloadInfoBuilder.build();
    }

    /**
     * Parses the organization logo url from intent.
     */
    private void parseOrganizationLogoUrlFromExtras(
            Context context, Intent intent, boolean isSelfOriginated) {
        Uri logoUri = intent.getParcelableExtra(EXTRA_PROVISIONING_LOGO_URI);
        if (logoUri != null) {
            // If we go through encryption, and if the uri is a content uri:
            // We'll lose the grant to this uri. So we need to save it to a local file.
            LogoUtils.saveOrganisationLogo(context, logoUri);
        } else if (!isSelfOriginated) {
            // If the intent is not from managed provisioning app, there is a slight possibility
            // that the logo is still kept on the file system from a previous provisioning. In
            // this case, remove it.
            LogoUtils.cleanUp(context);
        }
    }
    // End region for bundle extras provisioning data parsing.

    private static Locale stringToLocale(String string) throws IllformedLocaleException {
        if (string != null) {
            return new Locale.Builder().setLanguageTag(string.replace("_", "-")).build();
        } else {
            return null;
        }
    }

    private static String localeToString(Locale locale) {
        if (locale != null) {
            return locale.getLanguage() + "_" + locale.getCountry();
        } else {
            return null;
        }
    }
}
