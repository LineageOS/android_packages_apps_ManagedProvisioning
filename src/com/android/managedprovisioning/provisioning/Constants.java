/*
 * Copyright 2016, The Android Open Source Project
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

package com.android.managedprovisioning.provisioning;

/**
 * Constants used for communication between service and activity.
 */
public final class Constants {
    // Intents sent by the activity to the service
    /**
     * Intent action sent to the {@link ProvisioningService} to indicate that provisioning progress
     * should be cancelled.
     */
    public static final String ACTION_CANCEL_PROVISIONING =
            "com.android.managedprovisioning.CANCEL_PROVISIONING";

    /**
     * Intent action sent to the {@link ProvisioningService} to indicate that provisioning should be
     * started.
     */
    public static final String ACTION_START_PROVISIONING =
            "com.android.managedprovisioning.START_PROVISIONING";

    /**
     * Intent action sent to the {@link ProvisioningService} to get the latest provisioning state.
     */
    public static final String ACTION_GET_PROVISIONING_STATE =
            "com.android.managedprovisioning.GET_PROVISIONING_STATE";

    // Intents sent by the service to the activity
    /**
     * Intent action sent by the {@link ProvisioningService} to indicate that provisioning has
     * successfully completed.
     */
    public static final String ACTION_PROVISIONING_SUCCESS =
            "com.android.managedprovisioning.provisioning_success";

    /**
     * Intent action sent by the {@link ProvisioningService} to indicate that an error occured.
     */
    public static final String ACTION_PROVISIONING_ERROR =
            "com.android.managedprovisioning.error";

    /**
     * Integer extra added to {@link #ACTION_PROVISIONING_ERROR} that defines the resource id of the
     * user visible error message that should be shown.
     */
    public static final String EXTRA_USER_VISIBLE_ERROR_ID_KEY = "UserVisibleErrorMessage-Id";

    /**
     * Boolean extra added to {@link #ACTION_PROVISIONING_ERROR} that indicates whether a factory
     * reset should be triggered.
     */
    public static final String EXTRA_FACTORY_RESET_REQUIRED = "FactoryResetRequired";

    /**
     * Intent action sent by the {@link ProvisioningService} to indicate that provisioning was
     * cancelled.
     */
    public static final String ACTION_PROVISIONING_CANCELLED =
            "com.android.managedprovisioning.cancelled";

    /**
     * Intent action sent by the {@link ProvisioningService} to indicate that a progress update has
     * occured.
     */
    public static final String ACTION_PROGRESS_UPDATE =
            "com.android.managedprovisioning.progress_update";

    /**
     * Integer extra added to {@link #ACTION_PROGRESS_UPDATE} that defines the resource id of the
     * user visible progress message.
     */
    public static final String EXTRA_PROGRESS_MESSAGE_ID_KEY = "ProgressMessageId";

    private Constants() {
        // Do not instantiate
    }
}
