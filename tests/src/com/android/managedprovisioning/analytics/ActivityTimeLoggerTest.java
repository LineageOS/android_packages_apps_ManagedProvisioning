/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.managedprovisioning.analytics;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit-tests for {@link ActivityTimeLogger}.
 */
@SmallTest
public class ActivityTimeLoggerTest extends AndroidTestCase {

    private static final int CATEGORY = 1;
    private static final long START_TIME_MS = 1500;
    private static final long STOP_TIME_MS = 2500;

    private ActivityTimeLogger mActivityTimeLogger;

    @Mock private Context mContext;
    @Mock private MetricsLoggerWrapper mMetricsLoggerWrapper;
    @Mock private AnalyticsUtils mAnalyticsUtils;

    @Override
    public void setUp() {
        // this is necessary for mockito to work
        System.setProperty("dexmaker.dexcache", getContext().getCacheDir().toString());

        MockitoAnnotations.initMocks(this);

        mActivityTimeLogger = new ActivityTimeLogger(mContext, CATEGORY,
                mMetricsLoggerWrapper, mAnalyticsUtils);
    }

    @SmallTest
    public void testActivityTime_withStartTime() {
        // GIVEN that START_TIME_MS is the elapsed real time.
        when(mAnalyticsUtils.elapsedRealTime()).thenReturn(START_TIME_MS);
        // WHEN the activity starts.
        mActivityTimeLogger.start();

        // GIVEN that STOP_TIME_MS is the elapsed real time.
        when(mAnalyticsUtils.elapsedRealTime()).thenReturn(STOP_TIME_MS);
        // WHEN the activity stops.
        mActivityTimeLogger.stop();

        // THEN time taken by activity should be logged and the value should be stop time - the
        // start time.
        verify(mMetricsLoggerWrapper).logAction(mContext, CATEGORY,
                (int) (STOP_TIME_MS - START_TIME_MS));
    }

    @SmallTest
    public void testActivityTime_withStartTime_stopsTwice() {
        // GIVEN that START_TIME_MS is the elapsed real time.
        when(mAnalyticsUtils.elapsedRealTime()).thenReturn(START_TIME_MS);
        // WHEN the activity starts.
        mActivityTimeLogger.start();

        // GIVEN that STOP_TIME_MS is the elapsed real time.
        when(mAnalyticsUtils.elapsedRealTime()).thenReturn(STOP_TIME_MS);
        // WHEN the activity stops.
        mActivityTimeLogger.stop();

        // THEN time taken by activity should be logged and the value should be stop time - the
        // start time.
        verify(mMetricsLoggerWrapper).logAction(mContext, CATEGORY,
                (int) (STOP_TIME_MS - START_TIME_MS));

        // WHEN the activity stops again.
        mActivityTimeLogger.stop();
        // THEN nothing should be logged.
        verifyNoMoreInteractions(mMetricsLoggerWrapper);
    }

    @SmallTest
    public void testActivityTime_withoutStartTime() {
        // GIVEN activity was never started.
        // WHEN the activity stops.
        mActivityTimeLogger.stop();
        // THEN nothing should be logged.
        verifyZeroInteractions(mMetricsLoggerWrapper);
    }
}
