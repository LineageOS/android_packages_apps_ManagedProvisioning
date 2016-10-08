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

package com.android.managedprovisioning.task;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_USER;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.content.pm.ParceledListSlice;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.os.RemoteException;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.test.mock.MockPackageManager;
import android.view.inputmethod.InputMethodInfo;

import com.android.internal.view.IInputMethodManager;
import com.android.managedprovisioning.model.ProvisioningParams;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SmallTest
public class NonRequiredAppsHelperTest {
    private static final String TEST_DPC_PACKAGE_NAME = "dpc.package.name";
    private static final int TEST_USER_ID = 123;

    private @Mock Resources mResources;
    private @Mock IPackageManager mIPackageManager;
    private @Mock IInputMethodManager mIInputMethodManager;
    private @Mock Context mTestContext;

    private FakePackageManager mPackageManager;
    private String[] mSystemAppsWithLauncher;
    private NonRequiredAppsHelper mHelper;
    private File mSystemAppsTestFile;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mPackageManager = new FakePackageManager();
        when(mTestContext.getResources()).thenReturn(mResources);
        when(mTestContext.getPackageManager()).thenReturn(mPackageManager);
        when(mTestContext.getFilesDir()).thenReturn(
                InstrumentationRegistry.getTargetContext().getCacheDir());

