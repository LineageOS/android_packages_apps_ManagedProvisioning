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

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.IPackageInstallObserver;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class InstallPackageTaskTest extends AndroidTestCase {
    private static final String TEST_PACKAGE_NAME = "com.android.test";
    private static final String TEST_PACKAGE_LOCATION = "/sdcard/TestPackage.apk";
    private static final ProvisioningParams TEST_PARAMS = new ProvisioningParams.Builder()
            .setDeviceAdminPackageName(TEST_PACKAGE_NAME)
            .setProvisioningAction(ACTION_PROVISION_MANAGED_DEVICE)
            .build();
    private static final int TEST_USER_ID = 123;
    private static final Bundle TEST_BUNDLE;
    static {
        TEST_BUNDLE = new Bundle();
        TEST_BUNDLE.putString(DownloadPackageTask.EXTRA_PACKAGE_DOWNLOAD_LOCATION,
                TEST_PACKAGE_LOCATION);
    }

    @Mock private Context mContext;
    @Mock private PackageManager mPackageManager;
    @Mock private AbstractProvisioningTask.Callback mCallback;
    @Mock private DownloadPackageTask mDownloadPackageTask;
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
        mTask = new InstallPackageTask(mUtils, mDownloadPackageTask, mContext, TEST_PARAMS,
                mCallback);
    }

    @SmallTest
    public void testInstall_NoPackages() {
        // GIVEN no package was downloaded
        when(mDownloadPackageTask.getDownloadedPackageLocation()).thenReturn(null);

        // WHEN running the InstallPackageTask without specifying an install location
        mTask.run(TEST_USER_ID);
        // THEN no package is installed, but we get a success callback
        verify(mPackageManager, never()).installPackage(
                any(Uri.class),
                any(IPackageInstallObserver.class),
                anyInt(),
                anyString());
        verify(mCallback).onSuccess(mTask);
        assertTrue(mUtils.isPackageVerifierEnabled(mContext));
    }

    @SmallTest
    public void testInstall_OnePackage() throws Exception {
        // GIVEN a package was downloaded to TEST_LOCATION
        when(mDownloadPackageTask.getDownloadedPackageLocation()).thenReturn(TEST_PACKAGE_LOCATION);

        // WHEN running the InstallPackageTask specifying an install location
        mTask.run(TEST_USER_ID);

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
        verify(mCallback).onSuccess(mTask);
        assertTrue(mUtils.isPackageVerifierEnabled(mContext));
    }

    @SmallTest
    public void testInstall_InstallFailedVersionDowngrade() throws Exception {
        // GIVEN a package was downloaded to TEST_LOCATION
        when(mDownloadPackageTask.getDownloadedPackageLocation()).thenReturn(TEST_PACKAGE_LOCATION);

        // WHEN running the InstallPackageTask with a package already at a higher version
        mTask.run(TEST_USER_ID);
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
        verify(mCallback).onSuccess(mTask);
        assertTrue(mUtils.isPackageVerifierEnabled(mContext));
    }

    @SmallTest
    public void testPackageHasNoReceivers() {
        // GIVEN a package was downloaded to TEST_LOCATION
        when(mDownloadPackageTask.getDownloadedPackageLocation()).thenReturn(TEST_PACKAGE_LOCATION);

        mPackageInfo.receivers = null;
        mTask.run(TEST_USER_ID);
        verifyDontInstall();
        assertTrue(mUtils.isPackageVerifierEnabled(mContext));
    }

    @SmallTest
    public void testNoArchive() {
        // GIVEN a package was downloaded to TEST_LOCATION
        when(mDownloadPackageTask.getDownloadedPackageLocation()).thenReturn(TEST_PACKAGE_LOCATION);
        // GIVEN there is no archive at the package location
        when(mPackageManager.getPackageArchiveInfo(eq(TEST_PACKAGE_LOCATION), anyInt()))
                .thenReturn(null);
        // WHEN running the InstallPackageTask
        mTask.run(TEST_USER_ID);
        // THEN nothing is installed
        verifyDontInstall();
        assertTrue(mUtils.isPackageVerifierEnabled(mContext));
    }

    @SmallTest
    public void testWrongPackageName() {
        // GIVEN a package was downloaded to TEST_LOCATION
        when(mDownloadPackageTask.getDownloadedPackageLocation()).thenReturn(TEST_PACKAGE_LOCATION);

        // GIVEN the package name of the downloaded package is different from the one defined in
        // the provisioning params
        mPackageInfo.packageName = "wrong.test.package.name";

        // WHEN running the InstallPackageTask
        mTask.run(TEST_USER_ID);

        // THEN nothing is installed
        verifyDontInstall();
        assertTrue(mUtils.isPackageVerifierEnabled(mContext));
    }

    private void verifyDontInstall() {
        verify(mPackageManager, never()).installPackage(
                any(Uri.class),
                any(IPackageInstallObserver.class),
                anyInt(),
                anyString());
        verify(mCallback).onError(mTask, InstallPackageTask.ERROR_PACKAGE_INVALID);
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
