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

package com.android.managedprovisioning.ota;

import static org.mockito.Mockito.verify;

import android.support.test.filters.SmallTest;

import com.android.managedprovisioning.task.AbstractProvisioningTask;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link TaskExecutor}.
 */
@SmallTest
public class TaskExecutorTest {
    private final int TEST_USER_ID = 123;

    @Mock AbstractProvisioningTask mTask;
    TaskExecutor mExecutor = new TaskExecutor();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testExecute() {
        // WHEN executing a task
        mExecutor.execute(TEST_USER_ID, mTask);

        // THEN run method of the task should be called
        verify(mTask).run(TEST_USER_ID);
    }
}
