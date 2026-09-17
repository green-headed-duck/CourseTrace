-keep class org.eclipse.jgit.** { *; }
-dontwarn org.slf4j.**
-dontwarn javax.management.**
-dontwarn org.ietf.jgss.**
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean

# WorkManager/Room instantiate the generated database implementation by name.
# AGP 9 full-mode R8 can otherwise optimize away its zero-argument constructor.
-keep class androidx.work.impl.WorkDatabase_Impl {
    public <init>();
}
