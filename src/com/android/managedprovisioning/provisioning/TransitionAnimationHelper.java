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

import android.annotation.DrawableRes;
import android.annotation.StringRes;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.CrossFadeHelper;
import com.android.managedprovisioning.common.CrossFadeHelper.Callback;
import com.android.managedprovisioning.common.RepeatingVectorAnimation;
import java.util.Arrays;

/**
 * Handles the animated transitions in the education screens. Transitions consist of cross fade
 * animations between different headers and banner images.
 */
class TransitionAnimationHelper {

    private static final TransitionScreenWrapper[] MANAGED_PROFILE_SETUP_TRANSITIONS = {
        new TransitionScreenWrapper(R.string.work_profile_provisioning_step_1_header,
                R.drawable.enterprise_wp_animation),
        new TransitionScreenWrapper(R.string.work_profile_provisioning_step_2_header,
                R.drawable.enterprise_do_animation),
        new TransitionScreenWrapper(R.string.work_profile_provisioning_step_3_header,
                R.drawable.enterprise_wp_animation)
    };
    private static final TransitionScreenWrapper[] FULLY_MANAGED_DEVICE_SETUP_TRANSITIONS = {
        new TransitionScreenWrapper(R.string.fully_managed_device_provisioning_step_1_header,
                R.drawable.enterprise_do_animation),
        new TransitionScreenWrapper(R.string.fully_managed_device_provisioning_step_2_header,
                R.drawable.enterprise_wp_animation,
                R.string.fully_managed_device_provisioning_step_2_subheader,
                /* showContactAdmin */ true)
    };
    private static final int TRANSITION_TIME_MILLIS = 5000;
    private static final int CROSSFADE_ANIMATION_DURATION_MILLIS = 500;

    private final boolean mIsManagedProfileProvisioning;
    private final CrossFadeHelper mCrossFadeHelper;
    private final TextView mHeader;
    private final TextView mSubHeader;
    private final ImageView mImage;
    private final TextView mProviderInfo;
    private final Runnable mStartNextTransitionRunnable = this::startNextAnimation;

    private Handler mUiThreadHandler = new Handler(Looper.getMainLooper());
    private int mCurrentTransitionIndex;
    private RepeatingVectorAnimation mRepeatingVectorAnimation;

    TransitionAnimationHelper(boolean isWorkProfileProvisioning, TextView header,
            TextView subHeader, ImageView image, TextView providerInfo) {
        mIsManagedProfileProvisioning = isWorkProfileProvisioning;
        mHeader = checkNotNull(header);
        mSubHeader = checkNotNull(subHeader);
        mImage = checkNotNull(image);
        mProviderInfo = checkNotNull(providerInfo);
        mCrossFadeHelper = getCrossFadeHelper();

        updateUiValues(mCurrentTransitionIndex);
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
    }

    private CrossFadeHelper getCrossFadeHelper() {
        return new CrossFadeHelper(
            Arrays.asList(mHeader, mImage, mSubHeader, mProviderInfo),
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
                    if (mCurrentTransitionIndex < getTransitions().length-1) {
                        mUiThreadHandler.postDelayed(
                            mStartNextTransitionRunnable, TRANSITION_TIME_MILLIS);
                    }
                }
            });
    }

    private void startNextAnimation() {
        mCrossFadeHelper.start();
    }

    private void startCurrentAnimatedDrawable() {
        if (!(mImage.getDrawable() instanceof AnimatedVectorDrawable)) {
            return;
        }
        final AnimatedVectorDrawable vectorDrawable =
            (AnimatedVectorDrawable) mImage.getDrawable();
        mRepeatingVectorAnimation = new RepeatingVectorAnimation(vectorDrawable);
        mRepeatingVectorAnimation.start();
    }

    private void stopCurrentAnimatedDrawable() {
        if (!(mImage.getDrawable() instanceof AnimatedVectorDrawable)) {
            return;
        }
        mRepeatingVectorAnimation.stop();
    }

    private void updateUiValues(int currentTransitionIndex) {
        final TransitionScreenWrapper[] transitions = getTransitions();
        final TransitionScreenWrapper transition =
            transitions[currentTransitionIndex % transitions.length];
        mHeader.setText(transition.header);
        mImage.setImageResource(transition.drawable);
        if (transition.subHeader != 0) {
            mSubHeader.setVisibility(View.VISIBLE);
            mSubHeader.setText(transition.subHeader);
        } else {
            mSubHeader.setVisibility(View.INVISIBLE);
        }
        if (transition.showContactAdmin) {
            mProviderInfo.setVisibility(View.VISIBLE);
        } else {
            mProviderInfo.setVisibility(View.INVISIBLE);
        }
    }

    private TransitionScreenWrapper[] getTransitions() {
        return mIsManagedProfileProvisioning
                    ? MANAGED_PROFILE_SETUP_TRANSITIONS : FULLY_MANAGED_DEVICE_SETUP_TRANSITIONS;
    }

    private static final class TransitionScreenWrapper {
        final @StringRes int header;
        final @DrawableRes int drawable;
        final @StringRes int subHeader;
        final boolean showContactAdmin;

        TransitionScreenWrapper(@StringRes int header, @DrawableRes int drawable) {
            this(header, drawable, /* subHeader */ 0, /* showContactAdmin */ false);
        }

        TransitionScreenWrapper(@StringRes int header, @DrawableRes int drawable,
                @StringRes int subHeader, boolean showContactAdmin) {
            this.header = header;
            this.drawable = drawable;
            this.subHeader = subHeader;
            this.showContactAdmin = showContactAdmin;
        }
    }
}
