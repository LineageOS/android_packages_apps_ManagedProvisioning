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

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;

import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import java.lang.Exception;

public class MessageParserTest extends AndroidTestCase {

    @SmallTest
    public void testParseAndRecoverIntent(Intent i) throws Exception {
        Intent first = new Intent(ACTION_PROVISION_MANAGED_PROFILE);
        first.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_LOCALE, "locale.sample.string");
        ProvisioningParams params = new MessageParser().parseNonNfcIntent(first, getContext(),
                true);
        Intent second = new MessageParser().getIntentFromProvisioningParams(params);
        TestUtils.assertIntentEquals(first, second);
    }

}
