/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.managedprovisioning;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.content.Context;
import android.os.Handler;
import android.os.UserHandle;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit-tests for {@link Utils}.
 */
public class UtilsTest extends AndroidTestCase {
    @Mock private Context mockContext;
    @Mock private AccountManager mockAccountManager;

    @Override
    public void setUp() {
        // this is necessary for mockito to work
        System.setProperty("dexmaker.dexcache", getContext().getCacheDir().toString());

        MockitoAnnotations.initMocks(this);

        when(mockContext.getSystemService(Context.ACCOUNT_SERVICE)).thenReturn(mockAccountManager);
    }

    @SmallTest
    public void testMaybeCopyAccount() throws Exception {
        Account testAccount = new Account("test@afw-test.com", "com.google");
        UserHandle primaryUser = UserHandle.of(0);
        UserHandle managedProfile = UserHandle.of(10);

        AccountManagerFuture mockResult = mock(AccountManagerFuture.class);
        when(mockResult.getResult(anyLong(), any(TimeUnit.class))).thenReturn(true);

        when(mockAccountManager.copyAccountToUser(any(Account.class), any(UserHandle.class),
                any(UserHandle.class), any(AccountManagerCallback.class), any(Handler.class)))
                .thenReturn(mockResult);

        Utils.maybeCopyAccount(mockContext, testAccount, primaryUser, managedProfile);

        verify(mockAccountManager).copyAccountToUser(eq(testAccount), eq(primaryUser),
                eq(managedProfile), any(AccountManagerCallback.class), any(Handler.class));
        verify(mockResult).getResult(anyLong(), any(TimeUnit.class));
    }

    @SmallTest
    public void testMaybeCopyAccount_nullAccount() {
        UserHandle primaryUser = UserHandle.of(0);
        UserHandle managedProfile = UserHandle.of(10);

        Utils.maybeCopyAccount(mockContext, null /* accountToMigrate */, primaryUser,
                managedProfile);

        verifyZeroInteractions(mockAccountManager);
    }

    @SmallTest
    public void testMaybeCopyAccount_sameUser() {
        Account testAccount = new Account("test@afw-test.com", "com.google");
        UserHandle primaryUser = UserHandle.of(0);

        Utils.maybeCopyAccount(mockContext, testAccount, primaryUser, primaryUser);

        verifyZeroInteractions(mockAccountManager);
    }
}