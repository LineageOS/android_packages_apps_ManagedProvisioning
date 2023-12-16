package com.android.managedprovisioning.contracts

import android.content.ComponentName
import com.android.managedprovisioning.annotations.LegacyApi
import com.android.managedprovisioning.model.PackageDownloadInfo
import com.android.managedprovisioning.model.ProvisioningParams
import com.android.managedprovisioning.model.WifiInfo
import com.android.onboarding.contracts.IntentScope
import com.android.onboarding.contracts.ScopedIntentSerializer
import com.android.onboarding.contracts.provisioning.EXTRAS
import javax.inject.Inject

interface WithProvisioningParams : WithOptionalProvisioningParams {
    override val provisioningParams: ProvisioningParams
}

interface WithOptionalProvisioningParams {
    /** Only needed to preserve data parity when passing provisioning params bundle along */
    val provisioningParams: ProvisioningParams?
}

enum class FlowType {
    Unspecified,
    Legacy,
    AdminIntegrated
}

data class BaseProvisioningArguments(
    override val provisioningParams: ProvisioningParams,
    val flowType: FlowType,
    val deviceAdminDownloadInfo: PackageDownloadInfo?,
    val deviceAdminComponentName: ComponentName?,
    val wifiInfo: WifiInfo?,
    val useMobileData: Boolean,
    val isNfc: Boolean,
    val isQr: Boolean,
) : WithProvisioningParams {
    @LegacyApi
    constructor(
        provisioningParams: ProvisioningParams
    ) : this(
        provisioningParams = provisioningParams,
        flowType = FlowType.values()[provisioningParams.flowType],
        deviceAdminDownloadInfo = provisioningParams.deviceAdminDownloadInfo,
        deviceAdminComponentName = provisioningParams.deviceAdminComponentName,
        wifiInfo = provisioningParams.wifiInfo,
        useMobileData = provisioningParams.useMobileData,
        isNfc = provisioningParams.isNfc,
        isQr = provisioningParams.isQrProvisioning,
    )
}

class ProvisioningArgumentsSerializer @Inject constructor() :
    ScopedIntentSerializer<BaseProvisioningArguments> {

    override fun IntentScope.write(value: BaseProvisioningArguments) {
        val params =
            value.provisioningParams
                    .toBuilder()
                    .setFlowType(value.flowType.ordinal)
                    .setDeviceAdminDownloadInfo(value.deviceAdminDownloadInfo)
                    .setDeviceAdminComponentName(value.deviceAdminComponentName)
                    .setWifiInfo(value.wifiInfo)
                    .setUseMobileData(value.useMobileData)
                    .setIsNfc(value.isNfc)
                    .setIsQrProvisioning(value.isQr)
                    .build()
        this[EXTRAS.EXTRA_PROVISIONING_PARAMS] = params
    }

    override fun IntentScope.read(): BaseProvisioningArguments =
        parcelableExtra<ProvisioningParams>(EXTRAS.EXTRA_PROVISIONING_PARAMS).let {
            BaseProvisioningArguments(
                provisioningParams = it,
                flowType = FlowType.entries[it.flowType],
                deviceAdminDownloadInfo = it.deviceAdminDownloadInfo,
                deviceAdminComponentName = it.deviceAdminComponentName,
                wifiInfo = it.wifiInfo,
                useMobileData = it.useMobileData,
                isNfc = it.isNfc,
                isQr = it.isQrProvisioning,
            )
        }
}
