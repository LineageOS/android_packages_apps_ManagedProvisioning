/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.managedprovisioning.provisioning.crossprofile;

import static androidx.core.util.Preconditions.checkNotNull;

import static com.android.managedprovisioning.common.ProvisionLogger.loge;
import static com.android.managedprovisioning.provisioning.crossprofile.CrossProfileConsentActivity.CROSS_PROFILE_SUMMARY_META_DATA;

import android.annotation.Nullable;
import android.app.Application;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// Class must be public to avoid "Cannot create an instance of..." compiler errors.
/** Stores state and responds to UI interactions for the cross-profile consent screen. */
public class CrossProfileConsentViewModel extends AndroidViewModel {
    private static final String APP_NOT_FOUND_MESSAGE =
            "Application not found for cross-profile consent screen";

    private final MutableLiveData<List<CrossProfileItem>> mItems = new MutableLiveData<>();
    private final Context mApplicationContext;

    // Constructor must be public to avoid "Cannot create an instance of..." compiler errors.
    public CrossProfileConsentViewModel(@NonNull Application application) {
        super(application);
        mApplicationContext = checkNotNull(application).getApplicationContext();
    }

    /** Returns the list of cross-profile consent items. */
    LiveData<List<CrossProfileItem>> getItems() {
        return mItems;
    }

    void findItems() {
        final Set<String> crossProfilePackages =
                mApplicationContext.getSystemService(DevicePolicyManager.class)
                        .getDefaultCrossProfilePackages();
        final List<CrossProfileItem> crossProfileItems = new ArrayList<>();
        for (String crossProfilePackage : crossProfilePackages) {
            CrossProfileItem crossProfileItem = buildCrossProfileItem(crossProfilePackage);
            if (crossProfileItem == null) {
                // Error should have already been logged before it returned null.
                continue;
            }
            crossProfileItems.add(crossProfileItem);
        }
        mItems.setValue(crossProfileItems);
    }

    /**
     * Returns the {@link CrossProfileItem} associated with the given package name, or {@code null}
     * if it cannot be found. Logs the error if it cannot be found.
     */
    @Nullable
    private CrossProfileItem buildCrossProfileItem(String crossProfilePackage) {
        final ApplicationInfo applicationInfo = findApplicationInfo(crossProfilePackage);
        if (applicationInfo == null) {
            // Error should have already been logged before it returned null.
            return null;
        }
        String appTitle =
                (String) mApplicationContext.getPackageManager()
                        .getApplicationLabel(applicationInfo);
        String summary = findCrossProfileSummary(applicationInfo);
        Drawable icon = mApplicationContext.getPackageManager().getApplicationIcon(applicationInfo);
        return CrossProfileItem.builder()
                .setAppTitle(appTitle)
                .setSummary(summary)
                .setIcon(icon)
                .build();
    }

    /**
     * Returns the app title or {@code null} if it cannot be found. Logs the error if it cannot be
     * found.
     */
    @Nullable
    private ApplicationInfo findApplicationInfo(String crossProfilePackage) {
        final ApplicationInfo applicationInfo;
        try {
            applicationInfo = mApplicationContext.getPackageManager()
                    .getApplicationInfo(crossProfilePackage, /* flags= */ 0);
        } catch (PackageManager.NameNotFoundException e) {
            loge(APP_NOT_FOUND_MESSAGE, e);
            return null;
        }
        if (applicationInfo == null) {
            loge(APP_NOT_FOUND_MESSAGE);
            return null;
        }
        return applicationInfo;
    }

    /** Returns the cross-profile summary or empty if it cannot be found. */
    private String findCrossProfileSummary(ApplicationInfo applicationInfo) {
        Bundle metaData = applicationInfo.metaData;
        if (metaData == null) {
            loge("No meta-data to give a cross-profile summary for app "
                    + applicationInfo.packageName);
            return "";
        }
        if (!metaData.containsKey(CROSS_PROFILE_SUMMARY_META_DATA)) {
            loge("No meta-data defined with name " + CROSS_PROFILE_SUMMARY_META_DATA
                    + " to give a cross-profile summary for app " + applicationInfo.packageName);
            return "";
        }
        return metaData.getString(CROSS_PROFILE_SUMMARY_META_DATA);
    }

    void onButtonClicked() {}

    // Create a custom factory due to http://b/148841619. The default AndroidViewModel stores the
    // Application statically and reuses the factory instance so does not work with Robolectric
    // tests, which creates a new Application every time. We fix this here by checking that the
    // application is the same before reusing the instance.
    static class Factory implements ViewModelProvider.Factory {
        private static final String INVALID_CLASS_ERROR_PREFIX =
                "Invalid class for creating a CrossProfileConsentViewModel: ";

        private static Factory sInstance;

        private final Application mApplication;

        static Factory getInstance(Application application) {
            if (sInstance != null && sInstance.hasSameApplication(application)) {
                return sInstance;
            }
            sInstance = new Factory(application);
            return sInstance;
        }

        private Factory(Application application) {
            mApplication = checkNotNull(application);
        }

        boolean hasSameApplication(Application application) {
            return application.equals(mApplication);
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            if (!CrossProfileConsentViewModel.class.isAssignableFrom(modelClass)) {
                throw new IllegalArgumentException(INVALID_CLASS_ERROR_PREFIX + modelClass);
            }
            try {
                return modelClass.getConstructor(Application.class).newInstance(mApplication);
            } catch (InstantiationException
                    | InvocationTargetException
                    | NoSuchMethodException
                    | IllegalAccessException e) {
                throw new IllegalArgumentException(INVALID_CLASS_ERROR_PREFIX + modelClass, e);
            }
        }
    }
}
