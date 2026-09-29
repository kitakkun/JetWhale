# The build's lint plugin records build-time facts as annotations on some classes. Nothing reads
# them at runtime and they are not published, so an app shrinking this library needs no class for them.
-dontwarn com.kitakkun.kotrail.**
