#!/bin/sh
# usage: run_bench.sh <name> <script> <transactions> [extra pgbench args]
name=$1; script=$2; n=$3; shift 3
mkdir -p /tmp/res && cd /tmp/res && rm -f pgbench_log.* 
pgbench -n -U postgres -d spike -f /work/spike/collections/bench/$script -t $n -c 1 -l "$@" >/tmp/res/$name.out 2>&1
cat pgbench_log.* | awk '{print $3/1000}' | sort -n > /tmp/res/$name.ms
awk -v name=$name '{a[NR]=$1} END {printf "%-14s n=%d p50=%.1f p90=%.1f p99=%.1f max=%.1f ms\n", name, NR, a[int(NR*0.5)], a[int(NR*0.9)], a[int(NR*0.99)], a[NR]}' /tmp/res/$name.ms
