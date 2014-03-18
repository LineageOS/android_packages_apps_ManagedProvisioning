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

import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Parcelable;

import java.io.IOException;
import java.io.StringReader;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Properties;

/**
 * This class can initialize a ProvisioningParams object from an intent.
 * This intent has to contain Nfc data as produced by:
 * com.example.android.apis.app.DeviceProvisioningProgrammerSample.
 *
 * <p>
 * To add extra fields:
 * Add a static key string and id int.
 * Add a mapping from key to id in the mKeyToId.
 * Ad a case in putPropertyByString in which you parse the field and set corresponding field in the
 * ProvisioningParams.
 * </p>
 */
public class NfcMessageParser {
    private static final String NFC_MIME_TYPE = "application/com.android.managedprovisioning";

    // Keys for the properties in the packet.
    private static final String TIMEOUT_KEY = "timeout"; // int
    private static final String TIME_ZONE_KEY = "timeZone";
    private static final String LOCAL_TIME_KEY = "localTime"; // long
    private static final String LOCALE_KEY = "locale";
    private static final String OWNER_KEY = "owner";
    private static final String WIFI_SSID_KEY = "wifiSsid";
    private static final String WIFI_HIDDEN_KEY = "wifiHidden"; // boolean
    private static final String WIFI_SECURITY_TYPE_KEY = "wifiSecurityType";
    private static final String WIFI_PASSWORD_KEY = "wifiPassword";
    private static final String WIFI_PROXY_HOST_KEY = "wifiProxyHost";
    private static final String WIFI_PROXY_PORT_KEY = "wifiProxyPort"; // int
    private static final String WIFI_PROXY_BYPASS_KEY = "wifiProxyBypassHosts";
    private static final String MDM_PACKAGE_KEY = "mdmPackageName";
    private static final String MDM_ADMIN_RECEIVER_KEY = "mdmAdminReceiver";

    // Ids of properties.
    private static final int TIMEOUT_ID = 0;
    private static final int TIME_ZONE_ID = 1;
    private static final int LOCAL_TIME_ID = 2;
    private static final int LOCALE_ID = 3;
    private static final int OWNER_ID = 4;
    private static final int WIFI_SSID_ID = 5;
    private static final int WIFI_HIDDEN_ID = 6;
    private static final int WIFI_SECURITY_TYPE_ID = 7;
    private static final int WIFI_PASSWORD_ID = 8;
    private static final int WIFI_PROXY_HOST_ID = 9;
    private static final int WIFI_PROXY_PORT_ID = 10;
    private static final int WIFI_PROXY_BYPASS_ID = 11;
    private static final int MDM_PACKAGE_ID = 12;
    private static final int MDM_ADMIN_RECEIVER_ID = 13;

    private static final HashMap<String, Integer> mKeyToId = new HashMap<String, Integer>();

    static {
        mKeyToId.put(TIMEOUT_KEY, TIMEOUT_ID);
        mKeyToId.put(TIME_ZONE_KEY, TIME_ZONE_ID);
        mKeyToId.put(LOCAL_TIME_KEY, LOCAL_TIME_ID);
        mKeyToId.put(LOCAL_TIME_KEY, LOCAL_TIME_ID);
        mKeyToId.put(LOCALE_KEY, LOCALE_ID);
        mKeyToId.put(OWNER_KEY, OWNER_ID);
        mKeyToId.put(WIFI_SSID_KEY, WIFI_SSID_ID);
        mKeyToId.put(WIFI_HIDDEN_KEY, WIFI_HIDDEN_ID);
        mKeyToId.put(WIFI_SECURITY_TYPE_KEY, WIFI_SECURITY_TYPE_ID);
        mKeyToId.put(WIFI_PASSWORD_KEY, WIFI_PASSWORD_ID);
        mKeyToId.put(WIFI_PROXY_HOST_KEY, WIFI_PROXY_HOST_ID);
        mKeyToId.put(WIFI_PROXY_PORT_KEY, WIFI_PROXY_PORT_ID);
        mKeyToId.put(WIFI_PROXY_BYPASS_KEY, WIFI_PROXY_BYPASS_ID);
        mKeyToId.put(MDM_PACKAGE_KEY, MDM_PACKAGE_ID);
        mKeyToId.put(MDM_ADMIN_RECEIVER_KEY, MDM_ADMIN_RECEIVER_ID);
    }

    public ProvisioningParams parseNfcIntent(Intent nfcIntent) throws NfcParseException {
        ProvisionLogger.logi("Processing NFC Payload.");

        if (nfcIntent.hasExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)) {

            // Only one first message with NFC_MIME_TYPE is used.
            for (Parcelable rawMsg : nfcIntent
                    .getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)) {
                NdefMessage msg = (NdefMessage) rawMsg;

                // Assume only first record of message is used.
                NdefRecord firstRecord = msg.getRecords()[0];
                String mimeType = new String(firstRecord.getType());

                if (NFC_MIME_TYPE.equals(mimeType)) {
                    return parseNfcProperties(new String(firstRecord.getPayload()));
                }
            }
        } else {
            throw new NfcParseException(
                    "Intent does not contain EXTRA_NDEF_MESSAGES.");
        }
        return null;
    }

    private ProvisioningParams parseNfcProperties(String data) throws NfcParseException {
        ProvisionLogger.logd("Processing NFC Properties.");
        ProvisioningParams params = new ProvisioningParams();
        try {
            Properties props = new Properties();
            props.load(new StringReader(data));

            Enumeration<Object> propertyNames = props.keys();
            while (propertyNames.hasMoreElements()) {
                String propName = (String) propertyNames.nextElement();
                putPropertyByString(propName, props.getProperty(propName), params);
            }
            return params;
        } catch (IOException e) {
            throw new NfcParseException("Couldn't load payload", e);
        } catch (NumberFormatException e) {
            throw new NfcParseException("Incorrect numberformat in Nfc message.", e);
        }
    }

    /**
     * Fill a parameter field (indicated by the key) with its value after parsing it to the correct
     * type.
     */
    public void putPropertyByString(String key, String value, ProvisioningParams params)
            throws NumberFormatException {
        ProvisionLogger.logd("Processing NFC key " + key + " with value " + value);

        // Can't switch on string, so use integer id.
        Integer id = mKeyToId.get(key);
        if (id == null) {

            // Ignore unknown keys.
            ProvisionLogger.logi("Unknown key " + key + " in Nfc data.");
            return;
        }
        switch(id) {
            case TIMEOUT_ID:
                params.mTimeout = Integer.parseInt(value);
                break;
            case TIME_ZONE_ID:
                params.mTimeZone = value;
                break;
            case LOCAL_TIME_ID:
                params.mLocalTime = Long.parseLong(value);
                break;
            case LOCALE_ID:
                params.mLocale = value;
                break;
            case OWNER_ID:
                params.mOwner = value;
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
            case MDM_PACKAGE_ID:
                params.mMdmPackageName = value;
                break;
            case MDM_ADMIN_RECEIVER_ID:
                params.mMdmAdminReceiver = value;
                break;
            default:

                // Should never happen!
                ProvisionLogger.loge("Ignoring known key " + key + ", should never happen!");
                break;
        }
    }

    /**
     * Exception thrown when the ProvisioningParams initialization failed completely.
     *
     * Note: We're using a custom exception to avoid catching subsequent exceptions that might be
     * significant.
     */
    public static class NfcParseException extends Exception {
      public NfcParseException(String message) {
          super(message);
      }
      public NfcParseException(String message, Throwable t) {
          super(message, t);
      }
    }
}