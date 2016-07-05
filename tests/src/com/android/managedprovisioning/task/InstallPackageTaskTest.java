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

package com.android.managedprovisioning.task;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.IPackageInstallObserver;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.managedprovisioning.common.Utils;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class InstallPackageTaskTest extends AndroidTestCase {
    private static final String TEST_PACKAGE_NAME = "com.android.test";
    private static final String TEST_PACKAGE_LOCATION = "/sdcard/TestPackage.apk";

    @Mock private Context mContext;
    @Mock private PackageManager mPackageManager;
    @Mock private InstallPackageTask.Callback mCallback;
    private InstallPackageTask mTask;
    private PackageInfo mPackageInfo;
    private Utils mUtils;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // this is necessary for mockito to work
        System.setProperty("dexmaker.dexcache", getContext().getCacheDir().toString());
        MockitoAnnotations.initMocks(this);

        ActivityInfo activityInfo = new ActivityInfo();
        mPackageInfo = new PackageInfo();

        activityInfo.permission = android.Manifest.permission.BIND_DEVICE_ADMIN;

        mPackageInfo.packageName = TEST_PACKAGE_NAME;
        mPackageInfo.receivers = new ActivityInfo[] {activityInfo};

        when(mPackageManager.getPackageArchiveInfo(eq(TEST_PACKAGE_LOCATION), anyInt())).
                thenReturn(mPackageInfo);


        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mContext.getPackageName()).thenReturn(getContext().getPackageName());

        mUtils = new UtilsStub();
        mTask = new InstallPackageTask(mContext, mCallback, mUtils);
    }

    @SmallTest
    public void testInstall_NoPackages() {
        // WHEN running the InstallPackageTask without specifying an install location
        mTask.run(TEST_PACKAGE_NAME, null);
        // THEN no package is installed, but we get a success callback
        verify(mPackageManager, never()).installPackage(
                any(Uri.class),
                any(IPackageInstallObserver.class),
                anyInt(),
                anyString());
        verify(mCallback, times(1)).onSuccess();
        assertTrue(mUtils.isPackageVerifierEnabled(mContext));
    }

    @SmallTest
    public void testInstall_OnePackage() throws Exception {
        // WHEN running the InstallPackageTask specifying an install location
        mTask.run(TEST_PACKAGE_NAME, TEST_PACKAGE_LOCATION);
        ArgumentCaptor<IPackageInstallObserver> observer
                = ArgumentCaptor.forClass(IPackageInstallObserver.class);
        ArgumentCaptor<Integer> flags = ArgumentCaptor.forClass(Integer.class);
        // THEN the package is installed and we get a success callback
        verify(mPackageManager).installPackage(
                eq(Uri.parse("file://" + TEST_PACKAGE_LOCATION)),
                observer.capture(),
                flags.capture(),
                eq(getContext().getPackageName()));
        // make sure that the flags value has been set
        assertTrue(0 != (flags.getValue() & PackageManager.INSTALL_REPLACE_EXISTING));
        observer.getValue().packageInstalled(TEST_PACKAGE_NAME,
                PackageManager.INSTALL_SUCCEEDED);
        verify(mCallback, times(1)).onSuccess();
        assertTrue(mUtils.isPackageVerifierEnabled(mContext));
    }

    @SmallTest
    public void testInstall_InstallFailedVersionDowngrade() throws Exception {
        // WHEN running the InstallPackageTask with a package already at a higher version 
        mTask.run(TEST_PACKAGE_NAME, TEST_PACKAGE_LOCATION);
        ArgumentCaptor<IPackageInstallObserver> observer
                = ArgumentCaptor.forClass(IPackageInstallObserver.class);
        verify(mPackageManager).installPackage(
                eq(Uri.parse("file://" + TEST_PACKAGE_LOCATION)),
                observer.capture(),
                anyInt(),
                eq(getContext().getPackageName()));
        observer.getValue().packageInstalled(null,
                PackageManager.INSTALL_FAILED_VERSION_DOWNGRADE);
        // THEN we get a success callback
        verify(mCallback, times(1)).onSuccess();
        assertTrue(mUtils.isPackageVerifierEnabled(mContext));
    }

    @SmallTest
    public void testPackageHasNoReceivers() {
        mPackageInfo.receivers = null;
        mTask.run(TEST_PACKAGE_NAME, TEST_PACKAGE_LOCATION);
        verifyDontInstall();
        assertTrue(mUtils.isPackageVerifierEnabled(mContext));
    }

    @SmallTest
    public void testNoArchive() {
        // GIVEN there is no archive at the package location
        when(mPackageManager.getPackageArchiveInfo(eq(TEST_PACKAGE_LOCATION), anyInt()))
                .thenReturn(null);
        // WHEN running the InstallPackageTask
        mTask.run(TEST_PACKAGE_NAME, TEST_PACKAGE_LOCATION);
        // THEN nothing is installed
        verifyDontInstall();
        assertTrue(mUtils.isPackageVerifierEnabled(mContext));
    }

    @SmallTest
    public void testWrongPackageName() {
        mTask.run("wrong.test.package.name", TEST_PACKAGE_LOCATION);
        verifyDontInstall();
        assertTrue(mUtils.isPackageVerifierEnabled(mContext));
    }

    private void verifyDontInstall() {
        verify(mPackageManager, never()).installPackage(
                any(Uri.class),
                any(IPackageInstallObserver.class),
                anyInt(),
                anyString());
        verify(mCallback, times(1)).onError(InstallPackageTask.ERROR_PACKAGE_INVALID);
    }

    private static class UtilsStub extends Utils {
        private boolean mPackageVerifierEnabled = true;

        @Override
        public boolean isPackageVerifierEnabled(Context c) {
            return mPackageVerifierEnabled;
        }

        @Override
        public void setPackageVerifierEnabled(Context c, boolean packageVerifierEnabled) {
            mPackageVerifierEnabled = packageVerifierEnabled;
        }
    }
}
