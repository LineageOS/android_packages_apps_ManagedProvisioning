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
package com.android.managedprovisioning.preprovisioning.terms;

import static com.android.internal.util.Preconditions.checkNotNull;

import android.os.Bundle;
import android.widget.ExpandableListView;
import android.widget.Toolbar;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.SetupLayoutActivity;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.DisclaimersParam.Disclaimer;
import com.android.managedprovisioning.model.ProvisioningParams;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Activity responsible for displaying the Terms screen
 */
public class TermsActivity extends SetupLayoutActivity {
    private Utils mUtils = new Utils();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.terms_screen);

        ProvisioningParams params = checkNotNull(
                getIntent().getParcelableExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS)
        );

        List<TermsDocument> terms = getTerms(params);

        ExpandableListView container = (ExpandableListView) findViewById(R.id.terms_container);
        container.setAdapter(
                new TermsListAdapter(terms, getLayoutInflater(), container::isGroupExpanded));
        container.addHeaderView(
                getLayoutInflater().inflate(R.layout.terms_screen_header, container, false));

        // keep at most one group expanded at a time
        container.setOnGroupExpandListener((int groupPosition) -> {
            for (int i = 0; i < terms.size(); i++) {
                if (i != groupPosition && container.isGroupExpanded(i)) {
                    container.collapseGroup(i);
                }
            }
        });

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> TermsActivity.this.finish());
    }

    private List<TermsDocument> getTerms(ProvisioningParams params) {
        boolean isProfileOwnerAction = mUtils.isProfileOwnerAction(params.provisioningAction);
        String aospDisclaimer = getResources().getString(isProfileOwnerAction
                ? R.string.admin_has_ability_to_monitor_profile
                : R.string.admin_has_ability_to_monitor_device);

        List<TermsDocument> terms = new ArrayList<>();
        // TODO: finalize AOSP disclaimer header and content
        terms.add(TermsDocument.fromHtml("AOSP", aospDisclaimer));

        Disclaimer[] disclaimers = params.disclaimersParam == null ? null
                : params.disclaimersParam.mDisclaimers;
        if (disclaimers != null) {
            for (Disclaimer disclaimer : disclaimers) {
                try {
                    terms.add(TermsDocument.fromHtml(disclaimer.mHeader,
                            disclaimer.getDisclaimerContentString()));
                } catch (IOException e) {
                    ProvisionLogger.loge("Failed to read disclaimer", e);
                }
            }
        }
        return terms;
    }
}
