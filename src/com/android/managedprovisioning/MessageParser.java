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

import static java.nio.charset.StandardCharsets.UTF_8;

import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Parcelable;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigInteger;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Properties;

/**
 * This class can initialize a ProvisioningParams object from an intent.
 * There are two kinds of intents that can be parsed. Both store all data in a serialized
 * {@link Properties} object.
 *
 * <p>
 * The intent has the extra {@link NfcAdapter.EXTRA_NDEF_MESSAGES} when provisioning is started via
 * Nfc bump.
 * (constructed by for example
 * {@link com.example.android.apis.app.DeviceProvisioningProgrammerSample}).
 * </p>
 *
 * <p>
 * The intent has the extra {@link EXTRA_PROVISIONING_PROPERTIES} when provisioning is resumed after
 * encryption.
 * (constructed by {@link BootReminder}).
 * </p>
 *
 * <p>
 * To add extra fields:
 * Add a static key string and id int.
 * Add a mapping from key to id in the mKeyToId.
 * Ad a case in putPropertyByString in which you parse the field and set corresponding field in the
 * ProvisioningParams.
 * </p>
 */
public class MessageParser {
    protected static final String EXTRA_PROVISIONING_PROPERTIES
        = "com.android.managedprovisioning.provisioningProperties";

    private static final String NFC_MIME_TYPE = "application/com.android.managedprovisioning";

    // Used to store the {@link String} that was used in the last call to {@link parseProperties}.
    private static String cachedProvisioningProperties;

    // Keys for the properties in the packet.
    // They correspond to fields of ProvisioningParams (see {@link ProvisioningParams}).
    private static final String TIME_ZONE_KEY = "timeZone";
    private static final String LOCAL_TIME_KEY = "localTime";
    private static final String LOCALE_KEY = "locale";
    private static final String WIFI_SSID_KEY = "wifiSsid";
    private static final String WIFI_HIDDEN_KEY = "wifiHidden";
    private static final String WIFI_SECURITY_TYPE_KEY = "wifiSecurityType";
    private static final String WIFI_PASSWORD_KEY = "wifiPassword";
    private static final String WIFI_PROXY_HOST_KEY = "wifiProxyHost";
    private static final String WIFI_PROXY_PORT_KEY = "wifiProxyPort"; // int
    private static final String WIFI_PROXY_BYPASS_KEY = "wifiProxyBypassHosts";
    private static final String DEVICE_ADMIN_PACKAGE_KEY = "deviceAdminPackage";
    private static final String OWNER_KEY = "owner";
    private static final String DOWNLOAD_LOCATION_KEY = "downloadLocation";
    private static final String HASH_KEY = "hash";

    // Ids of properties.
    private static final int TIME_ZONE_ID = 0;
    private static final int LOCAL_TIME_ID = 1;
    private static final int LOCALE_ID = 2;
    private static final int WIFI_SSID_ID = 3;
    private static final int WIFI_HIDDEN_ID = 4;
    private static final int WIFI_SECURITY_TYPE_ID = 5;
    private static final int WIFI_PASSWORD_ID = 6;
    private static final int WIFI_PROXY_HOST_ID = 7;
    private static final int WIFI_PROXY_PORT_ID = 8;
    private static final int WIFI_PROXY_BYPASS_ID = 9;
    private static final int DEVICE_ADMIN_PACKAGE_ID = 10;
    private static final int OWNER_ID = 11;
    private static final int DOWNLOAD_LOCATION_ID = 12;
    private static final int HASH_ID = 13;

    // Map from keys to ids.
    private static final HashMap<String, Integer> mKeyToId = new HashMap<String, Integer>();

    static {
        mKeyToId.put(TIME_ZONE_KEY, TIME_ZONE_ID);
        mKeyToId.put(LOCAL_TIME_KEY, LOCAL_TIME_ID);
        mKeyToId.put(LOCALE_KEY, LOCALE_ID);
        mKeyToId.put(WIFI_SSID_KEY, WIFI_SSID_ID);
        mKeyToId.put(WIFI_HIDDEN_KEY, WIFI_HIDDEN_ID);
        mKeyToId.put(WIFI_SECURITY_TYPE_KEY, WIFI_SECURITY_TYPE_ID);
        mKeyToId.put(WIFI_PASSWORD_KEY, WIFI_PASSWORD_ID);
        mKeyToId.put(WIFI_PROXY_HOST_KEY, WIFI_PROXY_HOST_ID);
        mKeyToId.put(WIFI_PROXY_PORT_KEY, WIFI_PROXY_PORT_ID);
        mKeyToId.put(WIFI_PROXY_BYPASS_KEY, WIFI_PROXY_BYPASS_ID);
        mKeyToId.put(DEVICE_ADMIN_PACKAGE_KEY, DEVICE_ADMIN_PACKAGE_ID);
        mKeyToId.put(OWNER_KEY, OWNER_ID);
        mKeyToId.put(DOWNLOAD_LOCATION_KEY, DOWNLOAD_LOCATION_ID);
        mKeyToId.put(HASH_KEY, HASH_ID);
    }

