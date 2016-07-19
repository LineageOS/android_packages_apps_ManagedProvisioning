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

package com.android.managedprovisioning.common;

import android.app.Dialog;
import android.app.DialogFragment;
import android.app.ProgressDialog;
import android.os.Bundle;

/**
 * Utility class wrapping a {@link ProgressDialog} in a {@link DialogFragment}
 *
 * <p>In order to properly handle {@link Dialog} lifecycle we follow the practice of wrapping them
 * in a {@link DialogFragment}.
 */
public class SimpleProgressDialog extends DialogFragment {
    private static final String MESSAGE = "message";
    private static final String CANCELED_ON_TOUCH_OUTSIDE = "canceledOnTouchOutside";

    /**
     * Use the {@link Builder} instead. Keeping the constructor public only because
     * a {@link DialogFragment} must have an empty constructor that is public.
     */
    public SimpleProgressDialog() {
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        ProgressDialog dialog = new ProgressDialog(getActivity());
        Bundle args = getArguments();

        if (args.containsKey(MESSAGE)) {
            dialog.setMessage(getText(args.getInt(MESSAGE)));
        }

        if (args.containsKey(CANCELED_ON_TOUCH_OUTSIDE)) {
            dialog.setCanceledOnTouchOutside(args.getBoolean(CANCELED_ON_TOUCH_OUTSIDE));
        }

        return dialog;
    }

    public static class Builder implements DialogBuilder {
        private Integer mMessage;
        private Boolean mCancelable;
        private Boolean mCanceledOnTouchOutside;

        /**
         * Sets the message
         * @param message Message resource id.
         */
        public Builder setMessage(int message) {
            mMessage = message;
            return this;
        }

        /**
         * Sets whether the dialog is cancelable or not.  Default is true.
         */
        public Builder setCancelable(boolean cancelable) {
            mCancelable = cancelable;
            return this;
        }

        /**
         * Sets whether the dialog is canceled when touched outside the window's
         * bounds. If setting to true, the dialog is set to be cancelable if not
         * already set.
         *
         * @param canceledOnTouchOutside Whether the dialog should be canceled when touched outside
         * the window.
         */
        public Builder setCanceledOnTouchOutside(boolean canceledOnTouchOutside) {
            mCanceledOnTouchOutside = canceledOnTouchOutside;
            return this;
        }

        /**
         * Creates an {@link SimpleProgressDialog} with the arguments supplied to this builder.
         */
        @Override
        public DialogFragment build() {
            SimpleProgressDialog instance = new SimpleProgressDialog();
            Bundle args = new Bundle();

            if (mMessage != null) {
                args.putInt(MESSAGE, mMessage);
            }

            if (mCancelable != null) {
                instance.setCancelable(mCancelable);
            }

            if (mCanceledOnTouchOutside != null) {
                args.putBoolean(CANCELED_ON_TOUCH_OUTSIDE, mCanceledOnTouchOutside);
            }

            instance.setArguments(args);
            return instance;
        }
    }
}