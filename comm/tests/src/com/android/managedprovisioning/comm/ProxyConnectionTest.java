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

import org.junit.Test;

import java.net.URI;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;

public class ProxyConnectionTest {
    private static final String CONNECT = "CONNECT";
    private static final String GET = "GET";
    private static final String VERSION = "HTTP/1.1";
    private static final String HTTPS = "https://";
    private static final String HTTP = "http://";
    private static final String HTTPS_PORT = "443";
    private static final String HTTP_PORT = "80";

    private void checkInfo(String exMethod, String exHost, int exPort, URI exUri,
            ProxyConnection.RequestLineInfo actual) {
        assertEquals(exMethod, actual.method);

        // compare host and port separately to ensure the URI was parsed as expected (e.g. URI will
        // happily parse "www.google.com:80" into a scheme of "www.google.com" and a host of "80").
        assertEquals(exHost, actual.uri.getHost());
        assertEquals(exPort, actual.uri.getPort());

        // still compare the full URI to ensure any extra bits match
        assertEquals(exUri, actual.uri);
    }

    @Test
    public void testTypicalConnect() throws Exception {
        String host = "www.google.com";
        int port = 443;
        String hostPort = host + ":" + port;
        URI uri = new URI(hostPort);
        String line = CONNECT + " " + uri.toString() + " " + VERSION;

        ProxyConnection.RequestLineInfo info = ProxyConnection.parseRequestLine(line);

        checkInfo(CONNECT, host, port, new URI(HTTPS + hostPort), info);
    }

    @Test
    public void testConnectWithScheme() throws Exception {
        String host = "www.google.com";
        int port = 443;
        URI uri = new URI(HTTPS + host + ":" + port);
        String line = CONNECT + " " + uri + " " + VERSION;

        ProxyConnection.RequestLineInfo info = ProxyConnection.parseRequestLine(line);

        checkInfo(CONNECT, host, port, uri, info);
    }

    @Test
    public void testConnectWithoutPort() throws Exception {
        String host = "www.google.com";
        URI uri = new URI(host);
        String line = CONNECT + " " + uri + " " + VERSION;

        ProxyConnection.RequestLineInfo info = ProxyConnection.parseRequestLine(line);

        checkInfo(CONNECT, host, Integer.parseInt(HTTPS_PORT),
                new URI(HTTPS + host + ":" + HTTPS_PORT), info);
    }

    @Test
    public void testConnectInvalidAddress() throws Exception {
        String host = "LessThan(<)IsNotAllowedInURIs";
        String line = CONNECT + " " + host + " " + VERSION;

        ProxyConnection.RequestLineInfo info = ProxyConnection.parseRequestLine(line);
        assertNull(info);
    }

    @Test
    public void testGet() throws Exception {
        String host = "www.google.com";
        int port = 8001;
        URI uri = new URI(HTTPS + host + ":" + port);
        String line = GET + " " + uri + " " + VERSION;

        ProxyConnection.RequestLineInfo info = ProxyConnection.parseRequestLine(line);

        checkInfo(GET, host, port, uri, info);
    }

    @Test
    public void testGetHttpsWithoutPort() throws Exception {
        String host = "www.google.com";
        URI uri = new URI(HTTPS + host);
        String line = GET + " " + uri + " " + VERSION;

        ProxyConnection.RequestLineInfo info = ProxyConnection.parseRequestLine(line);

        checkInfo(GET, host, Integer.parseInt(HTTPS_PORT), new URI(HTTPS + host + ":" + HTTPS_PORT),
                info);
    }

    @Test
    public void testGetHttpWithoutPort() throws Exception {
        String host = "www.google.com";
        URI uri = new URI(HTTP + host);
        String line = GET + " " + uri + " " + VERSION;

        ProxyConnection.RequestLineInfo info = ProxyConnection.parseRequestLine(line);

        checkInfo(GET, host, Integer.parseInt(HTTP_PORT), new URI(HTTP + host + ":" + HTTP_PORT),
                info);
    }

    @Test
    public void testGetAbsPath() throws Exception {
        // GET requests using abs_path in the request line are unsupported
        String path = "/foo/bar/baz.html";
        String line = GET + " " + path + " " + VERSION;

        ProxyConnection.RequestLineInfo info = ProxyConnection.parseRequestLine(line);

        assertNull(info);
    }

    @Test
    public void testGetNoScheme() throws Exception {
        // GET requests with no scheme should also fail
        String host = "www.google.com";
        int port = 80;
        URI uri = new URI(host + ":" + port);
        String line = GET + " " + uri + " " + VERSION;

        ProxyConnection.RequestLineInfo info = ProxyConnection.parseRequestLine(line);

        assertNull(info);
    }

    @Test
    public void testGarbage() {
        String data = "\u1D4D\u1D44\u1D89\u1D66\u1D43\u1D4D\u1D49";
        ProxyConnection.RequestLineInfo info = ProxyConnection.parseRequestLine(data);
        assertNull(info);
    }

}
