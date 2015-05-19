package com.android.managedprovisioning.task;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;

import com.android.managedprovisioning.comm.ProvisionCommLogger;
import com.android.managedprovisioning.ProvisionLogger;
import com.android.managedprovisioning.ProvisioningParams.BluetoothInfo;
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
    private static final int BT_ENABLE_TIMEOUT_MS = 45000;

    private final Context mContext;
    private final Callback mCallback;

    /**
     * Handler used to execute {@link #onBluetoothEnabled()} if called from a {@linkplain
     * #mBtStateChangeReceiver broadcast receiver}.
     */
    private final Handler mHandler;

    /**
     * Set to {@code true} when the Bluetooth adapter has been enabled or when the task times out
     * waiting for the adapter.
     *
     * <p>This value should only be referenced from the handler thread of {@code mHandler}.
     */
    private final AtomicBoolean mTaskDone;

    private final BluetoothInfo mBluetoothInfo;
    private final boolean mHasWifiSsid;
    private final BluetoothAdapter mBtAdapter;

    /**
     * Set to {@code true} when Bluetooth has been set up or when the task times out waiting
     * Bluetooth to be set up times out.
     *
     * <p>This value should only be referenced from the handler thread of {@code mHandler}.
     */
    private boolean mBluetoothSetUp;

    /**
     * Listens for changes in the state of the BluetoothAdapter. Used to wait for Bluetooth to
     * be enabled. This will be initialized to {@code null} and set to a non-{@code null} value it
     * it has been registered.
     */
    private BroadcastReceiver mBtStateChangeReceiver;

    /**
     * Receives notification when the Bluetooth network proxy is set up. If the Bluetooth network
     * proxy is required, this task will wait for the proxy to be enabled and for network access to
     * be available.
     */
    private BroadcastReceiver mBtNetworkProxyReceiver;

    public BluetoothConnectTask(Context context, BluetoothInfo bluetoothInfo, boolean hasWifiSsid,
            Callback callback) {
        mCallback = callback;
        mContext = context;
        // Use the default Bluetooth adapter
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        // Read provisioning parameters
        mBluetoothInfo = bluetoothInfo;
        mHasWifiSsid = hasWifiSsid;
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
        if (TextUtils.isEmpty(mBluetoothInfo.mac) || TextUtils.isEmpty(mBluetoothInfo.uuid) ||
                TextUtils.isEmpty(mBluetoothInfo.deviceIdentifier)) {
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
                    new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), null, mHandler);
            if (!mBtAdapter.enable()) {
                cleanUp();
                mCallback.onError();
                return;
            }
            // Set a timeout for receiving the Bluetooth enabled broadcast
            mHandler.postDelayed(new Runnable() {
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
        ProvisionLogger.logd("useProxy: mBluetoothInfo.useProxy=" + mBluetoothInfo.useProxy +
                ", mHasWifiSsid=" + mHasWifiSsid +
                ", isConnectedToWifi=" + AddWifiNetworkTask.isConnectedToWifi(mContext));
        boolean useProxy = mBluetoothInfo.useProxy && !mHasWifiSsid &&
                !AddWifiNetworkTask.isConnectedToWifi(mContext);
        // Registers a receiver that will be notified by the BluetoothConnectionService once
        // the Bluetooth connection has been enabled. Because other tasks may require this
        // connection, this task won't succeed until the connection is active.
        mBtNetworkProxyReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                ProvisionCommLogger.logd("Bluetooth proxy; Setup complete");
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mBluetoothSetUp) {
                            return;
                        }
                        mBluetoothSetUp = true;
                        onBluetoothSetUp();
                    }
                });
            }
        };
        LocalBroadcastManager.getInstance(mContext).registerReceiver(
                mBtNetworkProxyReceiver,
                new IntentFilter(BluetoothConnectionService.ACTION_LOCAL_BLUETOOTH_SETUP));
        // Start Bluetooth Service.
        Intent intent = new Intent(mContext, BluetoothConnectionService.class);
        intent.putExtra(BluetoothConnectionService.EXTRA_BLUETOOTH_MAC, mBluetoothInfo.mac);
        intent.putExtra(BluetoothConnectionService.EXTRA_BLUETOOTH_UUID, mBluetoothInfo.uuid);
        intent.putExtra(BluetoothConnectionService.EXTRA_BLUETOOTH_DEVICE_ID,
                mBluetoothInfo.deviceIdentifier);
        intent.putExtra(BluetoothConnectionService.EXTRA_BLUETOOTH_USE_PROXY, useProxy);
        mContext.startService(intent);
        // Set a timeout that will cause the task to fail if Bluetooth isn't set up.
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mBluetoothSetUp) {
                    return;
                }
                mBluetoothSetUp = true;
                ProvisionLogger.loge("Timed out waiting for Bluetooth proxy");
                cleanUp();
                mCallback.onError();
            }
        },
        BT_ENABLE_TIMEOUT_MS);
    }

    /**
     * Called when the Bluetooth connection has been enabled.
     */
    private void onBluetoothSetUp() {
        // Clean up receivers and call on success.
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
        if (mBtNetworkProxyReceiver != null) {
            LocalBroadcastManager.getInstance(mContext).unregisterReceiver(mBtNetworkProxyReceiver);
            mBtNetworkProxyReceiver = null;
        }
        mHandler.removeCallbacksAndMessages(null);
    }

    public abstract static class Callback {
        public abstract void onSuccess();
        public abstract void onError();
    }
}
