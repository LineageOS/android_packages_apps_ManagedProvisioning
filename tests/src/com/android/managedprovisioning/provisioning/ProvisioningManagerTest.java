/*
 * Copyright 2016, The Android Open Source Project
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

package com.android.managedprovisioning.provisioning;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import com.android.managedprovisioning.analytics.ProvisioningAnalyticsTracker;
import com.android.managedprovisioning.model.ProvisioningParams;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;

/**
 * Unit tests for {@link ProvisioningManager}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class ProvisioningManagerTest {
    private final int TEST_PROGRESS_ID = 123;
    private final int TEST_ERROR_ID = 456;
    private final boolean TEST_FACTORY_RESET_REQUIRED = true;
    private final ComponentName TEST_ADMIN = new ComponentName("com.test.admin", ".AdminReceiver");
    private final ProvisioningParams TEST_PARAMS = new ProvisioningParams.Builder()
            .setProvisioningAction(ACTION_PROVISION_MANAGED_PROFILE)
            .setDeviceAdminComponentName(TEST_ADMIN)
            .build();

    @Mock private Context mContext;
    @Mock private ProvisioningControllerFactory mFactory;
    @Mock private ProvisioningAnalyticsTracker mAnalyticsTracker;
    @Mock private Handler mUiHandler;
    @Mock private ProvisioningManagerCallback mCallback;
    @Mock private AbstractProvisioningController mController;

    private ProvisioningManager mManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        // Immediately execute any message that is sent onto the handler
        when(mUiHandler.sendMessageAtTime(any(Message.class), anyLong())).thenAnswer(
                (InvocationOnMock invocation) ->
                    {
                        Message msg = (Message) invocation.getArguments()[0];
                        msg.getCallback().run();
                        return null;
                    });
        mManager = new ProvisioningManager(mContext, mUiHandler, mFactory, mAnalyticsTracker);
        when(mFactory.createProvisioningController(mContext, TEST_PARAMS, mManager))
                .thenReturn(mController);
    }

    @Test
    public void testInitiateProvisioning() {
        // GIVEN that provisioning is not currently running
        // WHEN calling initiateProvisioning
        mManager.initiateProvisioning(TEST_PARAMS);

        // THEN the newly created provisioning controller should be initialized
        verify(mController).initialize();

        // WHEN calling initiateProvisioning again
        mManager.initiateProvisioning(TEST_PARAMS);

        // THEN nothing should happen, because provisioning is already ongoing
        verifyNoMoreInteractions(mController);
    }

    @Test
    public void testStartProvisioning() {
        // GIVEN that provisioning has been initialized
        mManager.initiateProvisioning(TEST_PARAMS);
        final Looper looper = Looper.getMainLooper();

        // WHEN calling start provisioning
        mManager.startProvisioning(looper);

        // THEN the controller should be started
        verify(mController).start(looper);
    }

    @Test
    public void testCancelProvisioning() {
        // GIVEN that provisioning has been initialized and started
        mManager.initiateProvisioning(TEST_PARAMS);
        final Looper looper = Looper.getMainLooper();
        mManager.startProvisioning(looper);

        // WHEN cancelling provisioning
        mManager.cancelProvisioning();

        // THEN the controller should be cancelled
        verify(mController).cancel();
    }

    @Test
    public void testPreFinalizeProvisioning() {
        // GIVEN that provisioning has been initialized and started
        mManager.initiateProvisioning(TEST_PARAMS);
        final Looper looper = Looper.getMainLooper();
        mManager.startProvisioning(looper);

        // WHEN prefinalizing provisioning
        mManager.preFinalize();

        // THEN the controller should be prefinalized
        verify(mController).preFinalize();
    }

    @Test
    public void testRegisterListener_noProgress() {
        // GIVEN no progress has previously been achieved
        // WHEN a listener is registered
        mManager.registerListener(mCallback);

        // THEN no callback should be given
        verifyZeroInteractions(mCallback);

        // WHEN a progress callback was made
        mManager.progressUpdate(TEST_PROGRESS_ID);

        // THEN the listener should receive a callback
        verify(mCallback).progressUpdate(TEST_PROGRESS_ID);
        verifyNoMoreInteractions(mCallback);
    }

    @Test
    public void testListener_progress() {
        // GIVEN a listener is registered
        mManager.registerListener(mCallback);
        // WHEN some progress has occurred previously
        mManager.progressUpdate(TEST_PROGRESS_ID);
        // THEN the listener should receive a callback
        verify(mCallback).progressUpdate(TEST_PROGRESS_ID);

        // WHEN the listener is unregistered and registered again
        mManager.unregisterListener(mCallback);
        mManager.registerListener(mCallback);
        // THEN the listener should receive a callback again
        verify(mCallback, times(2)).progressUpdate(TEST_PROGRESS_ID);
        verifyNoMoreInteractions(mCallback);
    }

    @Test
    public void testListener_error() {
        // GIVEN a listener is registered
        mManager.registerListener(mCallback);
        // WHEN some progress has occurred previously
        mManager.error(TEST_ERROR_ID, TEST_FACTORY_RESET_REQUIRED);
        // THEN the listener should receive a callback
        verify(mCallback).error(TEST_ERROR_ID, TEST_FACTORY_RESET_REQUIRED);

        // WHEN the listener is unregistered and registered again
        mManager.unregisterListener(mCallback);
        mManager.registerListener(mCallback);
        // THEN the listener should receive a callback again
        verify(mCallback, times(2)).error(TEST_ERROR_ID, TEST_FACTORY_RESET_REQUIRED);
        verifyNoMoreInteractions(mCallback);
    }

    @Test
    public void testListener_tasksCompleted() {
        // GIVEN a listener is registered
        mManager.registerListener(mCallback);
        // WHEN some progress has occurred previously
        mManager.provisioningTasksCompleted();
        // THEN the listener should receive a callback
        verify(mCallback).provisioningTasksCompleted();

        // WHEN the listener is unregistered and registered again
        mManager.unregisterListener(mCallback);
        mManager.registerListener(mCallback);
        // THEN the listener should receive a callback again
        verify(mCallback, times(2)).provisioningTasksCompleted();
        verifyNoMoreInteractions(mCallback);
    }

    @Test
    public void testListener_cleanupCompleted() {
        // GIVEN a listener is registered
        mManager.registerListener(mCallback);
        // WHEN some progress has occurred previously
        mManager.cleanUpCompleted();
        // THEN the listener should receive a callback
        verify(mCallback).cleanUpCompleted();

        // WHEN the listener is unregistered and registered again
        mManager.unregisterListener(mCallback);
        mManager.registerListener(mCallback);
        // THEN the listener should receive a callback again
        verify(mCallback, times(2)).cleanUpCompleted();
        verifyNoMoreInteractions(mCallback);
    }

    @Test
    public void testListener_preFinalizationCompleted() {
        // GIVEN a listener is registered
        mManager.registerListener(mCallback);
        // WHEN some progress has occurred previously
        mManager.preFinalizationCompleted();
        // THEN the listener should receive a callback
        verify(mCallback).preFinalizationCompleted();

        // WHEN the listener is unregistered and registered again
        mManager.unregisterListener(mCallback);
        mManager.registerListener(mCallback);
        // THEN the listener should receive a callback again
        verify(mCallback, times(2)).preFinalizationCompleted();
        verifyNoMoreInteractions(mCallback);
    }

    @Test
    public void testUnregisterListener() {
        // GIVEN a register had previously been registered and then unregistered
        mManager.registerListener(mCallback);
        mManager.unregisterListener(mCallback);

        // WHEN a progress callback was made
        mManager.progressUpdate(TEST_PROGRESS_ID);

        // THEN the listener should not receive a callback
        verifyZeroInteractions(mCallback);
    }
}
