/*
 * Copyright 2019, The Android Open Source Project
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
package com.android.managedprovisioning.provisioning;

import static com.android.internal.util.Preconditions.checkNotNull;
import static com.android.managedprovisioning.provisioning.ProvisioningActivity.PROVISIONING_MODE_FULLY_MANAGED_DEVICE;
import static com.android.managedprovisioning.provisioning.ProvisioningActivity.PROVISIONING_MODE_WORK_PROFILE;
import static com.android.managedprovisioning.provisioning.ProvisioningActivity.PROVISIONING_MODE_WORK_PROFILE_ON_ORG_OWNED_DEVICE;

import android.annotation.StringRes;
import android.content.Context;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.CrossFadeHelper;
import com.android.managedprovisioning.common.CrossFadeHelper.Callback;
import com.android.managedprovisioning.common.RepeatingVectorAnimation;
import com.android.managedprovisioning.provisioning.ProvisioningActivity.ProvisioningMode;

import java.util.Arrays;
import java.util.List;

/**
 * Handles the animated transitions in the education screens. Transitions consist of cross fade
 * animations between different headers and banner images.
 */
class TransitionAnimationHelper {

    interface TransitionAnimationCallback {
        void onAllTransitionsShown();
    }

    @VisibleForTesting
    static final ProvisioningModeWrapper WORK_PROFILE_WRAPPER
            = new ProvisioningModeWrapper(new TransitionScreenWrapper[] {
        new TransitionScreenWrapper(R.string.work_profile_provisioning_step_1_header,
                R.drawable.separate_work_and_personal_animation),
        new TransitionScreenWrapper(R.string.work_profile_provisioning_step_2_header,
                R.drawable.pause_work_apps_animation),
        new TransitionScreenWrapper(R.string.work_profile_provisioning_step_3_header,
                R.drawable.not_private_animation)
    }, R.string.work_profile_provisioning_summary);

    @VisibleForTesting
    static final ProvisioningModeWrapper WORK_PROFILE_ON_ORG_OWNED_DEVICE_WRAPPER
            = new ProvisioningModeWrapper(new TransitionScreenWrapper[] {
        new TransitionScreenWrapper(R.string.cope_provisioning_step_1_header,
                R.drawable.separate_work_and_personal_animation),
        new TransitionScreenWrapper(R.string.cope_provisioning_step_2_header,
                R.drawable.personal_apps_separate_hidden_from_work_animation,
                /* subHeader */ 0,
                /* showContactAdmin */ false,
                /* shouldLoop */ false),
        new TransitionScreenWrapper(R.string.cope_provisioning_step_3_header,
                R.drawable.it_admin_control_device_block_apps_animation)
    }, R.string.cope_provisioning_summary);

    private static final int TRANSITION_TIME_MILLIS = 5000;
    private static final int CROSSFADE_ANIMATION_DURATION_MILLIS = 500;

    private final CrossFadeHelper mCrossFadeHelper;
    private final AnimationComponents mAnimationComponents;
    private final Runnable mStartNextTransitionRunnable = this::startNextAnimation;
    private final boolean mShowAnimations;
    private TransitionAnimationCallback mCallback;
    private final ProvisioningModeWrapper mProvisioningModeWrapper;

    private Handler mUiThreadHandler = new Handler(Looper.getMainLooper());
    private int mCurrentTransitionIndex;
    private RepeatingVectorAnimation mRepeatingVectorAnimation;

    TransitionAnimationHelper(@ProvisioningMode int provisioningMode,
            boolean adminCanGrantSensorsPermissions,
            AnimationComponents animationComponents, TransitionAnimationCallback callback) {
        mAnimationComponents = checkNotNull(animationComponents);
        mCallback = checkNotNull(callback);
        mProvisioningModeWrapper = getProvisioningModeWrapper(provisioningMode,
                adminCanGrantSensorsPermissions);
        mCrossFadeHelper = getCrossFadeHelper();
        mShowAnimations = shouldShowAnimations();

        applyContentDescription();
        updateUiValues(mCurrentTransitionIndex);
    }

    boolean areAllTransitionsShown() {
        return mCurrentTransitionIndex == mProvisioningModeWrapper.transitions.length - 1;
    }

    void start() {
        mUiThreadHandler.postDelayed(mStartNextTransitionRunnable, TRANSITION_TIME_MILLIS);
        updateUiValues(mCurrentTransitionIndex);
        startCurrentAnimatedDrawable();
    }

    void clean() {
        stopCurrentAnimatedDrawable();
        mCrossFadeHelper.cleanup();
        mUiThreadHandler.removeCallbacksAndMessages(null);
        mUiThreadHandler = null;
        mCallback = null;
    }

