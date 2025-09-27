# Landmark Verification App

ARCore Geospatial + Augmented Image presence verification for Android.

## GitHub Actions Build Instructions

Since you have GitHub Student Pack, you can use GitHub Actions to build the APK:

### Setup:
1. **Push this code to GitHub**:
   ```bash
   git init
   git add .
   git commit -m "Initial commit - CHECKPOINT 0"
   git branch -M main
   git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPO.git
   git push -u origin main
   ```

2. **Trigger Build**:
   - Go to your repo on GitHub
   - Click "Actions" tab
   - Click "Build APK" workflow
   - Click "Run workflow" button

3. **Download APK**:
   - After build completes, go to workflow run
   - Download the "debug-apk" artifact
   - Extract the APK file

### Install on Phone via ADB:
```bash
# Enable Developer Options and USB Debugging on your phone
adb devices                    # Verify phone is connected  
adb install app-debug.apk      # Install the APK
```

## Local Build (if needed):
```bash
./gradlew :app:assembleDebug   # Build APK locally
./gradlew :app:installDebug    # Direct install via ADB
```

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
