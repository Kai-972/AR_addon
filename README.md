# Landmark Verification App

ARCore Geospatial + Augmented Image presence verification for Android.

## Build Instructions

1. Open project in Android Studio
2. Sync Gradle files
3. Build: `./gradlew :app:assembleDebug`
4. Install: `./gradlew :app:installDebug`

## Checkpoint Development Process

This project is built using a checkpoint-based approach:
- Each checkpoint delivers specific files only
- Wait for completion marker: `===CHECKPOINT N COMPLETE===`
- Respond with `PROCEED CHECKPOINT N+1` to continue
- No feature creep or extra files between checkpoints

## Requirements

- Android SDK 26+ (target 33)
- ARCore supported device
- Location permissions required
- Camera permissions required

## Next Steps

After CHECKPOINT 0: Basic project structure established. Next checkpoint will add AR session management and permissions handling.
