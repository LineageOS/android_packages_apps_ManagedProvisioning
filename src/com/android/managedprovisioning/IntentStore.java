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
    private String mPrefsName; // Name of the file where mPrefs is stored.
    private Context mContext;
    private String[] mStringKeys;
    private String[] mLongKeys;
    private String[] mIntKeys;
    private String[] mBooleanKeys;
    private ComponentName mIntentTarget;

    private static final String IS_SET = "isSet";

    public IntentStore(Context context, String[] stringKeys, String[] longKeys, String[] intKeys,
            String[] booleanKeys, ComponentName intentTarget, String preferencesName) {
        mContext = context;
        mPrefsName = preferencesName;
        mPrefs = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE);
        mStringKeys = stringKeys;
        mLongKeys = longKeys;
        mIntKeys = intKeys;
        mBooleanKeys = booleanKeys;
        mIntentTarget = intentTarget;
    }

    public void clear() {
        mPrefs.edit().clear().commit();
    }

    public void save(Bundle data){
        SharedPreferences.Editor editor = mPrefs.edit();

        editor.clear();
        for (String key : mStringKeys) {
            editor.putString(key, data.getString(key));
        }
        for (String key : mLongKeys) {
            editor.putLong(key, data.getLong(key));
        }
        for (String key : mIntKeys) {
            editor.putInt(key, data.getInt(key));
        }
        for (String key : mBooleanKeys) {
            editor.putBoolean(key, data.getBoolean(key));
        }
        editor.putBoolean(IS_SET, true);
        editor.commit();
    }

    public Intent load() {
        if (!mPrefs.getBoolean(IS_SET, false)) {
            return null;
        }

        Intent result = new Intent();
        result.setComponent(mIntentTarget);

        for (String key : mStringKeys) {
            String value = mPrefs.getString(key, null);
            if (value != null) {
                result.putExtra(key, value);
            }
        }
        for (String key : mLongKeys) {
            if (mPrefs.contains(key)) {
                result.putExtra(key, mPrefs.getLong(key, 0));
            }
        }
        for (String key : mIntKeys) {
            if (mPrefs.contains(key)) {
                result.putExtra(key, mPrefs.getInt(key, 0));
            }
        }
        for (String key : mBooleanKeys) {
            if (mPrefs.contains(key)) {
                result.putExtra(key, mPrefs.getBoolean(key, false));
            }
        }

        return result;
    }
}
