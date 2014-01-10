/*
 * Copyright (C) 2014 Google Inc.
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

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.UserHandle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Profile;
import android.provider.ContactsContract.RawContacts;
import android.provider.Settings;
import android.text.TextUtils;

import com.android.internal.widget.LockPatternUtils;

import java.text.MessageFormat;

/**
 * A layer of abstraction above Settings.
 */
// TODO When common provisioning code is moved, check that there are no unneeded methods
// in here.
public class SettingsAdapter {
    private ContentResolver mContentResolver;

    // Matched to hidden value in Settings.Secure
    private static final String USER_SETUP_COMPLETE = "user_setup_complete";

    public SettingsAdapter(ContentResolver contentResolver) {
        mContentResolver = contentResolver;
    }

    public boolean isDeviceProvisioned() {
        return Settings.Global.getInt(mContentResolver, Settings.Global.DEVICE_PROVISIONED, 0) != 0;
    }

    public void setProvisioned() {
        Settings.Global.putInt(mContentResolver, Settings.Global.DEVICE_PROVISIONED, 1);
    }

    public void setUserSetupComplete() {
        // Equivalent to putIntForUser for the current user.
        Settings.Secure.putInt(mContentResolver, Settings.Secure.USER_SETUP_COMPLETE, 1);
    }

    public void setOwnerInfo(String ownerInfo, String displayName, Context context) {
        String formattedOwnerInfo = MessageFormat.format(ownerInfo, displayName);

        LockPatternUtils lpu = new LockPatternUtils(context);
        int userId = UserHandle.myUserId();
        lpu.setOwnerInfo(formattedOwnerInfo, userId);
        lpu.setOwnerInfoEnabled(true);
    }

    /**
     * Sets the user's name on this tablet. This code copied pretty much
     * verbatim from what the SetupWizard does.
     *
     * @param firstName The user's first name, or null if we don't have one.
     * @param lastName The user's last name, or null if we don't have one.
     */
    public void setUserProfile(String firstName, String lastName) {
        if (TextUtils.isEmpty(firstName) && TextUtils.isEmpty(lastName)) {
            return;
        }

        // See if there's already an existing "me" contact.
        final Uri profilesUri = ContactsContract.Profile.CONTENT_RAW_CONTACTS_URI;
        long meRawContactId = -1; // no ID can be < 0
        boolean newContact = true;
        Cursor c = mContentResolver.query(profilesUri, new String[] {
            RawContacts._ID
        },
                RawContacts.ACCOUNT_NAME + " IS NULL AND " + RawContacts.ACCOUNT_TYPE + " IS NULL",
                null, null);
        try {
            if (c.moveToFirst()) {
                meRawContactId = c.getLong(0);
                newContact = false;
            }
        } finally {
            c.close();
        }

        ContentValues values = new ContentValues();
        if (newContact) {
            meRawContactId = ContentUris.parseId(mContentResolver.insert(profilesUri, values));
        }

        // Add all the provided name fields.
        values.clear();
        String displayName = new String();
        if (!TextUtils.isEmpty(firstName)) {
            values.put(StructuredName.GIVEN_NAME, firstName);
            displayName = firstName;
        }
        if (!TextUtils.isEmpty(lastName)) {
            values.put(StructuredName.FAMILY_NAME, lastName);
            if (!TextUtils.isEmpty(displayName)) {
                displayName += " ";
            }
            displayName += lastName;
        }
        if (!TextUtils.isEmpty(displayName)) {
            values.put(StructuredName.DISPLAY_NAME, displayName);
        }
        if (values.size() > 0) {
            values.put(Data.RAW_CONTACT_ID, meRawContactId);
            values.put(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE);

            // update existing name or insert new one
            long structuredNameId;
            if (newContact || (structuredNameId = getProfileItem(
                    meRawContactId, StructuredName.CONTENT_ITEM_TYPE)) < 0) {
                mContentResolver.insert(Data.CONTENT_URI, values);
            } else {
                mContentResolver.update(
                        ContentUris.withAppendedId(Data.CONTENT_URI, structuredNameId), values,
                        null, null);
            }
        }
    }

    /**
     * Helper method to search for the first profile item of the specified type.
     * Returns the item's ID or -1 if not found.
     */
    private long getProfileItem(long profileContactId, String itemType) {
        Uri dataUri = ContactsContract.Profile.CONTENT_RAW_CONTACTS_URI.buildUpon()
                .appendPath(String.valueOf(profileContactId))
                .appendPath(ContactsContract.RawContacts.Data.CONTENT_DIRECTORY)
                .build();

        Cursor c = mContentResolver.query(dataUri, new String[] { Data._ID, Data.MIMETYPE },
                Data.MIMETYPE + " = ?", new String[] { itemType }, null);

        long id = -1; // not found
        try {
            if (c.moveToFirst()) {
                id = c.getLong(0);
            }
        } finally {
            c.close();
        }

        return id;
    }

    /**
     * Gets the display name for the "me" contact.
     */
    public String getDisplayName() {
        final Cursor profile = mContentResolver.query(Profile.CONTENT_URI,
                new String[] {Profile.DISPLAY_NAME}, null, null, null);
        if (profile == null) return null;

        try {
            if (!profile.moveToFirst()) {
                return null;
            }
            return profile.getString(0);
        } finally {
            profile.close();
        }
    }
}
