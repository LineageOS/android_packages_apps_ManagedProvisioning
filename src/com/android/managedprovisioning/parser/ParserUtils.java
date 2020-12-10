/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.managedprovisioning.parser;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_FINANCED_DEVICE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_TRIGGER;
import static android.app.admin.DevicePolicyManager.MIME_TYPE_PROVISIONING_NFC;
import static android.app.admin.DevicePolicyManager.PROVISIONING_TRIGGER_CLOUD_ENROLLMENT;
import static android.app.admin.DevicePolicyManager.PROVISIONING_TRIGGER_QR_CODE;
import static android.app.admin.DevicePolicyManager.PROVISIONING_TRIGGER_UNSPECIFIED;
import static android.nfc.NfcAdapter.ACTION_NDEF_DISCOVERED;

import static com.android.managedprovisioning.common.Globals.ACTION_PROVISION_MANAGED_DEVICE_SILENTLY;

import android.app.admin.DevicePolicyManager;
import android.content.Intent;

import com.android.managedprovisioning.common.IllegalProvisioningArgumentException;

/**
 * A utility class with methods related to parsing the provisioning extras
 */
public class ParserUtils {

    /**
     * Returns whether the given intent is for organization owned provisioning.
     *
     * <p>QR, cloud enrollment and NFC are considered owned by an organization.
     */
    boolean isOrganizationOwnedProvisioning(Intent intent) {
        if (ACTION_NDEF_DISCOVERED.equals(intent.getAction())) {
            return true;
        }
        int provisioningTrigger = extractProvisioningTrigger(intent);
        switch (provisioningTrigger) {
            case PROVISIONING_TRIGGER_CLOUD_ENROLLMENT:
            case PROVISIONING_TRIGGER_QR_CODE:
                return true;
            default:
                return false;
        }
    }

    /**
     * Returns the provisioning trigger supplied in the provisioning extras only if it was supplied
     * alongside the {@link DevicePolicyManager#ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE}
     * intent action. Otherwise it returns {@link
     * DevicePolicyManager#PROVISIONING_TRIGGER_UNSPECIFIED}.
     */
    int extractProvisioningTrigger(Intent intent) {
        if (!ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE.equals(intent.getAction())) {
            return PROVISIONING_TRIGGER_UNSPECIFIED;
        }
        return intent.getIntExtra(
                EXTRA_PROVISIONING_TRIGGER, PROVISIONING_TRIGGER_UNSPECIFIED);
    }

    /**
     * Translates a given managed provisioning intent to its corresponding provisioning flow, using
     * the action from the intent.
     *
     * <p>This is necessary because, unlike other provisioning actions which has 1:1 mapping, there
     * are multiple actions that can trigger the device owner provisioning flow. This includes
     * {@link ACTION_PROVISION_MANAGED_DEVICE}, {@link ACTION_NDEF_DISCOVERED} and
     * {@link ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE}. These 3 actions are equivalent
     * except they are sent from a different source.
     *
     * @return the appropriate DevicePolicyManager declared action for the given incoming intent.
     * @throws IllegalProvisioningArgumentException if intent is malformed
     */
    String extractProvisioningAction(Intent intent)
            throws IllegalProvisioningArgumentException {
        if (intent == null || intent.getAction() == null) {
            throw new IllegalProvisioningArgumentException("Null intent action.");
        }

        // Map the incoming intent to a DevicePolicyManager.ACTION_*, as there is a N:1 mapping in
        // some cases.
        switch (intent.getAction()) {
            // Trivial cases.
            case ACTION_PROVISION_MANAGED_DEVICE:
            case ACTION_PROVISION_MANAGED_PROFILE:
            case ACTION_PROVISION_FINANCED_DEVICE:
                return intent.getAction();

            // Silent device owner is same as device owner.
            case ACTION_PROVISION_MANAGED_DEVICE_SILENTLY:
                return ACTION_PROVISION_MANAGED_DEVICE;

            // NFC cases which need to take mime-type into account.
            case ACTION_NDEF_DISCOVERED:
                String mimeType = intent.getType();
                if (mimeType == null) {
                    throw new IllegalProvisioningArgumentException(
                            "Unknown NFC bump mime-type: " + mimeType);
                }
                switch (mimeType) {
                    case MIME_TYPE_PROVISIONING_NFC:
                        return ACTION_PROVISION_MANAGED_DEVICE;

                    default:
                        throw new IllegalProvisioningArgumentException(
                                "Unknown NFC bump mime-type: " + mimeType);
                }

            // Device owner provisioning from a trusted app.
            // TODO (b/27217042): review for new management modes in split system-user model
            case ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE:
                return ACTION_PROVISION_MANAGED_DEVICE;

            default:
                throw new IllegalProvisioningArgumentException("Unknown intent action "
                        + intent.getAction());
        }
    }
}
