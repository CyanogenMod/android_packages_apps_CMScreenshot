LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := CMScreenshot
LOCAL_CERTIFICATE := platform

LOCAL_MODULE_TAGS := optional

include $(BUILD_PACKAGE)

include $(CLEAR_VARS)

LOCAL_SHARED_LIBRARIES := \
        libcutils \
        libutils \
        libbinder \
        libui \
        libsurfaceflinger_client

LOCAL_SRC_FILES:= native/ss.cpp

LOCAL_MODULE:= screenshot
LOCAL_MODULE_TAGS := optional

include $(BUILD_EXECUTABLE)
