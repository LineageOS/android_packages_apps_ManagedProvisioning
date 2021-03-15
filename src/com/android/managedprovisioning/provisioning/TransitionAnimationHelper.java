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

import static java.util.Objects.requireNonNull;

import android.annotation.StringRes;
import android.content.Context;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.CrossFadeHelper;
import com.android.managedprovisioning.common.CrossFadeHelper.Callback;
import com.android.managedprovisioning.common.RepeatingVectorAnimation;
import com.android.managedprovisioning.provisioning.ProvisioningActivity.ProvisioningMode;

import com.google.android.setupdesign.util.ItemStyler;

import java.util.Arrays;
import java.util.List;

/**
 * Handles the animated transitions in the education screens. Transitions consist of cross fade
 * animations between different headers and banner images.
 */
class TransitionAnimationHelper {

    interface TransitionAnimationCallback {
        void onAllTransitionsShown();

        void onTransitionStart(int screenIndex, AnimatedVectorDrawable animatedVectorDrawable);

        void onHandleSupportLink(
                TextView textView, @StringRes int textResId, @StringRes int linkResId);
    }

    @VisibleForTesting
    static final ProvisioningModeWrapper WORK_PROFILE_WRAPPER =
            new ProvisioningModeWrapper(new TransitionScreenWrapper[] {
        new TransitionScreenWrapper(R.string.work_profile_provisioning_step_1_header,
                R.drawable.separate_work_and_personal_animation),
        new TransitionScreenWrapper(R.string.work_profile_provisioning_step_2_header,
                R.drawable.pause_work_apps_animation),
        new TransitionScreenWrapper(R.string.work_profile_provisioning_step_3_header,
                R.drawable.not_private_animation)
    }, R.string.work_profile_provisioning_summary);

