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

/**
 * A factory for CommPacketChannels
 */
public class CommPacketChannelFactory implements ChannelFactory {

    /**
     * Create a new CommPacketChannel
     * @param wrapper The socket which the Channel should wrap
     * @return The new CommPacketChannel
     */
    @Override
    public Channel newInstance(SocketWrapper wrapper) {
        return new CommPacketChannel(wrapper);
    }
}
