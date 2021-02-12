#############################################################
# ManagedProvisioning Robolectric test target.              #
#############################################################
LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := ManagedProvisioningRoboTests
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_MODULE_CLASS := JAVA_LIBRARIES

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_JAVA_RESOURCE_DIRS := config

# Include the testing libraries
LOCAL_JAVA_LIBRARIES := \
    robolectric_android-all-stub \
    Robolectric_all-target \
    mockito-robolectric-prebuilt \
    truth-prebuilt \
    androidx.test.core \
    androidx.test.rules \
    androidx.core_core

LOCAL_STATIC_JAVA_LIBRARIES := managedprovisioning_protoslite

LOCAL_INSTRUMENTATION_FOR := ManagedProvisioning
LOCAL_COMPATIBILITY_SUITE := general-tests

LOCAL_MODULE_TAGS := optional

# Generate test_config.properties
include external/robolectric-shadows/gen_test_config.mk

include $(BUILD_STATIC_JAVA_LIBRARY)

#################################################################
# ManagedProvisioning runner target to run the previous target. #
#################################################################
include $(CLEAR_VARS)

LOCAL_MODULE := RunManagedProvisioningRoboTests
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice

LOCAL_JAVA_LIBRARIES := \
    ManagedProvisioningRoboTests \
    robolectric_android-all-stub \
    Robolectric_all-target \
    mockito-robolectric-prebuilt \
    truth-prebuilt \
    androidx.test.core \
    androidx.test.rules \
    androidx.core_core

LOCAL_TEST_PACKAGE := ManagedProvisioning

LOCAL_INSTRUMENT_SOURCE_DIRS := $(dir $(LOCAL_PATH))../src

include external/robolectric-shadows/run_robotests.mk
