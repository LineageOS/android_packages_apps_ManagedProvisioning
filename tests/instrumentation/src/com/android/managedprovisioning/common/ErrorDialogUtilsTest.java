/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.managedprovisioning.common;

import static com.android.managedprovisioning.TestUtils.assertIntentsEqual;

import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import com.android.managedprovisioning.util.LazyStringResource;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ErrorDialogUtilsTest {
    private static final int DIALOG_TITLE_ID = 1;
    private static final String ERROR_MESSAGE = "message";
    private static final boolean IS_FACTORY_RESET_REQUIRED = true;

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private static final ErrorWrapper ERROR_WRAPPER = new ErrorWrapper(
            DIALOG_TITLE_ID,
            LazyStringResource.of(ERROR_MESSAGE),
            IS_FACTORY_RESET_REQUIRED);
    private static final ErrorWrapper ERROR_WRAPPER_INVALID_RES_IDS = new ErrorWrapper(
            /* dialogTitleId= */ 0,
            /* errorMessageResId= */ null,
            IS_FACTORY_RESET_REQUIRED);
    private static final Intent EXPECTED_INTENT = new Intent()
            .putExtra(ErrorDialogUtils.EXTRA_DIALOG_TITLE_ID, DIALOG_TITLE_ID)
            .putExtra(ErrorDialogUtils.EXTRA_ERROR_MESSAGE_RES, ERROR_MESSAGE)
            .putExtra(ErrorDialogUtils.EXTRA_FACTORY_RESET_REQUIRED, IS_FACTORY_RESET_REQUIRED);
    private static final Intent EXPECTED_INTENT_FOR_INVALID_RES_IDS = new Intent()
            .putExtra(ErrorDialogUtils.EXTRA_FACTORY_RESET_REQUIRED, IS_FACTORY_RESET_REQUIRED);

    @Test
    public void createResultIntent_works() {
        Intent resultIntent = ErrorDialogUtils.createResultIntent(ERROR_WRAPPER, mContext);

        assertIntentsEqual(resultIntent, EXPECTED_INTENT);
    }

    @Test
    public void createResultIntent_invalidResIds_works() {
        Intent resultIntent = ErrorDialogUtils.createResultIntent(ERROR_WRAPPER_INVALID_RES_IDS,
                mContext);

        assertIntentsEqual(resultIntent, EXPECTED_INTENT_FOR_INVALID_RES_IDS);
    }

    @Test
    public void createResultIntent_nullErrorWrapper_throwsException() {
        assertThrows(NullPointerException.class,
                () -> ErrorDialogUtils.createResultIntent(/* errorWrapper= */ null, mContext));
    }
}
