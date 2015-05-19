/*
 * Copyright 2015, The Android Open Source Project
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

package com.android.managedprovisioning.proxy;

import android.app.Service;
import android.app.admin.DeviceInitializerStatus;
import android.app.admin.DevicePolicyManager;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.content.LocalBroadcastManager;

import com.android.managedprovisioning.NetworkMonitor;
import com.android.managedprovisioning.ProvisionLogger;


import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service that establishes and manages a Bluetooth connection during device setup.
 */
public class BluetoothConnectionService extends Service implements NetworkMonitor.Callback {

    /**
     * Local broadcast sent when a status update should be sent. Broadcasts with this action will
     * have extras of {@link DevicePolicyManager.EXTRA_DEVICE_INITIALIZER_STATUS_CODE} and {@link
     * DevicePolicyManager.EXTRA_DEVICE_INITIALIZER_STATUS_DESCRIPTION} for the status code and
     * status description respectively.
     * @see #sendStatusUpdate(Context, int)
     * @see #handleStatusUpdate(Intent, boolean)
     */
    private static final String ACTION_LOCAL_PROVISIONING_STATUS =
            "com.android.managedprovisioning.action.LOCAL_PROVISIONING_STATUS";

    /**
     * Local broadcast sent when Bluetooth should be shutdown.
     * @see #sendBluetoothShutdownRequest(Context)
     */
    private static final String ACTION_LOCAL_SHUTDOWN_BLUETOOTH =
            "com.android.managedprovisioning.action.LOCAL_SHUTDOWN_BLUETOOTH";

    /**
     * Local broadcast sent when the Bluetooth connection has been set up.
     * @see #setUpBluetoothProxyIfNeeded()
     */
    public static final String ACTION_LOCAL_BLUETOOTH_SETUP =
            "com.android.managedprovisioning.action.LOCAL_BLUETOOTH_SETUP";

    public static final String EXTRA_BLUETOOTH_MAC = "BluetoothMac";
    public static final String EXTRA_BLUETOOTH_UUID = "BluetoothUuid";
    public static final String EXTRA_BLUETOOTH_DEVICE_ID = "BluetoothDeviceId";
    public static final String EXTRA_BLUETOOTH_USE_PROXY = "BluetoothUseProxy";

    private boolean mBluetoothUseProxy;

    /** Receives status updates sent by ManagedProvisioning. */
    private BroadcastReceiver mLocalStatusReceiver;

    /** Receives status updates sent by the device initializer. */
    private BroadcastReceiver mStatusReceiver;

    /**
     * Listen for changes in the Wi-Fi state.
     */
    private NetworkMonitor mNetworkMonitor;
    private ClientTetherConnection mBluetoothClient;

    /** Used to run potentially long setup tasks. */
    private Handler mHandler;

