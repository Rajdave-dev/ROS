LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE:= librosterm
LOCAL_SRC_FILES:= rosterm.c
include $(BUILD_SHARED_LIBRARY)
