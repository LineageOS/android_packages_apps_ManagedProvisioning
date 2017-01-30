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

import static com.android.internal.util.Preconditions.checkStringNotEmpty;

import android.content.Context;
import android.content.Intent;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.URLSpan;
import android.view.View;

import com.android.managedprovisioning.preprovisioning.WebActivity;

/**
 * Class responsible for storing disclaimers
 */
final class TermsDocument {
    public static final int HTML_MODE = Html.FROM_HTML_MODE_COMPACT;

    private final String mHeading;
    private final Spanned mContent;

    /**
     * Private constructor. We are expecting instances to be created from strings using {@link
     * TermsDocument#fromHtml(String, String)}
     */
    private TermsDocument(String heading, Spanned content) {
        mHeading = heading;
        mContent = content;
    }

    /**
     * Constructs a new {@link TermsDocument} object by converting html content to a {@link Spanned}
     * object
     */
    public static TermsDocument fromHtml(String heading, String htmlContent) {
        return new TermsDocument(checkStringNotEmpty(heading), getSpannedFromHtml(htmlContent));
    }

    /**
     * @return Document heading
     */
    public String getHeading() {
        return mHeading;
    }

    /**
     * @return Document content
     */
    public Spanned getContent() {
        return mContent;
    }


    private static Spanned getSpannedFromHtml(String htmlContent) {
        Spanned spanned = Html.fromHtml(checkStringNotEmpty(htmlContent), HTML_MODE);
        if (spanned == null) {
            return null;
        }
        // Make html <a> tag opens WebActivity
        SpannableStringBuilder spanBuilder = new SpannableStringBuilder(spanned);
        URLSpan[] urlSpans = spanBuilder.getSpans(0, spanBuilder.length(), URLSpan.class);
        for (URLSpan urlSpan : urlSpans) {
            int start = spanBuilder.getSpanStart(urlSpan);
            int end = spanBuilder.getSpanEnd(urlSpan);
            setSpanFromMark(spanBuilder, urlSpan, new WebviewUrlSpan(urlSpan.getURL()));
        }
        return spanBuilder;
    }

    private static void setSpanFromMark(Spannable text, Object mark, Object span) {
        int where = text.getSpanStart(mark);
        text.removeSpan(mark);
        int len = text.length();
        text.setSpan(span, where, len, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static class WebviewUrlSpan extends URLSpan {

        public WebviewUrlSpan(String url) {
            super(url);
        }

        @Override
        public void onClick(View widget) {
            Context context = widget.getContext();
            Intent intent = WebActivity.createIntent(context, getURL(), null);
            if (intent != null) {
                context.startActivity(intent);
            }
        }
    }
}