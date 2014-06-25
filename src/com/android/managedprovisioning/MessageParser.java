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

import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_TIME_ZONE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_LOCAL_TIME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_LOCALE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_SSID;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_HIDDEN;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_SECURITY_TYPE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_PASSWORD;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_PROXY_HOST;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_PROXY_PORT;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_PROXY_BYPASS;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_PAC_URL;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM;
import static android.app.admin.DevicePolicyManager.PROVISIONING_NFC_MIME_TYPE;
import static java.nio.charset.StandardCharsets.UTF_8;

import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Parcelable;
import android.text.TextUtils;

import java.io.IOException;
import java.io.StringReader;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Properties;

/**
 * This class can initialize a ProvisioningParams object from an intent.
 * There are two kinds of intents that can be parsed.
 *
 * <p>
 * Intent was received via Nfc.
 * The intent contains the extra {@link NfcAdapter.EXTRA_NDEF_MESSAGES}, which indicates that
 * provisioning was started via Nfc bump. This extra contains an NDEF message, which contains an
 * NfcRecord with mime type {@link PROVISIONING_NFC_MIME_TYPE}. This record stores a serialized
 * properties object, which contains the serialized extra's described in the next option.
 * A typical use case would be a programmer application that sends an Nfc bump to start Nfc
 * provisioning from a programmer device.
 *
 * <p>
 * Intent was received directly.
 * The intent contains the extra {@link #EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME},
 * and may contain {@link #EXTRA_PROVISIONING_TIME_ZONE},
 * {@link #EXTRA_PROVISIONING_LOCAL_TIME}, and {@link #EXTRA_PROVISIONING_LOCALE}. A download
 * location may be specified in {@link #EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION}
 * accompanied by the SHA-1 sum of the target file
 * {@link #EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM}. Furthermore a wifi network may be
 * specified in {@link #EXTRA_PROVISIONING_WIFI_SSID}, and if applicable
 * {@link #EXTRA_PROVISIONING_WIFI_HIDDEN}, {@link #EXTRA_PROVISIONING_WIFI_SECURITY_TYPE},
 * {@link #EXTRA_PROVISIONING_WIFI_PASSWORD}, {@link #EXTRA_PROVISIONING_WIFI_PROXY_HOST},
 * {@link #EXTRA_PROVISIONING_WIFI_PROXY_PORT}, {@link #EXTRA_PROVISIONING_WIFI_PROXY_BYPASS}.
 * A typical use case would be the {@link BootReminder} sending the intent after device encryption
 * and reboot.
 */
