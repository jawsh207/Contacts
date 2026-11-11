# R8 rules for release builds. The app itself uses no reflection.

# WorkManager (the daily birthday-calendar refresh) keeps its jobs in a Room database, and Room
# creates the generated database class through its no-argument constructor by reflection.
# R8's full mode strips that constructor, which crashes the app at startup.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
