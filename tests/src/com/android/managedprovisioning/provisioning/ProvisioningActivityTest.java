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

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.Espresso.pressBack;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.rule.ActivityTestRule;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.TestInstrumentationRunner;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link ProvisioningActivity}.
 */
@SmallTest
public class ProvisioningActivityTest {
    private static final ComponentName ADMIN = new ComponentName("com.test.admin", ".Receiver");
    private static final ProvisioningParams PROFILE_OWNER_PARAMS = new ProvisioningParams.Builder()
            .setProvisioningAction(ACTION_PROVISION_MANAGED_PROFILE)
            .setDeviceAdminComponentName(ADMIN)
            .build();
    private static final ProvisioningParams DEVICE_OWNER_PARAMS = new ProvisioningParams.Builder()
            .setProvisioningAction(ACTION_PROVISION_MANAGED_DEVICE)
            .setDeviceAdminComponentName(ADMIN)
            .build();
    private static final Intent PROFILE_OWNER_INTENT = new Intent()
            .putExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS, PROFILE_OWNER_PARAMS);
    private static final Intent DEVICE_OWNER_INTENT = new Intent()
            .putExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS, DEVICE_OWNER_PARAMS);

    @Rule
    public ActivityTestRule<ProvisioningActivity> mActivityRule = new ActivityTestRule<>(
            ProvisioningActivity.class, true /* Initial touch mode  */,
            false /* Lazily launch activity */);

    @Mock private ProvisioningManager mProvisioningManager;
    @Mock private Utils mUtils;
    private static int mRotationLocked;

    @BeforeClass
    public static void setUpClass() {
        // Stop the activity from rotating in order to keep hold of the context
        Context context = InstrumentationRegistry.getTargetContext();

        mRotationLocked = Settings.System.getInt(context.getContentResolver(),
                Settings.System.ACCELEROMETER_ROTATION, 0);
        Settings.System.putInt(context.getContentResolver(),
                Settings.System.ACCELEROMETER_ROTATION, 0);
    }

    @AfterClass
    public static void tearDownClass() {
        // Reset the rotation value back to what it was before the test
        Context context = InstrumentationRegistry.getTargetContext();

        Settings.System.putInt(context.getContentResolver(),
                Settings.System.ACCELEROMETER_ROTATION, mRotationLocked);
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        TestProvisioningActivity.sManager = mProvisioningManager;
        TestProvisioningActivity.sUtils = mUtils;

        TestInstrumentationRunner.registerReplacedActivity(ProvisioningActivity.class,
                TestProvisioningActivity.class);
    }

    @Test
    public void testLaunch() {
        // GIVEN the activity was launched with a profile owner intent
        launchActivityAndWait(PROFILE_OWNER_INTENT);

        // THEN the provisioning process should be initiated
        verify(mProvisioningManager).initiateProvisioning(PROFILE_OWNER_PARAMS);

        // THEN the activity should start listening for provisioning updates
        verify(mProvisioningManager).registerListener(any(ProvisioningManagerCallback.class));
        verifyNoMoreInteractions(mProvisioningManager);
    }

    @Test
    public void testPause() throws Throwable {
        // GIVEN the activity was launched with a profile owner intent
        launchActivityAndWait(PROFILE_OWNER_INTENT);

        // WHEN the activity is paused
        InstrumentationRegistry.getInstrumentation()
                .callActivityOnPause(mActivityRule.getActivity());

        // THEN the listener is unregistered
        verify(mProvisioningManager).unregisterListener(any(ProvisioningManagerCallback.class));
    }

    @Test
    public void testProgressUpdate() throws Throwable {
        // GIVEN the activity was launched with a profile owner intent
        launchActivityAndWait(PROFILE_OWNER_INTENT);
        final int progressMsgId = R.string.progress_initialize;

        // WHEN a progress update was posted
        mActivityRule.runOnUiThread(()
                -> mActivityRule.getActivity().progressUpdate(progressMsgId));

        // THEN the UI should show the progress update
        onView(withId(R.id.prog_text)).check(matches(withText(progressMsgId)));
    }

    @Test
    public void testErrorNoFactoryReset() throws Throwable {
        // GIVEN the activity was launched with a profile owner intent
        launchActivityAndWait(PROFILE_OWNER_INTENT);

        // WHEN an error occurred that does not require factory reset
        final int errorMsgId = R.string.managed_provisioning_error_text;
        mActivityRule.runOnUiThread(() -> mActivityRule.getActivity().error(errorMsgId, false));

        // THEN the UI should show an error dialog
        onView(withText(errorMsgId)).check(matches(isDisplayed()));

        // WHEN clicking ok
        onView(withId(android.R.id.button1))
                .check(matches(withText(R.string.device_owner_error_ok)))
                .perform(click());

        // THEN the activity should be finishing
        assertTrue(mActivityRule.getActivity().isFinishing());
    }

    @Test
    public void testErrorFactoryReset() throws Throwable {
        // GIVEN the activity was launched with a device owner intent
        launchActivityAndWait(DEVICE_OWNER_INTENT);

        // WHEN an error occurred that does not require factory reset
        final int errorMsgId = R.string.managed_provisioning_error_text;
        mActivityRule.runOnUiThread(() -> mActivityRule.getActivity().error(errorMsgId, true));

        // THEN the UI should show an error dialog
        onView(withText(errorMsgId)).check(matches(isDisplayed()));

        // WHEN clicking the ok button that says that factory reset is required
        onView(withId(android.R.id.button1))
                .check(matches(withText(R.string.device_owner_error_reset)))
                .perform(click());

        // THEN factory reset should be invoked
        verify(mUtils).sendFactoryResetBroadcast(any(Context.class), anyString());
    }

    @Test
    public void testCancelProfileOwner() throws Throwable {
        // GIVEN the activity was launched with a profile owner intent
        launchActivityAndWait(PROFILE_OWNER_INTENT);

        // WHEN the user tries to cancel
        pressBack();

        // THEN the cancel dialog should be shown
        onView(withText(R.string.profile_owner_cancel_message)).check(matches(isDisplayed()));

        // WHEN deciding not to cancel
        onView(withId(android.R.id.button2))
                .check(matches(withText(R.string.profile_owner_cancel_cancel)))
                .perform(click());

        // THEN the activity should not be finished
        assertFalse(mActivityRule.getActivity().isFinishing());

        // WHEN the user tries to cancel
        pressBack();

        // THEN the cancel dialog should be shown
        onView(withText(R.string.profile_owner_cancel_message)).check(matches(isDisplayed()));

        // WHEN deciding to cancel
        onView(withId(android.R.id.button1))
                .check(matches(withText(R.string.profile_owner_cancel_ok)))
                .perform(click());

        // THEN the manager should be informed
        verify(mProvisioningManager).cancelProvisioning();

        // THEN the activity should be finished
        assertTrue(mActivityRule.getActivity().isFinishing());
    }

    @Test
    public void testCancelDeviceOwner() throws Throwable {
        // GIVEN the activity was launched with a device owner intent
        launchActivityAndWait(DEVICE_OWNER_INTENT);

        // WHEN the user tries to cancel
        pressBack();

        // THEN the cancel dialog should be shown
        onView(withText(R.string.device_owner_cancel_message)).check(matches(isDisplayed()));

        // WHEN deciding not to cancel
        onView(withId(android.R.id.button2))
                .check(matches(withText(R.string.device_owner_cancel_cancel)))
                .perform(click());

        // THEN the activity should not be finished
        assertFalse(mActivityRule.getActivity().isFinishing());

        // WHEN the user tries to cancel
        pressBack();

        // THEN the cancel dialog should be shown
        onView(withText(R.string.device_owner_cancel_message)).check(matches(isDisplayed()));

        // WHEN deciding to cancel
        onView(withId(android.R.id.button1))
                .check(matches(withText(R.string.device_owner_error_reset)))
                .perform(click());

        // THEN factory reset should be invoked
        verify(mUtils).sendFactoryResetBroadcast(any(Context.class), anyString());
    }

    @Test
    public void testSuccess() throws Throwable {
        // GIVEN the activity was launched with a profile owner intent
        launchActivityAndWait(PROFILE_OWNER_INTENT);

        // WHEN provisioning completes successfully
        mActivityRule.runOnUiThread(()
                -> mActivityRule.getActivity().provisioningTasksCompleted());

        // THEN preFinalization should be invoked
        verify(mProvisioningManager).preFinalize();

        // WHEN preFinalization is completed
        mActivityRule.runOnUiThread(() -> mActivityRule.getActivity().preFinalizationCompleted());

        // THEN the activity should finish
        assertTrue(mActivityRule.getActivity().isFinishing());
    }

    private void launchActivityAndWait(Intent intent) {
        mActivityRule.launchActivity(intent);
        onView(withId(R.id.setup_wizard_layout));
    }
}
