package com.android.managedprovisioning.provisioning;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE;
import static com.android.managedprovisioning.provisioning.AbstractProvisioningController.MSG_RUN_TASK;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;

import android.content.ComponentName;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.managedprovisioning.model.PackageDownloadInfo;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.model.WifiInfo;
import com.android.managedprovisioning.task.AbstractProvisioningTask;
import com.android.managedprovisioning.task.AddWifiNetworkTask;
import com.android.managedprovisioning.task.DeleteNonRequiredAppsTask;
import com.android.managedprovisioning.task.DeviceOwnerInitializeProvisioningTask;
import com.android.managedprovisioning.task.DisallowAddUserTask;
import com.android.managedprovisioning.task.DownloadPackageTask;
import com.android.managedprovisioning.task.InstallPackageTask;
import com.android.managedprovisioning.task.SetDevicePolicyTask;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Unit tests for {@link DeviceOwnerProvisioningController}.
 */
public class DeviceOwnerProvisioningControllerTest extends AndroidTestCase {

    private static final int TEST_USER_ID = 123;
    private static final ComponentName TEST_ADMIN = new ComponentName("com.test.admin",
            "com.test.admin.AdminReceiver");

    private static final String TEST_SSID = "SomeSsid";
    private static final WifiInfo TEST_WIFI_INFO = new WifiInfo.Builder()
            .setSsid(TEST_SSID)
            .build();

    private static final String TEST_DOWNLOAD_LOCATION = "http://www.some.uri.com";
    private static final byte[] TEST_PACKAGE_CHECKSUM = new byte[] { '1', '2', '3', '4', '5' };
    private static final PackageDownloadInfo TEST_DOWNLOAD_INFO = new PackageDownloadInfo.Builder()
            .setLocation(TEST_DOWNLOAD_LOCATION)
            .setSignatureChecksum(TEST_PACKAGE_CHECKSUM)
            .build();

    private AbstractProvisioningTask mLastTask;

