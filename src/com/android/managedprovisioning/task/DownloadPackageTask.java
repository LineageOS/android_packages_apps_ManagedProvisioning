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

import static com.android.internal.util.Preconditions.checkNotNull;

import android.app.DownloadManager;
import android.app.DownloadManager.Query;
import android.app.DownloadManager.Request;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.database.Cursor;
import android.net.Uri;
import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.ProvisionLogger;
import com.android.managedprovisioning.common.StoreUtils;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.PackageDownloadInfo;
import java.io.File;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Downloads an apk . Also verifies that the downloaded file is the one that is expected.
 */
public class DownloadPackageTask {
    private static final boolean DEBUG = false; // To control logging.

    public static final int ERROR_HASH_MISMATCH = 0;
    public static final int ERROR_DOWNLOAD_FAILED = 1;
    public static final int ERROR_OTHER = 2;

    private final Context mContext;
    private final Callback mCallback;
    private BroadcastReceiver mReceiver;
    private final DownloadManager mDownloadManager;
    private final PackageManager mPackageManager;
    private final String mPackageName;
    private final PackageDownloadInfo mPackageDownloadInfo;
    private long mDownloadId;

    private final Utils mUtils;

    private String mDownloadLocationTo; //local file where the package is downloaded.
    private boolean mDoneDownloading;

    public DownloadPackageTask (Context context, Callback callback, String packageName,
            PackageDownloadInfo packageDownloadInfo) {
        this(context, callback, packageName, packageDownloadInfo, new Utils());
    }

    @VisibleForTesting
    DownloadPackageTask (Context context, Callback callback, String packageName,
            PackageDownloadInfo packageDownloadInfo, Utils utils) {
        mCallback = checkNotNull(callback);
        mContext = checkNotNull(context);
        mDownloadManager = (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);
        mDownloadManager.setAccessFilename(true);
        mPackageManager = context.getPackageManager();
        mUtils = checkNotNull(utils);
        mPackageName = packageName;
        mPackageDownloadInfo = packageDownloadInfo;
    }