    /**
     * Send a status update broadcast to the local receiver.
     * @param context Android context used to send update
     * @param statusCode status code of this update
     */
    public static void sendStatusUpdate(Context context, int statusCode) {
        ProvisionLogger.logd("BluetoothConnectionService.sendStatusUpdate");
        Intent intent = new Intent(ACTION_LOCAL_PROVISIONING_STATUS);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_INITIALIZER_STATUS_CODE, statusCode);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    /**
     * Send a broadcast that will shutdown the Bluetooth connection. This will stop the {@code
     * BluetoothConnectionService} service and all of its broadcast receivers.
     */
    public static void sendBluetoothShutdownRequest(Context context) {
        ProvisionLogger.logd("BluetoothConnectionService.sendBluetoothShutdownRequest");
        Intent intent = new Intent(ACTION_LOCAL_SHUTDOWN_BLUETOOTH);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    @Override
    public void onCreate() {
        HandlerThread thread = new HandlerThread(this.getClass().getSimpleName(),
                android.os.Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        Looper looper = thread.getLooper();
        mHandler = new Handler(looper);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ProvisionLogger.logd("BluetoothConnectionService.onStartCommand");
        String bluetoothMac = intent.getStringExtra(EXTRA_BLUETOOTH_MAC);
        String bluetoothUuid = intent.getStringExtra(EXTRA_BLUETOOTH_UUID);
        String bluetoothDeviceId = intent.getStringExtra(EXTRA_BLUETOOTH_DEVICE_ID);
        mBluetoothUseProxy = intent.getBooleanExtra(EXTRA_BLUETOOTH_USE_PROXY, false);
        // Setup Bluetooth connection
        mBluetoothClient = new BluetoothTetherClient(this,
                BluetoothAdapter.getDefaultAdapter(),
                bluetoothDeviceId, bluetoothMac, bluetoothUuid);
        // Receives local broadcasts
        if (mLocalStatusReceiver == null) {
            mLocalStatusReceiver = createLocalStatusReceiver();
            IntentFilter filter = new IntentFilter(ACTION_LOCAL_PROVISIONING_STATUS);
            filter.addAction(ACTION_LOCAL_SHUTDOWN_BLUETOOTH);
            LocalBroadcastManager.getInstance(this).registerReceiver(mLocalStatusReceiver, filter);
        }
        // Receives status updates from the device initializer
        if (mStatusReceiver == null) {
            mStatusReceiver = createStatusReceiver();
            registerReceiver(mStatusReceiver, new IntentFilter(
                    DevicePolicyManager.ACTION_SEND_DEVICE_INITIALIZER_STATUS));
        }
        setUpBluetoothProxyIfNeeded();
        return Service.START_REDELIVER_INTENT;
    }

    /**
     * If the Bluetooth network proxy is needed, start the proxy in a background thread. A
     * {@linkplain #ACTION_LOCAL_BLUETOOTH_SETUP} broadcast will be sent when the proxy has been
     * set up. This method may be called from the main thread.
     */
    private void setUpBluetoothProxyIfNeeded() {
        if (mBluetoothUseProxy) {
            ProvisionLogger.logv("BluetoothConnectionService.setUpBluetooth");
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (startProxy()) {
                        sendProxySetupBroadcast();
                    }
                }
            });
        } else {
            ProvisionLogger.logd("BluetoothConnectionService: No proxy.");
        }
    }

    private void sendProxySetupBroadcast() {
        Intent intent = new Intent(ACTION_LOCAL_BLUETOOTH_SETUP);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    /**
     * @return a receiver that listens for status update broadcasts.
     */
    private BroadcastReceiver createStatusReceiver() {
        return new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                handleStatusUpdate(intent, true);
            }
        };
    }

    /**
     * Process a received status update. The status update will be queued and sent to the remote
     * setup device.
     *
     * <p>If a custom status update is expected and the received status code is not a custom status
     * code, the update will not be sent.
     * @param intent intent containing status update data
     * @param customStatus {@code true} if a custom status is expected
     */
    private void handleStatusUpdate(Intent intent, boolean customStatus) {
        int statusCode = intent.getIntExtra(
                DevicePolicyManager.EXTRA_DEVICE_INITIALIZER_STATUS_CODE, 0);
        String data = intent.getStringExtra(
                DevicePolicyManager.EXTRA_DEVICE_INITIALIZER_STATUS_DESCRIPTION);
        if (customStatus && ((statusCode & DeviceInitializerStatus.FLAG_STATUS_CUSTOM) == 0)) {
            ProvisionLogger.logw("Expected custom status update.");
        } else {
            mBluetoothClient.sendStatusUpdate(statusCode, data);
        }
    }

    /**
     * Create a {@code BroadcastReceiver} that handles local broadcasts that affect the Bluetooth
     * connection. Accepted broadcasts by action:
     * <ul>
     * <li>{@link ACTION_LOCAL_PROVISIONING_STATUS}: receive provisioning status updates from within
     *    ManagedProvisioning.
     * <li>{@link ACTION_LOCAL_SHUTDOWN_BLUETOOTH}: shutdown this service and stop listening for
     *    status updates. This is called when the device is provisioned.
     * </ul>
     *
     * @return a local broadcast receiver
     */
    private BroadcastReceiver createLocalStatusReceiver() {
        return new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                ProvisionLogger.logd("Got local Bluetooth action: " + intent.getAction());
                switch (intent.getAction()) {
                    case ACTION_LOCAL_PROVISIONING_STATUS:
                        // Get a status update from managedprovisioning
                        handleStatusUpdate(intent, false);
                        break;
                    case ACTION_LOCAL_SHUTDOWN_BLUETOOTH:
                        // Shutdown this service
                        clearProxy();
                        stopSelf();
                        break;
                }
            }
        };
    }

    /**
     * Start Bluetooth network proxy. Network requests will be proxied over Bluetooth to the
     * remote device.
     *
     * <p>This method must be called from a background thread.
     * @return {@code true} if the proxy started
     */
    private boolean startProxy() {
        ProvisionLogger.logd("BluetoothConnectionService: Start proxy.");
        // Start listening
        try {
            mBluetoothClient.startGlobalProxy();
        } catch (IOException e) {
            ProvisionLogger.loge("Failure to set proxy.", e);
            return false;
        }
        mBluetoothClient.sendStatusUpdate(
                DeviceInitializerStatus.STATUS_STATE_CONNECTING_BLUETOOTH_PROXY, "Started proxy.");
        mNetworkMonitor = new NetworkMonitor(this, this);
        return true;
    }

    @Override
    public void onNetworkConnected() {
        ProvisionLogger.logw("Received Wi-Fi broadcast. Connected: " + NetworkMonitor.isConnectedToWifi(this));
        if (NetworkMonitor.isConnectedToWifi(this)) {
            ProvisionLogger.logd("Connected to a Wi-Fi network");
            clearProxy();
        }
    }

    @Override
    public void onNetworkDisconnected() {
        // Do nothing. Only care about network connect.
    }


    /**
     * Stop Bluetooth network proxy. The network proxy should be removed once a Wi-Fi connection
     * is available.
     */
    private void clearProxy() {
        ProvisionLogger.logd("Clear proxy.");
        if (mNetworkMonitor != null) {
            mNetworkMonitor.close();
            mNetworkMonitor = null;
        }
        mBluetoothClient.sendStatusUpdate(
                DeviceInitializerStatus.STATUS_STATE_DISCONNECTING_BLUETOOTH_PROXY,
                "Removing proxy.");
        mBluetoothClient.removeGlobalProxy();
    }

    @Override
    public void onDestroy() {
        clearProxy();
        if (mLocalStatusReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(mLocalStatusReceiver);
            mLocalStatusReceiver = null;
        }
        if (mStatusReceiver != null) {
            unregisterReceiver(mStatusReceiver);
            mStatusReceiver = null;
        }
        mHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
