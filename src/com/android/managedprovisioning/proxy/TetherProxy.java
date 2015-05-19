/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.ProxyInfo;

import com.android.managedprovisioning.ProvisionLogger;
import com.android.managedprovisioning.comm.Bluetooth.NetworkData;
import com.android.managedprovisioning.comm.Channel;
import com.android.managedprovisioning.comm.PacketUtil;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

/**
 * Sets up a connection between a server socket and a Channel connection. The output from the server
 * socket is written to the channel, and output from the channel is written back to the socket.
 * This allows for web access without an active web connection. All that is needed is a channel
 * connection to a device with a web connection.
 */
public class TetherProxy extends Thread {

    private static final String LOCALHOST = "localhost";
    private static final int SERVER_PORT = 0;
    private String mLocalPort = "8080";

    private boolean mRunning = false;

    private Context mContext = null;

    private ServerSocket mServerSocket;
    private final ReliableChannel mChannel;
    private int mConnectionIndex;
    private ChannelInputDispatcher mDispatcher;
    private List<Socket> mSockets;

    private final PacketUtil mPacketUtil;

    public TetherProxy(Context context, ReliableChannel channel, PacketUtil packetUtil) {
        mContext = context;
        mChannel = channel;
        mPacketUtil = packetUtil;
        mSockets = new ArrayList<>();
    }

    /**
     * Removes the global proxy from the device. This prevents new connections from using the
     * proxy but does not close existing connections through the proxy.
     */
    public void clearProxy() {
        if (isProxySet()) {
            try {
                ConnectivityManager cm = (ConnectivityManager)
                        mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
                cm.setGlobalProxy(null);
                ProvisionLogger.logd("Global proxy removed.");
            } catch (Exception e) {
                ProvisionLogger.loge("Problem setting proxy", e);
            }
        }
    }

    private boolean isProxySet() {
        return LOCALHOST.equals(System.getProperty("http.proxyHost"))
                && mLocalPort.equals(System.getProperty("http.proxyPort"));
    }

    private void setProxy(String host, int port) {
        try {
            ProxyInfo p = ProxyInfo.buildDirectProxy(host, port);
            ConnectivityManager cm =
                    (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            cm.setGlobalProxy(p);
        } catch (Exception e) {
            ProvisionLogger.loge("Problem setting proxy", e);
        }
    }

    /**
     * Starts the proxy server and returns the port it is hosted on.
     */
    public void startServer() throws IOException {
        mRunning = false;
        ProvisionLogger.logd("Running bluetooth web server");

        mServerSocket = new ServerSocket(SERVER_PORT);
        mServerSocket.setReuseAddress(true);

        int port = mServerSocket.getLocalPort();
        mLocalPort = Integer.toString(port);
        setProxy(LOCALHOST, port);
        mDispatcher = new ChannelInputDispatcher(mChannel);
        mDispatcher.start();
        mConnectionIndex = 0;
        start();
    }

    public boolean isShutdown() {
        return (mChannel.isSocketConnected() && mServerSocket.isClosed());
    }

    // Close Bluetooth connection, close server.
    public synchronized void stopServer() {
        ProvisionLogger.logd("Stopping BluetoothServer");

        mRunning = false;
        if (mServerSocket != null) {
            try {
                mServerSocket.close();
            } catch (IOException e) {
                ProvisionLogger.logd(e);
            }
        }

        try {
            join();
        } catch (InterruptedException e) {
            ProvisionLogger.logd(e);
        }
    }

    public void clearConnections() {
        for (Socket s : mSockets) {
            try {
                s.getInputStream().close();
            } catch (IOException e) {
                // Don't care.
            }
            try {
                s.getOutputStream().close();
            } catch (IOException e) {
                // Don't care.
            }
            try {
                s.close();
            } catch (IOException e) {
                // Don't care.
            }
        }
        mSockets.clear();
        mDispatcher.clearConnections();
    }

    @Override
    public void run() {
        try {
            mRunning = true;
            ProvisionLogger.logd("Server waiting to accept incoming connection...");
            while (mRunning) {
                final Socket socket = mServerSocket.accept();

                mDispatcher.addStream(mConnectionIndex, socket.getOutputStream());
                BluetoothWriter socketConn = new BluetoothWriter(socket.getInputStream(),
                        mConnectionIndex);
                socketConn.start();
                mSockets.add(socket);
                mConnectionIndex++;
            }

        } catch (SocketException e) {
            ProvisionLogger.logd(e);
        } catch (IOException e) {
            ProvisionLogger.logd(e);
        } finally {
            clearProxy();
            try {
                mServerSocket.close();
            } catch (IOException ex) {
                ProvisionLogger.logw("Could not close output.", ex);
            }
        }

        mRunning = false;
    }

    /**
     * Receives network requests from this device through the proxy and sends
     * that request data
     */
    private class BluetoothWriter extends Thread {

        private final InputStream mInput;
        private final int mConnectionId;

        public BluetoothWriter(InputStream fromSocket, int connectionId) {
            mInput = fromSocket;
            mConnectionId = connectionId;
        }

        @Override
        public void run() {
            final byte[] buffer = new byte[16384];

            try {
                while (mRunning) {
                    int bytesRead = mInput.read(buffer);
                    if (bytesRead < 0) {
                        break;
                    }
                    mChannel.write(mPacketUtil.createDataPacket(mConnectionId,
                            NetworkData.OK, buffer, bytesRead));
                }
                ProvisionLogger.logv("BluetoothWriter #" + mConnectionId
                        + " reached end of socket input stream and is closing.");
                mChannel.write(mPacketUtil.createDataPacket(mConnectionId,
                        NetworkData.EOF, null, 0));
            } catch (IOException ioe) {
                if (mRunning) {
                    ProvisionLogger.logd("BluetoothWriter #" + mConnectionId + " ending", ioe);
                }
            }
        }
    }
}
