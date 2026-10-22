# FinTrack has no reflection of its own. Room, WorkManager, Compose and Navigation
# ship consumer rules with their libraries, so nothing extra is needed here.
#
# Keep line numbers so crash reports from release builds stay readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Credential Manager finds its Play Services implementation by reflection.
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** {
  *;
}
