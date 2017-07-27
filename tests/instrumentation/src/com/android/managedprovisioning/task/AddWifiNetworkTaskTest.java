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
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.support.test.filters.SmallTest;

import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.model.WifiInfo;
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
    private static final String TEST_SSID = "TEST_SSID";
    private static final ProvisioningParams NO_WIFI_INFO_PARAMS = new ProvisioningParams.Builder()
            .setProvisioningAction(ACTION_PROVISION_MANAGED_PROFILE)
            .setDeviceAdminComponentName(ADMIN)
            .setWifiInfo(null)
            .build();
    private static final ProvisioningParams WIFI_INFO_PARAMS = new ProvisioningParams.Builder()
            .setProvisioningAction(ACTION_PROVISION_MANAGED_PROFILE)
            .setDeviceAdminComponentName(ADMIN)
            .setWifiInfo(new WifiInfo.Builder().setSsid(TEST_SSID).build())
            .build();

    @Mock private Context mContext;
    @Mock private ConnectivityManager mConnectivityManager;
    @Mock private WifiManager mWifiManager;
    @Mock private NetworkMonitor mNetworkMonitor;
    @Mock private WifiConfigurationProvider mWifiConfigurationProvider;
    @Mock private AbstractProvisioningTask.Callback mCallback;
    @Mock private Utils mUtils;
    @Mock private android.net.wifi.WifiInfo mWifiInfo;
    private AddWifiNetworkTask mTask;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(mContext.getSystemService(Context.WIFI_SERVICE)).thenReturn(mWifiManager);
        when(mContext.getSystemService(Context.CONNECTIVITY_SERVICE))
                .thenReturn(mConnectivityManager);
    }

    @Test
    public void testNoWifiInfo() {
        // GIVEN that no wifi info was passed in the parameter
        mTask = new AddWifiNetworkTask(mNetworkMonitor, mWifiConfigurationProvider, mContext,
                NO_WIFI_INFO_PARAMS, mCallback, mUtils);

        // WHEN running the task
        mTask.run(TEST_USER_ID);

        // THEN success should be called
        verify(mCallback).onSuccess(mTask);
    }

    @Test
    public void testWifiManagerNull() {
        // GIVEN that wifi info was passed in the parameter
        mTask = new AddWifiNetworkTask(mNetworkMonitor, mWifiConfigurationProvider, mContext,
                WIFI_INFO_PARAMS, mCallback, mUtils);

        // GIVEN that mWifiManager is null
        when(mContext.getSystemService(Context.WIFI_SERVICE)).thenReturn(null);

        // WHEN running the task
        mTask.run(TEST_USER_ID);

        // THEN error should be called
        verify(mCallback).onError(mTask, 0);
    }

    @Test
    public void testFailToEnableWifi() {
        // GIVEN that wifi info was passed in the parameter
        mTask = new AddWifiNetworkTask(mNetworkMonitor, mWifiConfigurationProvider, mContext,
                WIFI_INFO_PARAMS, mCallback, mUtils);

        // GIVEN that wifi is not enabled
        when(mWifiManager.isWifiEnabled()).thenReturn(false);

        // GIVEN that enabling wifi failed
        when(mWifiManager.setWifiEnabled(true)).thenReturn(false);

        // WHEN running the task
        mTask.run(TEST_USER_ID);

        // THEN error should be called
        verify(mCallback).onError(mTask, 0);
    }

    @Test
    public void testIsConnectedToSpecifiedWifiTrue() {
        // GIVEN that wifi info was passed in the parameter
        mTask = new AddWifiNetworkTask(mNetworkMonitor, mWifiConfigurationProvider, mContext,
                WIFI_INFO_PARAMS, mCallback, mUtils);

        // GIVEN that wifi is enabled
        when(mWifiManager.isWifiEnabled()).thenReturn(true);

        // GIVEN connected to wifi
        when(mUtils.isConnectedToWifi(mContext)).thenReturn(true);

        // GIVEN the connected SSID is the same as the wifi param
        when(mWifiManager.getConnectionInfo()).thenReturn(mWifiInfo);
        when(mWifiInfo.getSSID()).thenReturn(TEST_SSID);

        // WHEN running the task
        mTask.run(TEST_USER_ID);

        // THEN success should be called
        verify(mCallback).onSuccess(mTask);
    }
}
