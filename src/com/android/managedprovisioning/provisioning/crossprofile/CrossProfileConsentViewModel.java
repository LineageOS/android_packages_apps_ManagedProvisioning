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
import androidx.lifecycle.AbstractSavedStateViewModelFactory;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import androidx.savedstate.SavedStateRegistryOwner;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// Class must be public to avoid "Cannot create an instance of..." compiler errors.
/** Stores state and responds to UI interactions for the cross-profile consent screen. */
public class CrossProfileConsentViewModel extends ViewModel {
    private static final String ITEMS_KEY = "items";

    private static final String APP_NOT_FOUND_MESSAGE =
            "Application not found for cross-profile consent screen";

    private final Context mApplicationContext;
    private final SavedStateHandle mState;

    // Constructor must be public to avoid "Cannot create an instance of..." compiler errors.
    public CrossProfileConsentViewModel(Application application, SavedStateHandle state) {
        super();
        mApplicationContext = checkNotNull(application).getApplicationContext();
        mState = checkNotNull(state);
    }

    /** Returns the list of cross-profile consent items. */
    LiveData<List<CrossProfileItem>> getItems() {
        return mState.getLiveData(ITEMS_KEY);
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
        mState.set(ITEMS_KEY, crossProfileItems);
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
    // tests, which creates a new Application every time. We could fix it here by only using a
    // cached instance when the Application is equal, but caching also breaks support with
    // AbstractSavedStateViewModelFactory.
    static class Factory extends AbstractSavedStateViewModelFactory {
        private static final String INVALID_CLASS_ERROR_PREFIX =
                "Invalid class for creating a CrossProfileConsentViewModel: ";

        private final Application mApplication;

        Factory(SavedStateRegistryOwner owner, Application application) {
            super(owner, /* defaultArgs= */ null);
            mApplication = checkNotNull(application);
        }

        @NonNull
        @Override
        protected <T extends ViewModel> T create(
                @NonNull String key,
                @NonNull Class<T> modelClass,
                @NonNull SavedStateHandle savedStateHandle) {
            // The key is unused as this is not a keyed factory.
            if (!CrossProfileConsentViewModel.class.isAssignableFrom(modelClass)) {
                throw new IllegalArgumentException(INVALID_CLASS_ERROR_PREFIX + modelClass);
            }
            return (T) new CrossProfileConsentViewModel(mApplication, savedStateHandle);
        }
    }
}
