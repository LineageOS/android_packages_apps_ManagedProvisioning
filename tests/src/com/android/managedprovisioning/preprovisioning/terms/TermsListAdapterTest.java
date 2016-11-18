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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import android.support.test.filters.SmallTest;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.managedprovisioning.R;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SmallTest
public class TermsListAdapterTest {
    private @Mock LayoutInflater mLayoutInflater;
    private @Mock View mLayout;
    private @Mock View mDivider;
    private @Mock TextView mTextView;
    private @Mock ImageView mChevron;

    private TermsDocument mDoc1 = TermsDocument.fromHtml("h1", "c1");
    private TermsDocument mDoc2 = TermsDocument.fromHtml("h2", "c2");
    private TermsDocument mDoc3 = TermsDocument.fromHtml("h3", "c3");
    private List<TermsDocument> mDocs = Arrays.asList(mDoc1, mDoc2, mDoc3);
    private TermsListAdapter.GroupExpandedInfo mGroupInfoAlwaysCollapsed = i -> false;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void throwsForEmptyArguments() {
        List[] termsArr = {new ArrayList<TermsDocument>(), null};
        LayoutInflater[] inflaterArr = {mLayoutInflater, null};
        TermsListAdapter.GroupExpandedInfo[] expandedInfoArr = {mGroupInfoAlwaysCollapsed, null};

        int count = 0;
        for (@SuppressWarnings("unchecked") List<TermsDocument> terms : termsArr) {
            for (LayoutInflater inflater : inflaterArr) {
                for (TermsListAdapter.GroupExpandedInfo expandedInfo : expandedInfoArr) {
                    if (terms == null || inflater == null || expandedInfo == null) {
                        try {
                            new TermsListAdapter(terms, inflater, expandedInfo);
                        } catch (NullPointerException e) {
                            count++;
                            continue;
                        }
                        fail(); // no NullPointerException thrown despite a null argument
                    }
                }
            }
        }

        // there are 7 cases where at least one of the arguments is null (2 x 2 x 2 - 1)
        assertThat(count, equalTo(7));
    }

    @Test
    public void returnsCorrectDocument() {
        // given: an adapter
        TermsListAdapter adapter = new TermsListAdapter(mDocs, mLayoutInflater,
                mGroupInfoAlwaysCollapsed);

        // when: asked for a document from the initially passed-in list
        for (int i = 0; i < mDocs.size(); i++) {
            // then: elements from that list are returned
            assertThat(adapter.getChild(i, 0), sameInstance(mDocs.get(i)));
            assertThat(adapter.getGroup(i), sameInstance(mDocs.get(i)));
        }
    }

    // TODO: replace with TermsActivityTest (http://b/33289850)
    // as this is going too far towards 'copy of the implementation'
    @Test
    public void returnsGroupViews() {
        // given: an adapter
        TermsListAdapter adapter = new TermsListAdapter(mDocs, mLayoutInflater,
                mGroupInfoAlwaysCollapsed);

        when(mLayoutInflater.inflate(eq(R.layout.terms_disclaimer_header), anyObject(),
                eq(false))).thenReturn(mLayout);
        when(mLayout.findViewById(R.id.header_text)).thenReturn(mTextView);
        when(mLayout.findViewById(R.id.chevron)).thenReturn(mChevron);
        when(mLayout.findViewById(R.id.divider)).thenReturn(mDivider);

        // when: adapter is asked for a view for the first child
        View groupView = adapter.getGroupView(0, false, null, null);

        // then: a non-null view is returned
        assertNotNull(groupView);
    }
}