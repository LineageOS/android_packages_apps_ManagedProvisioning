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

import com.android.managedprovisioning.comm.Bluetooth.CommPacket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.google.protobuf.nano.CodedInputByteBufferNano;
import com.google.protobuf.nano.CodedOutputByteBufferNano;
import com.google.protobuf.nano.MessageNano;

/**
 * A CommPacketChannel is an implementation of {@link Channel} which allows communication over a
 * SocketWrapper using Protobuf messages.
 */
public class CommPacketChannel implements Channel {
    protected final SocketWrapper mSocket;

    // Used to synchronize writes while allowing simultaneous read/writing.
    protected final Object mWriteLock = new Object();

    public CommPacketChannel(SocketWrapper socket) {
        mSocket = socket;
    }

    // GuardedBy(mWriteLock)
    @Override
    public void write(CommPacket packet)
            throws IOException {
        synchronized (mWriteLock) {
            OutputStream outputStream = mSocket.getOutputStream();

            byte[] array = serializePacket(preparePacket(packet));

            outputStream.write(array);
            outputStream.flush();
        }
    }

    /**
     * Prepare a packet before serializing it. This exists to provide a hook for child classes that
     * need to transform outgoing messages. This implementation is a no-op.
     * @param packet The packet which is being sent
     * @return The transformed packet
     */
    protected MessageNano preparePacket(CommPacket packet) {
        return packet;
    }

    /**
     * Serialize a packet to a byte array. The returned byte array will be written to the socket.
     * @param packet The packet to serialize.
     * @return A byte array containing the serialized packet.
     * @throws IOException If serialization fails due to a size mismatch.
     */
    protected byte[] serializePacket(MessageNano packet) throws IOException {
        int size = packet.getSerializedSize();
        int delimitSize = CodedOutputByteBufferNano.computeRawVarint32Size(size);
        byte[] array = new byte[size + delimitSize];
        CodedOutputByteBufferNano outputBuffer = CodedOutputByteBufferNano.newInstance(array);
        outputBuffer.writeRawVarint32(size);
        packet.writeTo(outputBuffer);
        if (outputBuffer.spaceLeft() != 0) {
            throw new IOException("Incorrect size calculated");
        }
        return array;
    }

    @Override
    public synchronized CommPacket read() throws IOException {
        return read(mSocket.getInputStream());
    }

    @SuppressWarnings("unchecked")
    protected synchronized CommPacket read(InputStream inputStream) throws IOException {
        CodedInputByteBufferNano inputBuffer = readByteBuffer(inputStream);
        try {
            return readPacket(inputBuffer);
        } catch (ClassCastException e) {
            ProvisionCommLogger.loge("Incorrect type called for return value", e);
            return null;
        }
    }

    protected CodedInputByteBufferNano readByteBuffer(InputStream inputStream) throws IOException {
        byte[] readBuffer = new byte[512];
        int index = 0;
        // Read bytes while the most significant bit is set.  The CodedInputByteBufferNano from
        // proto-nano only reads up to 10 bytes, so we will do the same.
        do {
            while (inputStream.read(readBuffer, index, 1) <= 0);
        } while ((readBuffer[index++] < 0) && (index < 10));

        CodedInputByteBufferNano inputBuffer =
                CodedInputByteBufferNano.newInstance(readBuffer, 0, index);
        int size = inputBuffer.readRawVarint32();
        byte[] buffer = new byte[size];
        int readIndex = 0;
        while (readIndex < size) {
            int amount = inputStream.read(buffer, readIndex, size - readIndex);
            if (amount > 0) {
                readIndex += amount;
            }
        }
        return CodedInputByteBufferNano.newInstance(buffer);
    }

    protected CommPacket readPacket(CodedInputByteBufferNano inputBuffer)
            throws IOException {
        CommPacket packet = Bluetooth.CommPacket.parseFrom(inputBuffer);
        return packet;
    }

    @Override
    public void close() {
        try {
            mSocket.close();
        } catch (IOException ioe) {
            ProvisionCommLogger.logw(ioe);
        }
    }

    /**
     * Determine if the socket connection held by this instance is connected.
     * @return {@code true} if this socket is connected.
     */
    @Override
    public boolean isConnected() {
        return mSocket.isConnected();
    }

    /**
     * Flushes the contents of the buffer.  For unbuffered channels, this does nothing.
     * @throws IOException
     */
    @Override
    public void flush() throws IOException {
    }

    /**
     * Recreate the socket. Called when a retriable error is encountered.
     * @throws IOException
     */
    @Override
    public void reset() throws IOException {
        mSocket.recreate();
    }
}
