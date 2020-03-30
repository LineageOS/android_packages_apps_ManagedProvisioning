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

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.provisioning.crossprofile.CrossProfileAdapter.CrossProfileViewHolder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The recycler adapter for the default OEM cross-profile apps for the user to accept or deny. */
class CrossProfileAdapter extends RecyclerView.Adapter<CrossProfileViewHolder> {
    private final Context mContext;
    private final List<CrossProfileItem> mCrossProfileItems = new ArrayList<>();
    private final Map<CrossProfileItem, Boolean> mStartingToggleStates;

    CrossProfileAdapter(Context context, List<CrossProfileItem> newCrossProfileItems) {
        this(context, newCrossProfileItems, /* startingToggleStates= */ new HashMap<>());
    }

    CrossProfileAdapter(
            Context context,
            List<CrossProfileItem> newCrossProfileItems,
            Map<CrossProfileItem, Boolean> startingToggleStates) {
        mContext = requireNonNull(context);
        mCrossProfileItems.clear();
        mCrossProfileItems.addAll(newCrossProfileItems);
        mStartingToggleStates = requireNonNull(startingToggleStates);
    }

    @Override
    public CrossProfileViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new CrossProfileViewHolder(
                LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.cross_profile_item, parent, /* attachToRoot= */ false));
    }

    @Override
    public void onBindViewHolder(CrossProfileViewHolder holder, int position) {
        final CrossProfileItem item = mCrossProfileItems.get(position);
        holder.title().setText(item.appTitle());
        holder.summary().setText(item.summary());
        holder.icon().setImageDrawable(
                mContext.getPackageManager().getApplicationIcon(item.appInfo()));
        if (mStartingToggleStates.containsKey(item)) {
            holder.toggle().setChecked(mStartingToggleStates.get(item));
        }
        if (position == mCrossProfileItems.size() - 1) {
            holder.horizontalDivider().setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return mCrossProfileItems.size();
    }

    List<CrossProfileItem> getCrossProfileItems() {
        return mCrossProfileItems;
    }

    /**
     * The view holder for {@link CrossProfileAdapter}. Equality and hashing are not implemented, so
     * two different instances will not be considered equal, even if constructed with the same view.
     */
    static class CrossProfileViewHolder extends RecyclerView.ViewHolder {
        private final TextView mTitle;
        private final TextView mSummary;
        private final ImageView mIcon;
        private final View mHorizontalDivider;
        private final Switch mToggle;

        CrossProfileViewHolder(@NonNull View view) {
            super(view);
            mTitle = view.findViewById(R.id.cross_profile_item_title);
            mSummary = view.findViewById(R.id.cross_profile_item_summary);
            mIcon = view.findViewById(R.id.cross_profile_item_icon);
            mHorizontalDivider = view.findViewById(R.id.cross_profile_item_horizontal_divider);
            mToggle = view.findViewById(R.id.cross_profile_item_toggle);
        }

        TextView title() {
            return mTitle;
        }

        TextView summary() {
            return mSummary;
        }

        ImageView icon() {
            return mIcon;
        }

        View horizontalDivider() {
            return mHorizontalDivider;
        }

        Switch toggle() {
            return mToggle;
        }
    }
}
