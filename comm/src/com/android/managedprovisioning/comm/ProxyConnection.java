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

import com.google.common.annotations.VisibleForTesting;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * The connection between a channel and a socket to a web server. This connection handles most
 * compliant proxy clients. It expects an initial CONNECT request
 * {@see http://tools.ietf.org/html/rfc2817#section-5.2}. Additionally, it can handle clients which
 * omit the CONNECT request, as long as they specify an absoluteURI in their request line.
 * {@see http://www.w3.org/Protocols/rfc2616/rfc2616-sec5.html}.
 *
 * If a client does not send a CONNECT request, and attempts to make a request using an
 * abs_path {@see http://www.w3.org/Protocols/rfc2616/rfc2616-sec5.html} in the request line, the
 * connection will be rejected.
  */
public class ProxyConnection extends Thread {

    private static final String CONNECT = "CONNECT";
    private static final String HTTPS = "https";
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

    @VisibleForTesting
    protected static class RequestLineInfo {
        String method;
        URI uri;

        public RequestLineInfo(String method, URI uri) {
            this.method = method;
            this.uri = uri;
        }
    }

    /**
     * Parse a request line. Supports CONNECT requests, as well as other requests using absoluteURI
     * {@see http://www.w3.org/Protocols/rfc2616/rfc2616-sec5.html}
     * {@see http://tools.ietf.org/html/rfc2817#section-5.2}
     * {@see https://www.ietf.org/rfc/rfc2396.txt}
     * @param requestLine The requestLine from the HTTP request
     * @return A struct containing the parsed request if parsing was successful, otherwise null.
     */
    @VisibleForTesting
    protected static RequestLineInfo parseRequestLine(String requestLine) {
        String[] split = requestLine.split(" ");

        if (split.length < 2) {
            ProvisionCommLogger.loge("Could not parse request line: " + requestLine);
            return null;
        }

        String method = split[0];
        String uriString = split[1];

        if (CONNECT.equals(method)) {
            // CONNECT request lines come through as an 'authority' element (see RFC 2396), which do
            // not contain a scheme. We don't need the scheme to open the socket, but we do need it
            // for the ProxySelector - so force it to HTTPS.
            if (!uriString.contains("://")) {
                uriString = HTTPS + "://" + uriString;
            }
        }

        URI uri;
        try {
            // parse with URL first - this is more restrictive, and catches the case where a GET
            // request comes through with an abs_path, but no host, in the request line. This
            // situation is unsupported by this proxy (but should never happen with a compliant
            // client - we should always see a CONNECT request first).
            URL url = new URL(uriString);
            uri = url.toURI();
        } catch (MalformedURLException|URISyntaxException e) {
            ProvisionCommLogger.loge(
                    "Invalid or unsupported URI in request line: " + requestLine, e);
            return null;
        }

        if (uri.getPort() < 0) {
            // If no port was specified, choose a default
            int newPort = 80;
            if (CONNECT.equals(method) || HTTPS.equals(uri.getScheme().toLowerCase())) {
                newPort = 443;
            }

            try {
                // sadly this is the only way to "mutate" a URI
                uri = new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), newPort,
                        uri.getPath(), uri.getQuery(), uri.getFragment());
            } catch (URISyntaxException e) {
                ProvisionCommLogger.loge(
                        "Invalid URI when trying to enforce https: " + requestLine, e);
                return null;
            }
        }

        return new RequestLineInfo(method, uri);
    }

    /**
     * Create a socket to the requested host. Check for an applicable proxy, and use it if found.
     * @param uri URI containing the host to connect to.
     * @return
     * @throws IOException
     */
    private boolean createSocket(URI uri) throws IOException {
        boolean usingProxy = false;

        String host = uri.getHost();
        int port = uri.getPort();

        List<Proxy> list = ProxySelector.getDefault().select(uri);
        for (Proxy proxy : list) {
            if (proxy.equals(Proxy.NO_PROXY)) {
                break; // break out and create a normal socket
            } else {
                if (proxy.address() instanceof InetSocketAddress) {
                    // Only Inets created by PacProxySelector and ProxySelectorImpl.
                    InetSocketAddress inetSocketAddress =
                            (InetSocketAddress)proxy.address();
                    // A proxy specified with an IP addr should only ever use that IP. This
                    // will ensure that the proxy only ever connects to its specified
                    // address. If the proxy is resolved, use the associated IP address. If
                    // unresolved, use the specified host name.
                    host = inetSocketAddress.isUnresolved() ?
                            inetSocketAddress.getHostName() :
                            inetSocketAddress.getAddress().getHostAddress();
                    port = inetSocketAddress.getPort();
                    usingProxy = true;
                    break;
                } else {
                    ProvisionCommLogger.logw("Unsupported Inet type from ProxySelector, skipping:" +
                            proxy.address().getClass().getSimpleName());
                }
            }
        }

        mNetSocket = new Socket(host, port);
        return usingProxy;
    }

    private void processConnect() {
        try {
            String requestLine = getLine()  + '\r' + '\n';
            RequestLineInfo info = parseRequestLine(requestLine);

            if (info == null) {
                mNetRunning = false;
                return;
            }

            boolean usingProxy;
            try {
                usingProxy = createSocket(info.uri);
            } catch (IOException e) {
                ProvisionCommLogger.loge("Failed to create socket: " +
                        info.uri.getHost() + ":" + info.uri.getPort(), e);
                mNetRunning = false;
                mNetSocket = null;
                return;
            }

            if (CONNECT.equals(info.method) && !usingProxy) {
                // If we're not talking to a proxy, and we're handling a CONNECT, we need to
                // send a response
                handleConnect();
            } else {
                // Otherwise, pass through the request line, since we already read it from the
                // stream. The BtToNetThread will shuffle the rest of the request along when it
                // starts up.
                mNetSocket.getOutputStream().write(requestLine.getBytes());
            }

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
