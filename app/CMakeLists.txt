
cmake_minimum_required(VERSION 3.4.1)

#[[ Added by UlyssesZhan BEGIN ]]
set(FILE_PATH_OVERRIDE "-ffile-prefix-map=${CMAKE_SOURCE_DIR}=/build")
set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} ${FILE_PATH_OVERRIDE}")
set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} ${FILE_PATH_OVERRIDE}")
set(CMAKE_C_FLAGS_RELWITHDEBINFO "${CMAKE_C_FLAGS_RELWITHDEBINFO} ${FILE_PATH_OVERRIDE}")
set(CMAKE_CXX_FLAGS_RELWITHDEBINFO "${CMAKE_CXX_FLAGS_RELWITHDEBINFO} ${FILE_PATH_OVERRIDE}")
set(CMAKE_C_FLAGS_MINSIZEREL "${CMAKE_C_FLAGS_MINSIZEREL} ${FILE_PATH_OVERRIDE}")
set(CMAKE_CXX_FLAGS_MINSIZEREL "${CMAKE_CXX_FLAGS_MINSIZEREL} ${FILE_PATH_OVERRIDE}")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--build-id=none")
set(ENV{CFLAGS} "${FILE_PATH_OVERRIDE}")
set(ENV{CXXFLAGS} "${FILE_PATH_OVERRIDE}")

#[[ Added by UlyssesZhan END ]]
add_library( netguard
             SHARED
#[[ Added by UlyssesZhan BEGIN ]]
             src/main/jni/sunproxy/sunproxy.c
             src/main/jni/sniproxy/http.c
#[[ Added by UlyssesZhan END ]]
             src/main/jni/netguard/netguard.c
             src/main/jni/netguard/session.c
             src/main/jni/netguard/ip.c
             src/main/jni/netguard/tcp.c
             src/main/jni/netguard/udp.c
             src/main/jni/netguard/icmp.c
             src/main/jni/netguard/tls.c
             src/main/jni/netguard/dns.c
             src/main/jni/netguard/dhcp.c
             src/main/jni/netguard/pcap.c
             src/main/jni/netguard/util.c )

#[[ Deleted by UlyssesZhan BEGIN
include_directories( src/main/jni/netguard/ )
Deleted by UlyssesZhan END ]]
#[[ Added by UlyssesZhan BEGIN ]]
include_directories( src/main/jni/netguard/
                     src/main/jni/sniproxy/
                     src/main/jni/sunproxy/ )
#[[ Added by UlyssesZhan END ]]

find_library( log-lib
              log )

target_link_libraries( netguard
                       ${log-lib} )
