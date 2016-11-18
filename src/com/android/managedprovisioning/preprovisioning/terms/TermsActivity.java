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

import android.os.Bundle;
import android.view.View;
import android.widget.ExpandableListView;
import android.widget.Toolbar;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.SetupLayoutActivity;

import java.util.Arrays;
import java.util.List;

/**
 * Activity responsible for displaying the Terms screen
 */
public class TermsActivity extends SetupLayoutActivity {
    public static final String IS_PROFILE_OWNER_FLAG = "isProfileOwner";

    private static final String TERMS_HTML_SAMPLE =
            "<h1>Heading1<h2>Heading2<h3>Heading3<ul><li>Item1<li>Item2";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.terms_screen);

        String sampleDisclaimer = getResources().getString(
                getIntent().getExtras().getBoolean(IS_PROFILE_OWNER_FLAG)
                        ? R.string.admin_has_ability_to_monitor_profile
                        : R.string.admin_has_ability_to_monitor_device);

        // TODO: source actual disclaimers as a part of b/32760305
        List<TermsDocument> terms = Arrays.asList(
                TermsDocument.fromHtml("MyEMM", sampleDisclaimer),
                TermsDocument.fromHtml("Company (html)",
                        sampleDisclaimer + TERMS_HTML_SAMPLE + sampleDisclaimer),
                TermsDocument.fromHtml("Google", sampleDisclaimer),
                TermsDocument.fromHtml("Play for Work", sampleDisclaimer));

        ExpandableListView container = (ExpandableListView) findViewById(R.id.terms_container);
        container.setAdapter(
                new TermsListAdapter(terms, getLayoutInflater(), container::isGroupExpanded));
        container.addHeaderView(
                getLayoutInflater().inflate(R.layout.terms_screen_header, container, false));

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> TermsActivity.this.finish());
    }
}
