LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_STATIC_JAVA_LIBRARIES := android-support-v4

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res \
    vendor/google/apps/SetupWizard/res-shared

LOCAL_PACKAGE_NAME := ManagedProvisioning
LOCAL_CERTIFICATE := platform
LOCAL_PRIVILEGED_MODULE := true

include frameworks/opt/setupwizard/navigationbar/common.mk

include $(BUILD_PACKAGE)

