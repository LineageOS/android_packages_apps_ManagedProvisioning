/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.managedprovisioning.provisioning;


import static com.google.common.truth.Truth.assertThat;

import androidx.test.filters.SmallTest;

import org.junit.Test;

@SmallTest
public class ProvisioningViewModelTest {

    private static final int CURRENT_TRANSITION_SCREEN = 4;

    private final ProvisioningViewModel mViewModel = new ProvisioningViewModel();

    @Test
    public void getCurrentTransitionScreen_defaultsToZero() {
        assertThat(mViewModel.getCurrentTransitionScreen()).isEqualTo(0);
    }

    @Test
    public void setCurrentTransitionScreen_works() {
        mViewModel.setCurrentTransitionScreen(CURRENT_TRANSITION_SCREEN);
        assertThat(mViewModel.getCurrentTransitionScreen()).isEqualTo(CURRENT_TRANSITION_SCREEN);
    }
}
