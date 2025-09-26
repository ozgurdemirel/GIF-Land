# 📦 Distribution Guide - WebP Recorder

## Current Status
- ✅ DMG created: **67 MB** (down from 734 MB!)
- ❌ Code signing: Not signed
- ❌ Notarization: Not notarized
- ⚠️ Distribution: **Cannot be distributed on App Store or directly to users yet**

## Requirements for Distribution

### 1. Apple Developer Account ($99/year)
You need an Apple Developer account to:
- Get code signing certificates
- Notarize your app
- Distribute on Mac App Store (optional)

Sign up at: https://developer.apple.com/programs/

### 2. Code Signing Certificate
After getting developer account:
```bash
# List available signing identities
security find-identity -v -p codesigning

# You should see something like:
# "Developer ID Application: Your Name (TEAMID)"
```

### 3. Code Sign the Application

#### A. Update build.gradle.kts
```kotlin
compose.desktop {
    application {
        nativeDistributions {
            macOS {
                iconFile.set(project.file("src/jvmMain/resources/icons/app-icon.icns"))
                bundleID = "club.ozgur.gifland"

                // Add signing configuration
                signing {
                    sign.set(true)
                    identity.set("Developer ID Application: Your Name (TEAMID)")
                }
            }
        }
    }
}
```

#### B. Or Sign Manually After Build
```bash
# Sign the app
codesign --deep --force --verify --verbose \
    --sign "Developer ID Application: Your Name (TEAMID)" \
    --options runtime \
    "composeApp/build/compose/binaries/main/app/WebP Recorder.app"

# Verify signature
codesign -dv --verbose=4 "composeApp/build/compose/binaries/main/app/WebP Recorder.app"

# Create signed DMG
./gradlew :composeApp:packageDmg
```

### 4. Notarize the Application

#### A. Create App-Specific Password
1. Go to https://appleid.apple.com/account/manage
2. Security → App-Specific Passwords → Generate Password
3. Save this password securely

#### B. Store Credentials
```bash
# Store credentials in keychain
xcrun notarytool store-credentials "WebP-Recorder-Notary" \
    --apple-id "your-apple-id@example.com" \
    --team-id "TEAMID" \
    --password "app-specific-password"
```

#### C. Notarize the DMG
```bash
# Submit for notarization (use actual generated DMG path)
xcrun notarytool submit "composeApp/build/compose/binaries/main/dmg/WebP Recorder-1.0.0.dmg" \
    --keychain-profile "WebP-Recorder-Notary" \
    --wait

# Check status
xcrun notarytool history --keychain-profile "WebP-Recorder-Notary"

# Staple the ticket to DMG
xcrun stapler staple "composeApp/build/compose/binaries/main/dmg/WebP Recorder-1.0.0.dmg"

# Verify
spctl -a -t open --context context:primary-signature -v "WebP Recorder-1.0.0.dmg"
```

### 5. Distribution Options

#### Option A: Direct Distribution (Recommended)
- ✅ Upload DMG to GitHub Releases
- ✅ Host on your website
- ✅ Share download link
- ✅ No Apple review process
- ✅ Keep 100% of revenue

#### Option B: Mac App Store
- Need to modify app for sandboxing
- Submit for Apple review
- Apple takes 15-30% commission
- More visibility

### 6. Important Notes

#### System Requirements Warning
Your app uses:
- **Bundled FFmpeg**: FFmpeg binary is bundled; users do not need to install it
- **Screen Recording Permission**: macOS will ask for permission on first run
- **Accessibility Permission**: For global keyboard shortcuts

Add to your distribution page:
```markdown
## System Requirements
- macOS 10.15 or later
- Screen Recording permission (granted on first use)
```

#### Current Limitations
1. **No streaming support**: MP4 streaming not yet implemented with native encoder
2. **FFmpeg dependency**: Users must have FFmpeg installed
3. **No auto-update**: Consider adding Sparkle framework for updates

### 7. Testing Distribution

Before distributing, test on a clean Mac:
1. Download DMG
2. Check Gatekeeper: `spctl -a -t open --context context:primary-signature -v YourApp.dmg`
3. Open DMG
4. Drag to Applications
5. Try to run (should not show "unidentified developer" if properly signed)

### 8. Quick Distribution (Without Signing)

⚠️ **For testing only - users will see security warnings:**

Users will need to:
1. Download DMG
2. Open DMG
3. Drag to Applications
4. Right-click app → Open (first time only)
5. Click "Open" in security dialog

### 9. Automation Script

Create `sign-and-notarize.sh`:
```bash
#!/bin/bash

# Configuration
IDENTITY="Developer ID Application: Your Name (TEAMID)"
PROFILE="WebP-Recorder-Notary"
APP_PATH="composeApp/build/compose/binaries/main/app/WebP Recorder.app"
DMG_PATH="composeApp/build/compose/binaries/main/dmg/WebP Recorder-1.0.0.dmg"

# Build
./scripts/package/create-full-package.sh

# Sign
codesign --deep --force --verify --verbose \
    --sign "$IDENTITY" \
    --options runtime \
    "$APP_PATH"

# Create DMG (rebuild after signing)
./gradlew :composeApp:packageDmg

# Notarize
xcrun notarytool submit "$DMG_PATH" \
    --keychain-profile "$PROFILE" \
    --wait

# Staple
xcrun stapler staple "$DMG_PATH"

# Verify
spctl -a -t open --context context:primary-signature -v "$DMG_PATH"

echo "✅ DMG is ready for distribution: $DMG_PATH"
```

## Summary

**Current State**: ❌ Cannot distribute to general users
**Reason**: Not code signed or notarized
**Cost**: $99/year for Apple Developer account
**Time**: 1-2 hours to set up signing and notarization
**Alternative**: Share with technical users who can bypass Gatekeeper

---

## Resources
- [Apple Developer Program](https://developer.apple.com/programs/)
- [Notarizing macOS Software](https://developer.apple.com/documentation/security/notarizing_macos_software_before_distribution)
- [Code Signing Guide](https://developer.apple.com/library/archive/documentation/Security/Conceptual/CodeSigningGuide/Introduction/Introduction.html)
- [Compose Multiplatform Signing](https://github.com/JetBrains/compose-multiplatform/tree/master/tutorials/Native_distributions_and_local_execution)