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

package com.android.managedprovisioning.common;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.OperationCanceledException;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PackageInfo;
import android.content.pm.ParceledListSlice;
import android.os.Handler;
import android.os.UserHandle;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit-tests for {@link Utils}.
 */
@SmallTest
public class UtilsTest extends AndroidTestCase {
    private static final String TEST_PACKAGE_NAME_1 = "com.test.packagea";
    private static final String TEST_PACKAGE_NAME_2 = "com.test.packageb";
    private static final ComponentName TEST_COMPONENT_NAME = new ComponentName(TEST_PACKAGE_NAME_1,
            ".MainActivity");
    private static final int TEST_USER_ID = 10;

    @Mock private Context mockContext;
    @Mock private AccountManager mockAccountManager;
    @Mock private IPackageManager mockIPackageManager;
    @Mock private PackageManager mockPackageManager;

    private Utils mUtils;

    @Override
    public void setUp() {
        // this is necessary for mockito to work
        System.setProperty("dexmaker.dexcache", getContext().getCacheDir().toString());

        MockitoAnnotations.initMocks(this);

        when(mockContext.getSystemService(Context.ACCOUNT_SERVICE)).thenReturn(mockAccountManager);
        when(mockContext.getPackageManager()).thenReturn(mockPackageManager);

        mUtils = new Utils();
    }

    public void testGetCurrentSystemApps() throws Exception {
        // GIVEN two currently installed apps, one of which is system
        List<ApplicationInfo> appList = Arrays.asList(
                createApplicationInfo(TEST_PACKAGE_NAME_1, false),
                createApplicationInfo(TEST_PACKAGE_NAME_2, true));
        when(mockIPackageManager.getInstalledApplications(
                PackageManager.GET_UNINSTALLED_PACKAGES, TEST_USER_ID))
                .thenReturn(new ParceledListSlice(appList));
        // WHEN requesting the current system apps
        Set<String> res = mUtils.getCurrentSystemApps(mockIPackageManager, TEST_USER_ID);
        // THEN the one system app should be returned
        assertEquals(1, res.size());
        assertTrue(res.contains(TEST_PACKAGE_NAME_2));
    }

    public void testDisableComponent() throws Exception {
        // GIVEN a component name and a user id
        // WHEN disabling a component
        mUtils.disableComponent(mockIPackageManager, TEST_COMPONENT_NAME, TEST_USER_ID);
        // THEN the correct method on mockIPackageManager gets invoked
        verify(mockIPackageManager).setComponentEnabledSetting(eq(TEST_COMPONENT_NAME),
                eq(PackageManager.COMPONENT_ENABLED_STATE_DISABLED),
                eq(PackageManager.DONT_KILL_APP),
                eq(TEST_USER_ID));
        verifyNoMoreInteractions(mockIPackageManager);
    }

    public void testPackageRequiresUpdate_notPresent() throws Exception {
        // GIVEN that the requested package is not present on the device
        // WHEN checking whether an update is required
        when(mockPackageManager.getPackageInfo(TEST_PACKAGE_NAME_1, 0))
                .thenThrow(new NameNotFoundException());
        // THEN an update is required
        assertTrue(mUtils.packageRequiresUpdate(TEST_PACKAGE_NAME_1, 0, mockContext));
    }

    public void testPackageRequiresUpdate() throws Exception {
        // GIVEN a package that is installed on the device
        PackageInfo pi = new PackageInfo();
        pi.packageName = TEST_PACKAGE_NAME_1;
        pi.versionCode = 1;
        when(mockPackageManager.getPackageInfo(TEST_PACKAGE_NAME_1, 0)).thenReturn(pi);
        // WHEN checking whether an update is required
        // THEN verify that update required returns the correct result depending on the minimum
        // version code requested.
        assertFalse(mUtils.packageRequiresUpdate(TEST_PACKAGE_NAME_1, 0, mockContext));
        assertFalse(mUtils.packageRequiresUpdate(TEST_PACKAGE_NAME_1, 1, mockContext));
        assertTrue(mUtils.packageRequiresUpdate(TEST_PACKAGE_NAME_1, 2, mockContext));
    }

