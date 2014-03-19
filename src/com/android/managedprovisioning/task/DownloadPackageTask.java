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
package com.android.managedprovisioning.task;

import android.app.DownloadManager;
import android.app.DownloadManager.Query;
import android.app.DownloadManager.Request;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;

import com.android.managedprovisioning.ProvisionLogger;

import java.io.InputStream;
import java.io.IOException;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Downloads a given file and checks whether its hash matches a given hash to verify that the
 * intended file was downloaded.
 */
public class DownloadPackageTask {
    public static final int ERROR_HASH_MISMATCH = 0;
    public static final int ERROR_DOWNLOAD_FAILED = 1;
    public static final int ERROR_OTHER = 2;

    private static final String HASH_TYPE = "SHA-1";

    private Context mContext;
    private String mDownloadLocation;
    private Callback mCallback;
    private byte[] mHash;
    private boolean mDoneDownloading;

    private long mDownloadId;

    public DownloadPackageTask (Context context, String downloadLocation, byte[] hash) {
        mContext = context;
        mDownloadLocation = downloadLocation;
        mHash = hash;
        mDoneDownloading = false;
    }

    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    public boolean downloadLocationWasProvided() {
        return !TextUtils.isEmpty(mDownloadLocation);
    }

    public void run() {
        mContext.registerReceiver(createDownloadReceiver(),
                new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

        ProvisionLogger.logd("Starting download from " + mDownloadLocation);
        DownloadManager dm = (DownloadManager) mContext
                .getSystemService(Context.DOWNLOAD_SERVICE);
        Request r = new Request(Uri.parse(mDownloadLocation));
        mDownloadId = dm.enqueue(r);
    }

    private BroadcastReceiver createDownloadReceiver() {
        return new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
                    Query q = new Query();
                    q.setFilterById(mDownloadId);
                    DownloadManager dm = (DownloadManager) mContext
                            .getSystemService(Context.DOWNLOAD_SERVICE);
                    Cursor c = dm.query(q);
                    if (c.moveToFirst()) {
                        int columnIndex = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
                        if (DownloadManager.STATUS_SUCCESSFUL == c.getInt(columnIndex)) {
                            mContext.unregisterReceiver(this);
                            onDownloadSuccess(c.getString(
                                    c.getColumnIndex(DownloadManager.COLUMN_LOCAL_FILENAME)));
                        } else if (DownloadManager.STATUS_FAILED == c.getInt(columnIndex)){
                            mContext.unregisterReceiver(this);
                            onDownloadFail(c.getColumnIndex(DownloadManager.COLUMN_REASON));
                        }
                    }
                }
            }
        };
    }

    private void onDownloadSuccess(String location) {
        if (mDoneDownloading) {
            // DownloadManager can send success more than once. Only act first time.
            return;
        } else {
            mDoneDownloading = true;
        }

        ProvisionLogger.logd("Downloaded succesfully to: " + location);

        // Check whether hash of downloaded file matches hash given in constructor.
        byte[] hash = computeHash(location);
        if (hash == null) {

            // Error should have been reported in computeHash().
            return;
        }

        if (Arrays.equals(mHash, hash)) {
            ProvisionLogger.logd(HASH_TYPE + "-hashes matched, both are "
                    + byteArrayToHex(hash));
            mCallback.onSuccess(location);
        } else {
            ProvisionLogger.loge(HASH_TYPE + "-hash of downloaded file does not match given hash.");
            ProvisionLogger.loge(HASH_TYPE + "-hash of downloaded file: "
                    + byteArrayToHex(hash));
            ProvisionLogger.loge(HASH_TYPE + "-hash provided by programmer: "
                    + byteArrayToHex(mHash));

            // TODO: delete the file at location location.

            mCallback.onError(ERROR_HASH_MISMATCH);
        }
    }

    private void onDownloadFail(int errorCode) {
        ProvisionLogger.loge("Downloading package failed.");
        ProvisionLogger.loge("COLUMN_REASON in DownloadManager response has value: "
                + errorCode);
        mCallback.onError(ERROR_DOWNLOAD_FAILED);
    }

    private byte[] computeHash(String fileLocation) {
        InputStream fis = null;
        MessageDigest md;
        byte hash[] = null;
        try {
            md = MessageDigest.getInstance(HASH_TYPE);
        } catch (NoSuchAlgorithmException e) {
            ProvisionLogger.loge("Hashing algorithm " + HASH_TYPE + " not supported.", e);
            mCallback.onError(ERROR_OTHER);
            return null;
        }
        try {
            fis = new FileInputStream(fileLocation);

            byte[] buffer = new byte[256];
            int n = 0;
            while (n != -1) {
                n = fis.read(buffer);
                if (n > 0) {
                    md.update(buffer, 0, n);
                }
            }
            hash = md.digest();
        } catch (IOException e) {
            ProvisionLogger.loge("IO error.", e);
            mCallback.onError(ERROR_OTHER);
        } finally {
            // Close input stream quietly.
            try {
                if (fis != null) {
                    fis.close();
                }
            } catch (IOException e) {
                // Ignore.
            }
        }
        return hash;
    }

    // For logging purposes only.
    String byteArrayToHex(byte[] ba) {
        StringBuilder sb = new StringBuilder();
        for(byte b : ba) {
            sb.append(String.format("%02x", b&0xff));
        }
        return sb.toString();
    }

    public abstract static class Callback {
        public abstract void onSuccess(String downloadedPackageLocation);
        public abstract void onError(int errorCode);
    }
}