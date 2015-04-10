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

package com.android.managedprovisioning.comm;

import com.android.managedprovisioning.comm.Bluetooth.NetworkData;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * The connection between a channel and a socket to a web server. It does a basic check on the
 * first line and then passes through all data like a dummy proxy.
 */
public class ProxyConnection extends Thread {

    private static final String CONNECT = "CONNECT";

    private static final String RESPONSE_OK = "HTTP/1.1 200 OK\n\n";

    private NetToBtThread mNetToBt;
    private BtToNetThread mBtToNet;
    private volatile boolean mNetRunning;

    private Socket mNetSocket;
    private final PipedInputStream mHttpInput;
    private final OutputStream mHttpOutput;
    private final int mConnId;
    private final Channel mChannel;

    /**
     * Used to create network data response packets. The device Id can be empty because this is
     * called from the programmer device.
     */
    private final PacketUtil mPacketUtil = new PacketUtil("");

    public ProxyConnection(Channel channel, int connId) {
        mChannel = channel;
        mConnId = connId;
        mHttpInput = new PipedInputStream();
        mHttpOutput = new PipedOutputStream();
        try {
            mHttpInput.connect((PipedOutputStream) mHttpOutput);
        } catch (IOException e) {
            // The streams were just created so this shouldn't happen.
            ProvisionCommLogger.loge(e);
        }
        mNetRunning = true;
    }

    public boolean isRunning() {
        return mNetRunning;
    }

    public void shutdown() {
        ProvisionCommLogger.logd("Shutting down ConnectionProcessor");
        try {
            mHttpOutput.close();
        } catch (IOException io) {
            ProvisionCommLogger.logd(io);
        }
        endConnection();
    }

    @Override
    public void run() {
        ProvisionCommLogger.logd("Creating a new socket.");
        processConnect();
    }

    private void endConnection() {
        try {
            if (mChannel != null) {
                mChannel.write(mPacketUtil.createEndPacket(mConnId));
            } else {
                ProvisionCommLogger.logd(
                        "Attempted to write end of connection with null connection");
            }
        } catch (IOException io) {
            ProvisionCommLogger.logd("Could not write closing packet.", io);
        }
        try {
            if (mNetSocket != null) {
                mNetSocket.close();
            }
        } catch (IOException io) {
            ProvisionCommLogger.logd("Attempted to close socket when already closed.", io);
        }

        ProvisionCommLogger.logd("Ended connection");
    }

    private class NetToBtThread extends Thread {
        @Override
        public void run() {
            final byte[] buffer = new byte[16384];

            InputStream input = null;
            try {
                input = mNetSocket.getInputStream();
                while (mNetSocket.isConnected()) {
                    int readBytes = input.read(buffer);
                    if (readBytes < 0) {
                        ProvisionCommLogger.logd("Passing " + readBytes + " bytes");
                        mChannel.write(mPacketUtil.createEndPacket(mConnId));
                        break;
                    }
                    ProvisionCommLogger.logd("Passing " + readBytes + " bytes");
                    mChannel.write(mPacketUtil.createDataPacket(mConnId, NetworkData.OK, buffer,
                            readBytes));
                }
            } catch (IOException io) {
                ProvisionCommLogger.logd("Server socket input stream is closed.");
            } finally {
                if (input != null) {
                    try {
                        input.close();
                    } catch (IOException ex) {
                        ProvisionCommLogger.logw(
                                "Failed to close connection", ex);
                    }
                }
            }
            ProvisionCommLogger.logd("SocketReader is ending.");
            mNetRunning = false;
        }
    }

    private class BtToNetThread extends Thread {
        @Override
        public void run() {
            final byte[] buffer = new byte[16384];
            try {
                while (true) {
                    int readBytes = mHttpInput.read(buffer);
                    if (readBytes < 0) {
                        break;
                    }

                    if (mNetSocket == null) {
                        break;
                    } else {
                        mNetSocket.getOutputStream().write(buffer, 0, readBytes);
                    }
                }
            } catch (IOException io) {
                ProvisionCommLogger.logd("Bluetooth input stream for this connection is closed.");
            } finally {
                try {
                    mHttpInput.close();
                } catch (IOException ex) {
                    ProvisionCommLogger.logw("Failed to close connection", ex);
                }
            }
            ProvisionCommLogger.logd("SocketWriter is ending.");
        }
    }

