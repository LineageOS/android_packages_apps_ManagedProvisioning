/*
 * Copyright 2014, The Android Open Source Project
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

import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;



public class UserConsentSaver extends BroadcastReceiver {

    private static String PREFERENCES_NAME = "user-consent-saver";
    private static String TOKEN = "user-consent-token";
    private static String MDM_PACKAGE_NAME = "mdm-package-name";
    private static int NO_CONSENT_TOKEN = -1;
    static int NO_TOKEN_RECEIVED = -2;

    static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String mdmPackageName = intent.getStringExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME);
        int token = intent.getIntExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_TOKEN, NO_CONSENT_TOKEN);
        SharedPreferences.Editor editor = getSharedPreferences(context).edit();
        editor.putString(MDM_PACKAGE_NAME, mdmPackageName);
        editor.putInt(TOKEN, token);
        ProvisionLogger.logd("Recording user consent for the mdm package name "
                + mdmPackageName + " and the user consent token " + token);
        editor.commit();
    }

    public static boolean hasUserConsented(Context context, String mdmPackageName, int token) {
        SharedPreferences prefs = getSharedPreferences(context);
        return prefs.getString(MDM_PACKAGE_NAME, "").equals(mdmPackageName)
                && prefs.getInt(TOKEN, NO_CONSENT_TOKEN) == token;
    }

    public static void unsetUserConsent(Context context) {
        SharedPreferences.Editor editor = getSharedPreferences(context).edit();
        editor.remove(MDM_PACKAGE_NAME);
        editor.remove(TOKEN);
        editor.commit();
    }
}
