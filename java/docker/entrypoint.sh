#!/usr/bin/env bash

if [[ -z "$POD_MEM_LIMIT_MB" ]]; then
  POD_MEM_LIMIT=$(cat /sys/fs/cgroup/memory.max)
  POD_MEM_LIMIT_MB=`expr $POD_MEM_LIMIT / 1024 / 1024`
fi

[[ -z "${METASPACE_SIZE_MB}" ]] && METASPACE_SIZE_MB=`expr $POD_MEM_LIMIT_MB / 5`

#COMPRESSED_CLASS_SPACE_SIZE_MB=`expr $METASPACE_SIZE_MB / 5`

[[ -z "${RESERVED_CODE_CACHE_SIZE_MB}" ]] && RESERVED_CODE_CACHE_SIZE_MB=`expr $METASPACE_SIZE_MB / 2`

[[ -z "${DIRECT_MEMORY_SIZE_MB}" ]] && DIRECT_MEMORY_SIZE_MB=`expr $POD_MEM_LIMIT_MB / 10`

OTHER_USAGE_MB=`expr $POD_MEM_LIMIT_MB / 4`

NON_HEAP_SIZE_MB=`expr $METASPACE_SIZE_MB + $RESERVED_CODE_CACHE_SIZE_MB + $DIRECT_MEMORY_SIZE_MB + $OTHER_USAGE_MB`
HEAP_SIZE_MB=`expr $POD_MEM_LIMIT_MB - $NON_HEAP_SIZE_MB`

#-XX:CompressedClassSpaceSize=${COMPRESSED_CLASS_SPACE_SIZE_MB}M

echo POD_MEM_LIMIT_MB=$POD_MEM_LIMIT_MB -Xmx${HEAP_SIZE_MB}M \
-XX:MaxMetaspaceSize=${METASPACE_SIZE_MB}M \
-XX:ReservedCodeCacheSize=${RESERVED_CODE_CACHE_SIZE_MB}M \
-XX:MaxDirectMemorySize=${DIRECT_MEMORY_SIZE_MB}M

java \
-Xms${HEAP_SIZE_MB}M \
-Xmx${HEAP_SIZE_MB}M \
-XX:MaxMetaspaceSize=${METASPACE_SIZE_MB}M \
-XX:ReservedCodeCacheSize=${RESERVED_CODE_CACHE_SIZE_MB}M \
-XX:MaxDirectMemorySize=${DIRECT_MEMORY_SIZE_MB}M \
-XX:+UseStringDeduplication -XX:+ExitOnOutOfMemoryError \
-jar -agentpath:/async-profiler/lib/libasyncProfiler.so app.jar
