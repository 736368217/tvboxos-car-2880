#!/system/bin/sh
p=$(pidof system_server)
for key in BOOTCLASSPATH DEX2OATBOOTCLASSPATH SYSTEMSERVERCLASSPATH; do
 value=$(tr '\000' '\n' < /proc/$p/environ | grep "^$key=" | cut -d= -f2-)
 export "$key=$value"
done
exec nsenter -t "$p" -m -- /system/bin/sh "$@"
