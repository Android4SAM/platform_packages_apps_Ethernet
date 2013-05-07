/*
 * Copyright 2008, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Author: Yi Sun(beyounn@gmail.com)
 */

#define LOG_TAG "ethernet"

#include "jni.h"
#include <inttypes.h>
#include <utils/misc.h>
#include <android_runtime/AndroidRuntime.h>
#include <utils/Log.h>
#include <asm/types.h>
#include "netlink-types.h"
#include <linux/rtnetlink.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <poll.h>
#include <net/if_arp.h>
#include <fcntl.h>
#include <errno.h>
#include <string.h>
#include <dirent.h>

#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <linux/if.h>
#include <linux/sockios.h>
#include <arpa/inet.h>

extern "C" {
	int ifc_enable(const char *ifname);
	int ifc_disable(const char *ifname);
	int ifc_add_host_route(const char *ifname, uint32_t addr);
	int ifc_remove_host_routes(const char *ifname);
	int ifc_set_default_route(const char *ifname, uint32_t gateway);
	int ifc_get_default_route(const char *ifname);
	int ifc_remove_default_route(const char *ifname);
	int ifc_reset_connections(const char *ifname);
	int ifc_configure(const char *ifname, in_addr_t ipaddr, in_addr_t netmask, in_addr_t gateway, in_addr_t dns1, in_addr_t dns2);

	int dhcp_do_request(const char *ifname,
			in_addr_t *ipaddr,
			in_addr_t *gateway,
			in_addr_t *mask,
			in_addr_t *dns1,
			in_addr_t *dns2,
			in_addr_t *server,
			uint32_t  *lease);
	int dhcp_stop(const char *ifname);
	int dhcp_release_lease(const char *ifname);
	char *dhcp_get_errmsg();

	int dhcp_do_request_renew(const char *ifname,
			in_addr_t *ipaddr,
			in_addr_t *gateway,
			in_addr_t *mask,
			in_addr_t *dns1,
			in_addr_t *dns2,
			in_addr_t *server,
			uint32_t  *lease);
}

static const char *classPathName =  "android/net/ethernet/Native";

static struct fieldIds {
        jclass dhcpInfoClass;
        jmethodID constructorId;
        jfieldID ipaddress;
        jfieldID gateway;
        jfieldID netmask;
        jfieldID dns1;
        jfieldID dns2;
        jfieldID serverAddress;
        jfieldID leaseDuration;
    } dhcpInfoFieldIds;

typedef struct _interface_info_t {
        unsigned int i;                            /* interface index        */
        char *name;                       /* name (eth0, eth1, ...) */
        struct _interface_info_t *next;
    } interface_info_t;

interface_info_t *interfaces = NULL;
int total_int = 0;
#define NL_SOCK_INV      -1
#define RET_STR_SZ       4096
#define NL_POLL_MSG_SZ   8*1024
#define SYSFS_PATH_MAX   256

static const char SYSFS_CLASS_NET[]     = "/sys/class/net";
static int nl_socket_msg = NL_SOCK_INV;
static struct sockaddr_nl addr_msg;
static int nl_socket_poll = NL_SOCK_INV;
static struct sockaddr_nl addr_poll;
static int getinterfacename(int index, char *name, size_t len);

static interface_info_t *find_info_by_index(unsigned int index){
    interface_info_t *info = interfaces;
    while( info) {
        if (info->i == index)
            return info;
        info = info->next;
    }
    return NULL;
}

