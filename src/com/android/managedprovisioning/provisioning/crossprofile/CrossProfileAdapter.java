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

import android.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.provisioning.crossprofile.CrossProfileAdapter.CrossProfileViewHolder;

import java.util.ArrayList;
import java.util.List;

/** The recycler adapter for the default OEM cross-profile apps for the user to accept or deny. */
class CrossProfileAdapter extends RecyclerView.Adapter<CrossProfileViewHolder> {
    private final List<CrossProfileItem> crossProfileItems = new ArrayList<>();

    CrossProfileAdapter(List<CrossProfileItem> newCrossProfileItems) {
        crossProfileItems.clear();
        crossProfileItems.addAll(newCrossProfileItems);
    }

    @Override
    public CrossProfileViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new CrossProfileViewHolder(
                LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.cross_profile_item, parent, /* attachToRoot= */ false));
    }

    @Override
    public void onBindViewHolder(CrossProfileViewHolder holder, int position) {
        final CrossProfileItem item = crossProfileItems.get(position);
        holder.title().setText(item.appTitle());
        holder.summary().setText(item.summary());
        holder.icon().setImageDrawable(item.icon());
        if (position == crossProfileItems.size() - 1) {
            holder.horizontalDivider().setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return crossProfileItems.size();
    }

    /**
     * The view holder for {@link CrossProfileAdapter}. Equality and hashing are not implemented, so
     * two different instances will not be considered equal, even if constructed with the same view.
     */
    static class CrossProfileViewHolder extends RecyclerView.ViewHolder {
        private final TextView title;
        private final TextView summary;
        private final ImageView icon;
        private final View horizontalDivider;

        CrossProfileViewHolder(@NonNull View view) {
            super(view);
            this.title = view.findViewById(R.id.cross_profile_item_title);
            this.summary = view.findViewById(R.id.cross_profile_item_summary);
            this.icon = view.findViewById(R.id.cross_profile_item_icon);
            this.horizontalDivider = view.findViewById(R.id.cross_profile_item_horizontal_divider);
        }

        TextView title() {
            return title;
        }

        TextView summary() {
            return summary;
        }

        ImageView icon() {
            return icon;
        }

        View horizontalDivider() {
            return horizontalDivider;
        }
    }
}
