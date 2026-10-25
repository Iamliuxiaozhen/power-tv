# ShizukuShellProcReader reaches the deprecated (and private since API 13)
# Shizuku.newProcess reflectively, so R8 must not rename or strip it.
-keep class rikka.shizuku.Shizuku {
    private static ** newProcess(java.lang.String[], java.lang.String[], java.lang.String);
}
-keep class rikka.shizuku.ShizukuRemoteProcess { *; }
