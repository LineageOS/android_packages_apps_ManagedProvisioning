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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import android.os.Bundle;

/**
 * Helper class to load/save resume information from Intents into a SharedPreferences.
 */
public class IntentStore {
    private SharedPreferences mPrefs;
    private String[] mStringKeys;
    private ComponentName mIntentTarget;

    public IntentStore(Context context, String[] stringKeys, ComponentName intentTarget,
            String preferencesName) {
        mPrefs = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE);
        mStringKeys = stringKeys;
        mIntentTarget = intentTarget;
    }

    public void clear() {
        mPrefs.edit().clear().commit();
    }

    public void save(Bundle data){
        SharedPreferences.Editor editor = mPrefs.edit();

        editor.clear();
        for (String stringKey : mStringKeys) {
            editor.putString(stringKey, data.getString(stringKey));
        }
        editor.commit();
    }

    public Intent load() {
        Intent result = new Intent();
        result.setComponent(mIntentTarget);

        for (String key : mStringKeys) {
            String value = mPrefs.getString(key, null);
            if (value == null) {
                return null;
            }
            result.putExtra(key, value);
        }

        return result;
    }
}
