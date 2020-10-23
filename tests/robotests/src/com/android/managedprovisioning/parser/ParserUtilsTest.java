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
import static android.app.admin.DevicePolicyManager.PROVISIONING_TRIGGER_PERSISTENT_DEVICE_OWNER;
import static android.app.admin.DevicePolicyManager.PROVISIONING_TRIGGER_QR_CODE;
import static android.app.admin.DevicePolicyManager.PROVISIONING_TRIGGER_UNSPECIFIED;
import static android.nfc.NfcAdapter.ACTION_NDEF_DISCOVERED;

import static com.android.managedprovisioning.common.Globals.ACTION_PROVISION_MANAGED_DEVICE_SILENTLY;

import static com.google.common.truth.Truth.assertThat;

import android.content.Intent;

import com.android.managedprovisioning.common.IllegalProvisioningArgumentException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Tests for {@link ParserUtils}.
 */
@RunWith(RobolectricTestRunner.class)
public class ParserUtilsTest {

    private final ParserUtils mParserUtils = new ParserUtils();

    @Test
    public void isOrganizationOwnedProvisioning_nfcIntent_returnsTrue() {
        Intent nfcIntent = new Intent(ACTION_NDEF_DISCOVERED);

        assertThat(mParserUtils.isOrganizationOwnedProvisioning(nfcIntent)).isTrue();
    }

    @Test
    public void isOrganizationOwnedProvisioning_cloudEnrollmentIntent_returnsTrue() {
        Intent intent = new Intent(ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE);
        intent.putExtra(EXTRA_PROVISIONING_TRIGGER, PROVISIONING_TRIGGER_CLOUD_ENROLLMENT);

        assertThat(mParserUtils.isOrganizationOwnedProvisioning(intent)).isTrue();
    }

    @Test
    public void isOrganizationOwnedProvisioning_qrIntent_returnsTrue() {
        Intent intent = new Intent(ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE);
        intent.putExtra(EXTRA_PROVISIONING_TRIGGER, PROVISIONING_TRIGGER_QR_CODE);

        assertThat(mParserUtils.isOrganizationOwnedProvisioning(intent)).isTrue();
    }

    @Test
    public void isOrganizationOwnedProvisioning_provisionManagedProfileIntent_returnsFalse() {
        Intent intent = new Intent(ACTION_PROVISION_MANAGED_PROFILE);

        assertThat(mParserUtils.isOrganizationOwnedProvisioning(intent)).isFalse();
    }

    @Test
    public void isOrganizationOwnedProvisioning_provisionManagedDeviceIntent_returnsFalse() {
        Intent intent = new Intent(ACTION_PROVISION_MANAGED_DEVICE);

        assertThat(mParserUtils.isOrganizationOwnedProvisioning(intent)).isFalse();
    }

    @Test
    public void isOrganizationOwnedProvisioning_provisionTrustedSourceIntentWithNoProvisioningTrigger_returnsFalse() {
        Intent intent = new Intent(ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE);

        assertThat(mParserUtils.isOrganizationOwnedProvisioning(intent)).isFalse();
    }

    @Test
    public void extractProvisioningTrigger_provisionManagedDeviceIntent_returnsUnspecified() {
        Intent intent = new Intent(ACTION_PROVISION_MANAGED_DEVICE);

        assertThat(mParserUtils.extractProvisioningTrigger(intent))
                .isEqualTo(PROVISIONING_TRIGGER_UNSPECIFIED);
    }

    @Test
    public void extractProvisioningTrigger_provisionTrustedSourceIntentWithNoProvisioningTrigger_returnsUnspecified() {
        Intent intent = new Intent(ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE);

        assertThat(mParserUtils.extractProvisioningTrigger(intent))
                .isEqualTo(PROVISIONING_TRIGGER_UNSPECIFIED);
    }

    @Test
    public void
    extractProvisioningTrigger_provisionTrustedSourceWithQrProvisioningTrigger_returnsQr() {
        Intent intent = new Intent(ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE);
        intent.putExtra(EXTRA_PROVISIONING_TRIGGER, PROVISIONING_TRIGGER_QR_CODE);

        assertThat(mParserUtils.extractProvisioningTrigger(intent))
                .isEqualTo(PROVISIONING_TRIGGER_QR_CODE);
    }

