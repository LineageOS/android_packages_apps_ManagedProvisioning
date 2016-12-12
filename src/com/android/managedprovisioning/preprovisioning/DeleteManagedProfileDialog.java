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

package com.android.managedprovisioning.preprovisioning;

import android.annotation.Nullable;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.MdmPackageInfo;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.SimpleDialog;
import com.android.setupwizardlib.util.SystemBarHelper;

/**
 * Displays information about an existing managed profile and asks the user if it should be deleted.
 *
 * <p>Expects parent component to implement {@link SimpleDialog.SimpleDialogListener} for
 * user-response handling.
 */
public class DeleteManagedProfileDialog extends DialogFragment {
    private static final String KEY_USER_PROFILE_CALLBACK_ID = "user_profile_callback_id";
    private static final String KEY_MDM_PACKAGE_NAME = "mdm_package_name";
    private static final String KEY_PROFILE_OWNER_DOMAIN = "profile_owner_domain";

    private final SettingsFacade mSettingsFacade = new SettingsFacade();

    /**
     * @param managedProfileUserId user-id for the managed profile
     * @param mdmPackagename package name of the MDM application for the current managed profile,
     * or null if the managed profile has no profile owner associated.
     * @param profileOwnerDomain domain name of the organization which owns the managed profile, or
     * null if not known
     * @return initialized dialog
     */
    public static DeleteManagedProfileDialog newInstance(
            int managedProfileUserId, @Nullable ComponentName mdmPackagename,
            @Nullable String profileOwnerDomain) {
        Bundle args = new Bundle();
        args.putInt(KEY_USER_PROFILE_CALLBACK_ID, managedProfileUserId);
        // The device could be in a inconsistent state where it has a managed profile but no
        // associated profile owner package, for example after an unexpected reboot in the middle
        // of provisioning.
        if (mdmPackagename != null) {
            args.putString(KEY_MDM_PACKAGE_NAME, mdmPackagename.getPackageName());
        }
        args.putString(KEY_PROFILE_OWNER_DOMAIN, profileOwnerDomain);

        DeleteManagedProfileDialog dialog = new DeleteManagedProfileDialog();
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        if (!(getActivity() instanceof SimpleDialog.SimpleDialogListener)) {
            throw new IllegalStateException("Calling activity must implement " +
                    "DeleteManagedProfileCallback, found: " + getActivity().getLocalClassName());
        }
        SimpleDialog.SimpleDialogListener dialogListener =
                (SimpleDialog.SimpleDialogListener) getActivity();

        Bundle arguments = getArguments();
        String mdmPackageName = arguments.getString(KEY_MDM_PACKAGE_NAME);

        String appLabel;
        Drawable appIcon;
        MdmPackageInfo mdmPackageInfo = null;
        if (mdmPackageName != null) {
            mdmPackageInfo = MdmPackageInfo.createFromPackageName(getActivity(), mdmPackageName);
        }
        if (mdmPackageInfo != null) {
            appLabel = mdmPackageInfo.appLabel;
            appIcon = mdmPackageInfo.packageIcon;
        } else {
            appLabel = getResources().getString(android.R.string.unknownName);
            appIcon = getActivity().getPackageManager().getDefaultActivityIcon();
        }

        final Dialog dialog = new Dialog(getActivity(), R.style.ManagedProvisioningDialogTheme);
        dialog.setTitle(R.string.delete_profile_title);
        dialog.setContentView(R.layout.delete_managed_profile_dialog);
        dialog.setCanceledOnTouchOutside(false);
        if (!mSettingsFacade.isUserSetupCompleted(getActivity())) {
            SystemBarHelper.hideSystemBars(dialog);
        }

        ImageView imageView = (ImageView) dialog.findViewById(
                R.id.device_manager_icon_view);
        imageView.setImageDrawable(appIcon);
        imageView.setContentDescription(
                getResources().getString(R.string.mdm_icon_label, appLabel));

        TextView deviceManagerName = (TextView) dialog.findViewById(
                R.id.device_manager_name);
        deviceManagerName.setText(appLabel);

        Button positiveButton = (Button) dialog.findViewById(
                R.id.delete_managed_profile_positive_button);
        positiveButton.setOnClickListener(v -> {
            dialog.dismiss();
            dialogListener.onPositiveButtonClick(DeleteManagedProfileDialog.this);
        });

        Button negativeButton = (Button) dialog.findViewById(
                R.id.delete_managed_profile_negative_button);
        negativeButton.setOnClickListener(v -> {
            dialog.dismiss();
            dialogListener.onNegativeButtonClick(DeleteManagedProfileDialog.this);
        });

        return dialog;
    }

    /**
     * @return User id with which the dialog was instantiated
     */
    public int getUserId() {
        return getArguments().getInt(KEY_USER_PROFILE_CALLBACK_ID);
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        dialog.dismiss();
        ((SimpleDialog.SimpleDialogListener) getActivity()).onNegativeButtonClick(
                DeleteManagedProfileDialog.this);
    }
}