    @VisibleForTesting
    static final ProvisioningModeWrapper WORK_PROFILE_ON_ORG_OWNED_DEVICE_WRAPPER =
            new ProvisioningModeWrapper(new TransitionScreenWrapper[] {
        new TransitionScreenWrapper(R.string.cope_provisioning_step_1_header,
                R.drawable.separate_work_and_personal_animation),
        new TransitionScreenWrapper(R.string.cope_provisioning_step_2_header,
                /* description= */ 0,
                R.drawable.personal_apps_separate_hidden_from_work_animation,
                /* link */ 0,
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
            AnimationComponents animationComponents,
            TransitionAnimationCallback callback,
            int currentTransitionIndex) {
        mAnimationComponents = checkNotNull(animationComponents);
        mCallback = checkNotNull(callback);
        mProvisioningModeWrapper = getProvisioningModeWrapper(provisioningMode,
                adminCanGrantSensorsPermissions);
        mCrossFadeHelper = getCrossFadeHelper();
        mShowAnimations = shouldShowAnimations();

        // TODO(b/182824327): Gracefully pause/resume edu screen animations rather than restarting
        mCurrentTransitionIndex = currentTransitionIndex;

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
        if (!(mAnimationComponents.mImage.getDrawable() instanceof AnimatedVectorDrawable)) {
            return;
        }
        final AnimatedVectorDrawable vectorDrawable =
                (AnimatedVectorDrawable) mAnimationComponents.mImage.getDrawable();
        boolean shouldLoop = getTransitionForIndex(mCurrentTransitionIndex).shouldLoop;
        mRepeatingVectorAnimation = new RepeatingVectorAnimation(vectorDrawable, shouldLoop);
        mRepeatingVectorAnimation.start();
        mCallback.onTransitionStart(mCurrentTransitionIndex, vectorDrawable);
    }

    @VisibleForTesting
    void stopCurrentAnimatedDrawable() {
        if (!mShowAnimations) {
            return;
        }
        if (!(mAnimationComponents.mImage.getDrawable() instanceof AnimatedVectorDrawable)) {
            return;
        }
        mRepeatingVectorAnimation.stop();
    }

    @VisibleForTesting
    void updateUiValues(int currentTransitionIndex) {
        final TransitionScreenWrapper transition =
                getTransitionForIndex(currentTransitionIndex);
        mAnimationComponents.mHeader.setText(transition.header);
        setupDescriptionText(transition);
        setupAnimation(transition);
        updateItemValues(
                mAnimationComponents.mItem1,
                transition.subHeaderIcon,
                transition.subHeaderTitle,
                transition.subHeader);
        updateItemValues(
                mAnimationComponents.mItem2,
                transition.secondarySubHeaderIcon,
                transition.secondarySubHeaderTitle,
                transition.secondarySubHeader);
    }

    private void setupAnimation(TransitionScreenWrapper transition) {
        if (mShowAnimations && transition.drawable != 0) {
            mAnimationComponents.mImage.setImageResource(transition.drawable);
            mAnimationComponents.mImageContainer.setVisibility(View.VISIBLE);
        } else {
            mAnimationComponents.mImageContainer.setVisibility(View.GONE);
        }
    }

    private void setupDescriptionText(TransitionScreenWrapper transition) {
        if (transition.description != 0) {
            if (transition.link != 0) {
                mCallback.onHandleSupportLink(
                        mAnimationComponents.mDescription,
                        transition.description,
                        transition.link);
            } else {
                mAnimationComponents.mDescription.setText(transition.description);
            }
            mAnimationComponents.mDescription.setVisibility(View.VISIBLE);
        } else {
            mAnimationComponents.mDescription.setVisibility(View.GONE);
        }
    }

    private void updateItemValues(ViewGroup item, int icon, int subHeaderTitle, int subHeader) {
        if (icon != 0) {
            ((ImageView) item.findViewById(R.id.sud_items_icon)).setImageResource(icon);
            ((TextView) item.findViewById(R.id.sud_items_title)).setText(subHeaderTitle);
            ((TextView) item.findViewById(R.id.sud_items_summary)).setText(subHeader);
            ItemStyler.applyPartnerCustomizationItemStyle(item);
            item.setVisibility(View.VISIBLE);
        } else {
            item.setVisibility(View.GONE);
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
    private static ProvisioningModeWrapper getProvisioningModeWrapperForFullyManaged(
            boolean adminCanGrantSensorsPermissions) {
        final int provisioningSummaryId;
        TransitionScreenWrapper.Builder secondScreenBuilder =
                new TransitionScreenWrapper.Builder()
                        .setHeader(R.string.fully_managed_device_provisioning_step_2_header);

        if (adminCanGrantSensorsPermissions) {
            secondScreenBuilder
                    .setSubHeaderTitle(
                            R.string.fully_managed_device_provisioning_permissions_header)
                    .setSubHeader(R.string.fully_managed_device_provisioning_permissions_subheader)
                    .setSubHeaderIcon(R.drawable.ic_history)
                    .setSecondarySubHeaderTitle(
                            R.string.fully_managed_device_provisioning_permissions_secondary_header)
                    .setSecondarySubHeader(R.string
                            .fully_managed_device_provisioning_permissions_secondary_subheader)
                    .setSecondarySubHeaderIcon(R.drawable.ic_perm_device_information)
                    .setShouldLoop(true);
            provisioningSummaryId =
                    R.string.fully_managed_device_with_permission_control_provisioning_summary;
        } else {
            provisioningSummaryId = R.string.fully_managed_device_provisioning_summary;
            secondScreenBuilder
                    .setDescription(R.string.fully_managed_device_provisioning_step_2_subheader)
                    .setLink(R.string.organization_admin)
                    .setAnimation(R.drawable.not_private_animation);
        }

        TransitionScreenWrapper firstScreen = new TransitionScreenWrapper(
                R.string.fully_managed_device_provisioning_step_1_header,
                R.drawable.connect_on_the_go_animation);
        return new ProvisioningModeWrapper(new TransitionScreenWrapper[] {
                firstScreen, secondScreenBuilder.build()}, provisioningSummaryId);
    }

    private boolean shouldShowAnimations() {
        final Context context = mAnimationComponents.mHeader.getContext();
        return context.getResources().getBoolean(R.bool.show_edu_animations);
    }

    private void applyContentDescription() {
        final TextView header = mAnimationComponents.mHeader;
        final Context context = header.getContext();
        header.setContentDescription(context.getString(mProvisioningModeWrapper.summary));
    }

    private static final class ProvisioningModeWrapper {
        final TransitionScreenWrapper[] transitions;
        final @StringRes int summary;

        ProvisioningModeWrapper(TransitionScreenWrapper[] transitions, @StringRes int summary) {
            this.transitions = requireNonNull(transitions);
            this.summary = summary;
        }
    }

    static final class AnimationComponents {
        private final TextView mHeader;
        private final TextView mDescription;
        private final ImageView mImage;
        private final ViewGroup mImageContainer;
        private final ViewGroup mItem1;
        private final ViewGroup mItem2;

        AnimationComponents(TextView header, TextView description, ViewGroup item1,
                ViewGroup item2, ImageView image, ViewGroup imageContainer) {
            this.mHeader = requireNonNull(header);
            this.mDescription = requireNonNull(description);
            this.mItem1 = requireNonNull(item1);
            this.mItem2 = requireNonNull(item2);
            this.mImageContainer = requireNonNull(imageContainer);
            this.mImage = requireNonNull(image);
        }

        List<View> asList() {
            return Arrays.asList(mHeader, mItem1, mItem2, mImageContainer);
        }
    }
}
