LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SHARED_LIBRARIES := \
        libcutils \
        libutils \
        libbinder \
        libui \
        libsurfaceflinger_client

LOCAL_SRC_FILES:= ss.cpp

LOCAL_MODULE:= screenshot
LOCAL_MODULE_TAGS := optional

include $(BUILD_EXECUTABLE)
