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
import android.util.Base64;

import com.android.managedprovisioning.ProvisionLogger;
import com.android.managedprovisioning.ProvisioningParams;

import java.io.InputStream;
import java.io.IOException;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Downloads the device admin and the device initializer if download locations were provided for
 * them in the provisioning parameters. Also checks that each file's hash matches a given hash to
 * verify that the downloaded files are the ones that are expected.
 */
public class DownloadPackageTask {
    public static final int ERROR_HASH_MISMATCH = 0;
    public static final int ERROR_DOWNLOAD_FAILED = 1;
    public static final int ERROR_OTHER = 2;

    public static final String DEVICE_OWNER = "deviceOwner";
    public static final String INITIALIZER = "initializer";

    private static final String HASH_TYPE = "SHA-1";

    private final Context mContext;
    private final Callback mCallback;
    private BroadcastReceiver mReceiver;
    private final DownloadManager mDlm;

    private Set<DownloadInfo> mDownloads;

    public DownloadPackageTask (Context context, ProvisioningParams params, Callback callback) {
        mCallback = callback;
        mContext = context;
        mDlm = (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);

        mDownloads = new HashSet<DownloadInfo>();
        if (!TextUtils.isEmpty(params.mDeviceAdminPackageDownloadLocation)) {
            mDownloads.add(new DownloadInfo(
                    params.mDeviceAdminPackageDownloadLocation,
                    params.mDeviceAdminPackageChecksum,
                    params.mDeviceAdminPackageDownloadCookieHeader,
                    DEVICE_OWNER));
        }
        if (params.mDeviceInitializerComponentName != null
                && !TextUtils.isEmpty(params.mDeviceInitializerPackageDownloadLocation)) {
            mDownloads.add(new DownloadInfo(
                    params.mDeviceInitializerPackageDownloadLocation,
                    params.mDeviceInitializerPackageChecksum,
                    params.mDeviceInitializerPackageDownloadCookieHeader,
                    INITIALIZER));
        }
    }

    public void run() {
        if (mDownloads.size() == 0) {
            mCallback.onSuccess();
            return;
        }
        mReceiver = createDownloadReceiver();
        mContext.registerReceiver(mReceiver,
                new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

        DownloadManager dm = (DownloadManager) mContext
                .getSystemService(Context.DOWNLOAD_SERVICE);
        for (DownloadInfo downloadInfo : mDownloads) {
            ProvisionLogger.logd("Starting download from " + downloadInfo.mDownloadLocationFrom);

            Request request = new Request(Uri.parse(downloadInfo.mDownloadLocationFrom));
            if (downloadInfo.mHttpCookieHeader != null) {
                request.addRequestHeader("Cookie", downloadInfo.mHttpCookieHeader);
                ProvisionLogger.logd(
                        "Downloading with http cookie header: " + downloadInfo.mHttpCookieHeader);
            }
            downloadInfo.mDownloadId = dm.enqueue(request);
        }
    }

    private BroadcastReceiver createDownloadReceiver() {
        return new BroadcastReceiver() {
            /**
             * Whenever the download manager finishes a download, record the successful download for
             * the corresponding DownloadInfo.
             */
            @Override
            public void onReceive(Context context, Intent intent) {
                if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
                    Query q = new Query();
                    for (DownloadInfo downloadInfo : mDownloads) {
                        q.setFilterById(downloadInfo.mDownloadId);
                        Cursor c = mDlm.query(q);
                        if (c.moveToFirst()) {
                            long downloadId =
                                    c.getLong(c.getColumnIndex(DownloadManager.COLUMN_ID));
                            int columnIndex = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
                            if (DownloadManager.STATUS_SUCCESSFUL == c.getInt(columnIndex)) {
                                String location = c.getString(
                                        c.getColumnIndex(DownloadManager.COLUMN_LOCAL_FILENAME));
                                c.close();
                                onDownloadSuccess(downloadId, location);
                            } else if (DownloadManager.STATUS_FAILED == c.getInt(columnIndex)){
                                int reason = c.getInt(
                                        c.getColumnIndex(DownloadManager.COLUMN_REASON));
                                c.close();
                                onDownloadFail(reason);
                            }
                        }
                    }
                }
            }
        };
    }

