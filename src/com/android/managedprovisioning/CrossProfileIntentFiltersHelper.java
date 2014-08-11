/*
 * Copyright 2014, The Android Open Source Project
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
package com.android.managedprovisioning;

import static android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.content.SharedPreferences;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.MediaStore;
import android.util.Log;

import com.android.managedprovisioning.ProvisionLogger;

import java.util.List;
/**
 * Class to set CrossProfileIntentFilters during managed profile creation, and reset them after an
 * ota.
 */
public class CrossProfileIntentFiltersHelper {

    public static class FiltersReseter extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(Intent.ACTION_PRE_BOOT_COMPLETED.equals(intent.getAction())) {
                int currentUserId = context.getUserId();
                if (currentUserId == UserHandle.USER_OWNER) {
                    // Reseting the cross-profile intent filters for the managed profiles who have
                    // this user as their parent.
                    UserManager um = (UserManager) context.getSystemService(
                            Context.USER_SERVICE);
                    List<UserInfo> profiles = um.getProfiles(currentUserId);
                    if (profiles.size() > 1) {
                        PackageManager pm = context.getPackageManager();
                        pm.clearCrossProfileIntentFilters(currentUserId);
                        for (UserInfo userInfo : profiles) {
                            if (userInfo.isManagedProfile() && userInfo.id != currentUserId) {
                                pm.clearCrossProfileIntentFilters(userInfo.id);
                                setFilters(pm, currentUserId, userInfo.id);
                            }
                        }
                    }
                }
            }
        }
    }

    public static void setFilters(PackageManager pm, int parentUserId, int managedProfileUserId) {
        ProvisionLogger.logd("Setting cross-profile intent filters");

        IntentFilter mimeTypeTelephony = new IntentFilter();
        mimeTypeTelephony.addAction(Intent.ACTION_DIAL);
        mimeTypeTelephony.addCategory(Intent.CATEGORY_DEFAULT);
        mimeTypeTelephony.addCategory(Intent.CATEGORY_BROWSABLE);
        try {
            mimeTypeTelephony.addDataType("vnd.android.cursor.item/phone");
            mimeTypeTelephony.addDataType("vnd.android.cursor.item/person");
            mimeTypeTelephony.addDataType("vnd.android.cursor.dir/calls");
        } catch (IntentFilter.MalformedMimeTypeException e) {
            //will not happen
        }
        pm.addCrossProfileIntentFilter(mimeTypeTelephony, managedProfileUserId, parentUserId,
                PackageManager.SKIP_CURRENT_PROFILE);

        IntentFilter mimeTypeEmergencyPrivileged = new IntentFilter();
        mimeTypeEmergencyPrivileged.addAction(Intent.ACTION_CALL_EMERGENCY);
        mimeTypeEmergencyPrivileged.addAction(Intent.ACTION_CALL_PRIVILEGED);
        mimeTypeEmergencyPrivileged.addCategory(Intent.CATEGORY_DEFAULT);
        try {
            mimeTypeEmergencyPrivileged.addDataType("vnd.android.cursor.item/phone");
            mimeTypeEmergencyPrivileged.addDataType("vnd.android.cursor.item/phone_v2");
            mimeTypeEmergencyPrivileged.addDataType("vnd.android.cursor.item/person");
        } catch (IntentFilter.MalformedMimeTypeException e) {
            //will not happen
        }
        pm.addCrossProfileIntentFilter(mimeTypeEmergencyPrivileged, managedProfileUserId,
                parentUserId, PackageManager.SKIP_CURRENT_PROFILE);

        IntentFilter callDial = new IntentFilter();
        callDial.addAction(Intent.ACTION_DIAL);
        callDial.addAction(Intent.ACTION_CALL);
        callDial.addAction(Intent.ACTION_VIEW);
        callDial.addAction(Intent.ACTION_CALL_EMERGENCY);
        callDial.addAction(Intent.ACTION_CALL_PRIVILEGED);
        callDial.addCategory(Intent.CATEGORY_DEFAULT);
        callDial.addCategory(Intent.CATEGORY_BROWSABLE);
        callDial.addDataScheme("tel");
        callDial.addDataScheme("voicemail");
        callDial.addDataScheme("sip");
        callDial.addDataScheme("tel");
        pm.addCrossProfileIntentFilter(callDial, managedProfileUserId, parentUserId,
                PackageManager.SKIP_CURRENT_PROFILE);

        IntentFilter callDialNoData = new IntentFilter();
        callDialNoData.addAction(Intent.ACTION_DIAL);
        callDialNoData.addAction(Intent.ACTION_CALL);
        callDialNoData.addAction(Intent.ACTION_CALL_BUTTON);
        callDialNoData.addCategory(Intent.CATEGORY_DEFAULT);
        callDialNoData.addCategory(Intent.CATEGORY_BROWSABLE);
        pm.addCrossProfileIntentFilter(callDialNoData, managedProfileUserId, parentUserId,
                PackageManager.SKIP_CURRENT_PROFILE);

        IntentFilter smsMms = new IntentFilter();
        smsMms.addAction(Intent.ACTION_VIEW);
        smsMms.addAction(Intent.ACTION_SENDTO);
        smsMms.addCategory(Intent.CATEGORY_DEFAULT);
        smsMms.addCategory(Intent.CATEGORY_BROWSABLE);
        smsMms.addDataScheme("sms");
        smsMms.addDataScheme("smsto");
        smsMms.addDataScheme("mms");
        smsMms.addDataScheme("mmsto");
        pm.addCrossProfileIntentFilter(smsMms, managedProfileUserId, parentUserId,
                PackageManager.SKIP_CURRENT_PROFILE);

        IntentFilter mobileNetworkSettings = new IntentFilter();
        mobileNetworkSettings.addAction(android.provider.Settings.ACTION_DATA_ROAMING_SETTINGS);
        mobileNetworkSettings.addAction(android.provider.Settings.ACTION_NETWORK_OPERATOR_SETTINGS);
        mobileNetworkSettings.addCategory(Intent.CATEGORY_DEFAULT);
        pm.addCrossProfileIntentFilter(mobileNetworkSettings, managedProfileUserId,
                parentUserId, PackageManager.SKIP_CURRENT_PROFILE);

        IntentFilter home = new IntentFilter();
        home.addAction(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_DEFAULT);
        home.addCategory(Intent.CATEGORY_HOME);
        pm.addCrossProfileIntentFilter(home, managedProfileUserId, parentUserId,
                PackageManager.SKIP_CURRENT_PROFILE);

        IntentFilter send = new IntentFilter();
        send.addAction(Intent.ACTION_SEND);
        send.addAction(Intent.ACTION_SEND_MULTIPLE);
        send.addCategory(Intent.CATEGORY_DEFAULT);
        try {
            send.addDataType("*/*");
        } catch (IntentFilter.MalformedMimeTypeException e) {
            //will not happen
        }
        pm.addCrossProfileIntentFilter(send, parentUserId, managedProfileUserId, 0);

        IntentFilter getContent = new IntentFilter();
        getContent.addAction(Intent.ACTION_GET_CONTENT);
        getContent.addCategory(Intent.CATEGORY_DEFAULT);
        getContent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            getContent.addDataType("*/*");
        } catch (IntentFilter.MalformedMimeTypeException e) {
            //will not happen
        }
        pm.addCrossProfileIntentFilter(getContent, managedProfileUserId, parentUserId, 0);

        IntentFilter openDocument = new IntentFilter();
        openDocument.addAction(Intent.ACTION_OPEN_DOCUMENT);
        openDocument.addCategory(Intent.CATEGORY_DEFAULT);
        openDocument.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            openDocument.addDataType("*/*");
        } catch (IntentFilter.MalformedMimeTypeException e) {
            //will not happen
        }
        pm.addCrossProfileIntentFilter(openDocument, managedProfileUserId, parentUserId, 0);

        IntentFilter pick = new IntentFilter();
        pick.addAction(Intent.ACTION_PICK);
        pick.addCategory(Intent.CATEGORY_DEFAULT);
        try {
            pick.addDataType("*/*");
        } catch (IntentFilter.MalformedMimeTypeException e) {
            //will not happen
        }
        pm.addCrossProfileIntentFilter(pick, managedProfileUserId, parentUserId, 0);

        IntentFilter pickNoData = new IntentFilter();
        pickNoData.addAction(Intent.ACTION_PICK);
        pickNoData.addCategory(Intent.CATEGORY_DEFAULT);
        pm.addCrossProfileIntentFilter(pickNoData, managedProfileUserId,
                parentUserId, 0);

        IntentFilter recognizeSpeech = new IntentFilter();
        recognizeSpeech.addAction(ACTION_RECOGNIZE_SPEECH);
        recognizeSpeech.addCategory(Intent.CATEGORY_DEFAULT);
        pm.addCrossProfileIntentFilter(recognizeSpeech, managedProfileUserId, parentUserId, 0);

        IntentFilter capture = new IntentFilter();
        capture.addAction(MediaStore.ACTION_IMAGE_CAPTURE);
        capture.addAction(MediaStore.ACTION_IMAGE_CAPTURE_SECURE);
        capture.addAction(MediaStore.ACTION_VIDEO_CAPTURE);
        capture.addAction(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
        capture.addAction(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE);
        capture.addAction(MediaStore.INTENT_ACTION_VIDEO_CAMERA);
        capture.addCategory(Intent.CATEGORY_DEFAULT);
        pm.addCrossProfileIntentFilter(capture, managedProfileUserId, parentUserId, 0);
    }
}