static jstring android_net_ethernet_waitForEvent(JNIEnv *env,
                                                     jobject clazz)
{
    char *buff;
    struct nlmsghdr *nh;
    struct ifinfomsg *einfo;
    struct iovec iov;
    struct msghdr msg;
    char *result = NULL;
    char rbuf[4096];
    unsigned int left;
    interface_info_t *info;
    int len;
    ALOGI("Poll events from ethernet devices");
    /*
    *wait on uevent netlink socket for the ethernet device
    */
    buff = (char *)malloc(NL_POLL_MSG_SZ);
    if (!buff) {
        ALOGE("Allocate poll buffer failed");
        goto error;
    }

    iov.iov_base = buff;
    iov.iov_len = NL_POLL_MSG_SZ;
    msg.msg_name = (void *)&addr_msg;
    msg.msg_namelen =  sizeof(addr_msg);
    msg.msg_iov =  &iov;
    msg.msg_iovlen =  1;
    msg.msg_control =  NULL;
    msg.msg_controllen =  0;
    msg.msg_flags =  0;

    if((len = recvmsg(nl_socket_poll, &msg, 0))>= 0) {
        ALOGI("recvmsg get data");
        result = rbuf;
        left = 4096;
        rbuf[0] = '\0';
        for (nh = (struct nlmsghdr *) buff; NLMSG_OK (nh, len);
            nh = NLMSG_NEXT (nh, len))
        {
            if (nh->nlmsg_type == NLMSG_DONE){
                ALOGE("Did not find useful eth interface information");
                goto error;
            }

            if (nh->nlmsg_type == NLMSG_ERROR){
                /* Do some error handling. */
                ALOGE("Read device name failed");
                goto error;
            }
            ALOGV(" event :%d  found",nh->nlmsg_type);
            einfo = (struct ifinfomsg *)NLMSG_DATA(nh);
            ALOGV("the device flag :%X",einfo->ifi_flags);
            if (nh->nlmsg_type == RTM_DELLINK ||
                nh->nlmsg_type == RTM_NEWLINK ||
                nh->nlmsg_type == RTM_DELADDR ||
                nh->nlmsg_type == RTM_NEWADDR)
            {
                int type = nh->nlmsg_type;
                if (type == RTM_NEWLINK &&
                    (!(einfo->ifi_flags & IFF_LOWER_UP))) {
                    type = RTM_DELLINK;
                }
                if (type == RTM_NEWADDR &&
                    (!(einfo->ifi_flags & IFF_LOWER_UP))) {
                    type = RTM_DELLINK;
                }
                if ( (info = find_info_by_index
                    (((struct ifinfomsg*)
                    NLMSG_DATA(nh))->ifi_index))!=NULL)
                    snprintf(result,left, "%s:%d:",info->name,type);
                    left = left - strlen(result);
                    result =(char *)(result+ strlen(result));
                }
        }
        ALOGV("Done parsing");
        rbuf[4096 - left] = '\0';
        ALOGV("poll state :%s, left:%d",rbuf, left);
    }

    error:
        if(buff)
            free(buff);
        return env->NewStringUTF(rbuf);
}

static int netlink_send_dump_request(int sock, int type, int family)
{
    int ret;
    char buf[4096];
    struct sockaddr_nl snl;
    struct nlmsghdr *nlh;
    struct rtgenmsg *g;

    memset(&snl, 0, sizeof(snl));
    snl.nl_family = AF_NETLINK;

    memset(buf, 0, sizeof(buf));
    nlh = (struct nlmsghdr *)buf;
    g = (struct rtgenmsg *)(buf + sizeof(struct nlmsghdr));

    nlh->nlmsg_len = NLMSG_LENGTH(sizeof(struct rtgenmsg));
    nlh->nlmsg_flags = NLM_F_REQUEST|NLM_F_DUMP;
    nlh->nlmsg_type = type;
    g->rtgen_family = family;

    ret = sendto(sock, buf, nlh->nlmsg_len, 0, (struct sockaddr *)&snl, sizeof(snl));
    if (ret < 0) {
        perror("netlink_send_dump_request sendto");
        return -1;
    }
    return ret;
}

static void free_int_list()
{
    interface_info_t *tmp = interfaces;
    while(tmp) {
        if (tmp->name) free(tmp->name);
        interfaces = tmp->next;
        free(tmp);
        tmp = interfaces;
        total_int--;
    }
    if (total_int != 0 )
    {
        ALOGE("Wrong interface count found");
        total_int = 0;
    }
}

static void add_int_to_list(interface_info_t *node)
{
    /*
    *Todo: Lock here!!!!
    */
    node->next = interfaces;
    interfaces = node;
    total_int ++;
}

