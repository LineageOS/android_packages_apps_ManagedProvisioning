/*
 * Copyright 2014, The Android Open Source Project
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
package com.android.managedprovisioning;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/**
 * Activity to show a dialog that asks the user for their content to create a managed profile.
 *
 * This is a separate activity instead of a standard Dialog, because it needs to be shown on top
 * of other activities that are full screen immersive and therefore don't allow popups.
 */
public class UserConsentActivity extends Activity {

    protected static final String USER_CONSENT_KEY = "userConsentKey";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final LayoutInflater inflater = getLayoutInflater();
        final View contentView = inflater.inflate(R.layout.user_consent, null);
        setContentView(contentView);

        Button positiveButton = (Button) contentView.findViewById(R.id.positive_button);
        positiveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                returnUserConsentResult(true);
            }
        });
    }

    private void returnUserConsentResult(Boolean userHasConsented) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra(USER_CONSENT_KEY, userHasConsented);
        setResult(RESULT_OK, resultIntent);
        finish();
    }
}
