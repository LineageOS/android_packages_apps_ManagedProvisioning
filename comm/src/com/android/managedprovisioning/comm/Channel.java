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

import java.io.IOException;

/**
 * A channel controls the I/O of a single socket. It handles reading from/writing to the socket, as
 * well as parsing incoming messages and serializing outgoing messages.
 */
public interface Channel extends AutoCloseable {

    /**
     * Write a message to the socket.
     * @param packet The message to be written
     * @throws IOException If the write fails
     */
    void write(Bluetooth.CommPacket packet) throws IOException;

    /**
     * Read a message from the socket.
     * @return The parsed message
     * @throws IOException If reading or parsing fails
     */
    Bluetooth.CommPacket read() throws IOException;

    @Override
    /**
     * Close this socket
     */
    void close();

    /**
     * Determine if the socket connection held by this instance is connected.
     * @return {@code true} if this socket is connected.
     */
    boolean isConnected();

    /**
     * Flush the contents of the buffer.
     * @throws IOException
     */
    void flush() throws IOException;

    /**
     * Called when an error which could be retried is encountered.
     * @throws IOException
     */
    void reset() throws IOException;
}