static int netlink_init_interfaces_list(void)
{
    int ret = -1;
    DIR  *netdir;
    struct dirent *de;
    char path[SYSFS_PATH_MAX];
    interface_info_t *intfinfo;
    int index;
    FILE *ifidx;
    #define MAX_FGETS_LEN 4
    char idx[MAX_FGETS_LEN+1];

    if ((netdir = opendir(SYSFS_CLASS_NET)) != NULL) {
        while((de = readdir(netdir))!=NULL) {
            if (strncmp(de->d_name, "eth", 3))
                    continue;
            snprintf(path, SYSFS_PATH_MAX,"%s/%s/phy80211",SYSFS_CLASS_NET,de->d_name);
            if (access(path, F_OK)) {
                snprintf(path, SYSFS_PATH_MAX,"%s/%s/wireless",SYSFS_CLASS_NET,de->d_name);
                if (!access(path, F_OK))
                    continue;
            } else {
                continue;
            }
            snprintf(path, SYSFS_PATH_MAX,"%s/%s/ifindex",SYSFS_CLASS_NET,de->d_name);
            if ((ifidx = fopen(path,"r")) != NULL ) {
                memset(idx,0,MAX_FGETS_LEN+1);
                if(fgets(idx,MAX_FGETS_LEN,ifidx) != NULL) {
                    index = strtoimax(idx,NULL,10);
                } else {
                    ALOGE("Can not read %s",path);
                    continue;
                }
            } else {
                ALOGE("Can not open %s for read",path);
                continue;
            }
            /* make some room! */
            intfinfo = (interface_info_t *)
            malloc(sizeof(struct _interface_info_t));
            if (intfinfo == NULL) {
                ALOGE("malloc in netlink_init_interfaces_table");
                goto error;
            }
            /* copy the interface name (eth0, eth1, ...) */
            intfinfo->name = strndup((char *) de->d_name, SYSFS_PATH_MAX);
            intfinfo->i = index;
            ALOGV("interface %s:%d found",intfinfo->name,intfinfo->i);
            add_int_to_list(intfinfo);
        }
        closedir(netdir);
    }
    ret = 0;
    error:
        return ret;
}

static void die(const char *s)
{
    fprintf(stderr,"error: %s (%s)\n", s, strerror(errno));
    ALOGE("ifconfig error");
}

static void setflags(int s, struct ifreq *ifr, int set, int clr)
{
    if(ioctl(s, SIOCGIFFLAGS, ifr) < 0) die("SIOCGIFFLAGS");
    ifr->ifr_flags = (ifr->ifr_flags & (~clr)) | set;
    if(ioctl(s, SIOCSIFFLAGS, ifr) < 0) die("SIOCSIFFLAGS");
}

static inline void init_sockaddr_in(struct sockaddr_in *sin, const char *addr)
{
    sin->sin_family = AF_INET;
    sin->sin_port = 0;
    sin->sin_addr.s_addr = inet_addr(addr);
}

static jint android_net_ethernet_enablenet(JNIEnv *env,
                                                        jobject clazz,
                                                        jstring ifname)
{
    struct ifreq ifr;
    int s;

    const char *nameStr = env->GetStringUTFChars(ifname, NULL);
    memset(&ifr, 0, sizeof(struct ifreq));
    strncpy(ifr.ifr_name, nameStr, IFNAMSIZ);
    ifr.ifr_name[IFNAMSIZ-1] = 0;

    if((s = socket(AF_INET, SOCK_DGRAM, 0)) < 0) {
        die("cannot open control socket\n");
    }

    setflags(s, &ifr, IFF_UP, 0);

    return 1;
}

/*
* The netlink socket
*/

static jint android_net_ethernet_initEthernetNative(JNIEnv *env,
                                                        jobject clazz)
{
    int ret = -1;

    ALOGV("==>%s",__FUNCTION__);
    memset(&addr_msg, 0, sizeof(sockaddr_nl));
    addr_msg.nl_family = AF_NETLINK;
    memset(&addr_poll, 0, sizeof(sockaddr_nl));
    addr_poll.nl_family = AF_NETLINK;
    addr_poll.nl_pid = 0;//getpid();
    addr_poll.nl_groups = RTMGRP_LINK | RTMGRP_IPV4_IFADDR;

    /*
    *Create connection to netlink socket
    */
    nl_socket_msg = socket(AF_NETLINK,SOCK_RAW,NETLINK_ROUTE);
    if (nl_socket_msg <= 0) {
        ALOGE("Can not create netlink msg socket");
        goto error;
    }
    if (bind(nl_socket_msg, (struct sockaddr *)(&addr_msg),
        sizeof(struct sockaddr_nl))) {
        ALOGE("Can not bind to netlink msg socket");
        goto error;
    }

    nl_socket_poll = socket(AF_NETLINK,SOCK_RAW,NETLINK_ROUTE);
    if (nl_socket_poll <= 0) {
        ALOGE("Can not create netlink poll socket");
        goto error;
    }

    errno = 0;
    if(bind(nl_socket_poll, (struct sockaddr *)(&addr_poll),
        sizeof(struct sockaddr_nl))) {
        ALOGE("Can not bind to netlink poll socket,%s",strerror(errno));
        goto error;
    }

    if ((ret = netlink_init_interfaces_list()) < 0) {
        ALOGE("Can not collect the interface list");
        goto error;
    }
    ALOGE("%s exited with success",__FUNCTION__);
    return ret;
    error:
        ALOGE("%s exited with error",__FUNCTION__);
        if (nl_socket_msg >0)
            close(nl_socket_msg);
        if (nl_socket_poll >0)
            close(nl_socket_poll);
        return ret;
}

