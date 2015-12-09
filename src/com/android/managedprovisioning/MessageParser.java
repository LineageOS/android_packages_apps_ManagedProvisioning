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

import com.android.managedprovisioning.Utils.IllegalProvisioningArgumentException;

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
    private static final String EXTRA_PROVISIONING_STARTED_BY_NFC  =
            "com.android.managedprovisioning.extra.started_by_nfc";
    private static final String EXTRA_PROVISIONING_DEVICE_ADMIN_SUPPORT_SHA1_PACKAGE_CHECKSUM =
            "com.android.managedprovisioning.extra.device_admin_support_sha1_package_checksum";
    /**
     * An intent can be converted to ProvisioningParams with parseNfcIntent, and then we can get
     * an intent back with this method.
     */
    public Intent getIntentFromProvisioningParams(ProvisioningParams params) {
        Intent intent = new Intent();
        intent.setAction(params.provisioningAction);
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
                Utils.byteArrayToString(params.deviceAdminDownloadInfo.packageChecksum));
        intent.putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_SUPPORT_SHA1_PACKAGE_CHECKSUM,
                params.deviceAdminDownloadInfo.packageChecksumSupportsSha1);
        intent.putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM,
                Utils.byteArrayToString(params.deviceAdminDownloadInfo.signatureChecksum));
        intent.putExtra(EXTRA_PROVISIONING_LOCAL_TIME, params.localTime);
        intent.putExtra(EXTRA_PROVISIONING_STARTED_BY_NFC, params.startedByNfc);
        intent.putExtra(EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED,
                params.leaveAllSystemAppsEnabled);
        intent.putExtra(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, params.adminExtrasBundle);
        intent.putExtra(EXTRA_PROVISIONING_SKIP_ENCRYPTION, params.skipEncryption);
        intent.putExtra(EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE, params.accountToMigrate);
        intent.putExtra(EXTRA_PROVISIONING_MAIN_COLOR, params.mainColor);
        return intent;
    }

    public ProvisioningParams parseNfcIntent(Intent nfcIntent)
            throws IllegalProvisioningArgumentException {
        ProvisionLogger.logi("Processing Nfc Payload.");
        NdefRecord firstRecord = Utils.firstNdefRecord(nfcIntent);
        if (firstRecord != null) {
            ProvisioningParams params = parseProperties(
                    new String(firstRecord.getPayload(), UTF_8));
            params.startedByNfc = true;
            params.provisioningAction = Utils.mapIntentToDpmAction(nfcIntent);
            ProvisionLogger.logi("End processing Nfc Payload.");
            return params;
        }
        throw new IllegalProvisioningArgumentException(
                "Intent does not contain NfcRecord with the correct MIME type.");
    }

    // Note: EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE property contains a Properties object
    // serialized into String. See Properties.store() and Properties.load() for more details.
    // The property value is optional.
    private ProvisioningParams parseProperties(String data)
            throws IllegalProvisioningArgumentException {
        ProvisioningParams params = new ProvisioningParams();
        try {
            Properties props = new Properties();
            props.load(new StringReader(data));

            String s; // Used for parsing non-Strings.
            params.timeZone
                    = props.getProperty(EXTRA_PROVISIONING_TIME_ZONE);
            if ((s = props.getProperty(EXTRA_PROVISIONING_LOCALE)) != null) {
                params.locale = stringToLocale(s);
            }
            params.wifiInfo.ssid = props.getProperty(EXTRA_PROVISIONING_WIFI_SSID);
            params.wifiInfo.securityType = props.getProperty(EXTRA_PROVISIONING_WIFI_SECURITY_TYPE);
            params.wifiInfo.password = props.getProperty(EXTRA_PROVISIONING_WIFI_PASSWORD);
            params.wifiInfo.proxyHost = props.getProperty(EXTRA_PROVISIONING_WIFI_PROXY_HOST);
            params.wifiInfo.proxyBypassHosts =
                    props.getProperty(EXTRA_PROVISIONING_WIFI_PROXY_BYPASS);
            params.wifiInfo.pacUrl = props.getProperty(EXTRA_PROVISIONING_WIFI_PAC_URL);
            if ((s = props.getProperty(EXTRA_PROVISIONING_WIFI_PROXY_PORT)) != null) {
                params.wifiInfo.proxyPort = Integer.parseInt(s);
            }
            if ((s = props.getProperty(EXTRA_PROVISIONING_WIFI_HIDDEN)) != null) {
                params.wifiInfo.hidden = Boolean.parseBoolean(s);
            }

            params.deviceAdminPackageName
                    = props.getProperty(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME);
            String componentNameString = props.getProperty(
                    EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME);
            if (componentNameString != null) {
                params.deviceAdminComponentName = ComponentName.unflattenFromString(
                        componentNameString);
            }
            if ((s = props.getProperty(
                    EXTRA_PROVISIONING_DEVICE_ADMIN_MINIMUM_VERSION_CODE)) != null) {
                params.deviceAdminDownloadInfo.minVersion = Integer.parseInt(s);
            } else {
                params.deviceAdminDownloadInfo.minVersion =
                        ProvisioningParams.DEFAULT_MINIMUM_VERSION;
            }
            params.deviceAdminDownloadInfo.location
                    = props.getProperty(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION);
            params.deviceAdminDownloadInfo.cookieHeader = props.getProperty(
                    EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_COOKIE_HEADER);
            if ((s = props.getProperty(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM)) != null) {
                params.deviceAdminDownloadInfo.packageChecksum = Utils.stringToByteArray(s);
                // Still support SHA-1 for device admin package hash if we are provisioned by a Nfc
                // programmer.
                // TODO: remove once SHA-1 is fully deprecated.
                params.deviceAdminDownloadInfo.packageChecksumSupportsSha1 = true;
            }
            if ((s = props.getProperty(EXTRA_PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM))
                    != null) {
                params.deviceAdminDownloadInfo.signatureChecksum = Utils.stringToByteArray(s);
            }

            if ((s = props.getProperty(EXTRA_PROVISIONING_LOCAL_TIME)) != null) {
                params.localTime = Long.parseLong(s);
            }

            if ((s = props.getProperty(EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED)) != null) {
                params.leaveAllSystemAppsEnabled = Boolean.parseBoolean(s);
            }
            if ((s = props.getProperty(EXTRA_PROVISIONING_SKIP_ENCRYPTION)) != null) {
                params.skipEncryption = Boolean.parseBoolean(s);
            }

            params.adminExtrasBundle = deserializeExtrasBundle(props,
                    EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE);

            checkValidityOfProvisioningParams(params);
            return params;
        } catch (IOException e) {
            throw new Utils.IllegalProvisioningArgumentException("Couldn't load payload", e);
        } catch (NumberFormatException e) {
            throw new Utils.IllegalProvisioningArgumentException("Incorrect numberformat.", e);
        } catch (IllformedLocaleException e) {
            throw new Utils.IllegalProvisioningArgumentException("Invalid locale.", e);
        }
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

    public ProvisioningParams parseMinimalistNonNfcIntent(Intent intent, Context context,
            boolean trusted) throws IllegalProvisioningArgumentException {
        ProvisionLogger.logi("Processing mininalist non-nfc intent.");
        ProvisioningParams params = parseMinimalistNonNfcIntentInternal(intent, context, trusted);
        if (params.deviceAdminComponentName == null) {
            throw new IllegalProvisioningArgumentException("Must provide the component name of the"
                    + " device admin");
        }
        return params;
    }

    private ProvisioningParams parseMinimalistNonNfcIntentInternal(Intent intent, Context context,
            boolean trusted) throws IllegalProvisioningArgumentException {
        ProvisioningParams params = new ProvisioningParams();
        params.deviceAdminComponentName = (ComponentName) intent.getParcelableExtra(
                EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME);
        params.skipEncryption = intent.getBooleanExtra(
                EXTRA_PROVISIONING_SKIP_ENCRYPTION,
                ProvisioningParams.DEFAULT_EXTRA_PROVISIONING_SKIP_ENCRYPTION);
        params.leaveAllSystemAppsEnabled = intent.getBooleanExtra(
                EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED,
                ProvisioningParams.DEFAULT_LEAVE_ALL_SYSTEM_APPS_ENABLED);
        params.accountToMigrate = (Account) intent.getParcelableExtra(
                EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE);
        if (intent.hasExtra(EXTRA_PROVISIONING_MAIN_COLOR)) {
            params.mainColor = intent.getIntExtra(EXTRA_PROVISIONING_MAIN_COLOR, 0 /* not used */);
        } else {
            final TypedArray typedArray = context.obtainStyledAttributes(new int[]{
                    android.R.attr.statusBarColor});
            params.mainColor = typedArray.getColor(0, 0);
        }
        params.provisioningAction = Utils.mapIntentToDpmAction(intent);
        try {
            params.adminExtrasBundle = (PersistableBundle) intent.getParcelableExtra(
                    EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE);
        } catch (ClassCastException e) {
            throw new IllegalProvisioningArgumentException("Extra "
                    + EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE
                    + " must be of type PersistableBundle.", e);
        }
        Uri logoUri = intent.getParcelableExtra(EXTRA_PROVISIONING_LOGO_URI);
        if (logoUri != null) {
            // If we go through encryption, and if the uri is a content uri:
            // We'll lose the grant to this uri. So we need to save it to a local file.
            LogoUtils.saveOrganisationLogo(context, logoUri);
        } else if (!trusted) {
            // If the intent is not trusted: there is a slight possibility that the logo is still
            // kept on the file system from a previous provisioning. In this case, remove it.
            LogoUtils.cleanUp(context);
        }
        return params;
    }

    /**
     * Parse an intent and return a corresponding {@link ProvisioningParams} object.
     *
     * @param intent intent to be parsed.
     * @param trusted whether the intent is trusted or not. A trusted intent can contain internal
     * extras which are not part of the public API. These extras often control sensitive aspects of
     * ManagedProvisioning such as whether deprecated SHA-1 is supported, or whether MP was started
     * from NFC (hence no user consent dialog). Intents used by other apps to start MP should always
     * be untrusted.
     */
    public ProvisioningParams parseNonNfcIntent(Intent intent, Context context, boolean trusted)
            throws IllegalProvisioningArgumentException {
        ProvisionLogger.logi("Processing non-nfc intent.");
        ProvisioningParams params = parseMinimalistNonNfcIntentInternal(intent, context, trusted);

        params.timeZone = intent.getStringExtra(EXTRA_PROVISIONING_TIME_ZONE);
        String localeString = intent.getStringExtra(EXTRA_PROVISIONING_LOCALE);
        if (localeString != null) {
            params.locale = stringToLocale(localeString);
        }
        params.wifiInfo.ssid = intent.getStringExtra(EXTRA_PROVISIONING_WIFI_SSID);
        params.wifiInfo.securityType = intent.getStringExtra(EXTRA_PROVISIONING_WIFI_SECURITY_TYPE);
        params.wifiInfo.password = intent.getStringExtra(EXTRA_PROVISIONING_WIFI_PASSWORD);
        params.wifiInfo.proxyHost = intent.getStringExtra(EXTRA_PROVISIONING_WIFI_PROXY_HOST);
        params.wifiInfo.proxyBypassHosts =
                intent.getStringExtra(EXTRA_PROVISIONING_WIFI_PROXY_BYPASS);
        params.wifiInfo.pacUrl = intent.getStringExtra(EXTRA_PROVISIONING_WIFI_PAC_URL);
        params.wifiInfo.proxyPort = intent.getIntExtra(EXTRA_PROVISIONING_WIFI_PROXY_PORT,
                ProvisioningParams.DEFAULT_WIFI_PROXY_PORT);
        params.wifiInfo.hidden = intent.getBooleanExtra(EXTRA_PROVISIONING_WIFI_HIDDEN,
                ProvisioningParams.DEFAULT_WIFI_HIDDEN);

        params.deviceAdminPackageName
                = intent.getStringExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME);
        params.deviceAdminDownloadInfo.minVersion = intent.getIntExtra(
                EXTRA_PROVISIONING_DEVICE_ADMIN_MINIMUM_VERSION_CODE,
                ProvisioningParams.DEFAULT_MINIMUM_VERSION);
        params.deviceAdminDownloadInfo.location
                = intent.getStringExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION);
        params.deviceAdminDownloadInfo.cookieHeader = intent.getStringExtra(
                EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_COOKIE_HEADER);
        String packageHash =
                intent.getStringExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM);
        if (packageHash != null) {
            params.deviceAdminDownloadInfo.packageChecksum = Utils.stringToByteArray(packageHash);
            // If we are restarted after an encryption reboot, use stored (trusted) value for this.
            if (trusted) {
                params.deviceAdminDownloadInfo.packageChecksumSupportsSha1 = intent.getBooleanExtra(
                        EXTRA_PROVISIONING_DEVICE_ADMIN_SUPPORT_SHA1_PACKAGE_CHECKSUM, false);
            }
        }
        String sigHash =
                intent.getStringExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM);
        if (sigHash != null) {
            params.deviceAdminDownloadInfo.signatureChecksum = Utils.stringToByteArray(sigHash);
        }

        params.localTime = intent.getLongExtra(EXTRA_PROVISIONING_LOCAL_TIME,
                ProvisioningParams.DEFAULT_LOCAL_TIME);
        if (trusted) {
            // The only case where startedByNfc can be true in this code path is we are reloading
            // a stored Nfc bump intent after encryption reboot, which is a trusted intent.
            params.startedByNfc = intent.getBooleanExtra(EXTRA_PROVISIONING_STARTED_BY_NFC,
                    false);
        }

        params.accountToMigrate = (Account) intent.getParcelableExtra(
                EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE);
        params.deviceAdminComponentName = (ComponentName) intent.getParcelableExtra(
                EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME);

        checkValidityOfProvisioningParams(params);
        return params;
    }

    /**
     * Check whether necessary fields are set.
     */
    private void checkValidityOfProvisioningParams(ProvisioningParams params)
            throws IllegalProvisioningArgumentException  {
        if (TextUtils.isEmpty(params.deviceAdminPackageName)
                && params.deviceAdminComponentName == null) {
            throw new IllegalProvisioningArgumentException("Must provide the name of the device"
                    + " admin package or component name");
        }
        checkDownloadInfoHasChecksum(params.deviceAdminDownloadInfo, "device admin");
    }

    private void checkDownloadInfoHasChecksum(ProvisioningParams.PackageDownloadInfo info,
            String downloadName) throws IllegalProvisioningArgumentException {
        if (!TextUtils.isEmpty(info.location)) {
            if ((info.packageChecksum == null || info.packageChecksum.length == 0)
                    && (info.signatureChecksum == null || info.signatureChecksum.length == 0)) {
                throw new IllegalProvisioningArgumentException("Checksum of installer file"
                        + " or its signature is required for downloading " + downloadName
                        + ", but neither is provided.");
            }
        }
    }

    public static Locale stringToLocale(String string)
        throws IllformedLocaleException {
        return new Locale.Builder().setLanguageTag(string.replace("_", "-")).build();
    }

    public static String localeToString(Locale locale) {
        if (locale != null) {
            return locale.getLanguage() + "_" + locale.getCountry();
        } else {
            return null;
        }
    }
}
