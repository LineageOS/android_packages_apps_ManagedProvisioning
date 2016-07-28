package com.android.managedprovisioning.task;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.app.DownloadManager;
import android.app.DownloadManager.Query;
import android.app.DownloadManager.Request;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.Signature;
import android.database.MatrixCursor;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.PackageDownloadInfo;
import com.android.managedprovisioning.model.ProvisioningParams;

import org.mockito.ArgumentCaptor;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoAnnotations.Mock;
import org.mockito.Spy;

@SmallTest
public class DownloadPackageTaskTest extends AndroidTestCase {
    @Mock private Context mContext;
    @Mock private AbstractProvisioningTask.Callback mCallback;
    @Mock private DownloadManager mDownloadManager;
    @Mock private PackageManager mPackageManager;
    @Spy private Utils mUtils;
    @Mock private PackageInfo mPackageInfo;

    private static final String TEST_PACKAGE_NAME = "sample.package.name";
    private static final String TEST_PACKAGE_LOCATION = "http://www.some.uri.com";
    private static final String TEST_LOCAL_FILENAME = "/local/filename";
    private static final int TEST_USER_ID = 123;

    private static final byte[] TEST_PACKAGE_CHECKSUM = new byte[] { '1', '2', '3', '4', '5' };
    private static final byte[] TEST_SIGNATURE = new byte[] {'a', 'b', 'c', 'd'};

    private byte[] mTestPackageChecksumHash;
    private byte[] mTestSignatureHash;

    private static final long TEST_DOWNLOAD_ID = 1234;
    private static final int PACKAGE_VERSION = 43;

    private DownloadPackageTask mTask;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // This is necessary for mockito to work
        System.setProperty("dexmaker.dexcache", getContext().getCacheDir().toString());
        MockitoAnnotations.initMocks(this);

        mTestPackageChecksumHash = mUtils.computeHashOfByteArray(TEST_PACKAGE_CHECKSUM);
        mTestSignatureHash = mUtils.computeHashOfByteArray(TEST_SIGNATURE);

