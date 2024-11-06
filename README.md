# Managed Provisioning

Bundled app responsible for provisioning an enterprise device

## Flows

### QR

```json
{
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME": "com.afwsamples.testdpc/com.afwsamples.testdpc.DeviceAdminReceiver",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": "https://testdpc-latest-apk.appspot.com/preview",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM": "gJD2YwtOiWJHkSMkkIfLRlj-quNqG1fb6v100QmzM9w="
}
```

## AS Setup

```bash
aidegen -n -i=s -p=/opt/android-studio-with-blaze-canary \
    packages/apps/ManagedProvisioning \
    frameworks/base \
    cts \
    vendor/xts \
    packages/apps/Settings \
    vendor/unbundled_google/packages/SettingsGoogle \
    external/connectedappssdk \
    packages/services/Car/packages/CarManagedProvisioning \
    vendor/google/apps/SetupWizardOverlay/PixelSetupWizard
```

## References

- [Local Development - Flagging for Trunk Stable Development](http://go/trunk-stable-flags-local-development)
