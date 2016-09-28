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

package com.android.managedprovisioning.provisioning;

import static com.android.managedprovisioning.provisioning.Constants.ACTION_START_PROVISIONING;

import android.app.Service;
import android.content.Intent;
import android.os.HandlerThread;
import android.os.IBinder;

import com.android.managedprovisioning.ProvisionLogger;

/**
 * Service that runs the provisioning process. This service creates a {@link HandlerThread} for the
 * execution of the provisioning process. It retrieves the {@link AbstractProvisioningController}
 * from the {@link ProvisioningManager} and executes this on the handler thread.
 */
public class ProvisioningService extends Service {
    private HandlerThread mHandlerThread;

    @Override
    public void onCreate() {
        super.onCreate();

        // TODO: Move handler thread into ProvisioningManager
        mHandlerThread = new HandlerThread("ProvisioningHandler");
        mHandlerThread.start();
    }

    @Override
    public void onDestroy() {
        mHandlerThread.quitSafely();
        super.onDestroy();
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            ProvisionLogger.logw("Missing intent or action: " + intent);
            return START_NOT_STICKY;
        }

        if (ACTION_START_PROVISIONING.equals(intent.getAction())) {
            ProvisioningManager.getInstance(this).startProvisioning(mHandlerThread.getLooper());
        } else {
            ProvisionLogger.loge("Unknown intent action: " + intent.getAction());
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