        when(mContext.getSystemService(Context.DOWNLOAD_SERVICE)).thenReturn(mDownloadManager);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);

        when(mPackageManager.getPackageArchiveInfo(TEST_LOCAL_FILENAME,
                PackageManager.GET_SIGNATURES)).thenReturn(mPackageInfo);

        // Package is not installed
        when(mPackageManager.getPackageInfo(TEST_PACKAGE_NAME, 0))
                .thenThrow(new NameNotFoundException());

        when(mUtils.computeHashOfFile(TEST_LOCAL_FILENAME, Utils.SHA256_TYPE))
                .thenReturn(mTestPackageChecksumHash);

        mPackageInfo.signatures = new Signature[] {new Signature(TEST_SIGNATURE)};
    }

    public void testNothingToDo() {
        // WHEN running the download package task
        runAndMockDownload(null, 1);

        // THEN we get a success callback
        verify(mCallback).onSuccess(mTask);
        verifyNoMoreInteractions(mCallback);
    }

    public void testNotConnected() throws Exception {
        // GIVEN we're not connected to a network
        doReturn(false).when(mUtils).isConnectedToNetwork(any(Context.class));

        // GIVEN a valid download info
        final PackageDownloadInfo packageDownloadInfo = new PackageDownloadInfo.Builder()
                .setLocation(TEST_PACKAGE_LOCATION)
                .setSignatureChecksum(mTestSignatureHash)
                .build();

        // WHEN running the download package task
        runTaskWithDownloadInfo(packageDownloadInfo);

        // THEN we get an error callback
        verify(mCallback).onError(mTask, DownloadPackageTask.ERROR_OTHER);
        verifyNoMoreInteractions(mCallback);
    }

    public void testAlreadyInstalled() throws Exception {
        // GIVEN the package is already installed, with the right version
        doReturn(mPackageInfo).when(mPackageManager).getPackageInfo(TEST_PACKAGE_NAME, 0);
        mPackageInfo.versionCode = PACKAGE_VERSION;
        PackageDownloadInfo packageDownloadInfo = new PackageDownloadInfo.Builder()
                .setLocation(TEST_PACKAGE_LOCATION)
                .setSignatureChecksum(mTestSignatureHash)
                .setMinVersion(PACKAGE_VERSION)
                .build();

        // WHEN running the download package task
        runTaskWithDownloadInfo(packageDownloadInfo);

        // THEN we get a success callback directly
        verifyOnTaskFinished(null);
        verifyNoMoreInteractions(mCallback);
    }

    public void testAlreadyInstalledEarlierVersion() throws Exception {
        // GIVEN the package is already installed with an earlier version
        doReturn(mPackageInfo).when(mPackageManager).getPackageInfo(TEST_PACKAGE_NAME, 0);
        mPackageInfo.versionCode = PACKAGE_VERSION - 1;
        PackageDownloadInfo packageDownloadInfo = new PackageDownloadInfo.Builder()
                .setLocation(TEST_PACKAGE_LOCATION)
                .setSignatureChecksum(mTestSignatureHash)
                .setMinVersion(PACKAGE_VERSION)
                .build();
        // WHEN running the download package task
        runAndMockDownload(packageDownloadInfo, 1);
        // THEN we get a success callback
        verifyOnTaskFinished(TEST_LOCAL_FILENAME);
        verifyNoMoreInteractions(mCallback);
    }

    public void testDownloadPackageChecksum() throws Exception {
        // GIVEN we specify a packageDownloadInfo with the right package checksum hash
        PackageDownloadInfo packageDownloadInfo = new PackageDownloadInfo.Builder()
                .setLocation(TEST_PACKAGE_LOCATION)
                .setPackageChecksum(mTestPackageChecksumHash)
                .build();
        // WHEN running the download package task
        runAndMockDownload(packageDownloadInfo, 1);
        // THEN we get a success callback
        verifyOnTaskFinished(TEST_LOCAL_FILENAME);
        verifyNoMoreInteractions(mCallback);
    }

    public void testDownloadSignatureChecksum() throws Exception {
        // GIVEN we specify a packageDownloadInfo with the right signature checksum hash
        PackageDownloadInfo packageDownloadInfo = new PackageDownloadInfo.Builder()
                .setLocation(TEST_PACKAGE_LOCATION)
                .setSignatureChecksum(mTestSignatureHash)
                .build();
        // WHEN running the download package task
        runAndMockDownload(packageDownloadInfo, 1);
        // THEN we get a success callback
        verifyOnTaskFinished(TEST_LOCAL_FILENAME);
        verifyNoMoreInteractions(mCallback);
    }

    /** Test that it works fine even if DownloadManager sends the broadcast twice */
    public void testSendBroadcastTwice() throws Exception {
        // GIVEN we specify a packageDownloadInfo with the right signature checksum hash
        PackageDownloadInfo packageDownloadInfo = new PackageDownloadInfo.Builder()
                .setLocation(TEST_PACKAGE_LOCATION)
                .setSignatureChecksum(mTestSignatureHash)
                .build();
        // WHEN running the download package task and sending the broadcast twice
        runAndMockDownload(packageDownloadInfo, 2);
        // THEN we get a success callback
        verifyOnTaskFinished(TEST_LOCAL_FILENAME);
        verifyNoMoreInteractions(mCallback);
    }

    public void testDownloadSignatureChecksumError() {
        // GIVEN we specify a packageDownloadInfo with an invalid signature checksum hash
        PackageDownloadInfo packageDownloadInfo = new PackageDownloadInfo.Builder()
                .setLocation(TEST_PACKAGE_LOCATION)
                .setSignatureChecksum(new byte[] {1, 2})
                .build();
        // WHEN running the download package task
        runAndMockDownload(packageDownloadInfo, 1);
        // THEN we get an error callback
        verify(mCallback).onError(mTask, DownloadPackageTask.ERROR_HASH_MISMATCH);
        verifyNoMoreInteractions(mCallback);
    }

    private void runTaskWithDownloadInfo(PackageDownloadInfo packageDownloadInfo) {
        final ProvisioningParams params = new ProvisioningParams.Builder()
                .setDeviceAdminPackageName(TEST_PACKAGE_NAME)
                .setProvisioningAction(ACTION_PROVISION_MANAGED_DEVICE)
                .setDeviceAdminDownloadInfo(packageDownloadInfo)
                .build();
        mTask = new DownloadPackageTask(
                mUtils,
                mContext,
                params,
                mCallback);

        mTask.run(TEST_USER_ID);
    }

    private void runAndMockDownload(PackageDownloadInfo packageDownloadInfo, int broadcastCount) {
        doReturn(true).when(mUtils).isConnectedToNetwork(any(Context.class));
        when(mDownloadManager.enqueue(any(Request.class))).thenReturn(TEST_DOWNLOAD_ID);
        MatrixCursor cursor = new MatrixCursor(new String[] {
                DownloadManager.COLUMN_STATUS,
                DownloadManager.COLUMN_LOCAL_FILENAME});
        cursor.addRow(new Object[] {DownloadManager.STATUS_SUCCESSFUL, TEST_LOCAL_FILENAME});
        when(mDownloadManager.query(any(Query.class))).thenReturn(cursor);

        runTaskWithDownloadInfo(packageDownloadInfo);

        verify(mDownloadManager).setAccessFilename(true);

        if (packageDownloadInfo != null) {
            ArgumentCaptor<BroadcastReceiver> broadcastReceiver = ArgumentCaptor.forClass(
                    BroadcastReceiver.class);
            ArgumentCaptor<IntentFilter> intentFilter = ArgumentCaptor.forClass(
                    IntentFilter.class);
            verify(mContext).registerReceiver(broadcastReceiver.capture(), intentFilter.capture());
            assertEquals(intentFilter.getValue().getAction(0),
                    DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            for (int i = 0; i < broadcastCount; i++) {
                broadcastReceiver.getValue().onReceive(mContext,
                        new Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
            }
        }
    }

    private void verifyOnTaskFinished(String location) {
        verify(mCallback).onSuccess(mTask);
        assertEquals(location, mTask.getDownloadedPackageLocation());
    }
}
