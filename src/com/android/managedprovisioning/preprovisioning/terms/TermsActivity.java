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

import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DISCLAIMER_CONTENT;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DISCLAIMER_HEADER;
import static android.content.pm.PackageManager.GET_META_DATA;
import static android.content.pm.PackageManager.MATCH_SYSTEM_ONLY;

import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.PROVISIONING_TERMS_ACTIVITY_TIME_MS;
import static com.android.internal.util.Preconditions.checkNotNull;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.annotation.VisibleForTesting;
import android.util.ArraySet;
import android.widget.ExpandableListView;
import android.widget.Toolbar;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.analytics.ProvisioningAnalyticsTracker;
import com.android.managedprovisioning.common.ClickableSpanFactory;
import com.android.managedprovisioning.common.HtmlToSpannedParser;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.SetupLayoutActivity;
import com.android.managedprovisioning.common.StoreUtils;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.DisclaimersParam.Disclaimer;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.preprovisioning.WebActivity;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Activity responsible for displaying the Terms screen
 */
public class TermsActivity extends SetupLayoutActivity {
    private final StoreUtils.TextFileReader mTextFileReader;
    private final ProvisioningAnalyticsTracker mProvisioningAnalyticsTracker;
    private final Set<Integer> mExpandedGroupsPosition = new ArraySet<>();

    @SuppressWarnings("unused")
    public TermsActivity() {
        this(StoreUtils::readString);
    }

    @SuppressWarnings("WeakerAccess")
    @VisibleForTesting
    TermsActivity(StoreUtils.TextFileReader textFileReader) {
        super(new Utils());
        mTextFileReader = textFileReader;
        mProvisioningAnalyticsTracker = ProvisioningAnalyticsTracker.getInstance();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.terms_screen);

        ProvisioningParams params = checkNotNull(
                getIntent().getParcelableExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS));

        List<TermsDocument> terms = getTerms(params);

        ExpandableListView container = (ExpandableListView) findViewById(R.id.terms_container);
        container.setAdapter(
                new TermsListAdapter(terms, getLayoutInflater(), container::isGroupExpanded));
        container.addHeaderView(
                getLayoutInflater().inflate(R.layout.terms_screen_header, container, false));

        // Add default open terms to the expanded groups set.
        for (int i = 0; i < terms.size(); i++) {
            if (container.isGroupExpanded(i)) {
                mExpandedGroupsPosition.add(i);
            }
        }

        // keep at most one group expanded at a time
        container.setOnGroupExpandListener((int groupPosition) -> {
            mExpandedGroupsPosition.add(groupPosition);
            for (int i = 0; i < terms.size(); i++) {
                if (i != groupPosition && container.isGroupExpanded(i)) {
                    container.collapseGroup(i);
                }
            }
        });

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> TermsActivity.this.finish());

        mProvisioningAnalyticsTracker.logNumberOfTermsDisplayed(this, terms.size());
    }

    @Override
    public void onDestroy() {
        mProvisioningAnalyticsTracker.logNumberOfTermsRead(this, mExpandedGroupsPosition.size());
        super.onDestroy();
    }

    @Override
    protected int getMetricsCategory() {
        return PROVISIONING_TERMS_ACTIVITY_TIME_MS;
    }

    private List<TermsDocument> getTerms(ProvisioningParams params) {
        TermsDocument.Factory termsDocumentFactory = new TermsDocument.Factory(
                new HtmlToSpannedParser(new ClickableSpanFactory(getColor(R.color.blue)),
                        url -> WebActivity.createIntent(TermsActivity.this, url,
                                getWindow().getStatusBarColor())));

        boolean isProfileOwnerAction = mUtils.isProfileOwnerAction(params.provisioningAction);
        String aospDisclaimer = getResources().getString(isProfileOwnerAction
                ? R.string.admin_has_ability_to_monitor_profile
                : R.string.admin_has_ability_to_monitor_device);

        List<TermsDocument> terms = new ArrayList<>();
        // TODO: finalize AOSP disclaimer header and content
        terms.add(termsDocumentFactory.create("AOSP", aospDisclaimer));
        if (!isProfileOwnerAction) {
            terms.addAll(getSystemAppTerms(termsDocumentFactory));
        }

        Disclaimer[] disclaimers = params.disclaimersParam == null ? null
                : params.disclaimersParam.mDisclaimers;
        if (disclaimers != null) {
            for (Disclaimer disclaimer : disclaimers) {
                try {
                    String htmlContent = mTextFileReader.read(
                            new File(disclaimer.mContentFilePath));
                    terms.add(termsDocumentFactory.create(disclaimer.mHeader, htmlContent));
                } catch (IOException e) {
                    ProvisionLogger.loge("Failed to read disclaimer", e);
                }
            }
        }
        return terms;
    }

    private List<TermsDocument> getSystemAppTerms(TermsDocument.Factory termsDocumentFactory) {
        List<TermsDocument> terms = new ArrayList<>();
        List<ApplicationInfo> appInfos = getPackageManager().getInstalledApplications(
                MATCH_SYSTEM_ONLY | GET_META_DATA);
        for (ApplicationInfo appInfo : appInfos) {
            String header = getStringMetaData(appInfo, EXTRA_PROVISIONING_DISCLAIMER_HEADER);
            String content = getStringMetaData(appInfo, EXTRA_PROVISIONING_DISCLAIMER_CONTENT);
            if (header != null && content != null) {
                terms.add(termsDocumentFactory.create(header, content));
            }
        }
        return terms;
    }

    private String getStringMetaData(ApplicationInfo appInfo, String key) {
        if (appInfo.metaData != null) {
            int resId = appInfo.metaData.getInt(key);
            if (resId != 0) {
                try {
                    return getPackageManager().getResourcesForApplication(appInfo).getString(resId);
                } catch (NameNotFoundException | Resources.NotFoundException e) {
                    ProvisionLogger.loge("NameNotFoundException", e);
                }
            }
        }
        return null;
    }
}