/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.managedprovisioning.preprovisioning.terms;

import static com.android.managedprovisioning.provisioning.ProvisioningActivityTest.DEVICE_OWNER_PARAMS;
import static com.android.managedprovisioning.provisioning.ProvisioningActivityTest.PROFILE_OWNER_PARAMS;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.pm.PackageManager;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

@SmallTest
public class TermsProviderTest {
    private final Context mContext = InstrumentationRegistry.getTargetContext();
    private final String mStringGeneralPo = mContext.getString(R.string.work_profile_info);
    private final String mStringGeneralDo = mContext.getString(R.string.managed_device_info);
    private final String mStringAdminDisclaimerDo = mContext.getString(R.string.admin_has_ability_to_monitor_device);
    private final String mStringAdminDisclaimerPo = mContext.getString(R.string.admin_has_ability_to_monitor_profile);

    private final TermsProvider mTermsProvider = new TermsProvider(mContext, s -> "", new Utils());

    @Test
    public void generalHeading_presentAsFirst_profileOwner() throws Exception {
        assumeHasManagedUsersFeature();
        List<TermsDocument> terms = mTermsProvider.getTerms(PROFILE_OWNER_PARAMS, 0);
        assertThat(terms.get(0).getHeading(), equalTo(mStringGeneralPo));
        assertThat(terms.get(0).getContent(), equalTo(mStringAdminDisclaimerPo));
    }

    @Test
    public void generalHeading_presentAsFirst_deviceOwner() throws Exception {
        assumeHasDeviceAdminFeature();
        List<TermsDocument> terms = mTermsProvider.getTerms(DEVICE_OWNER_PARAMS, 0);
        assertThat(terms.get(0).getHeading(), equalTo(mStringGeneralDo));
        assertThat(terms.get(0).getContent(), equalTo(mStringAdminDisclaimerDo));
    }

    @Test
    public void flag_skipGeneral() {
        ProvisioningParams[] params = {PROFILE_OWNER_PARAMS, DEVICE_OWNER_PARAMS};
        for (ProvisioningParams p : params) {
            List<TermsDocument> terms = mTermsProvider.getTerms(p,
                    TermsProvider.Flags.SKIP_GENERAL_DISCLAIMER);
            if (terms != null && !terms.isEmpty()) {
                assertThat(terms.get(0), not(equalTo(mStringGeneralPo)));
                assertThat(terms.get(0), not(equalTo(mStringGeneralDo)));
            }
        }
    }

    private void assumeHasManagedUsersFeature() {
        assumeTrue("Device doesn't support managed profile",
                mContext.getPackageManager()
                        .hasSystemFeature(PackageManager.FEATURE_MANAGED_USERS));
    }

    private void assumeHasDeviceAdminFeature() {
        assumeTrue("Device doesn't support managed device",
                mContext.getPackageManager()
                        .hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN));
    }
}