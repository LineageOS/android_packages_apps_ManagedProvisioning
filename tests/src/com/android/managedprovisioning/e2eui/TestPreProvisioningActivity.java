/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.managedprovisioning.e2eui;

import com.android.managedprovisioning.TestInstrumentationRunner;
import com.android.managedprovisioning.uiflows.PreProvisioningActivity;

public class TestPreProvisioningActivity extends PreProvisioningActivity {
    /** ManagedProfileTest is running in ManagedProvisioning process, while the AdminReceiver is in
     * test package process. Mock the calling package to pretend we provision it from test package,
     * not from ManagedProvisioning.
     */
    @Override
    public String getCallingPackage() {
        return TestInstrumentationRunner.TEST_PACKAGE_NAME;
    }
}
