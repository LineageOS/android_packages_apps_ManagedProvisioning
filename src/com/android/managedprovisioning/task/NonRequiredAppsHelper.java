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
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_SHAREABLE_DEVICE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_USER;

import static com.android.internal.util.Preconditions.checkNotNull;

import android.app.AppGlobals;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Xml;
import android.view.inputmethod.InputMethodInfo;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.FastXmlSerializer;
import com.android.internal.view.IInputMethodManager;
import com.android.managedprovisioning.ProvisionLogger;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Helper class to compute non-required apps.
 */
public class NonRequiredAppsHelper {

    private static final String TAG_SYSTEM_APPS = "system-apps";
    private static final String TAG_PACKAGE_LIST_ITEM = "item";
    private static final String ATTR_VALUE = "value";

    public static final int DEVICE_OWNER = 0;
    public static final int PROFILE_OWNER = 1;
    public static final int MANAGED_USER = 2;

    private final Context mContext;
    private final IPackageManager mIPackageManager;
    private final PackageManager mPm;
    private final IInputMethodManager mIInputMethodManager;
    private final String mDpcPackageName;
    private final List<String> mRequiredAppsList;
    private final List<String> mDisallowedAppsList;
    private final List<String> mVendorRequiredAppsList;
    private final List<String> mVendorDisallowedAppsList;
    private final int mProvisioningType;
    private final boolean mLeaveAllSystemAppsEnabled;
    private final Utils mUtils = new Utils();
    private final boolean mNewProfile;

    public NonRequiredAppsHelper(Context context, ProvisioningParams params, boolean newProfile) {
        this(context, params, AppGlobals.getPackageManager(), getIInputMethodManager(), newProfile);
    }

    @VisibleForTesting
    NonRequiredAppsHelper(Context context, ProvisioningParams params,
            IPackageManager iPackageManager, IInputMethodManager iInputMethodManager,
            boolean newProfile) {
        mContext = checkNotNull(context);
        mIPackageManager = checkNotNull(iPackageManager);
        mPm = checkNotNull(context.getPackageManager());
        mIInputMethodManager = checkNotNull(iInputMethodManager);
        mDpcPackageName = checkNotNull(params.inferDeviceAdminPackageName());
        mNewProfile = newProfile;

        // For split system user devices that will have a system device owner, don't adjust the set
        // of enabled packages in the system user as we expect the right set of packages to be
        // enabled for the system user out of the box. For other devices, the set of available
        // packages can vary depending on management state.
        mLeaveAllSystemAppsEnabled = params.leaveAllSystemAppsEnabled ||
                params.provisioningAction.equals(ACTION_PROVISION_MANAGED_SHAREABLE_DEVICE);

        int requiredAppsListArray;
        int vendorRequiredAppsListArray;
        int disallowedAppsListArray;
        int vendorDisallowedAppsListArray;
        switch (params.provisioningAction) {
            case ACTION_PROVISION_MANAGED_USER:
                mProvisioningType = MANAGED_USER;
                requiredAppsListArray = R.array.required_apps_managed_user;
                disallowedAppsListArray = R.array.disallowed_apps_managed_user;
                vendorRequiredAppsListArray = R.array.vendor_required_apps_managed_user;
                vendorDisallowedAppsListArray = R.array.vendor_disallowed_apps_managed_user;
                break;
            case ACTION_PROVISION_MANAGED_PROFILE:
                mProvisioningType = PROFILE_OWNER;
                requiredAppsListArray = R.array.required_apps_managed_profile;
                disallowedAppsListArray = R.array.disallowed_apps_managed_profile;
                vendorRequiredAppsListArray = R.array.vendor_required_apps_managed_profile;
                vendorDisallowedAppsListArray = R.array.vendor_disallowed_apps_managed_profile;
                break;
            case ACTION_PROVISION_MANAGED_DEVICE:
            case ACTION_PROVISION_MANAGED_SHAREABLE_DEVICE:
                mProvisioningType = DEVICE_OWNER;
                requiredAppsListArray = R.array.required_apps_managed_device;
                disallowedAppsListArray = R.array.disallowed_apps_managed_device;
                vendorRequiredAppsListArray = R.array.vendor_required_apps_managed_device;
                vendorDisallowedAppsListArray = R.array.vendor_disallowed_apps_managed_device;
                break;
            default:
                throw new IllegalArgumentException("Provisioning action "
                        + params.provisioningAction + " not implemented.");
        }

        Resources resources = context.getResources();
        mRequiredAppsList = Arrays.asList(resources.getStringArray(requiredAppsListArray));
        mDisallowedAppsList = Arrays.asList(resources.getStringArray(disallowedAppsListArray));
        mVendorRequiredAppsList = Arrays.asList(
                resources.getStringArray(vendorRequiredAppsListArray));
        mVendorDisallowedAppsList = Arrays.asList(
                resources.getStringArray(vendorDisallowedAppsListArray));
    }