    public void testMaybeCopyAccount_success() throws Exception {
        // GIVEN an account on the primary user and a managed profile present and no timeout
        // or error during migration
        Account testAccount = new Account("test@afw-test.com", "com.google");
        UserHandle primaryUser = UserHandle.of(0);
        UserHandle managedProfile = UserHandle.of(10);

        AccountManagerFuture mockResult = mock(AccountManagerFuture.class);
        when(mockResult.getResult(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(mockAccountManager.copyAccountToUser(any(Account.class), any(UserHandle.class),
                any(UserHandle.class), any(AccountManagerCallback.class), any(Handler.class)))
                .thenReturn(mockResult);

        // WHEN copying the account from the primary user to the managed profile
        // THEN the account migration succeeds
        assertTrue(mUtils.maybeCopyAccount(mockContext, testAccount, primaryUser, managedProfile));
        verify(mockAccountManager).copyAccountToUser(eq(testAccount), eq(primaryUser),
                eq(managedProfile), any(AccountManagerCallback.class), any(Handler.class));
        verify(mockResult).getResult(anyLong(), any(TimeUnit.class));
    }

    public void testMaybeCopyAccount_error() throws Exception {
        // GIVEN an account on the primary user and a managed profile present and an error occurs
        // during migration
        Account testAccount = new Account("test@afw-test.com", "com.google");
        UserHandle primaryUser = UserHandle.of(0);
        UserHandle managedProfile = UserHandle.of(10);

        AccountManagerFuture mockResult = mock(AccountManagerFuture.class);
        when(mockResult.getResult(anyLong(), any(TimeUnit.class))).thenReturn(false);
        when(mockAccountManager.copyAccountToUser(any(Account.class), any(UserHandle.class),
                any(UserHandle.class), any(AccountManagerCallback.class), any(Handler.class)))
                .thenReturn(mockResult);

        // WHEN copying the account from the primary user to the managed profile
        // THEN the account migration fails
        assertFalse(mUtils.maybeCopyAccount(mockContext, testAccount, primaryUser, managedProfile));
        verify(mockAccountManager).copyAccountToUser(eq(testAccount), eq(primaryUser),
                eq(managedProfile), any(AccountManagerCallback.class), any(Handler.class));
        verify(mockResult).getResult(anyLong(), any(TimeUnit.class));
    }

    public void testMaybeCopyAccount_timeout() throws Exception {
        // GIVEN an account on the primary user and a managed profile present and a timeout occurs
        // during migration
        Account testAccount = new Account("test@afw-test.com", "com.google");
        UserHandle primaryUser = UserHandle.of(0);
        UserHandle managedProfile = UserHandle.of(10);

        AccountManagerFuture mockResult = mock(AccountManagerFuture.class);
        when(mockResult.getResult(anyLong(), any(TimeUnit.class)))
                .thenThrow(new OperationCanceledException());
        when(mockAccountManager.copyAccountToUser(any(Account.class), any(UserHandle.class),
                any(UserHandle.class), any(AccountManagerCallback.class), any(Handler.class)))
                .thenReturn(mockResult);

        // WHEN copying the account from the primary user to the managed profile
        // THEN the account migration fails
        assertFalse(mUtils.maybeCopyAccount(mockContext, testAccount, primaryUser, managedProfile));
        verify(mockAccountManager).copyAccountToUser(eq(testAccount), eq(primaryUser),
                eq(managedProfile), any(AccountManagerCallback.class), any(Handler.class));
        verify(mockResult).getResult(anyLong(), any(TimeUnit.class));
    }

    public void testMaybeCopyAccount_nullAccount() {
        // GIVEN a device with a managed profile present
        UserHandle primaryUser = UserHandle.of(0);
        UserHandle managedProfile = UserHandle.of(10);

        // WHEN trying to copy a null account from the primary user to the managed profile
        // THEN request is ignored
        assertFalse(mUtils.maybeCopyAccount(mockContext, null /* accountToMigrate */, primaryUser,
                managedProfile));
        verifyZeroInteractions(mockAccountManager);
    }

    public void testMaybeCopyAccount_sameUser() {
        // GIVEN an account on the primary user and no managed profile
        Account testAccount = new Account("test@afw-test.com", "com.google");
        UserHandle primaryUser = UserHandle.of(0);

        // WHEN trying to invoke copying an account from the primary to the primary user
        // THEN request is ignored
        assertFalse(mUtils.maybeCopyAccount(mockContext, testAccount, primaryUser, primaryUser));
        verifyZeroInteractions(mockAccountManager);
    }

    private ApplicationInfo createApplicationInfo(String packageName, boolean system) {
        ApplicationInfo ai = new ApplicationInfo();
        ai.packageName = packageName;
        if (system) {
            ai.flags = ApplicationInfo.FLAG_SYSTEM;
        }
        return ai;
    }
}