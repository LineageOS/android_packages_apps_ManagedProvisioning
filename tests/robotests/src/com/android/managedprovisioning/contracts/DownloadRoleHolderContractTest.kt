package com.android.managedprovisioning.contracts

import android.os.Build
import com.android.onboarding.contracts.testing.assertArgumentEncodesCorrectly
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(minSdk = Build.VERSION_CODES.VANILLA_ICE_CREAM, application = HiltTestApplication::class)
@HiltAndroidTest
class DownloadRoleHolderContractTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var contract: DownloadRoleHolderContract

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun completeArgument_encodesCorrectly() {
        val rhDownloadInfo = requireNotNull(aProvisioningParams.roleHolderDownloadInfo)
        val args =
            DownloadRoleHolderArguments(
                suwArguments = aSuwArguments,
                provisioningParams = aProvisioningParams,
                location = rhDownloadInfo.location,
                checksum = Checksum.SignatureChecksum(rhDownloadInfo.signatureChecksum),
                cookieHeader = rhDownloadInfo.cookieHeader,
                minVersion = rhDownloadInfo.minVersion,
            )
        assertArgumentEncodesCorrectly(contract, args)
    }
}
