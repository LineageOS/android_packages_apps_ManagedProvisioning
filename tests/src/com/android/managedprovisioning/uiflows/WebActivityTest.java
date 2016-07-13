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
package com.android.managedprovisioning.uiflows;

import android.app.Instrumentation;
import android.support.test.InstrumentationRegistry;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.SmallTest;
import android.support.test.rule.UiThreadTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.test.ActivityUnitTestCase;
import android.view.ViewGroup;
import android.webkit.WebView;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class WebActivityTest extends ActivityUnitTestCase<WebActivity> {
    private static final String TEST_URL = "http://www.test.com/support";
    @Rule
    public UiThreadTestRule uiThreadTestRule = new UiThreadTestRule();

    public WebActivityTest() {
        super(WebActivity.class);
    }

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @UiThreadTest
    @Test
    public void testNoUrl() {
        startActivity(WebActivity.createIntent(getInstrumentation().getTargetContext(),
                null, null), null, null);
        assertTrue(isFinishCalled());
    }

    @UiThreadTest
    @Test
    public void testUrlLaunched() {
        startActivity(WebActivity.createIntent(getInstrumentation().getTargetContext(),
                TEST_URL, null), null, null);
        assertFalse(isFinishCalled());
        WebView webView = (WebView) ((ViewGroup) getActivity().findViewById(android.R.id.content))
                .getChildAt(0);
        assertEquals(TEST_URL, webView.getUrl());
    }

    @Override
    public Instrumentation getInstrumentation() {
        return InstrumentationRegistry.getInstrumentation();
    }
}