    @VisibleForTesting
    CrossFadeHelper getCrossFadeHelper() {
        return new CrossFadeHelper(
            mAnimationComponents.asList(),
            CROSSFADE_ANIMATION_DURATION_MILLIS,
            new Callback() {
                @Override
                public void fadeOutCompleted() {
                    stopCurrentAnimatedDrawable();
                    mCurrentTransitionIndex++;
                    updateUiValues(mCurrentTransitionIndex);
                    startCurrentAnimatedDrawable();
                }

                @Override
                public void fadeInCompleted() {
                    mUiThreadHandler.postDelayed(
                        mStartNextTransitionRunnable, TRANSITION_TIME_MILLIS);
                }
            });
    }

    @VisibleForTesting
    void startNextAnimation() {
        if (mCurrentTransitionIndex >= mProvisioningModeWrapper.transitions.length-1) {
            if (mCallback != null) {
                mCallback.onAllTransitionsShown();
            }
            return;
        }
        mCrossFadeHelper.start();
    }

    @VisibleForTesting
    void startCurrentAnimatedDrawable() {
        if (!mShowAnimations) {
            return;
        }
        if (!(mAnimationComponents.image.getDrawable() instanceof AnimatedVectorDrawable)) {
            return;
        }
        final AnimatedVectorDrawable vectorDrawable =
            (AnimatedVectorDrawable) mAnimationComponents.image.getDrawable();
        boolean shouldLoop = getTransitionForIndex(mCurrentTransitionIndex).shouldLoop;
        mRepeatingVectorAnimation = new RepeatingVectorAnimation(vectorDrawable, shouldLoop);
        mRepeatingVectorAnimation.start();
    }

    @VisibleForTesting
    void stopCurrentAnimatedDrawable() {
        if (!mShowAnimations) {
            return;
        }
        if (!(mAnimationComponents.image.getDrawable() instanceof AnimatedVectorDrawable)) {
            return;
        }
        mRepeatingVectorAnimation.stop();
    }

    private void setTextViewIfResourceValid(TextView textView, int resId) {
        if (resId != 0) {
            textView.setVisibility(View.VISIBLE);
            textView.setText(resId);
        } else {
            textView.setVisibility(View.GONE);
        }
    }

    private void setImageViewIconIfResourceValid(ImageView imageView, int iconResId) {
        if (iconResId != 0) {
            Context context = imageView.getContext();
            imageView.setImageDrawable(context.getDrawable(iconResId));
        }
    }

    @VisibleForTesting
    void updateUiValues(int currentTransitionIndex) {
        final TransitionScreenWrapper transition =
                getTransitionForIndex(currentTransitionIndex);

        mAnimationComponents.header.setText(transition.header);

        final ImageView image = mAnimationComponents.image;
        if (mShowAnimations) {
            image.setImageResource(transition.drawable);
        } else {
            image.setVisibility(View.GONE);
        }

        // First subheader and its title
        setTextViewIfResourceValid(mAnimationComponents.subHeaderTitle, transition.subHeaderTitle);
        setTextViewIfResourceValid(mAnimationComponents.subHeader, transition.subHeader);
        setImageViewIconIfResourceValid(mAnimationComponents.subHeaderIcon,
                transition.subHeaderIcon);

        // Second subheader and its title
        setTextViewIfResourceValid(mAnimationComponents.secondarySubHeaderTitle,
                transition.secondarySubHeaderTitle);
        setTextViewIfResourceValid(mAnimationComponents.secondarySubHeader,
                transition.secondarySubHeader);
        setImageViewIconIfResourceValid(mAnimationComponents.secondarySubHeaderIcon,
                transition.secondarySubHeaderIcon);

        final TextView providerInfo = mAnimationComponents.providerInfo;
        if (transition.showContactAdmin) {
            providerInfo.setVisibility(View.VISIBLE);
        } else {
            providerInfo.setVisibility(View.INVISIBLE);
        }
    }

    private TransitionScreenWrapper getTransitionForIndex(int currentTransitionIndex) {
        TransitionScreenWrapper[] transitions = mProvisioningModeWrapper.transitions;
        return transitions[currentTransitionIndex % transitions.length];
    }