        setSystemInputMethods();
        setRequiredAppsManagedDevice();
        setVendorRequiredAppsManagedDevice();
        setDisallowedAppsManagedDevice();
        setVendorDisallowedAppsManagedDevice();
        setRequiredAppsManagedProfile();
        setVendorRequiredAppsManagedProfile();
        setDisallowedAppsManagedProfile();
        setVendorDisallowedAppsManagedProfile();
        setRequiredAppsManagedUser();
        setVendorRequiredAppsManagedUser();
        setDisallowedAppsManagedUser();
        setVendorDisallowedAppsManagedUser();
        mSystemAppsTestFile = NonRequiredAppsHelper.getSystemAppsFile(mTestContext, TEST_USER_ID);
    }

    @After
    public void tearDown() {
        if (mSystemAppsTestFile.exists()) {
            mSystemAppsTestFile.delete();
        }
    }

    @Test
    public void testAppsWithLauncherAreNonRequiredByDefault() {
        setSystemAppsWithLauncher("app.a", "app.b");
        setInstalledSystemApps("app.a", "app.b");

        buildHelper(ACTION_PROVISION_MANAGED_DEVICE, true, false);
        verifyAppsAreNonRequired("app.a", "app.b");
        verifyNewSystemApps("app.a", "app.b");
    }

    @Test
    public void testDeviceOwnerRequiredApps() {
        setSystemAppsWithLauncher("app.a", "app.b", "app.c");
        setInstalledSystemApps("app.a", "app.b", "app.c");
        setRequiredAppsManagedDevice("app.a");
        setVendorRequiredAppsManagedDevice("app.b");

        buildHelper(ACTION_PROVISION_MANAGED_DEVICE, true, false);
        verifyAppsAreNonRequired("app.c");
        verifyNewSystemApps("app.a", "app.b", "app.c");
    }

    @Test
    public void testProfileOwnerRequiredApps() {
        setSystemAppsWithLauncher("app.a", "app.b", "app.c");
        setInstalledSystemApps("app.a", "app.b", "app.c");
        setRequiredAppsManagedProfile("app.a");
        setVendorRequiredAppsManagedProfile("app.b");

        buildHelper(ACTION_PROVISION_MANAGED_PROFILE, true, false);
        verifyAppsAreNonRequired("app.c");
        verifyNewSystemApps("app.a", "app.b", "app.c");
    }

    @Test
    public void testManagedUserRequiredApps() {
        setSystemAppsWithLauncher("app.a", "app.b", "app.c");
        setInstalledSystemApps("app.a", "app.b", "app.c");
        setRequiredAppsManagedUser("app.a");
        setVendorRequiredAppsManagedUser("app.b");

        buildHelper(ACTION_PROVISION_MANAGED_USER, true, false);
        verifyAppsAreNonRequired("app.c");
        verifyNewSystemApps("app.a", "app.b", "app.c");
    }

    @Test
    public void testDpcIsRequired() {
        setSystemAppsWithLauncher("app.a", TEST_DPC_PACKAGE_NAME);
        setInstalledSystemApps("app.a", TEST_DPC_PACKAGE_NAME);

        buildHelper(ACTION_PROVISION_MANAGED_DEVICE, true, false);
        verifyAppsAreNonRequired("app.a");
        verifyNewSystemApps("app.a", TEST_DPC_PACKAGE_NAME);
    }

    @Test
    public void testDisallowedAppsAreNonRequiredEvenIfNoLauncher() {
        setSystemAppsWithLauncher();
        setInstalledSystemApps("app.a", "app.b");
        setDisallowedAppsManagedDevice("app.a");
        setVendorDisallowedAppsManagedDevice("app.b");

        buildHelper(ACTION_PROVISION_MANAGED_DEVICE, true, false);
        verifyAppsAreNonRequired("app.a", "app.b");
        verifyNewSystemApps("app.a", "app.b");
    }

    @Test
    public void testDeviceOwnerImesAreRequired() {
        setSystemAppsWithLauncher("app.a", "app.b");
        setSystemInputMethods("app.a");
        setInstalledSystemApps("app.a", "app.b");

        buildHelper(ACTION_PROVISION_MANAGED_DEVICE, true, false);
        verifyAppsAreNonRequired("app.b");
        verifyNewSystemApps("app.a", "app.b");
    }

    @Test
    public void testProfileOwnerImesAreNonRequired() {
        setSystemAppsWithLauncher("app.a", "app.b");
        setSystemInputMethods("app.a");
        setInstalledSystemApps("app.a", "app.b");

        buildHelper(ACTION_PROVISION_MANAGED_PROFILE, true, false);
        verifyAppsAreNonRequired("app.a", "app.b");
        verifyNewSystemApps("app.a", "app.b");
    }

    @Test
    public void testManagedUserImesAreRequired() {
        setSystemAppsWithLauncher("app.a", "app.b");
        setSystemInputMethods("app.a");
        setInstalledSystemApps("app.a", "app.b");

        buildHelper(ACTION_PROVISION_MANAGED_USER, true, false);
        verifyAppsAreNonRequired("app.b");
        verifyNewSystemApps("app.a", "app.b");
    }

    @Test
    public void testDisallowedAppsAreNonInstalled() {
        setSystemAppsWithLauncher("app.a");
        setInstalledSystemApps("app.a", "app.b");
        setDisallowedAppsManagedDevice("app.c");

        buildHelper(ACTION_PROVISION_MANAGED_DEVICE, true, false);
        verifyAppsAreNonRequired("app.a", "app.c");
        verifyNewSystemApps("app.a", "app.b");
    }

    @Test
    public void testLeaveAllSystemAppsEnabled() {
        setSystemAppsWithLauncher("app.a", "app.b");
        setInstalledSystemApps("app.a", "app.b");

        buildHelper(ACTION_PROVISION_MANAGED_DEVICE, true, true);
        verifyAppsAreNonRequired();
        verifyNewSystemApps("app.a", "app.b");
    }

    @Test
    public void testAfterOta() {
        setSystemAppsWithLauncher("app.a");
        setInstalledSystemApps("app.a", "app.b");
        setDisallowedAppsManagedDevice("app.c");

        buildHelper(ACTION_PROVISION_MANAGED_DEVICE, true, false);
        verifyAppsAreNonRequired("app.a", "app.c");
        verifyNewSystemApps("app.a", "app.b");

        setSystemAppsWithLauncher();
        setInstalledSystemApps("app.b", "app.c");
        buildHelper(ACTION_PROVISION_MANAGED_DEVICE, false, false);
        verifyAppsAreNonRequired("app.c");
        verifyNewSystemApps("app.c");
    }

    @Test
    public void testWhenSystemAppsWithNoSystemAppsFile() {
        setSystemAppsWithLauncher("app.a", "app.b");
        setInstalledSystemApps("app.a", "app.b");
        // Now, we set a wrong value to mTestContext.getFilesDir. So it should not find the
        // system apps file.
        when(mTestContext.getFilesDir()).thenReturn(new File(""));

        buildHelper(ACTION_PROVISION_MANAGED_DEVICE, false, false);
        verifyAppsAreNonRequired("app.a", "app.b");
        verifyNewSystemApps(null);
    }

    @Test
    public void testReadWriteSystemApps() {
        buildHelper(ACTION_PROVISION_MANAGED_DEVICE, true, false);
        final Set<String> systemTestApps = setFromArray("app.a", "app.b", "app.c");
        mHelper.writeSystemApps(systemTestApps, mSystemAppsTestFile);
        assertEquals(systemTestApps, mHelper.readSystemApps(mSystemAppsTestFile));
    }

    private void verifyAppsAreNonRequired(String... appArray) {
        assertEquals(setFromArray(appArray), mHelper.getNonRequiredApps(TEST_USER_ID));
    }

    private void verifyNewSystemApps(String... appArray) {
        assertEquals(setFromArray(appArray), mHelper.getNewSystemApps(TEST_USER_ID));
    }

    private void buildHelper(String action, boolean newProfile, boolean leaveAllSystemAppsEnabled) {
        ProvisioningParams params = new ProvisioningParams.Builder()
                .setProvisioningAction(action)
                .setDeviceAdminPackageName(TEST_DPC_PACKAGE_NAME)
                .setLeaveAllSystemAppsEnabled(leaveAllSystemAppsEnabled)
                .build();
        mHelper = new NonRequiredAppsHelper(mTestContext, params,
                mIPackageManager, mIInputMethodManager, newProfile);
    }

    private void setRequiredAppsManagedDevice(String... apps) {
        setStringArray(com.android.managedprovisioning.R.array.required_apps_managed_device,
                apps);
    }

    private void setVendorRequiredAppsManagedDevice(String... apps) {
        setStringArray(com.android.managedprovisioning.R.array.vendor_required_apps_managed_device,
                apps);
    }

    private void setDisallowedAppsManagedDevice(String... apps) {
        setStringArray(com.android.managedprovisioning.R.array.disallowed_apps_managed_device,
                apps);
    }

    private void setVendorDisallowedAppsManagedDevice(String... apps) {
        setStringArray(
                com.android.managedprovisioning.R.array.vendor_disallowed_apps_managed_device,
                apps);
    }

    private void setRequiredAppsManagedProfile(String... apps) {
        setStringArray(com.android.managedprovisioning.R.array.required_apps_managed_profile,
                apps);
    }

    private void setVendorRequiredAppsManagedProfile(String... apps) {
        setStringArray(com.android.managedprovisioning.R.array.vendor_required_apps_managed_profile,
                apps);
    }

    private void setDisallowedAppsManagedProfile(String... apps) {
        setStringArray(com.android.managedprovisioning.R.array.disallowed_apps_managed_profile,
                apps);
    }

    private void setVendorDisallowedAppsManagedProfile(String... apps) {
        setStringArray(
                com.android.managedprovisioning.R.array.vendor_disallowed_apps_managed_profile,
                apps);
    }

    private void setRequiredAppsManagedUser(String... apps) {
        setStringArray(com.android.managedprovisioning.R.array.required_apps_managed_user,
                apps);
    }

    private void setVendorRequiredAppsManagedUser(String... apps) {
        setStringArray(com.android.managedprovisioning.R.array.vendor_required_apps_managed_user,
                apps);
    }

    private void setDisallowedAppsManagedUser(String... apps) {
        setStringArray(com.android.managedprovisioning.R.array.disallowed_apps_managed_user,
                apps);
    }

    private void setVendorDisallowedAppsManagedUser(String... apps) {
        setStringArray(
                com.android.managedprovisioning.R.array.vendor_disallowed_apps_managed_user,
                apps);
    }

    private void setStringArray(int resourceId, String[] strs) {
        when(mResources.getStringArray(eq(resourceId)))
                .thenReturn(strs);
    }

    private void setInstalledSystemApps(String... installedSystemApps) {
        List<ApplicationInfo> applications = new ArrayList<ApplicationInfo>();
        for (String app : installedSystemApps) {
            ApplicationInfo aInfo = new ApplicationInfo();
            aInfo.flags = ApplicationInfo.FLAG_SYSTEM;
            aInfo.packageName = app;
            applications.add(aInfo);
        }
        try {
            when(mIPackageManager.getInstalledApplications(PackageManager.GET_UNINSTALLED_PACKAGES,
                    TEST_USER_ID)).thenReturn(new ParceledListSlice(applications));
        } catch (RemoteException e) {
            fail(e.toString());
        }
    }

    private void setSystemInputMethods(String... packageNames) {
        List<InputMethodInfo> inputMethods = new ArrayList<InputMethodInfo>();
        for (String packageName : packageNames) {
            ApplicationInfo aInfo = new ApplicationInfo();
            aInfo.flags = ApplicationInfo.FLAG_SYSTEM;
            ServiceInfo serviceInfo = new ServiceInfo();
            serviceInfo.applicationInfo = aInfo;
            serviceInfo.packageName = packageName;
            serviceInfo.name = "";
            ResolveInfo ri = new ResolveInfo();
            ri.serviceInfo = serviceInfo;
            InputMethodInfo inputMethodInfo = new InputMethodInfo(ri, false, null, null, 0, false);
            inputMethods.add(inputMethodInfo);
        }
        try {
            when(mIInputMethodManager.getInputMethodList()).thenReturn(inputMethods);
        } catch (RemoteException e) {
            fail(e.toString());
        }
    }

    private void setSystemAppsWithLauncher(String... apps) {
        mSystemAppsWithLauncher = apps;
    }

    private <T> Set<T> setFromArray(T... array) {
        if (array == null) {
            return null;
        }
        return new HashSet<T>(Arrays.asList(array));
    }

    class FakePackageManager extends MockPackageManager {
        @Override
        public List<ResolveInfo> queryIntentActivitiesAsUser(Intent intent, int flags, int userId) {
            assertTrue("Expected an intent with action ACTION_MAIN",
                    Intent.ACTION_MAIN.equals(intent.getAction()));
            assertEquals("Expected an intent with category CATEGORY_LAUNCHER",
                    setFromArray(Intent.CATEGORY_LAUNCHER), intent.getCategories());
            assertTrue("Expected the flag MATCH_UNINSTALLED_PACKAGES",
                    (flags & PackageManager.MATCH_UNINSTALLED_PACKAGES) != 0);
            assertTrue("Expected the flag MATCH_DISABLED_COMPONENTS",
                    (flags & PackageManager.MATCH_DISABLED_COMPONENTS) != 0);
            assertTrue("Expected the flag MATCH_ENCRYPTION_AWARE_AND_UNAWARE",
                    (flags & PackageManager.MATCH_ENCRYPTION_AWARE_AND_UNAWARE) != 0);
            assertEquals(userId, TEST_USER_ID);
            List<ResolveInfo> result = new ArrayList<>();
            if (mSystemAppsWithLauncher == null) {
                return result;
            }
            for (String packageName : mSystemAppsWithLauncher) {
                ActivityInfo ai = new ActivityInfo();
                ai.packageName = packageName;
                ResolveInfo ri = new ResolveInfo();
                ri.activityInfo  = ai;
                result.add(ri);
            }
            return result;
        }
    }
}