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

import static com.android.managedprovisioning.provisioning.ProvisioningActivity.PROVISIONING_MODE_FULLY_MANAGED_DEVICE;
import static com.android.managedprovisioning.provisioning.ProvisioningActivity.PROVISIONING_MODE_WORK_PROFILE;
import static com.android.managedprovisioning.provisioning.ProvisioningActivity.PROVISIONING_MODE_WORK_PROFILE_ON_ORG_OWNED_DEVICE;

import static java.util.Objects.requireNonNull;

import android.annotation.StringRes;
import android.content.Context;
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
import com.android.managedprovisioning.provisioning.ProvisioningActivity.ProvisioningMode;

import com.airbnb.lottie.LottieAnimationView;
import com.google.android.setupdesign.util.ItemStyler;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Handles the animated transitions in the education screens. Transitions consist of cross fade
 * animations between different headers and banner images.
 */
class TransitionAnimationHelper {

    interface TransitionAnimationCallback {
        void onAllTransitionsShown();

        void onAnimationSetup(LottieAnimationView animationView);
    }

    interface TransitionAnimationStateManager {
        void saveState(TransitionAnimationState state);

        TransitionAnimationState restoreState();
    }

    @VisibleForTesting
    static final ProvisioningModeWrapper WORK_PROFILE_WRAPPER =
            new ProvisioningModeWrapper(new TransitionScreenWrapper[] {
        new TransitionScreenWrapper(R.string.work_profile_provisioning_step_1_header,
                R.raw.separate_work_and_personal_animation),
        new TransitionScreenWrapper(R.string.work_profile_provisioning_step_2_header,
                R.raw.pause_work_apps_animation),
        new TransitionScreenWrapper(R.string.work_profile_provisioning_step_3_header,
                R.raw.not_private_animation)
    }, R.string.work_profile_provisioning_summary);

    @VisibleForTesting
    static final ProvisioningModeWrapper WORK_PROFILE_ON_ORG_OWNED_DEVICE_WRAPPER =
            new ProvisioningModeWrapper(new TransitionScreenWrapper[] {
        new TransitionScreenWrapper(R.string.cope_provisioning_step_1_header,
                R.raw.separate_work_and_personal_animation),
        new TransitionScreenWrapper(R.string.cope_provisioning_step_2_header,
                /* description= */ 0,
                R.raw.personal_apps_separate_hidden_from_work_animation,
                /* shouldLoop */ false),
        new TransitionScreenWrapper(R.string.cope_provisioning_step_3_header,
                R.raw.it_admin_control_device_block_apps_animation)
    }, R.string.cope_provisioning_summary);

    private static final int TRANSITION_TIME_MILLIS = 5000;
    private static final int CROSSFADE_ANIMATION_DURATION_MILLIS = 500;

    private final CrossFadeHelper mCrossFadeHelper;
    private final AnimationComponents mAnimationComponents;
    private final Runnable mStartNextTransitionRunnable = this::startNextAnimation;
    private final boolean mShowAnimations;
    private TransitionAnimationCallback mCallback;
    private TransitionAnimationStateManager mStateManager;
    private final ProvisioningModeWrapper mProvisioningModeWrapper;

    private Handler mUiThreadHandler = new Handler(Looper.getMainLooper());
    private TransitionAnimationState mTransitionAnimationState;

    TransitionAnimationHelper(@ProvisioningMode int provisioningMode,
            boolean adminCanGrantSensorsPermissions,
            AnimationComponents animationComponents,
            TransitionAnimationCallback callback,
            TransitionAnimationStateManager stateManager) {
        mAnimationComponents = requireNonNull(animationComponents);
        mCallback = requireNonNull(callback);
        mStateManager = requireNonNull(stateManager);
        mProvisioningModeWrapper = getProvisioningModeWrapper(provisioningMode,
                adminCanGrantSensorsPermissions);
        mCrossFadeHelper = getCrossFadeHelper();
        mShowAnimations = shouldShowAnimations();

        applyContentDescription(
                mAnimationComponents.mAnimationView,
                mProvisioningModeWrapper.summary);
    }

    boolean areAllTransitionsShown() {
        return mTransitionAnimationState.mAnimationIndex
                == mProvisioningModeWrapper.transitions.length - 1;
    }

    void start() {
        mTransitionAnimationState = maybeRestoreState();
        scheduleNextTransition(getTimeLeftForTransition(mTransitionAnimationState));
        updateUiValues(mTransitionAnimationState.mAnimationIndex);
        startCurrentAnimatedDrawable(mTransitionAnimationState.mProgress);
    }

    private long getTimeLeftForTransition(TransitionAnimationState transitionAnimationState) {
        long timeSinceLastTransition =
                System.currentTimeMillis() - transitionAnimationState.mLastTransitionTimestamp;
        return TRANSITION_TIME_MILLIS - timeSinceLastTransition;
    }

    void stop() {
        updateState();
        mStateManager.saveState(mTransitionAnimationState);
        clean();
    }

    private void updateState() {
        mTransitionAnimationState.mProgress = mAnimationComponents.mAnimationView.getProgress();
    }

    private TransitionAnimationState maybeRestoreState() {
        TransitionAnimationState transitionAnimationState = mStateManager.restoreState();
        if (transitionAnimationState == null) {
            return new TransitionAnimationState(
                    /* animationIndex */ 0,
                    /* progress */ 0,
                    /* lastTransitionTimestamp */ System.currentTimeMillis());
        }
        return transitionAnimationState;
    }

