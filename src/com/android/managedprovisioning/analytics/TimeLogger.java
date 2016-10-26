/*
 * Copyright 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.managedprovisioning.analytics;

import static com.android.internal.logging.MetricsProto.MetricsEvent.PROVISIONING_PROVISIONING_ACTIVITY_TIME_MS;
import static com.android.internal.logging.MetricsProto.MetricsEvent.PROVISIONING_PREPROVISIONING_ACTIVITY_TIME_MS;
import static com.android.internal.logging.MetricsProto.MetricsEvent.PROVISIONING_ENCRYPT_DEVICE_ACTIVITY_TIME_MS;
import static com.android.internal.logging.MetricsProto.MetricsEvent.PROVISIONING_WEB_ACTIVITY_TIME_MS;
import static com.android.internal.util.Preconditions.checkNotNull;

import android.annotation.IntDef;
import android.content.Context;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.common.ProvisionLogger;

/**
 * Utility class to log time.
 */
public class TimeLogger {

    private final int mCategory;
    private final Context mContext;
    private final MetricsLoggerWrapper mMetricsLoggerWrapper;
    private final AnalyticsUtils mAnalyticsUtils;
    private Long mStartTime;

    @IntDef({
            PROVISIONING_PROVISIONING_ACTIVITY_TIME_MS,
            PROVISIONING_PREPROVISIONING_ACTIVITY_TIME_MS,
            PROVISIONING_ENCRYPT_DEVICE_ACTIVITY_TIME_MS,
            PROVISIONING_WEB_ACTIVITY_TIME_MS})
    public @interface TimeCategory {}

    public TimeLogger(Context context, @TimeCategory int category) {
        this(context, category, new MetricsLoggerWrapper(), new AnalyticsUtils());
    }

    @VisibleForTesting
    TimeLogger(
            Context context,
            int category,
            MetricsLoggerWrapper metricsLoggerWrapper,
            AnalyticsUtils analyticsUtils) {
        mContext = checkNotNull(context);
        mCategory = checkNotNull(category);
        mMetricsLoggerWrapper = checkNotNull(metricsLoggerWrapper);
        mAnalyticsUtils = checkNotNull(analyticsUtils);
    }

    /**
     * Notifies start time to logger.
     */
    public void start() {
        mStartTime = mAnalyticsUtils.elapsedRealTime();
        ProvisionLogger.logi(
                "TimeLogger, category:" + mCategory + ", start time:" + mStartTime);
    }

    /**
     * Notifies stop time to logger. Call is ignored if there is no start time.
     */
    public void stop() {
        // Ignore logging time if we couldn't find start time.
        if (mStartTime != null) {
            // Provisioning wouldn't run for 25 days, so int should be fine.
            final int time = (int) (mAnalyticsUtils.elapsedRealTime() - mStartTime);
            ProvisionLogger.logi("TimeLogger, category:" + mCategory + ", total time:" + time);
            // Clear stored start time, we shouldn't log total time twice for same start time.
            mStartTime = null;
            mMetricsLoggerWrapper.logAction(mContext, mCategory, time);
        } else {
            ProvisionLogger.logi("TimeLogger, category:" + mCategory + ", no start time");
        }
    }
}
