/*
 * Copyright 2014, The Android Open Source Project
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

package com.android.managedprovisioning.task;

import com.android.managedprovisioning.ProvisionLogger;
import com.android.managedprovisioning.ManagedProvisioningActivity.ProvisioningState;

/**
 * This tasks creates a secondary user and sets the application that triggered the provisioning as
 * the profile owner for this user.
 *
 * TODO This is a stubb
 *   - Create a secondary user as related user to primary user
 *   - start new activity as secondary user
 *   - set secondary user as profile owner
 *   - disable mdm for primary user (either here or elsewhere)
 *
 * TODO Here or somewhere else in the BYOD flow check for permissions and ask user for their
 *      permission to create a secondary profile.
 */
public class CreateProfileTask extends ProvisionTask {

    public CreateProfileTask() {
        super("Create Profile task");
    }

    @Override
    public void executeTask(String... params) {
        ProvisionLogger.logd("creating secondary user stubb");
        mTaskManager.registerProvisioningState(ProvisioningState.CREATE_PROFILE, "");
        onSuccess();
    }

    @Override
    public void shutdown() {
    }

    @Override
    public void hasFailed() {
    }
}