static jstring android_net_ethernet_getInterfaceName(JNIEnv *env,
                                                         jobject clazz,
                                                         jint index)
{
    int i = 0;
    interface_info_t *info;
    ALOGV("User ask for device name on %d, list:%X, total:%d",index,
             (unsigned int)interfaces, total_int);
    info = interfaces;
    if (total_int != 0 && index <= (total_int -1)) {
        while (info != NULL) {
        if (index == i) {
            ALOGV("Found :%s",info->name);
            return env->NewStringUTF(info->name);
        }
        info = info->next;
        i ++;
        }
    }
    ALOGI("No device name found");
    return env->NewStringUTF(NULL);
}

static jint android_net_ethernet_getInterfaceCnt() {
    return total_int;
}

static jboolean android_net_utils_configureInterface(JNIEnv* env,
		jobject clazz,
		jstring ifname,
		jint ipaddr,
		jint mask,
		jint gateway,
		jint dns1,
		jint dns2)
{
	    int result;
		uint32_t lease;

		const char *nameStr = env->GetStringUTFChars(ifname, NULL);
		ALOGE("Here we call ::ifc_configure \
				ifname  %s                 \
				ipaddr  %d                 \
				mask    %d                 \
				gateway %d                 \
				dns1    %d                 \
				", nameStr, ipaddr, mask, gateway, dns1);
		result = ::ifc_configure(nameStr, ipaddr, mask, gateway, dns1, dns2);
		ALOGE("::ifc_configure return %d\n", result);
		env->ReleaseStringUTFChars(ifname, nameStr);
		return (jboolean)(result == 0);
}

static jint android_net_utils_removeDefaultRoute(JNIEnv* env, jobject clazz, jstring ifname)
{
	int result;

	const char *nameStr = env->GetStringUTFChars(ifname, NULL);
	result = ::ifc_remove_default_route(nameStr);
	env->ReleaseStringUTFChars(ifname, nameStr);

	return (jint)result;
}


static JNINativeMethod methods[] = {
    {"eth_waitForEvent", "()Ljava/lang/String;",
        (void *)android_net_ethernet_waitForEvent},
    {"eth_getInterfaceName", "(I)Ljava/lang/String;",
        (void *)android_net_ethernet_getInterfaceName},
    {"eth_initEthernetNative", "()I",
        (void *)android_net_ethernet_initEthernetNative},
    {"eth_getInterfaceCnt","()I",
        (void *)android_net_ethernet_getInterfaceCnt},
    {"eth_enableInterface","(Ljava/lang/String;)I",
        (void *)android_net_ethernet_enablenet},
    { "eth_configureNative", "(Ljava/lang/String;IIIII)Z",
	(void *)android_net_utils_configureInterface },
    { "removeDefaultRoute", "(Ljava/lang/String;)I",
	(void *)android_net_utils_removeDefaultRoute }

};

/*
* Register several native methods for one class.
*/
static int registerNativeMethods(JNIEnv* env, const char* className,
	    JNINativeMethod* gMethods, int numMethods)
{
    jclass clazz;

    clazz = env->FindClass(className);
    if (clazz == NULL) {
        ALOGE("Native registration unable to find class '%s'", className);
        return JNI_FALSE;
    }
    if (env->RegisterNatives(clazz, gMethods, numMethods) < 0) {
        ALOGE("RegisterNatives failed for '%s'", className);
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

/*
* Register native methods for all classes we know about.
*
* returns JNI_TRUE on success.
*/
static int registerNatives(JNIEnv* env)
{
    if (!registerNativeMethods(env, classPathName,
        methods, sizeof(methods) / sizeof(methods[0]))) {
        return JNI_FALSE;
    }

    return JNI_TRUE;
}


// ----------------------------------------------------------------------------

/*
 * This is called by the VM when the shared library is first loaded.
 */

typedef union {
    JNIEnv* env;
    void* venv;
} UnionJNIEnvToVoid;

jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    UnionJNIEnvToVoid uenv;
    uenv.venv = NULL;
    jint result = -1;
    JNIEnv* env = NULL;

    ALOGV("JNI_OnLoad");

    if (vm->GetEnv(&uenv.venv, JNI_VERSION_1_4) != JNI_OK) {
        ALOGE("ERROR: GetEnv failed");
        goto bail;
    }
    env = uenv.env;

    if (registerNatives(env) != JNI_TRUE) {
        ALOGE("ERROR: registerNatives failed");
        goto bail;
    }

    result = JNI_VERSION_1_4;

bail:
    return result;
}

