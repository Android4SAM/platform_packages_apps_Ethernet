LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_SRC_FILES +=	\
	src/android/net/ethernet/IEthernetManager.aidl \

LOCAL_PACKAGE_NAME := Ethernet

LOCAL_CERTIFICATE := platform

LOCAL_JNI_SHARED_LIBRARIES := libethernet_jni

include $(BUILD_PACKAGE)

# ============================================================

# Also build all of the sub-targets under this one: the shared library.
include $(call all-makefiles-under,$(LOCAL_PATH))