    /**
     * Computes non-required apps. All the system apps with a launcher that are not in
     * the required set of packages will be considered as non-required apps.
     *
     * Note: If an app is mistakenly listed as both required and disallowed, it will be treated as
     * disallowed.
     *
     * @param userId The userId for which the non-required apps needs to be computed.
     * @return the set of non-required apps.
     */
    public Set<String> getNonRequiredApps(int userId) {
        if (mLeaveAllSystemAppsEnabled) {
            return Collections.emptySet();
        }

        Set<String> nonRequiredApps = getCurrentAppsWithLauncher(userId);
        // Newly installed system apps are uninstalled when they are not required and are either
        // disallowed or have a launcher icon.
        nonRequiredApps.removeAll(getRequiredApps());
        // Don't delete the system input method packages in case of Device owner provisioning.
        if (mProvisioningType == DEVICE_OWNER || mProvisioningType == MANAGED_USER) {
            nonRequiredApps.removeAll(getSystemInputMethods());
        }
        nonRequiredApps.addAll(getDisallowedApps());
        return nonRequiredApps;
    }

    /**
     * Finds the new system apps installed in the user. When called for the first time, it will
     * just return all the system apps installed and also saves them to the disk. when called after
     * this, it will get all the system apps and compares it with the system apps last saved.
     *
     * @param userId The userId for which the new system apps needs to be computed.
     * @return the set of new system apps or null in case of an error.
     */
    public Set<String> getNewSystemApps(int userId) {
        File systemAppsFile = getSystemAppsFile(mContext, userId);
        systemAppsFile.getParentFile().mkdirs(); // Creating the folder if it does not exist

        Set<String> currentSystemApps = mUtils.getCurrentSystemApps(mIPackageManager, userId);
        final Set<String> previousSystemApps;
        if (mNewProfile) {
            // Provisioning case.
            previousSystemApps = Collections.<String>emptySet();
        } else if (!systemAppsFile.exists()) {
            // OTA case.
            ProvisionLogger.loge("Could not find the system apps file " +
                    systemAppsFile.getAbsolutePath());
            return null;
        } else {
            // OTA case.
            previousSystemApps = readSystemApps(systemAppsFile);
        }

        writeSystemApps(currentSystemApps, systemAppsFile);
        Set<String> newApps = currentSystemApps;
        newApps.removeAll(previousSystemApps);
        return newApps;
    }

    /**
     * Call this method when the current set of system apps of a user needs to be saved to disk.
     * Note: If the DPC chose to skip the disabling of system apps, then this method won't do
     * anything.
     *
     * @param userId The userId for which the system apps info needs to be saved.
     */
    public void writeCurrentSystemAppsIfNeeded(int userId) {
        if (mLeaveAllSystemAppsEnabled) {
            return;
        }
        final File systemAppsFile = getSystemAppsFile(mContext, userId);
        systemAppsFile.getParentFile().mkdirs(); // Creating the folder if it does not exist
        writeSystemApps(mUtils.getCurrentSystemApps(mIPackageManager, userId), systemAppsFile);
    }

    /**
     * @return {@code true} if DPC chose to skip the disabling of system apps or if the system user
     * device owner is being provisioned.
     */
    public boolean leaveSystemAppsEnabled() {
        return mLeaveAllSystemAppsEnabled;
    }

    static File getSystemAppsFile(Context context, int userId) {
        return new File(context.getFilesDir() + File.separator + "system_apps"
                + File.separator + "user" + userId + ".xml");
    }

