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

package com.android.managedprovisioning;

import android.app.Activity;
import android.content.Intent;
import android.support.test.runner.AndroidJUnitRunner;
import android.util.ArrayMap;
import android.util.Log;

import java.util.Map;

public class TestInstrumentationRunner extends AndroidJUnitRunner {
    // hard-coded package name as context.getPackageName() provides ManagedProvisioning app name
    // instead of test package name
    public static final String TEST_PACKAGE_NAME = "com.android.managedprovisioning.tests";

    private static final String TAG = "TestInstrumentationRunner";
    private static final Map<String, Class<?>> sReplacedActivityMap = new ArrayMap();

    public static void registerReplacedActivity(Class<?> oldActivity, Class<?> newActivity) {
        sReplacedActivityMap.put(oldActivity.getCanonicalName(), newActivity);
    }

    public static void unregisterReplacedActivity(Class<?> oldActivity) {
        sReplacedActivityMap.remove(oldActivity.getCanonicalName());
    }

    @Override
    public Activity newActivity(ClassLoader cl, String className, Intent intent)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        Class<?> replacedActivity = sReplacedActivityMap.get(className);
        if (replacedActivity != null) {
            Log.i(TAG, "Launching " + replacedActivity.getCanonicalName()
                    + " for an intent launching " + className);
            cl = replacedActivity.getClassLoader();
            className = replacedActivity.getCanonicalName();
        }
        return super.newActivity(cl, className, intent);
    }

}
