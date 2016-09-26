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

package com.android.managedprovisioning.analytics;

import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.PackageManager;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit-tests for {@link AnalyticsUtils}.
 */
@SmallTest
public class AnalyticsUtilsTest extends AndroidTestCase {
    private static final String INVALID_PACKAGE_NAME = "invalid-package-name";
    private static final String VALID_PACKAGE_NAME = "valid-package-name";
    private static final String VALID_INSTALLER_PACKAGE = "valid-installer-package";

    @Mock private Context mockContext;
    @Mock private PackageManager mockPackageManager;

    @Override
    public void setUp() {
        // this is necessary for mockito to work
        System.setProperty("dexmaker.dexcache", getContext().getCacheDir().toString());

        MockitoAnnotations.initMocks(this);

        when(mockContext.getPackageManager()).thenReturn(mockPackageManager);
    }

    public void testGetInstallerPackageName_invalidPackage() throws Exception {
        // WHEN getting installer package name for an invalid package.
        when(mockPackageManager.getInstallerPackageName(INVALID_PACKAGE_NAME))
                .thenThrow(new IllegalArgumentException());
        // THEN null should be returned and exception should be digested.
        assertNull(AnalyticsUtils.getInstallerPackageName(mockContext, INVALID_PACKAGE_NAME));
    }

    public void testGetInstallerPackageName_validPackage() throws Exception {
        // WHEN getting installer package name for a valid package.
        when(mockPackageManager.getInstallerPackageName(VALID_PACKAGE_NAME))
                .thenReturn(VALID_INSTALLER_PACKAGE);
        // THEN valid installer package name should be returned.
        assertEquals(AnalyticsUtils.getInstallerPackageName(mockContext, VALID_PACKAGE_NAME),
                VALID_INSTALLER_PACKAGE);
    }
}