    @VisibleForTesting
    ProvisioningModeWrapper getProvisioningModeWrapper(
            @ProvisioningMode int provisioningMode, boolean adminCanGrantSensorsPermissions) {
        switch (provisioningMode) {
            case PROVISIONING_MODE_WORK_PROFILE:
                return WORK_PROFILE_WRAPPER;
            case PROVISIONING_MODE_FULLY_MANAGED_DEVICE:
                return getProvisioningModeWrapperForFullyManaged(adminCanGrantSensorsPermissions);
            case PROVISIONING_MODE_WORK_PROFILE_ON_ORG_OWNED_DEVICE:
                return WORK_PROFILE_ON_ORG_OWNED_DEVICE_WRAPPER;
        }
        throw new IllegalStateException("Unexpected provisioning mode " + provisioningMode);
    }

    /** Return the provisioning mode wrapper for a fully-managed device.
     * The second screen, as well as the accessible summary, will be different, depending on whether
     * the admin can grant sensors-related permissions on this device or not.
     */
    private ProvisioningModeWrapper getProvisioningModeWrapperForFullyManaged(
            boolean adminCanGrantSensorsPermissions) {
        // Common second screen activity.
        TransitionScreenWrapper.Builder secondScreenBuilder =
                new TransitionScreenWrapper.Builder();
        secondScreenBuilder.setHeader(
                R.string.fully_managed_device_provisioning_step_2_header)
                .setSubHeaderTitle(
                        R.string.fully_managed_device_provisioning_step_2_subheader_title)
                .setSubHeader(R.string.fully_managed_device_provisioning_step_2_subheader)
                .setSubHeaderIcon(R.drawable.ic_history)
                .setShowContactAdmin(false)
                .setAnimation(R.drawable.not_private_animation)
                .setShouldLoop(true);

        final int provisioningSummaryId;

        if (adminCanGrantSensorsPermissions) {
            secondScreenBuilder.setSecondarySubHeaderTitle(
                    R.string.fully_managed_device_provisioning_permissions_title)
                    .setSecondarySubHeader(
                            R.string.fully_managed_device_provisioning_permissions_subheader)
                    .setSecondarySubHeaderIcon(R.drawable.ic_perm_device_information);
            //TODO(b/180386738): Change to new summary.
            provisioningSummaryId = R.string.fully_managed_device_provisioning_summary;
        } else {
            provisioningSummaryId = R.string.fully_managed_device_provisioning_summary;
        }

        TransitionScreenWrapper firstScreen = new TransitionScreenWrapper(
                R.string.fully_managed_device_provisioning_step_1_header,
                R.drawable.connect_on_the_go_animation);
        return new ProvisioningModeWrapper(new TransitionScreenWrapper[] {
                firstScreen, secondScreenBuilder.build()}, provisioningSummaryId);
    }

    private boolean shouldShowAnimations() {
        final Context context = mAnimationComponents.header.getContext();
        return context.getResources().getBoolean(R.bool.show_edu_animations);
    }

    private void applyContentDescription() {
        final TextView header = mAnimationComponents.header;
        final Context context = header.getContext();
        header.setContentDescription(context.getString(mProvisioningModeWrapper.summary));
    }

    private static final class ProvisioningModeWrapper {
        final TransitionScreenWrapper[] transitions;
        final @StringRes int summary;

        ProvisioningModeWrapper(TransitionScreenWrapper[] transitions, @StringRes int summary) {
            this.transitions = checkNotNull(transitions);
            this.summary = summary;
        }
    }

    static final class AnimationComponents {
        private final TextView header;
        private final ImageView subHeaderIcon;
        private final TextView subHeaderTitle;
        private final TextView subHeader;
        private final ImageView secondarySubHeaderIcon;
        private final TextView secondarySubHeaderTitle;
        private final TextView secondarySubHeader;
        private final ImageView image;
        private final TextView providerInfo;

        AnimationComponents(
                TextView header, ImageView subHeaderIcon, TextView subHeaderTitle,
                TextView subHeader, ImageView secondarySubHeaderIcon,
                TextView secondarySubHeaderTitle, TextView secondarySubHeader, ImageView image,
                TextView providerInfo) {
            this.header = checkNotNull(header);
            this.subHeaderIcon = checkNotNull(subHeaderIcon);
            this.subHeaderTitle = checkNotNull(subHeaderTitle);
            this.subHeader = checkNotNull(subHeader);
            this.secondarySubHeaderIcon = checkNotNull(secondarySubHeaderIcon);
            this.secondarySubHeaderTitle = checkNotNull(secondarySubHeaderTitle);
            this.secondarySubHeader = checkNotNull(secondarySubHeader);
            this.image = checkNotNull(image);
            this.providerInfo = checkNotNull(providerInfo);
        }

        List<View> asList() {
            return Arrays.asList(header, subHeaderIcon, subHeaderTitle, subHeader,
                    secondarySubHeaderIcon, secondarySubHeaderTitle, secondarySubHeader,
                    image, providerInfo);
        }
    }
}