    public void run() {
        if (mPackageDownloadInfo == null || !mUtils.packageRequiresUpdate(mPackageName,
                mPackageDownloadInfo.minVersion, mContext)) {
            mCallback.onSuccess(null);
            return;
        }
        if (!mUtils.isConnectedToNetwork(mContext)) {
            ProvisionLogger.loge("DownloadPackageTask: not connected to the network, can't download"
                    + " the package");
            mCallback.onError(ERROR_OTHER);
            return;
        }
        mReceiver = createDownloadReceiver();
        mContext.registerReceiver(mReceiver,
                new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        if (DEBUG) {
            ProvisionLogger.logd("Starting download from " + mPackageDownloadInfo.location);
        }

        Request request = new Request(Uri.parse(mPackageDownloadInfo.location));

        // Note that the apk may not actually be downloaded to this path. This could happen if
        // this file already exists.
        String path = mContext.getExternalFilesDir(null)
                + "/download_cache/managed_provisioning_downloaded_app.apk";
        File downloadedFile = new File(path);
        downloadedFile.getParentFile().mkdirs(); // If the folder doesn't exists it is created
        request.setDestinationUri(Uri.fromFile(downloadedFile));

        if (mPackageDownloadInfo.cookieHeader != null) {
            request.addRequestHeader("Cookie", mPackageDownloadInfo.cookieHeader);
            if (DEBUG) {
                ProvisionLogger.logd("Downloading with http cookie header: "
                        + mPackageDownloadInfo.cookieHeader);
            }
        }
        mDownloadId = mDownloadManager.enqueue(request);
    }

    private BroadcastReceiver createDownloadReceiver() {
        return new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
                    Query q = new Query();
                    q.setFilterById(mDownloadId);
                    Cursor c = mDownloadManager.query(q);
                    if (c.moveToFirst()) {
                        int columnIndex = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
                        if (DownloadManager.STATUS_SUCCESSFUL == c.getInt(columnIndex)) {
                            mDownloadLocationTo = c.getString(
                                    c.getColumnIndex(DownloadManager.COLUMN_LOCAL_FILENAME));
                            c.close();
                            onDownloadSuccess();
                        } else if (DownloadManager.STATUS_FAILED == c.getInt(columnIndex)) {
                            int reason = c.getColumnIndex(DownloadManager.COLUMN_REASON);
                            c.close();
                            onDownloadFail(reason);
                        }
                    }
                }
            }
        };
    }

    /**
     * For a successful download, check that the downloaded file is the expected file.
     * If the package hash is provided then that is used, otherwise a signature hash is used.
     * @param location the file location of the downloaded file.
     */
    private void onDownloadSuccess() {
        if (mDoneDownloading) {
            // DownloadManager can send success more than once. Only act first time.
            return;
        } else {
            mDoneDownloading = true;
        }

        ProvisionLogger.logd("Downloaded succesfully to: " + mDownloadLocationTo);

        boolean downloadedContentsCorrect = false;
        if (mPackageDownloadInfo.packageChecksum.length > 0) {
            downloadedContentsCorrect = doesPackageHashMatch();
        } else if (mPackageDownloadInfo.signatureChecksum.length > 0) {
            downloadedContentsCorrect = doesASignatureHashMatch();
        }

        if (downloadedContentsCorrect) {
            mCallback.onSuccess(mDownloadLocationTo);
        } else {
            mCallback.onError(ERROR_HASH_MISMATCH);
        }
    }

    /**
     * Check whether package hash of downloaded file matches the hash given in PackageDownloadInfo.
     * By default, SHA-256 is used to verify the file hash.
     * If mPackageDownloadInfo.packageChecksumSupportsSha1 == true, SHA-1 hash is also supported for
     * backwards compatibility.
     */
    private boolean doesPackageHashMatch() {
        byte[] packageSha256Hash, packageSha1Hash = null;

        ProvisionLogger.logd("Checking file hash of entire apk file.");
        packageSha256Hash = mUtils.computeHashOfFile(mDownloadLocationTo, Utils.SHA256_TYPE);
        if (packageSha256Hash == null) {
            return false;
        }

        if (Arrays.equals(mPackageDownloadInfo.packageChecksum, packageSha256Hash)) {
            return true;
        }

        // Fall back to SHA-1
        if (mPackageDownloadInfo.packageChecksumSupportsSha1) {
            packageSha1Hash = mUtils.computeHashOfFile(mDownloadLocationTo, Utils.SHA1_TYPE);
            if (packageSha1Hash == null) {
                return false;
            }
            if (Arrays.equals(mPackageDownloadInfo.packageChecksum, packageSha1Hash)) {
                return true;
            }
        }

        ProvisionLogger.loge("Provided hash does not match file hash.");
        ProvisionLogger.loge("Hash provided by programmer: "
                + StoreUtils.byteArrayToString(mPackageDownloadInfo.packageChecksum));
        ProvisionLogger.loge("SHA-256 Hash computed from file: " + StoreUtils.byteArrayToString(
                packageSha256Hash));
        if (packageSha1Hash != null) {
            ProvisionLogger.loge("SHA-1 Hash computed from file: " + StoreUtils.byteArrayToString(
                    packageSha1Hash));
        }
        return false;
    }

    private boolean doesASignatureHashMatch() {
        // Check whether a signature hash of downloaded apk matches the hash given in constructor.
        ProvisionLogger.logd("Checking " + Utils.SHA256_TYPE
                + "-hashes of all signatures of downloaded package.");
        List<byte[]> sigHashes = computeHashesOfAllSignatures(mDownloadLocationTo);
        if (sigHashes == null) {
            // Error should have been reported in computeHashesOfAllSignatures().
            return false;
        }
        if (sigHashes.isEmpty()) {
            ProvisionLogger.loge("Downloaded package does not have any signatures.");
            return false;
        }
        for (byte[] sigHash : sigHashes) {
            if (Arrays.equals(sigHash, mPackageDownloadInfo.signatureChecksum)) {
                return true;
            }
        }

        ProvisionLogger.loge("Provided hash does not match any signature hash.");
        ProvisionLogger.loge("Hash provided by programmer: "
                + StoreUtils.byteArrayToString(mPackageDownloadInfo.signatureChecksum));
        ProvisionLogger.loge("Hashes computed from package signatures: ");
        for (byte[] sigHash : sigHashes) {
            ProvisionLogger.loge(StoreUtils.byteArrayToString(sigHash));
        }

        return false;
    }

    private void onDownloadFail(int errorCode) {
        ProvisionLogger.loge("Downloading package failed.");
        ProvisionLogger.loge("COLUMN_REASON in DownloadManager response has value: "
                + errorCode);
        mCallback.onError(ERROR_DOWNLOAD_FAILED);
    }

    private List<byte[]> computeHashesOfAllSignatures(String packageArchiveLocation) {
        PackageInfo info = mPackageManager.getPackageArchiveInfo(packageArchiveLocation,
                PackageManager.GET_SIGNATURES);
        if (info == null) {
            ProvisionLogger.loge("Unable to get package archive info from "
                    + packageArchiveLocation);
            mCallback.onError(ERROR_OTHER);
            return null;
        }

        List<byte[]> hashes = new LinkedList<byte[]>();
        Signature signatures[] = info.signatures;
        try {
            for (Signature signature : signatures) {
               byte[] hash = mUtils.computeHashOfByteArray(signature.toByteArray());
               hashes.add(hash);
            }
        } catch (NoSuchAlgorithmException e) {
            ProvisionLogger.loge("Hashing algorithm " + Utils.SHA256_TYPE + " not supported.", e);
            mCallback.onError(ERROR_OTHER);
            return null;
        }
        return hashes;
    }

    public void cleanUp() {
        if (mReceiver != null) {
            //Unregister receiver.
            mContext.unregisterReceiver(mReceiver);
            mReceiver = null;
        }

        boolean removeSuccess = mDownloadManager.remove(mDownloadId) == 1;
        if (removeSuccess) {
            ProvisionLogger.logd("Successfully removed installer file.");
        } else {
            ProvisionLogger.loge("Could not remove installer file.");
            // Ignore this error. Failing cleanup should not stop provisioning flow.
        }
    }

    public abstract static class Callback {
        public abstract void onSuccess(String downloadedLocation);
        public abstract void onError(int errorCode);
    }
}