public class MessageParser {
    public ProvisioningParams parseIntent(Intent intent) throws ParseException {
        ProvisionLogger.logi("Processing intent.");
        if (intent.hasExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)) {
            return parseNfcIntent(intent);
        } else {
            return parseNonNfcIntent(intent);
        }
    }

    public ProvisioningParams parseNfcIntent(Intent nfcIntent)
        throws ParseException {
        ProvisionLogger.logi("Processing Nfc Payload.");
        // Only one first message with NFC_MIME_TYPE is used.
        for (Parcelable rawMsg : nfcIntent
                     .getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)) {
            NdefMessage msg = (NdefMessage) rawMsg;

            // Assume only first record of message is used.
            NdefRecord firstRecord = msg.getRecords()[0];
            String mimeType = new String(firstRecord.getType(), UTF_8);

            if (PROVISIONING_NFC_MIME_TYPE.equals(mimeType)) {
                return parseProperties(new String(firstRecord.getPayload(), UTF_8));
            }
        }
        throw new ParseException(
                "Intent does not contain NfcRecord with the correct MIME type.",
                R.string.device_owner_error_parse_fail);
    }

    private ProvisioningParams parseProperties(String data)
            throws ParseException {
        ProvisioningParams params = new ProvisioningParams();
        try {
            Properties props = new Properties();
            props.load(new StringReader(data));

            String s; // Used for parsing non-Strings.
            params.mDeviceAdminPackageName
                    = props.getProperty(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME);
            params.mTimeZone
                    = props.getProperty(EXTRA_PROVISIONING_TIME_ZONE);
            if ((s = props.getProperty(EXTRA_PROVISIONING_LOCAL_TIME)) != null) {
                params.mLocalTime = Long.parseLong(s);
            }
            if ((s = props.getProperty(EXTRA_PROVISIONING_LOCALE)) != null) {
                params.mLocale = stringToLocale(s);
            }
            params.mWifiSsid = props.getProperty(EXTRA_PROVISIONING_WIFI_SSID);
            if ((s = props.getProperty(EXTRA_PROVISIONING_WIFI_HIDDEN)) != null) {
                params.mWifiHidden = Boolean.parseBoolean(s);
            }
            params.mWifiSecurityType = props.getProperty(EXTRA_PROVISIONING_WIFI_SECURITY_TYPE);
            params.mWifiPassword = props.getProperty(EXTRA_PROVISIONING_WIFI_PASSWORD);
            params.mWifiProxyHost = props.getProperty(EXTRA_PROVISIONING_WIFI_PROXY_HOST);
            if ((s = props.getProperty(EXTRA_PROVISIONING_WIFI_PROXY_PORT)) != null) {
                params.mWifiProxyPort = Integer.parseInt(s);
            }
            params.mWifiProxyBypassHosts = props.getProperty(EXTRA_PROVISIONING_WIFI_PROXY_BYPASS);
            params.mWifiPacUrl = props.getProperty(EXTRA_PROVISIONING_WIFI_PAC_URL);
            params.mDeviceAdminPackageDownloadLocation
                    = props.getProperty(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION);
            if ((s = props.getProperty(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM)) != null) {
                params.mDeviceAdminPackageChecksum = stringToByteArray(s);
            }

            checkValidityOfProvisioningParams(params);
            return params;
        } catch (IOException e) {
            throw new ParseException("Couldn't load payload",
                    R.string.device_owner_error_parse_fail, e);
        } catch (NumberFormatException e) {
            throw new ParseException("Incorrect numberformat.",
                    R.string.device_owner_error_parse_fail, e);
        }
    }

    public ProvisioningParams parseNonNfcIntent(Intent intent)
        throws ParseException {
        ProvisionLogger.logi("Processing intent.");
        ProvisioningParams params = new ProvisioningParams();

        params.mDeviceAdminPackageName
                = intent.getStringExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME);
        params.mTimeZone = intent.getStringExtra(EXTRA_PROVISIONING_TIME_ZONE);
        params.mLocalTime = intent.getLongExtra(EXTRA_PROVISIONING_LOCAL_TIME,
                ProvisioningParams.DEFAULT_LOCAL_TIME);
        String localeString = intent.getStringExtra(EXTRA_PROVISIONING_LOCALE);
        if (localeString != null) {
            params.mLocale = stringToLocale(localeString);
        }
        params.mWifiSsid = intent.getStringExtra(EXTRA_PROVISIONING_WIFI_SSID);
        params.mWifiHidden = intent.getBooleanExtra(EXTRA_PROVISIONING_WIFI_HIDDEN,
                ProvisioningParams.DEFAULT_WIFI_HIDDEN);
        params.mWifiSecurityType = intent.getStringExtra(EXTRA_PROVISIONING_WIFI_SECURITY_TYPE);
        params.mWifiPassword = intent.getStringExtra(EXTRA_PROVISIONING_WIFI_PASSWORD);
        params.mWifiProxyHost = intent.getStringExtra(EXTRA_PROVISIONING_WIFI_PROXY_HOST);
        params.mWifiProxyPort = intent.getIntExtra(EXTRA_PROVISIONING_WIFI_PROXY_PORT,
                ProvisioningParams.DEFAULT_WIFI_PROXY_PORT);
        params.mWifiProxyBypassHosts = intent.getStringExtra(EXTRA_PROVISIONING_WIFI_PROXY_BYPASS);
        params.mWifiPacUrl = intent.getStringExtra(EXTRA_PROVISIONING_WIFI_PAC_URL);
        params.mDeviceAdminPackageDownloadLocation
                = intent.getStringExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION);
        String hashString = intent.getStringExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM);
        if (hashString != null) {
            params.mDeviceAdminPackageChecksum = stringToByteArray(hashString);
        }

        checkValidityOfProvisioningParams(params);
        return params;
    }

    /**
     * Check whether necessary fields are set.
     */
    private void checkValidityOfProvisioningParams(ProvisioningParams params)
        throws ParseException  {
        if (TextUtils.isEmpty(params.mDeviceAdminPackageName)) {
            throw new ParseException("Must provide the name of the device admin package.",
                    R.string.device_owner_error_no_package_name);
        }
        if (!TextUtils.isEmpty(params.mDeviceAdminPackageDownloadLocation)) {
            if (params.mDeviceAdminPackageChecksum == null ||
                    params.mDeviceAdminPackageChecksum.length == 0) {
                throw new ParseException("Checksum of installer file is required for downloading " +
                        "device admin file, but not provided.",
                        R.string.device_owner_error_no_hash);
            }
        }
    }

    /**
     * Exception thrown when the ProvisioningParams initialization failed completely.
     *
     * Note: We're using a custom exception to avoid catching subsequent exceptions that might be
     * significant.
     */
    public static class ParseException extends Exception {
        private int mErrorMessageId;

        public ParseException(String message, int errorMessageId) {
            super(message);
            mErrorMessageId = errorMessageId;
        }

        public ParseException(String message, int errorMessageId, Throwable t) {
            super(message, t);
            mErrorMessageId = errorMessageId;
        }

        public int getErrorMessageId() {
            return mErrorMessageId;
        }
    }

    public static byte[] stringToByteArray(String s)
        throws NumberFormatException {
        int l = s.length();
        if (l%2!=0) {
            throw new NumberFormatException("Hex String should have even length.");
        }
        byte[] data = new byte[l / 2];
        for (int i = 0; i < l; i += 2) {
            int firstDigit = Character.digit(s.charAt(i), 16);
            if (firstDigit<0) {
                throw new NumberFormatException("Hex String contains invalid character " +
                        s.charAt(i));
            }
            int secondDigit = Character.digit(s.charAt(i+1), 16);
            if (secondDigit<0) {
                throw new NumberFormatException("Hex String contains invalid character " +
                        s.charAt(i+1));
            }
            data[i / 2] = (byte) (( firstDigit << 4) + secondDigit);
        }
        return data;
    }

    public static Locale stringToLocale(String s)
        throws NumberFormatException {
        if (s.length() == 5) {
            return new Locale(s.substring(0, 2), s.substring(3, 5));
        } else {
            throw new NumberFormatException("The locale code is not 5 characters long.");
        }
    }
}