    /**
     * For a given successful download, check that the downloaded file is the expected file. Check
     * if this was the last file the task had to download and finish the DownloadPackageTask if that
     * is the case.
     * @param downloadId the unique download id for the completed download.
     * @param location the file location of the downloaded file.
     */
    private void onDownloadSuccess(long downloadId, String location) {
        DownloadInfo downloadInfo = null;
        for (DownloadInfo info : mDownloads) {
            if (downloadId == info.mDownloadId) {
                downloadInfo = info;
            }
        }
        if (downloadInfo == null || downloadInfo.mDoneDownloading) {
            // DownloadManager can send success more than once. Only act first time.
            return;
        } else {
            downloadInfo.mDoneDownloading = true;
        }
        ProvisionLogger.logd("Downloaded succesfully to: " + location);

        // Check whether hash of downloaded file matches hash given in constructor.
        byte[] hash = computeHash(location);
        if (hash == null) {
            // Error should have been reported in computeHash().
            return;
        }

        if (Arrays.equals(downloadInfo.mHash, hash)) {
            ProvisionLogger.logd(HASH_TYPE + "-hashes matched, both are "
                    + byteArrayToString(hash));
            downloadInfo.mLocation = location;
            downloadInfo.mSuccess = true;
            checkSuccess();
        } else {
            ProvisionLogger.loge(HASH_TYPE + "-hash of downloaded file does not match given hash.");
            ProvisionLogger.loge(HASH_TYPE + "-hash of downloaded file: "
                    + byteArrayToString(hash));
            ProvisionLogger.loge(HASH_TYPE + "-hash provided by programmer: "
                    + byteArrayToString(downloadInfo.mHash));

            mCallback.onError(ERROR_HASH_MISMATCH);
        }
    }

    private void checkSuccess() {
        for (DownloadInfo info : mDownloads) {
            if (!info.mSuccess) {
                return;
            }
        }
        mCallback.onSuccess();
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

    public String getDownloadedPackageLocation(String packageType) {
        for (DownloadInfo info : mDownloads) {
            if (packageType.equals(info.mPackageType)) {
                return info.mLocation;
            }
        }
        return "";
    }

    public void cleanUp() {
        if (mReceiver != null) {
            //Unregister receiver.
            mContext.unregisterReceiver(mReceiver);
            mReceiver = null;
        }

        //Remove download.
        DownloadManager dm = (DownloadManager) mContext
                .getSystemService(Context.DOWNLOAD_SERVICE);
        for (DownloadInfo info : mDownloads) {
            boolean removeSuccess = dm.remove(info.mDownloadId) == 1;
            if (removeSuccess) {
                ProvisionLogger.logd("Successfully removed installer file.");
            } else {
                ProvisionLogger.loge("Could not remove installer file.");
                // Ignore this error. Failing cleanup should not stop provisioning flow.
            }
        }
    }

    // For logging purposes only.
    String byteArrayToString(byte[] ba) {
        return Base64.encodeToString(ba, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }

    public abstract static class Callback {
        public abstract void onSuccess();
        public abstract void onError(int errorCode);
    }

    private static class DownloadInfo {
        public final String mDownloadLocationFrom;
        public final byte[] mHash;
        public final String mHttpCookieHeader;
        public final String mPackageType;
        public long mDownloadId;
        public String mLocation;
        public boolean mDoneDownloading;
        public boolean mSuccess;

        public DownloadInfo(String downloadLocation, byte[] hash, String httpCookieHeader,
                String packageType) {
            mDownloadLocationFrom = downloadLocation;
            mHash = hash;
            mHttpCookieHeader = httpCookieHeader;
            mPackageType = packageType;
            mDoneDownloading = false;
        }
    }
}