    @Mock private AbstractProvisioningController.ProvisioningServiceInterface mService;
    private DeviceOwnerProvisioningController mController;
    private FakeTaskHandler mHandler;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        // this is necessary for mockito to work
        System.setProperty("dexmaker.dexcache", getContext().getCacheDir().toString());
        MockitoAnnotations.initMocks(this);
        HandlerThread thread = new HandlerThread("TestHandler");
        thread.start();
        mHandler = new FakeTaskHandler(thread.getLooper());
    }

    @SmallTest
    public void testRunAllTasks() throws Exception {
        // GIVEN device owner provisioning was invoked with a wifi and download info
        createController(TEST_WIFI_INFO, TEST_DOWNLOAD_INFO);

        // WHEN starting the test run
        mController.start();

        // THEN the initialization task is run first
        verifyTaskRun(DeviceOwnerInitializeProvisioningTask.class);

        // WHEN the task completes successfully
        mController.onSuccess(mLastTask);

        // THEN the add wifi task should be run
        verifyTaskRun(AddWifiNetworkTask.class);

        // WHEN the task completes successfully
        mController.onSuccess(mLastTask);

        // THEN the download package task should be run
        verifyTaskRun(DownloadPackageTask.class);

        // WHEN the task completes successfully
        mController.onSuccess(mLastTask);

        // THEN the install package task should be run
        verifyTaskRun(InstallPackageTask.class);

        // WHEN the task completes successfully
        mController.onSuccess(mLastTask);

        // THEN the delete non-required apps task should be run
        verifyTaskRun(DeleteNonRequiredAppsTask.class);

        // WHEN the task completes successfully
        mController.onSuccess(mLastTask);

        // THEN the set device policy task should be run
        verifyTaskRun(SetDevicePolicyTask.class);

        // WHEN the task completes successfully
        mController.onSuccess(mLastTask);

        // THEN the set device policy task should be run
        verifyTaskRun(DisallowAddUserTask.class);

        // WHEN the task completes successfully
        mController.onSuccess(mLastTask);

        // THEN the provisioning complete callback should have happened
        verify(mService).provisioningComplete();
    }

    @SmallTest
    public void testNoWifiInfo() throws Exception {
        // GIVEN device owner provisioning was invoked with a wifi and download info
        createController(null, TEST_DOWNLOAD_INFO);

        // WHEN starting the test run
        mController.start();

        // THEN the initialization task is run first
        verifyTaskRun(DeviceOwnerInitializeProvisioningTask.class);

        // WHEN the task completes successfully
        mController.onSuccess(mLastTask);

        // THEN the download package task should be run
        verifyTaskRun(DownloadPackageTask.class);

        // WHEN the task completes successfully
        mController.onSuccess(mLastTask);

        // THEN the install package task should be run
        verifyTaskRun(InstallPackageTask.class);

        // WHEN the task completes successfully
        mController.onSuccess(mLastTask);

        // THEN the delete non-required apps task should be run
        verifyTaskRun(DeleteNonRequiredAppsTask.class);

        // WHEN the task completes successfully
        mController.onSuccess(mLastTask);

        // THEN the set device policy task should be run
        verifyTaskRun(SetDevicePolicyTask.class);

        // WHEN the task completes successfully
        mController.onSuccess(mLastTask);

        // THEN the set device policy task should be run
        verifyTaskRun(DisallowAddUserTask.class);

        // WHEN the task completes successfully
        mController.onSuccess(mLastTask);

        // THEN the provisioning complete callback should have happened
        verify(mService).provisioningComplete();
    }

    @SmallTest
    public void testNoDownloadInfo() throws Exception {
        // GIVEN device owner provisioning was invoked with a wifi and download info
        createController(TEST_WIFI_INFO, null);

        // WHEN starting the test run
        mController.start();

        // THEN the initialization task is run first
        verifyTaskRun(DeviceOwnerInitializeProvisioningTask.class);

        // WHEN the task completes successfully
        mController.onSuccess(mLastTask);

        // THEN the add wifi task should be run
        verifyTaskRun(AddWifiNetworkTask.class);

        // WHEN the task completes successfully
        mController.onSuccess(mLastTask);

        // THEN the delete non-required apps task should be run
        verifyTaskRun(DeleteNonRequiredAppsTask.class);

        // WHEN the task completes successfully
        mController.onSuccess(mLastTask);

        // THEN the set device policy task should be run
        verifyTaskRun(SetDevicePolicyTask.class);

        // WHEN the task completes successfully
        mController.onSuccess(mLastTask);

        // THEN the set device policy task should be run
        verifyTaskRun(DisallowAddUserTask.class);

        // WHEN the task completes successfully
        mController.onSuccess(mLastTask);

        // THEN the provisioning complete callback should have happened
        verify(mService).provisioningComplete();
    }

    @SmallTest
    public void testErrorAddWifiTask() throws Exception {
        // GIVEN device owner provisioning was invoked with a wifi and download info
        createController(TEST_WIFI_INFO, TEST_DOWNLOAD_INFO);

        // WHEN starting the test run
        mController.start();

        // THEN the initialization task is run first
        verifyTaskRun(DeviceOwnerInitializeProvisioningTask.class);

        // WHEN the task completes successfully
        mController.onSuccess(mLastTask);

        // THEN the add wifi task should be run
        verifyTaskRun(AddWifiNetworkTask.class);

        // WHEN the task causes an error
        mController.onError(mLastTask, 0);

        // THEN the onError callback should have been called without factory reset being required
        verify(mService).error(anyInt(), eq(false));
    }

    @SmallTest
    public void testErrorDownloadAppTask() throws Exception {
        // GIVEN device owner provisioning was invoked with a wifi and download info
        createController(TEST_WIFI_INFO, TEST_DOWNLOAD_INFO);

        // WHEN starting the test run
        mController.start();

        // THEN the initialization task is run first
        verifyTaskRun(DeviceOwnerInitializeProvisioningTask.class);

        // WHEN the task completes successfully
        mController.onSuccess(mLastTask);

        // THEN the add wifi task should be run
        verifyTaskRun(AddWifiNetworkTask.class);

        // WHEN the task completes successfully
        mController.onSuccess(mLastTask);

        // THEN the download package task should be run
        verifyTaskRun(DownloadPackageTask.class);

        // WHEN the task causes an error
        mController.onError(mLastTask, 0);

        // THEN the onError callback should have been called with factory reset being required
        verify(mService).error(anyInt(), eq(true));
    }

    private void verifyTaskRun(Class expected) throws Exception {
        mLastTask = mHandler.getLastTask();
        assertNotNull(mLastTask);
        assertEquals(expected, mLastTask.getClass());
    }

    private void createController(WifiInfo wifiInfo, PackageDownloadInfo downloadInfo) {
        ProvisioningParams params = new ProvisioningParams.Builder()
                .setDeviceAdminComponentName(TEST_ADMIN)
                .setProvisioningAction(ACTION_PROVISION_MANAGED_DEVICE)
                .setWifiInfo(wifiInfo)
                .setDeviceAdminDownloadInfo(downloadInfo)
                .build();

        mController = new DeviceOwnerProvisioningController(
                getContext(),
                params,
                TEST_USER_ID,
                mService,
                mHandler);
        mController.initialize();
    }

    private class FakeTaskHandler extends Handler {

        FakeTaskHandler(Looper looper) {
            super(looper);
        }

        private BlockingQueue<AbstractProvisioningTask> mBlockingQueue
                = new ArrayBlockingQueue<>(1);

        public AbstractProvisioningTask getLastTask() throws Exception {
            return mBlockingQueue.poll(10, TimeUnit.SECONDS);
        }

        public void handleMessage(Message msg) {
            if (msg.what == MSG_RUN_TASK) {
                assertTrue(mBlockingQueue.add((AbstractProvisioningTask) msg.obj));
            } else {
                fail("Unknown message " + msg.what);
            }
        }
    }
}
