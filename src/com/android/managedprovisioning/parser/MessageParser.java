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

package com.android.managedprovisioning.parser;

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
import static android.nfc.NfcAdapter.ACTION_NDEF_DISCOVERED;
import static com.android.internal.util.Preconditions.checkNotNull;
import static com.android.managedprovisioning.common.Globals.ACTION_RESUME_PROVISIONING;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.VisibleForTesting;
import com.android.managedprovisioning.common.IllegalProvisioningArgumentException;
import com.android.managedprovisioning.common.StoreUtils;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;

/**
 * This class can initialize a {@link ProvisioningParams} object from an intent.
 *
 * <p>A {@link ProvisioningParams} object stores various parameters both for the device owner
 * provisioning and profile owner provisioning.
 */
public class MessageParser implements ProvisioningDataParser {
    public static final String EXTRA_PROVISIONING_ACTION =
            "com.android.managedprovisioning.extra.provisioning_action";
    @VisibleForTesting
    static final String EXTRA_PROVISIONING_DEVICE_ADMIN_SUPPORT_SHA1_PACKAGE_CHECKSUM =
            "com.android.managedprovisioning.extra.device_admin_support_sha1_package_checksum";
    @VisibleForTesting
    static final String EXTRA_PROVISIONING_STARTED_BY_TRUSTED_SOURCE  =
            "com.android.managedprovisioning.extra.started_by_trusted_source";

    private final Utils mUtils;

    public MessageParser() {
        this(new Utils());
    }

    @VisibleForTesting
    MessageParser(Utils utils) {
        mUtils = checkNotNull(utils);
    }

    @Override
    public ProvisioningParams parse(Intent provisioningIntent, Context context)
            throws IllegalProvisioningArgumentException {
        return getParser(provisioningIntent).parse(provisioningIntent, context);
    }

    @VisibleForTesting
    ProvisioningDataParser getParser(Intent provisioningIntent) {
        if (ACTION_NDEF_DISCOVERED.equals(provisioningIntent.getAction())) {
            return new PropertiesProvisioningDataParser(mUtils);
        } else {
            return new ExtrasProvisioningDataParser(mUtils);
        }
    }
}
