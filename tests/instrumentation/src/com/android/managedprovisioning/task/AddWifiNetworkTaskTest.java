/*
 * Copyright 2017, The Android Open Source Project
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

package com.android.managedprovisioning.task;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.support.test.filters.SmallTest;

import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.task.wifi.NetworkMonitor;
import com.android.managedprovisioning.task.wifi.WifiConfigurationProvider;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit-tests for {@link AddWifiNetworkTask}.
 */
@SmallTest
public class AddWifiNetworkTaskTest {

    private static final int TEST_USER_ID = 123;
    private static final ComponentName ADMIN = new ComponentName("com.test.admin", ".Receiver");
    private static final ProvisioningParams NO_WIFI_INFO_PARAMS = new ProvisioningParams.Builder()
            .setProvisioningAction(ACTION_PROVISION_MANAGED_PROFILE)
            .setDeviceAdminComponentName(ADMIN)
            .setWifiInfo(null)
            .build();

    @Mock private Context mContext;
    @Mock private WifiManager mWifiManager;
    @Mock private NetworkMonitor mNetworkMonitor;
    @Mock private WifiConfigurationProvider mWifiConfigurationProvider;
    @Mock private AbstractProvisioningTask.Callback mCallback;
    private AddWifiNetworkTask mTask;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(mContext.getSystemService(Context.WIFI_SERVICE)).thenReturn(mWifiManager);
    }

    @Test
    public void testNoWifiInfo() {
        // GIVEN that no wifi info was passed in the parameter
        mTask = new AddWifiNetworkTask(mNetworkMonitor, mWifiConfigurationProvider, mContext,
                NO_WIFI_INFO_PARAMS, mCallback);

        // WHEN running the task
        mTask.run(TEST_USER_ID);

        // THEN success should be called
        verify(mCallback).onSuccess(mTask);
    }
}