    private void clean() {
        stopCurrentAnimatedDrawable();
        mCrossFadeHelper.cleanup();
        mUiThreadHandler.removeCallbacksAndMessages(null);
        mUiThreadHandler = null;
        mCallback = null;
        mStateManager = null;
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
                    mTransitionAnimationState.mAnimationIndex++;
                    updateUiValues(mTransitionAnimationState.mAnimationIndex);
                    startCurrentAnimatedDrawable(/* startProgress */ 0f);
                }

                @Override
                public void fadeInCompleted() {
                    mTransitionAnimationState.mLastTransitionTimestamp = System.currentTimeMillis();
                    scheduleNextTransition(TRANSITION_TIME_MILLIS);
                }
            });
    }

    private void scheduleNextTransition(long timeLeftForTransition) {
        mUiThreadHandler.postDelayed(mStartNextTransitionRunnable, timeLeftForTransition);
    }

    @VisibleForTesting
    void startNextAnimation() {
        if (mTransitionAnimationState.mAnimationIndex
                >= mProvisioningModeWrapper.transitions.length - 1) {
            if (mCallback != null) {
                mCallback.onAllTransitionsShown();
            }
            return;
        }
        mCrossFadeHelper.start();
    }

    @VisibleForTesting
    void startCurrentAnimatedDrawable(float startProgress) {
        if (!mShowAnimations) {
            return;
        }
        boolean shouldLoop =
                getTransitionForIndex(mTransitionAnimationState.mAnimationIndex).shouldLoop;
        mAnimationComponents.mAnimationView.loop(shouldLoop);
        mAnimationComponents.mAnimationView.setProgress(startProgress);
        mAnimationComponents.mAnimationView.playAnimation();
    }

    @VisibleForTesting
    void stopCurrentAnimatedDrawable() {
        if (!mShowAnimations) {
            return;
        }
        mAnimationComponents.mAnimationView.pauseAnimation();
    }

    @VisibleForTesting
    void updateUiValues(int currentTransitionIndex) {
        final TransitionScreenWrapper transition =
                getTransitionForIndex(currentTransitionIndex);
        setupHeaderText(transition);
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

    private void setupHeaderText(TransitionScreenWrapper transition) {
        mAnimationComponents.mHeader.setText(transition.header);
        triggerTextToSpeechIfFocused(mAnimationComponents.mHeader);
    }

    private void triggerTextToSpeechIfFocused(TextView view) {
        if (view.isAccessibilityFocused()) {
            view.announceForAccessibility(view.getText().toString());
        }
    }

    private void setupAnimation(TransitionScreenWrapper transition) {
        if (mShowAnimations && transition.drawable != 0) {
            mAnimationComponents.mAnimationView.setAnimation(transition.drawable);
            mCallback.onAnimationSetup(mAnimationComponents.mAnimationView);
            mAnimationComponents.mImageContainer.setVisibility(View.VISIBLE);
        } else {
            mAnimationComponents.mImageContainer.setVisibility(View.GONE);
        }
    }

    private void setupDescriptionText(TransitionScreenWrapper transition) {
        if (transition.description != 0) {
            mAnimationComponents.mDescription.setText(transition.description);
            mAnimationComponents.mDescription.setVisibility(View.VISIBLE);
            triggerTextToSpeechIfFocused(mAnimationComponents.mDescription);
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
                    .setAnimation(R.raw.not_private_animation);
        }

        TransitionScreenWrapper firstScreen = new TransitionScreenWrapper(
                R.string.fully_managed_device_provisioning_step_1_header,
                R.raw.connect_on_the_go_animation);
        return new ProvisioningModeWrapper(new TransitionScreenWrapper[] {
                firstScreen, secondScreenBuilder.build()}, provisioningSummaryId);
    }

    private boolean shouldShowAnimations() {
        final Context context = mAnimationComponents.mHeader.getContext();
        return context.getResources().getBoolean(R.bool.show_edu_animations);
    }

    private void applyContentDescription(View view, @StringRes int summaryRes) {
        Context context = view.getContext();
        view.setContentDescription(context.getString(summaryRes));
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
        private final LottieAnimationView mAnimationView;
        private final ViewGroup mImageContainer;
        private final ViewGroup mItem1;
        private final ViewGroup mItem2;

        AnimationComponents(TextView header, TextView description, ViewGroup item1,
                ViewGroup item2, LottieAnimationView animationView, ViewGroup imageContainer) {
            this.mHeader = requireNonNull(header);
            this.mDescription = requireNonNull(description);
            this.mItem1 = requireNonNull(item1);
            this.mItem2 = requireNonNull(item2);
            this.mImageContainer = requireNonNull(imageContainer);
            this.mAnimationView = requireNonNull(animationView);
        }

        List<View> asList() {
            return Arrays.asList(mHeader, mItem1, mItem2, mImageContainer);
        }
    }

    static final class TransitionAnimationState {
        private int mAnimationIndex;
        private float mProgress;
        private long mLastTransitionTimestamp;

        TransitionAnimationState(
                int animationIndex,
                float progress,
                long lastTransitionTimestamp) {
            mAnimationIndex = animationIndex;
            mProgress = progress;
            mLastTransitionTimestamp = lastTransitionTimestamp;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TransitionAnimationState)) return false;
            TransitionAnimationState that = (TransitionAnimationState) o;
            return mAnimationIndex == that.mAnimationIndex &&
                    Float.compare(that.mProgress, mProgress) == 0 &&
                    mLastTransitionTimestamp == that.mLastTransitionTimestamp;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mAnimationIndex, mProgress, mLastTransitionTimestamp);
        }
    }
}
