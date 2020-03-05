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

import static android.app.AppOpsManager.MODE_ALLOWED;
import static android.app.AppOpsManager.MODE_IGNORED;

import static androidx.core.util.Preconditions.checkNotNull;

import static com.android.managedprovisioning.common.ProvisionLogger.loge;
import static com.android.managedprovisioning.provisioning.crossprofile.CrossProfileConsentActivity.CROSS_PROFILE_SUMMARY_META_DATA;

import android.annotation.Nullable;
import android.app.Application;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.CrossProfileApps;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.lifecycle.AbstractSavedStateViewModelFactory;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import androidx.savedstate.SavedStateRegistryOwner;

import com.android.managedprovisioning.common.ManagedProvisioningSharedPreferences;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Class must be public to avoid "Cannot create an instance of..." compiler errors.
/** Stores state and responds to UI interactions for the cross-profile consent screen. */
public class CrossProfileConsentViewModel extends ViewModel {
    private static final String ITEMS_KEY = "items";
    private static final String ITEM_PACKAGE_NAMES_KEY = "itemPackageNames";

    private static final String APP_NOT_FOUND_MESSAGE =
            "Application not found for cross-profile consent screen";

    private final Context mApplicationContext;
    private final SavedStateHandle mState;
    private final ManagedProvisioningSharedPreferences mSharedPreferences;

    // Constructor must be public to avoid "Cannot create an instance of..." compiler errors.
    public CrossProfileConsentViewModel(Application application, SavedStateHandle state) {
        super();
        mApplicationContext = checkNotNull(application).getApplicationContext();
        mState = checkNotNull(state);
        mSharedPreferences = new ManagedProvisioningSharedPreferences(mApplicationContext);
    }

    /** Returns the list of cross-profile consent items. */
    LiveData<List<CrossProfileItem>> getItems() {
        return mState.getLiveData(ITEMS_KEY);
    }

    void findItems() {
        final Set<String> configurableCrossProfilePackages =
                getConfigurableDefaultCrossProfilePackages();
        final List<CrossProfileItem> crossProfileItems = new ArrayList<>();
        final Map<CrossProfileItem, String> crossProfileItemPackageNames = new HashMap<>();
        for (String crossProfilePackage : configurableCrossProfilePackages) {
            CrossProfileItem crossProfileItem = buildCrossProfileItem(crossProfilePackage);
            if (crossProfileItem == null) {
                // Error should have already been logged before it returned null.
                continue;
            }
            crossProfileItems.add(crossProfileItem);
            crossProfileItemPackageNames.put(crossProfileItem, crossProfilePackage);
        }
        mState.set(ITEMS_KEY, crossProfileItems);
        mState.set(ITEM_PACKAGE_NAMES_KEY, crossProfileItemPackageNames);
    }

    private Set<String> getConfigurableDefaultCrossProfilePackages() {
        final CrossProfileApps crossProfileApps =
                mApplicationContext.getSystemService(CrossProfileApps.class);
        final Set<String> crossProfilePackages =
                mApplicationContext.getSystemService(DevicePolicyManager.class)
                        .getDefaultCrossProfilePackages();
        final Set<String> configurablePackages = new HashSet<>();
        for (String crossProfilePackage : crossProfilePackages) {
            if (crossProfileApps.canConfigureInteractAcrossProfiles(crossProfilePackage)) {
                configurablePackages.add(crossProfilePackage);
            } else {
                loge("Package whitelisted for cross-profile consent during provisioning is not "
                        + "valid for user configuration: " + crossProfilePackage);
            }
        }
        return configurablePackages;
    }

    /**
     * Returns the {@link CrossProfileItem} associated with the given package name, or {@code null}
     * if it cannot be found. Logs the error if it cannot be found.
     */
    @Nullable
    private CrossProfileItem buildCrossProfileItem(String crossProfilePackage) {
        final Set<String> consentedPackages =
                mSharedPreferences.getConsentedCrossProfilePackages();
        if (consentedPackages.contains(crossProfilePackage)) {
            return null;
        }
        final ApplicationInfo applicationInfo = findApplicationInfo(crossProfilePackage);
        if (applicationInfo == null) {
            // Error should have already been logged before it returned null.
            return null;
        }
        String appTitle =
                (String) mApplicationContext.getPackageManager()
                        .getApplicationLabel(applicationInfo);
        String summary = findCrossProfileSummary(applicationInfo);
        return CrossProfileItem.builder()
                .setAppTitle(appTitle)
                .setSummary(summary)
                .setAppInfo(applicationInfo)
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
                    .getApplicationInfo(crossProfilePackage, PackageManager.GET_META_DATA);
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

    /**
     * Responds to the completion of the consent screen, given the state of the toggle for
     * each cross-profile item. Before calling this method, check that each {@link CrossProfileItem}
     * provided is accounted for in {@link #getItems()} first, in case of an unexpected race
     * condition.
     */
    void onConsentComplete(Map<CrossProfileItem, Boolean> toggleStates) {
        setInteractAcrossProfilesAppOps(toggleStates);
        setConsentedPackagesSharedPreference();
    }

    private void setInteractAcrossProfilesAppOps(Map<CrossProfileItem, Boolean> toggleStates) {
        final CrossProfileApps crossProfileApps =
                mApplicationContext.getSystemService(CrossProfileApps.class);
        final Map<CrossProfileItem, String> crossProfileItemPackageNames =
                mState.get(ITEM_PACKAGE_NAMES_KEY);
        for (CrossProfileItem crossProfileItem : toggleStates.keySet()) {
            crossProfileApps.setInteractAcrossProfilesAppOp(
                    crossProfileItemPackageNames.get(crossProfileItem),
                    toggleStates.get(crossProfileItem) ? MODE_ALLOWED : MODE_IGNORED);
        }
    }

    private void setConsentedPackagesSharedPreference() {
        // The user has either consented to all configurable whitelisted packages now or in the
        // past.
        mSharedPreferences.writeConsentedCrossProfilePackages(
                getConfigurableDefaultCrossProfilePackages());
    }

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
