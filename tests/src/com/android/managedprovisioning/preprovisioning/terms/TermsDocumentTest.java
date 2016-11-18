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

import static android.text.TextUtils.isEmpty;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

import android.text.Spanned;
import org.junit.Test;

public class TermsDocumentTest {
    @Test
    public void throwsExceptionForEmptyInputs() {
        // given: empty, null, typical string
        String[] inputs = {"", null, "aaa"};

        int count = 0;
        for (String header : inputs) {
            for (String htmlContent : inputs) {
                if (isEmpty(header) || isEmpty(htmlContent)) { // when at least one empty arg
                    try {
                        TermsDocument.fromHtml(header, htmlContent);
                    } catch (IllegalArgumentException e) { // expect an exception
                        count++;
                        continue;
                    }
                    fail(); // no IllegalArgumentException thrown despite an empty argument
                }
            }
        }

        // there are 8 cases where at least one of the arguments is empty (3 x 3 - 1)
        assertThat(count, equalTo(8));
    }

    @Test
    public void handlesSimpleText() {
        // given: pure text inputs
        String header = "aa";
        String inputHtml = "bb\n\ncc\ndd";
        String textRaw = "bb cc dd"; // whitespace stripped out in the process of HTML conversion

        // then: raw text interpreted correctly
        assertRawTextCorrect(header, inputHtml, textRaw);
    }

    @Test
    public void handlesComplexHtml() {
        // given: pure text inputs
        String header = "aa";
        String inputHtml = "a <b> b </b> <h1> ch1 </h1> <ol> <li> i1 </li> </ol> e";
        String textRaw = "a b \nch1 \ni1 \ne";

        // then: raw text interpreted correctly
        assertRawTextCorrect(header, inputHtml, textRaw);
        // TODO: add testing of formatting
    }

    /**
     * Typically not something that is tested, but this time it's part of an API contract with DPCs
     */
    @Test
    public void assertFieldTypes() throws Exception {
        TermsDocument termsDocument = TermsDocument.fromHtml("a", "b");
        assertThat(termsDocument.getHeading(), instanceOf(String.class));
        assertThat(termsDocument.getContent(), instanceOf(Spanned.class));
    }

    private void assertRawTextCorrect(String header, String inputHtml, String textRaw) {
        // when: document created from inputs
        TermsDocument actual = TermsDocument.fromHtml(header, inputHtml);

        // then: raw text values match expected ones
        assertThat(actual.getHeading(), equalTo(header));
        assertThat(actual.getContent().toString(), equalTo(textRaw));
    }
}