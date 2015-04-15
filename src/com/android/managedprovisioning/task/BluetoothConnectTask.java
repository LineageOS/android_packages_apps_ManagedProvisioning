package com.android.managedprovisioning.task;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.text.TextUtils;

import com.android.managedprovisioning.ProvisionLogger;
import com.android.managedprovisioning.ProvisioningParams;
import com.android.managedprovisioning.proxy.BluetoothConnectionService;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Connect to a remote programmer device over Bluetooth. The default Bluetooth adapter is enabled
 * and then used to connect to the remote device. This connection is used to send status updates
 * and to share network access so that the device that is being provisioned can use the programmer
 * device's Internet connection. This task is only run if provisioning was started via NFC bump and
 * if that bump contains the required Bluetooth fields.
 */
public class BluetoothConnectTask {
    /** Time to wait (in milliseconds) for the Bluetooth adapter to be enabled. */
    private static final int BT_ENABLE_TIMEOUT_MS = 30000;

    private final Context mContext;
    private final Callback mCallback;

    /**
     * Handler used to execute {@link #onBluetoothEnabled()} if called from a {@linkplain
     * #mBtStateChangeReceiver broadcast receiver}.
     */
    private final Handler mHandler;
    private final AtomicBoolean mTaskDone;

    private final boolean mUseBluetoothProxy;
    private final String mBluetoothMac;
    private final String mBluetoothUuid;
    private final String mBluetoothDeviceId;
    private final boolean mHasWifiSsid;
    private final BluetoothAdapter mBtAdapter;

    /**
     * Listens for changes in the state of the BluetoothAdapter. Used to wait for Bluetooth to
     * be enabled. This will be initialized to {@code null} and set to a non-{@code null} value it
     * it has been registered.
     */
    private BroadcastReceiver mBtStateChangeReceiver;

    public BluetoothConnectTask(Context context, ProvisioningParams params, Callback callback) {
        mCallback = callback;
        mContext = context;
        // Use the default Bluetooth adapter
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        // Read provisioning parameters
        mBluetoothMac = params.mBluetoothMac;
        mBluetoothUuid = params.mBluetoothUuid;
        mBluetoothDeviceId = params.mBluetoothDeviceIdentifier;
        mUseBluetoothProxy = params.mUseBluetoothProxy;
        mHasWifiSsid = !TextUtils.isEmpty(params.mWifiSsid);
        // Handler used to execute onBluetoothEnabled
        HandlerThread thread = new HandlerThread("Timeout thread",
                android.os.Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        Looper looper = thread.getLooper();
        mHandler = new Handler(looper);
        mTaskDone = new AtomicBoolean();
    }

    public void run() {
        // The adapter will be null if Bluetooth is not supported
        if (mBtAdapter == null) {
            mCallback.onSuccess();
            return;
        }
        // If any of the required fields are invalid, do not connect over Bluetooth.
        if (TextUtils.isEmpty(mBluetoothMac) || TextUtils.isEmpty(mBluetoothUuid) ||
                TextUtils.isEmpty(mBluetoothDeviceId)) {
            mCallback.onSuccess();
            return;
        }
        if (mBtAdapter.isEnabled()) {
            ProvisionLogger.logd("Bluetooth already enabled.");
            onBluetoothEnabled();
        } else {
            ProvisionLogger.logd("Attempt to enable Bluetooth.");
            mBtStateChangeReceiver = createStateChangeReceiver();
            mContext.registerReceiver(mBtStateChangeReceiver,
                    new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
            if (!mBtAdapter.enable()) {
                cleanUp();
                mCallback.onError();
                return;
            }
            // Set a timeout for receiving the Bluetooth enabled broadcast
            mHandler.postDelayed(new Runnable(){
                @Override
                public void run() {
                    if (mTaskDone.getAndSet(true)) {
                        ProvisionLogger.logd("Timeout received but already succeeded.");
                        return;
                    }
                    ProvisionLogger.loge("Timed out waiting for Bluetooth.");
                    cleanUp();
                    mCallback.onError();
                }
            },
            BT_ENABLE_TIMEOUT_MS);
        }
    }

    /**
     * Create a {@code BroadcastReceiver} that waits for Bluetooth to be enabled. After calling
     * {@link BluetoothAdapter#enable()}, we must wait for the adapter to turn on before using
     * any Bluetooth functionality. This receiver will call {@link #onBluetoothEnabled()} when
     * the state changes.
     * @return a broadcast receiver that waits for Bluetooth to be enabled
     */
    private BroadcastReceiver createStateChangeReceiver() {
        return new BroadcastReceiver() {
            // Prevents onBluetoothEnabled() from being called more than once.
            private boolean mHasBeenEnabled;
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                    int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                            BluetoothAdapter.STATE_OFF);
                    if (state == BluetoothAdapter.STATE_ON && !mHasBeenEnabled) {
                        if (mTaskDone.getAndSet(true)) {
                            ProvisionLogger.logd("Bluetooth enabled but already timed out.");
                            return;
                        }
                        mHasBeenEnabled = true;
                        ProvisionLogger.logd("Received bluetooth enabled status.");
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                onBluetoothEnabled();
                            }
                        });
                    }
                }
            }
        };
    }

    /**
     * Creates a Bluetooth connection with a remote device. Run after Bluetooth has been enabled.
     */
    private void onBluetoothEnabled() {
        ProvisionLogger.logd("Bluetooth enabled.");
        // Discovery will lower Bluetooth connection bandwidth. It should be canceled.
        if (mBtAdapter.isDiscovering()) {
            mBtAdapter.cancelDiscovery();
        }
        // Don't start the Bluetooth proxy if a Wi-Fi network is available. If an SSID is available,
        // assume that a Wi-Fi network will be added and do not start the network proxy. If a
        // Wi-Fi connection is already available, do not use the proxy.
        ProvisionLogger.logd("useProxy: mUseBluetoothProxy=" + mUseBluetoothProxy +
                ", mHasWifiSsid=" + mHasWifiSsid +
                ", isConnectedToWifi=" + AddWifiNetworkTask.isConnectedToWifi(mContext));
        boolean useProxy = mUseBluetoothProxy && !mHasWifiSsid &&
                !AddWifiNetworkTask.isConnectedToWifi(mContext);
        // Start Bluetooth Service
        Intent intent = new Intent(mContext, BluetoothConnectionService.class);
        intent.putExtra(BluetoothConnectionService.EXTRA_BLUETOOTH_MAC, mBluetoothMac);
        intent.putExtra(BluetoothConnectionService.EXTRA_BLUETOOTH_UUID, mBluetoothUuid);
        intent.putExtra(BluetoothConnectionService.EXTRA_BLUETOOTH_DEVICE_ID, mBluetoothDeviceId);
        intent.putExtra(BluetoothConnectionService.EXTRA_BLUETOOTH_USE_PROXY, useProxy);
        mContext.startService(intent);
        cleanUp();
        mCallback.onSuccess();
    }

    /**
     * Unregister receivers.
     */
    public void cleanUp() {
        if (mBtStateChangeReceiver != null) {
            mContext.unregisterReceiver(mBtStateChangeReceiver);
            mBtStateChangeReceiver = null;
        }
        mHandler.removeCallbacksAndMessages(null);
    }

    public abstract static class Callback {
        public abstract void onSuccess();
        public abstract void onError();
    }
}