    private Set<String> getCurrentAppsWithLauncher(int userId) {
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolveInfos = mPm.queryIntentActivitiesAsUser(launcherIntent,
                PackageManager.MATCH_UNINSTALLED_PACKAGES
                        | PackageManager.MATCH_DISABLED_COMPONENTS
                        | PackageManager.MATCH_DIRECT_BOOT_AWARE
                        | PackageManager.MATCH_DIRECT_BOOT_UNAWARE,
                userId);
        Set<String> apps = new HashSet<>();
        for (ResolveInfo resolveInfo : resolveInfos) {
            apps.add(resolveInfo.activityInfo.packageName);
        }
        return apps;
    }

    private Set<String> getSystemInputMethods() {
        // InputMethodManager is final so it cannot be mocked.
        // So, we're using IInputMethodManager directly because it can be mocked.
        List<InputMethodInfo> inputMethods;
        try {
            inputMethods = mIInputMethodManager.getInputMethodList();
        } catch (RemoteException e) {
            ProvisionLogger.loge("Could not communicate with IInputMethodManager", e);
            return Collections.emptySet();
        }
        Set<String> systemInputMethods = new HashSet<>();
        for (InputMethodInfo inputMethodInfo : inputMethods) {
            ApplicationInfo applicationInfo = inputMethodInfo.getServiceInfo().applicationInfo;
            if ((applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                systemInputMethods.add(inputMethodInfo.getPackageName());
            }
        }
        return systemInputMethods;
    }

    @VisibleForTesting
    void writeSystemApps(Set<String> packageNames, File systemAppsFile) {
        try {
            FileOutputStream stream = new FileOutputStream(systemAppsFile, false);
            XmlSerializer serializer = new FastXmlSerializer();
            serializer.setOutput(stream, "utf-8");
            serializer.startDocument(null, true);
            serializer.startTag(null, TAG_SYSTEM_APPS);
            for (String packageName : packageNames) {
                serializer.startTag(null, TAG_PACKAGE_LIST_ITEM);
                serializer.attribute(null, ATTR_VALUE, packageName);
                serializer.endTag(null, TAG_PACKAGE_LIST_ITEM);
            }
            serializer.endTag(null, TAG_SYSTEM_APPS);
            serializer.endDocument();
            stream.close();
        } catch (IOException e) {
            ProvisionLogger.loge("IOException trying to write the system apps", e);
        }
    }

    @VisibleForTesting
    Set<String> readSystemApps(File systemAppsFile) {
        Set<String> result = new HashSet<>();
        if (!systemAppsFile.exists()) {
            return result;
        }
        try {
            FileInputStream stream = new FileInputStream(systemAppsFile);

            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(stream, null);

            int type = parser.next();
            int outerDepth = parser.getDepth();
            while ((type=parser.next()) != XmlPullParser.END_DOCUMENT
                    && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
                if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                    continue;
                }
                String tag = parser.getName();
                if (tag.equals(TAG_PACKAGE_LIST_ITEM)) {
                    result.add(parser.getAttributeValue(null, ATTR_VALUE));
                } else {
                    ProvisionLogger.loge("Unknown tag: " + tag);
                }
            }
            stream.close();
        } catch (IOException e) {
            ProvisionLogger.loge("IOException trying to read the system apps", e);
        } catch (XmlPullParserException e) {
            ProvisionLogger.loge("XmlPullParserException trying to read the system apps", e);
        }
        return result;
    }

    private Set<String> getRequiredApps() {
        HashSet<String> requiredApps = new HashSet<>();
        requiredApps.addAll(mRequiredAppsList);
        requiredApps.addAll(mVendorRequiredAppsList);
        requiredApps.add(mDpcPackageName);
        return requiredApps;
    }

    private Set<String> getDisallowedApps() {
        HashSet<String> disallowedApps = new HashSet<>();
        disallowedApps.addAll(mDisallowedAppsList);
        disallowedApps.addAll(mVendorDisallowedAppsList);
        return disallowedApps;
    }

    private static IInputMethodManager getIInputMethodManager() {
        IBinder b = ServiceManager.getService(Context.INPUT_METHOD_SERVICE);
        return IInputMethodManager.Stub.asInterface(b);
    }
}