    public ProvisioningParams parseIntent(Intent intent) throws ParseException {
        ProvisionLogger.logi("Processing intent.");
        if (intent.hasExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)) {
            return parseNfcIntent(intent);
        } else if (intent.hasExtra(EXTRA_PROVISIONING_PROPERTIES)) {
            return parseProperties(intent.getStringExtra(EXTRA_PROVISIONING_PROPERTIES));
        } else {
            throw new ParseException(
                    "Intent does not contain EXTRA_NDEF_MESSAGES or EXTRA_PROVISIONING_PROPERTIES.",
                    R.string.device_owner_error_parse_fail);
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

            if (NFC_MIME_TYPE.equals(mimeType)) {
                return parseProperties(new String(firstRecord.getPayload(), UTF_8));
            }
        }
        throw new ParseException(
                "Intent does not contain NfcRecord with the correct MIME type.",
                R.string.device_owner_error_parse_fail);
    }

    private ProvisioningParams parseProperties(String data)
            throws ParseException {
        ProvisionLogger.logi("Parsing Properties.");
        ProvisioningParams params = new ProvisioningParams();
        try {
            Properties props = new Properties();
            props.load(new StringReader(data));

            Enumeration<Object> propertyNames = props.keys();
            while (propertyNames.hasMoreElements()) {
                String propName = (String) propertyNames.nextElement();
                putPropertyByString(propName, props.getProperty(propName), params);
            }

            checkValidityOfProvisioningParams(params);
            cachedProvisioningProperties = data;
            return params;
        } catch (IOException e) {
            throw new ParseException("Couldn't load payload",
                    R.string.device_owner_error_parse_fail, e);
        } catch (NumberFormatException e) {
            throw new ParseException("Incorrect numberformat.",
                    R.string.device_owner_error_parse_fail, e);
        }
    }

    /**
     * Fill a parameter field (indicated by the key) with its value after parsing it to the correct
     * type.
     */
    public void putPropertyByString(String key, String value, ProvisioningParams params)
            throws NumberFormatException {
        ProvisionLogger.logd("Processing property key " + key + " with value " + value);

        // Can't switch on string, so use integer id.
        Integer id = mKeyToId.get(key);
        if (id == null) {

            // Ignore unknown keys.
            ProvisionLogger.logi("Unknown key " + key + " in properties data.");
            return;
        }
        switch(id) {
            case TIME_ZONE_ID:
                params.mTimeZone = value;
                break;
            case LOCAL_TIME_ID:
                params.mLocalTime = Long.parseLong(value);
                break;
            case LOCALE_ID:
                if (value.length() == 5) {
                    params.mLocale = new Locale(value.substring(0, 2), value.substring(3, 5));
                } else {
                    throw new NumberFormatException("The locale code is not 5 characters long.");
                }
                break;
            case WIFI_SSID_ID:
                params.mWifiSsid = value;
                break;
            case WIFI_HIDDEN_ID:
                params.mWifiHidden = Boolean.parseBoolean(value);
                break;
            case WIFI_SECURITY_TYPE_ID:
                params.mWifiSecurityType = value;
                break;
            case WIFI_PASSWORD_ID:
                params.mWifiPassword = value;
                break;
            case WIFI_PROXY_HOST_ID:
                params.mWifiProxyHost = value;
                break;
            case WIFI_PROXY_PORT_ID:
                params.mWifiProxyPort = Integer.parseInt(value);
                break;
            case WIFI_PROXY_BYPASS_ID:
                params.mWifiProxyBypassHosts = value;
                break;
            case DEVICE_ADMIN_PACKAGE_ID:
                params.mDeviceAdminPackageName = value;
                break;
            case OWNER_ID:
                params.mOwner = value;
                break;
            case DOWNLOAD_LOCATION_ID:
                params.mDownloadLocation = value;
                break;
            case HASH_ID:
                params.mHash = new BigInteger(value,16).toByteArray();
                break;
            default:

                // Should never happen!
                ProvisionLogger.loge("Ignoring known key " + key + ", should never happen!");
                break;
        }
    }

    /**
     * Check whether necessary fields are set.
     */
    private void checkValidityOfProvisioningParams(ProvisioningParams params)
        throws ParseException  {
        if (params.mDeviceAdminPackageName == null) {
            throw new ParseException("Must provide the name of the device admin package.",
                    R.string.device_owner_error_no_package_name);
        }
        if (params.mDownloadLocation != null) {
            if (params.mHash == null) {
                throw new ParseException("Hash of installer file is required for downloading " +
                        "device admin file, but not provided.",
                        R.string.device_owner_error_no_hash);
            }
            if (params.mWifiSsid == null) {
                throw new ParseException("Wifi ssid is required for downloading device admin " +
                        "file, but not provided.",
                        R.string.device_owner_error_no_wifi_ssid);
            }
        }
    }

    public static String getCachedProvisioningProperties() {
        return cachedProvisioningProperties;
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
}