    private String getLine() throws IOException {
        ProvisionCommLogger.logi("getLine");
        StringBuilder buffer = new StringBuilder();
        int ch;
        while ((ch = mHttpInput.read()) != -1) {
            if (ch == '\r')
                continue;
            if (ch == '\n')
                break;
            buffer.append((char) ch);
        }
        ProvisionCommLogger.logi("Proxy reading: " + buffer);

        return buffer.toString();
    }

    private void processConnect() {
        try {
            String requestLine = getLine()  + '\r' + '\n';
            String[] split = requestLine.split(" ");

            String method = split[0];
            String uri = split[1];

            ProvisionCommLogger.logi("Method: " + method);
            String host = "";
            int port = 80;
            String toSend = "";

            if (CONNECT.equals(method)) {
                String[] hostPortSplit = uri.split(":");
                host = hostPortSplit[0];
                try {
                    port = Integer.parseInt(hostPortSplit[1]);
                } catch (NumberFormatException nfe) {
                    port = 443;
                }
                uri = "Https://" + host + ":" + port;
            } else {
                try {
                    URI url = new URI(uri);
                    host = url.getHost();
                    port = url.getPort();
                    if (port < 0) {
                        port = 80;
                    }
                } catch (URISyntaxException e) {
                    ProvisionCommLogger.logw("Trying to proxy invalid URL", e);
                    mNetRunning = false;
                    return;
                }
                toSend = requestLine;
            }

            List<Proxy> list = new ArrayList<Proxy>();
            try {
                list = ProxySelector.getDefault().select(new URI(uri));
            } catch (URISyntaxException e) {
                ProvisionCommLogger.loge("Unable to parse URI from request", e);
            }
            for (Proxy proxy : list) {
                try {
                    if (proxy.equals(Proxy.NO_PROXY)) {
                        mNetSocket = new Socket(host, port);
                        if (CONNECT.equals(method)) {
                            handleConnect();
                        } else {
                            toSend = requestLine;
                        }
                    } else {
                        if (proxy.address() instanceof InetSocketAddress) {
                            // Only Inets created by PacProxySelector and ProxySelectorImpl.
                            InetSocketAddress inetSocketAddress =
                                    (InetSocketAddress)proxy.address();
                            // A proxy specified with an IP addr should only ever use that IP. This
                            // will ensure that the proxy only ever connects to its specified
                            // address. If the proxy is resolved, use the associated IP address. If
                            // unresolved, use the specified host name.
                            String hostName = inetSocketAddress.isUnresolved() ?
                                    inetSocketAddress.getHostName() :
                                    inetSocketAddress.getAddress().getHostAddress();
                            mNetSocket = new Socket(hostName, inetSocketAddress.getPort());
                            toSend = requestLine;
                        } else {
                            ProvisionCommLogger.logw("Unsupported Inet Type from ProxySelector");
                            continue;
                        }
                    }
                } catch (IOException ioe) {

                }
                if (mNetSocket != null) {
                    break;
                }
            }
            if (mNetSocket == null) {
                mNetSocket = new Socket(host, port);
                if (CONNECT.equals(method)) {
                    handleConnect();
                } else {
                    toSend = requestLine;
                }
            }

            // For HTTP or PROXY, send the request back out.
            mNetSocket.getOutputStream().write(toSend.getBytes());

            mNetToBt = new NetToBtThread();
            mNetToBt.start();
            mBtToNet = new BtToNetThread();
            mBtToNet.start();
        } catch (Exception e) {
            ProvisionCommLogger.logd(e);
            mNetRunning = false;
        }
    }

    public void closePipe() {
        try {
            mHttpInput.close();
        } catch (IOException e) {
            ProvisionCommLogger.logd(e);
        }
        try {
            mHttpOutput.close();
        } catch (IOException e) {
            ProvisionCommLogger.logd(e);
        }
    }

    private void handleConnect() throws IOException {
        while (getLine().length() != 0);
        // No proxy to respond so we must.
        mChannel.write(mPacketUtil.createDataPacket(mConnId, NetworkData.OK,
                RESPONSE_OK.getBytes(),
                RESPONSE_OK.length()));
    }

    public OutputStream getOutput() {
        return mHttpOutput;
    }
}
