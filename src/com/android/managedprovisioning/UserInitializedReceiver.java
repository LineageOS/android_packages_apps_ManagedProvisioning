/*
 * Copyright 2015, The Android Open Source Project
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Process;
import android.os.UserHandle;

/**
 * Sends a broadcast to the primary user to request CA certs. Runs on secondary users on user
 * initialization.
 */
public class UserInitializedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent receivedIntent) {
        ProvisionLogger.logi("User is initialized");
        if (!Utils.isCurrentUserOwner()) {
            Intent intent = new Intent(InstallCertRequestReceiver.REQUEST_CERT_ACTION);
            intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
            intent.putExtra(CertService.EXTRA_REQUESTING_USER, Process.myUserHandle());
            context.sendBroadcastAsUser(intent, UserHandle.OWNER);
        }
    }
}