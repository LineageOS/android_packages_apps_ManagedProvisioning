/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.managedprovisioning.provisioning.crossprofile;

import static com.android.internal.util.Preconditions.checkNotNull;

import android.annotation.Nullable;
import android.content.pm.ApplicationInfo;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/** A single item in the list of default OEM cross-profile apps for the user to accept or deny. */
class CrossProfileItem implements Parcelable {
    private final String appTitle;
    private final String summary;
    private final ApplicationInfo appInfo;

    private CrossProfileItem(Builder builder) {
        this.appTitle = checkNotNull(builder.appTitle);
        this.summary = checkNotNull(builder.summary);
        this.appInfo = checkNotNull(builder.appInfo);
    }

    private CrossProfileItem(Parcel source) {
        this.appTitle = source.readString();
        this.summary = source.readString();
        this.appInfo = source.readTypedObject(ApplicationInfo.CREATOR);
    }

    String appTitle() {
        return appTitle;
    }

    String summary() {
        return summary;
    }

    ApplicationInfo appInfo() {
        return appInfo;
    }

    @Override
    public boolean equals(@Nullable Object object) {
        if (object instanceof CrossProfileItem) {
            CrossProfileItem that = (CrossProfileItem) object;
            return this.appTitle.equals(that.appTitle)
                    && this.summary.equals(that.summary)
                    && this.appInfo.equals(that.appInfo);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(appTitle, summary, appInfo);
    }

    static Builder builder() {
        return new Builder();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(appTitle);
        dest.writeString(summary);
        dest.writeTypedObject(appInfo, flags);
    }

    public static final Parcelable.Creator<CrossProfileItem> CREATOR =
            new Parcelable.Creator<CrossProfileItem>() {
                public CrossProfileItem createFromParcel(Parcel source) {
                    return new CrossProfileItem(source);
                }

                public CrossProfileItem[] newArray(int size) {
                    return new CrossProfileItem[size];
                }
            };

    static class Builder {
        private String appTitle;
        private String summary;
        private ApplicationInfo appInfo;

        Builder setAppTitle(String appTitle) {
            this.appTitle = appTitle;
            return this;
        }

        Builder setSummary(String summary) {
            this.summary = summary;
            return this;
        }

        Builder setAppInfo(ApplicationInfo appInfo) {
            this.appInfo = appInfo;
            return this;
        }

        CrossProfileItem build() {
            return new CrossProfileItem(this);
        }
    }
}
