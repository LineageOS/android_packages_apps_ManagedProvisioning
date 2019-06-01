/*
 * Copyright (C) 2019 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.managedprovisioning.analytics;

import com.android.managedprovisioning.DevicePolicyProtos.DevicePolicyEvent;
import com.android.managedprovisioning.common.ProvisionLogger;

import static com.android.internal.util.Preconditions.checkNotNull;

import android.annotation.Nullable;
import android.app.admin.DevicePolicyEventLogger;
import android.os.AsyncTask;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Reads the logs from the {@link InputStream} written to by {@link DeferredMetricsWriter}
 * and writes them to another {@link MetricsWriter}.
 *
 * @see DeferredMetricsWriter
 */
public class DeferredMetricsReader {

    private final MetricsWriter mMetricsWriter;
    private final File mFile;

    /**
     * Constructs a new {@link DeferredMetricsReader}.
     *
     * <p>The specified {@link File} is deleted after everything has been read from it.
     */
    public DeferredMetricsReader(File file, MetricsWriter metricsWriter) {
        mMetricsWriter = checkNotNull(metricsWriter);
        mFile = checkNotNull(file);
    }

    /**
     * Asynchronously reads the logs from the {@link File} specified in the constructor
     * and writes them to the specified {@link MetricsWriter}.
     *
     * <p>The {@link File} will be deleted after they are written to the {@link MetricsWriter}.
     */
    public void dumpMetricsAndClearFile() {
        new ReadDeferredMetricsAsyncTask(mFile, mMetricsWriter).execute();
    }

    private static class ReadDeferredMetricsAsyncTask extends AsyncTask<Void, Void, Void> {
        private final MetricsWriter mMetricsWriter;
        private final File mFile;

        ReadDeferredMetricsAsyncTask(File file,
                MetricsWriter metricsWriter) {
            mFile = checkNotNull(file);
            mMetricsWriter = checkNotNull(metricsWriter);
        }

        @Override
        protected Void doInBackground(Void... voids) {
            try (InputStream inputStream =  new FileInputStream(mFile)) {
                DevicePolicyEvent event;
                while ((event = DevicePolicyEvent.parseDelimitedFrom(inputStream)) != null) {
                    mMetricsWriter.write(devicePolicyEventToLogger(event));
                }
            } catch (IOException e) {
                ProvisionLogger.loge(
                        "Could not parse DevicePolicyEvent while reading from stream.", e);
            } finally {
                mFile.delete();
            }
            return null;
        }

        private DevicePolicyEventLogger devicePolicyEventToLogger(DevicePolicyEvent event) {
            final DevicePolicyEventLogger eventLogger = DevicePolicyEventLogger
                    .createEvent(event.getEventId())
                    .setAdmin(event.getAdminPackageName())
                    .setInt(event.getIntegerValue())
                    .setBoolean(event.getBooleanValue())
                    .setTimePeriod(event.getTimePeriodMillis());
            if (event.getStringListValueCount() > 0) {
                eventLogger.setStrings(event.getStringListValueList().toArray(new String[0]));
            }
            return eventLogger;
        }
    }
}
