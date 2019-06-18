/*
 * Copyright (C) 2019 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.managedprovisioning.provisioning;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;

import static com.android.managedprovisioning.provisioning.AbstractProvisioningActivity.ERROR_DIALOG_OK;
import static com.android.managedprovisioning.provisioning.AbstractProvisioningActivity.ERROR_DIALOG_RESET;

import static com.google.common.truth.Truth.assertThat;

import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.ComponentName;
import android.content.Intent;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.model.ProvisioningParams;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.List;

/**
 * Robolectric tests for {@link ProvisioningActivity}.
 */
@RunWith(RobolectricTestRunner.class)
public class ProvisioningActivityRoboTest {

    private static final String ADMIN_PACKAGE = "com.test.admin";
    private static final ComponentName ADMIN = new ComponentName(ADMIN_PACKAGE, ".Receiver");
    private static final ProvisioningParams PROFILE_OWNER_PARAMS = new ProvisioningParams.Builder()
            .setProvisioningAction(ACTION_PROVISION_MANAGED_PROFILE)
            .setDeviceAdminComponentName(ADMIN)
            .build();
    private static final Intent PROFILE_OWNER_INTENT = new Intent()
            .putExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS, PROFILE_OWNER_PARAMS);
    private static final int ERROR_MESSAGE_ID = R.string.managed_provisioning_error_text;

    private Application mContext = RuntimeEnvironment.application;

    @Test
    public void testError_noFactoryReset_showsDialogue() {
        final ProvisioningActivity activity =
                Robolectric.buildActivity(ProvisioningActivity.class, PROFILE_OWNER_INTENT).get();

        activity.error(R.string.cant_set_up_device, ERROR_MESSAGE_ID, /* resetRequired= */ false);

        final Fragment dialog = activity.getFragmentManager().findFragmentByTag(ERROR_DIALOG_OK);
        assertThat(dialog).isNotNull();
    }

    @Test
    public void testError_noFactoryReset_doesNotReset() throws Exception {
        final ProvisioningActivity activity =
                Robolectric.buildActivity(ProvisioningActivity.class, PROFILE_OWNER_INTENT).get();
        activity.error(R.string.cant_set_up_device, ERROR_MESSAGE_ID, /* resetRequired= */ false);

        final Fragment dialog = activity.getFragmentManager().findFragmentByTag(ERROR_DIALOG_OK);
        clickOnOkButton(activity, (DialogFragment) dialog);

        final List<Intent> intents = shadowOf(mContext).getBroadcastIntents();
        assertThat(intentsContainsAction(intents, Intent.ACTION_FACTORY_RESET)).isFalse();
    }

    @Test
    public void testError_factoryReset_showsDialogue() {
        final ProvisioningActivity activity =
                Robolectric.buildActivity(ProvisioningActivity.class, PROFILE_OWNER_INTENT).get();

        activity.error(R.string.cant_set_up_device, ERROR_MESSAGE_ID, /* resetRequired= */ true);

        final Fragment dialog = activity.getFragmentManager().findFragmentByTag(ERROR_DIALOG_RESET);
        assertThat(dialog).isNotNull();
    }

    @Test
    public void testError_factoryReset_resets() throws Exception {
        final ProvisioningActivity activity =
                Robolectric.buildActivity(ProvisioningActivity.class, PROFILE_OWNER_INTENT).get();
        activity.error(R.string.cant_set_up_device, ERROR_MESSAGE_ID, /* resetRequired= */ true);

        final Fragment dialog = activity.getFragmentManager().findFragmentByTag(ERROR_DIALOG_RESET);
        clickOnOkButton(activity, (DialogFragment) dialog);

        final List<Intent> intents = shadowOf(mContext).getBroadcastIntents();
        assertThat(intentsContainsAction(intents, Intent.ACTION_FACTORY_RESET)).isTrue();
    }

    private boolean intentsContainsAction(List<Intent> intents, String action) {
        return intents.stream().anyMatch(intent -> intent.getAction().equals(action));
    }

    private void clickOnOkButton(ProvisioningActivity activity, DialogFragment dialog) {
        // TODO(135181317): This should be replaced by
        //  activity.findViewById(android.R.id.button1).performClick();
        activity.onPositiveButtonClick(dialog);
    }
}