    @Test
    public void extractProvisioningTrigger_provisionTrustedSourceWithCloudEnrollmentProvisioningTrigger_returnsCloudEnrollment() {
        Intent intent = new Intent(ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE);
        intent.putExtra(EXTRA_PROVISIONING_TRIGGER, PROVISIONING_TRIGGER_CLOUD_ENROLLMENT);

        assertThat(mParserUtils.extractProvisioningTrigger(intent))
                .isEqualTo(PROVISIONING_TRIGGER_CLOUD_ENROLLMENT);
    }

    @Test
    public void extractProvisioningTrigger_provisionTrustedSourceWithDeviceOwnerProvisioningTrigger_returnsPersistentDeviceOwner() {
        Intent intent = new Intent(ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE);
        intent.putExtra(EXTRA_PROVISIONING_TRIGGER, PROVISIONING_TRIGGER_PERSISTENT_DEVICE_OWNER);

        assertThat(mParserUtils.extractProvisioningTrigger(intent))
                .isEqualTo(PROVISIONING_TRIGGER_PERSISTENT_DEVICE_OWNER);
    }

    @Test
    public void
    extractProvisioningAction_provisionManagedDeviceIntent_returnsProvisionManagedDevice()
            throws IllegalProvisioningArgumentException {
        Intent intent = new Intent(ACTION_PROVISION_MANAGED_DEVICE);

        assertThat(mParserUtils.extractProvisioningAction(intent))
                .isEqualTo(ACTION_PROVISION_MANAGED_DEVICE);
    }

    @Test
    public void
    extractProvisioningAction_provisionManagedProfileIntent_returnsProvisionManagedProfile()
            throws IllegalProvisioningArgumentException {
        Intent intent = new Intent(ACTION_PROVISION_MANAGED_PROFILE);

        assertThat(mParserUtils.extractProvisioningAction(intent))
                .isEqualTo(ACTION_PROVISION_MANAGED_PROFILE);
    }

    @Test
    public void
    extractProvisioningAction_provisionFinancedDeviceIntent_returnsProvisionFinancedDevice()
            throws IllegalProvisioningArgumentException {
        Intent intent = new Intent(ACTION_PROVISION_FINANCED_DEVICE);

        assertThat(mParserUtils.extractProvisioningAction(intent))
                .isEqualTo(ACTION_PROVISION_FINANCED_DEVICE);
    }

    @Test
    public void
    extractProvisioningAction_provisionManagedDeviceSilentlyIntent_returnsProvisionManagedDevice()
            throws IllegalProvisioningArgumentException {
        Intent intent = new Intent(ACTION_PROVISION_MANAGED_DEVICE_SILENTLY);

        assertThat(mParserUtils.extractProvisioningAction(intent))
                .isEqualTo(ACTION_PROVISION_MANAGED_DEVICE);
    }

    @Test
    public void extractProvisioningAction_nfcIntent_returnsProvisionManagedDevice()
            throws IllegalProvisioningArgumentException {
        Intent intent = new Intent(ACTION_NDEF_DISCOVERED);
        intent.setType(MIME_TYPE_PROVISIONING_NFC);

        assertThat(mParserUtils.extractProvisioningAction(intent))
                .isEqualTo(ACTION_PROVISION_MANAGED_DEVICE);
    }

    @Test(expected = IllegalProvisioningArgumentException.class)
    public void testExtractProvisioningAction_nfcIntentWithNoMimeType_throwsException()
            throws IllegalProvisioningArgumentException {
        Intent intent = new Intent(ACTION_NDEF_DISCOVERED);

        mParserUtils.extractProvisioningAction(intent);
    }

    @Test(expected = IllegalProvisioningArgumentException.class)
    public void extractProvisioningAction_nfcIntentWithWrongMimeType_throwsException()
            throws IllegalProvisioningArgumentException {
        Intent intent = new Intent(ACTION_NDEF_DISCOVERED);
        intent.setType("wrongMimeType");

        mParserUtils.extractProvisioningAction(intent);
    }
}
