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

import static com.android.internal.util.Preconditions.checkNotNull;

import android.annotation.DrawableRes;
import android.annotation.StringRes;

/**
 * A wrapper describing the contents of an education screen.
 */
final class TransitionScreenWrapper {
    public final @StringRes int header;
    public final @DrawableRes int drawable;
    public final @StringRes int subHeaderTitle;
    public final @StringRes int subHeader;
    public final @DrawableRes int subHeaderIcon;
    public final boolean showContactAdmin;
    public final boolean shouldLoop;
    public final @StringRes int secondarySubHeaderTitle;
    public final @StringRes int secondarySubHeader;
    public final @DrawableRes int secondarySubHeaderIcon;

    TransitionScreenWrapper(@StringRes int header, @DrawableRes int drawable) {
        this(header, drawable, /* subHeader */ 0, /* showContactAdmin */ false,
                /* shouldLoop */ true);
    }

    TransitionScreenWrapper(@StringRes int header, @DrawableRes int drawable,
            @StringRes int subHeader, boolean showContactAdmin, boolean shouldLoop) {
        this(header, drawable, 0, subHeader, 0, showContactAdmin, shouldLoop, 0, 0, 0);
    }

    private TransitionScreenWrapper(int header, int drawable, int subHeaderTitle, int subHeader,
            int subHeaderIcon, boolean showContactAdmin, boolean shouldLoop,
            int secondarySubHeaderTitle, int secondarySubHeader, int secondarySubHeaderIcon) {
        this.header = checkNotNull(header,
                "Header resource id must be a positive number.");
        this.drawable = checkNotNull(drawable,
                "Drawable resource id must be a positive number.");
        this.subHeaderTitle = subHeaderTitle;
        this.subHeader = subHeader;
        this.subHeaderIcon = subHeaderIcon;
        this.showContactAdmin = showContactAdmin;
        this.shouldLoop = shouldLoop;
        this.secondarySubHeaderTitle = secondarySubHeaderTitle;
        this.secondarySubHeader = secondarySubHeader;
        this.secondarySubHeaderIcon = secondarySubHeaderIcon;
    }

    public static final class Builder {
        @StringRes int mHeader;
        @DrawableRes int mDrawable;
        @StringRes private int mSubHeaderTitle;
        @StringRes int mSubHeader;
        @DrawableRes int mSubHeaderIcon;
        boolean mShowContactAdmin;
        boolean mShouldLoop;
        @StringRes int mSecondarySubHeaderTitle;
        @StringRes int mSecondarySubHeader;
        @DrawableRes int mSecondarySubHeaderIcon;

        public Builder setHeader(int header) {
            mHeader = header;
            return this;
        }

        public Builder setAnimation(int drawable) {
            mDrawable = drawable;
            return this;
        }

        public Builder setSubHeaderTitle(int subHeaderTitle) {
            mSubHeaderTitle = subHeaderTitle;
            return this;
        }

        public Builder setSubHeader(int subHeader) {
            mSubHeader = subHeader;
            return this;
        }

        public Builder setSubHeaderIcon(int subHeaderIcon) {
            mSubHeaderIcon = subHeaderIcon;
            return this;
        }

        public Builder setShowContactAdmin(boolean showContactAdmin) {
            mShowContactAdmin = showContactAdmin;
            return this;
        }

        public Builder setShouldLoop(boolean shouldLoop) {
            mShouldLoop = shouldLoop;
            return this;
        }

        public Builder setSecondarySubHeaderTitle(int secondarySubHeaderTitle) {
            mSecondarySubHeaderTitle = secondarySubHeaderTitle;
            return this;
        }

        public Builder setSecondarySubHeader(int secondarySubHeader) {
            mSecondarySubHeader = secondarySubHeader;
            return this;
        }

        public Builder setSecondarySubHeaderIcon(int secondarySubHeaderIcon) {
            mSecondarySubHeaderIcon = secondarySubHeaderIcon;
            return this;
        }

        public TransitionScreenWrapper build() {
            return new TransitionScreenWrapper(mHeader, mDrawable, mSubHeaderTitle, mSubHeader,
                    mSubHeaderIcon, mShowContactAdmin, mShouldLoop, mSecondarySubHeaderTitle,
                    mSecondarySubHeader, mSecondarySubHeaderIcon);
        }
    }
}
