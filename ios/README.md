# Cleanup - iOS Storage Optimizer

A comprehensive iOS app to clean up and optimize your iPhone storage.

## Features

- **Duplicate Photo Detection**: Find and remove duplicate photos
- **Similar Photo Grouping**: Smart detection of similar photos with "Best" marking
- **Screenshot Cleanup**: Bulk delete screenshots
- **Large Video Detection**: Find and remove large videos
- **Video Compression**: Compress videos without losing quality
- **Contact Management**: Merge duplicate contacts, remove incomplete entries
- **Email Cleanup**: Tips and guidance for cleaning up emails
- **Secret Vault**: PIN/biometric protected private space for photos and videos
- **Charging Animations**: Customizable animations when charging
- **Home Screen Widgets**: Storage, battery, and quick cleanup widgets
- **Subscription Management**: Weekly and lifetime subscription options

## Requirements

- iOS 16.0+
- Xcode 15.0+
- Swift 5.9+

## Setup Instructions

### 1. Install XcodeGen (Recommended)

```bash
brew install xcodegen
```

### 2. Generate Xcode Project

Navigate to the ios folder and run:

```bash
cd ~/Documents/GitHub/Cleanup/ios
xcodegen generate
```

This will create `Cleanup.xcodeproj` from the `project.yml` configuration.

### 3. Open in Xcode

```bash
open Cleanup.xcodeproj
```

### 4. Configure Signing

1. Select the project in the navigator
2. Select your team in Signing & Capabilities
3. Update the bundle identifier if needed

### 5. Add App Icon

Replace the placeholder in:
- `Cleanup/Resources/Assets.xcassets/AppIcon.appiconset/`

### 6. Configure In-App Purchases

1. Go to App Store Connect
2. Create subscription products with IDs:
   - `com.cleanup.weekly`
   - `com.cleanup.lifetime`

## Project Structure

```
Cleanup/
├── App/                    # Main app entry point and views
├── Core/
│   ├── Services/          # Business logic services
│   ├── Managers/          # State managers
│   ├── Extensions/        # Swift extensions
│   └── Utilities/         # Helper functions
├── Features/
│   ├── Photos/            # Photo cleanup feature
│   ├── Videos/            # Video cleanup & compression
│   ├── Contacts/          # Contact management
│   ├── Email/             # Email cleanup guidance
│   ├── Vault/             # Secret vault feature
│   ├── Widgets/           # Widget configuration
│   ├── ChargingAnimations/ # Charging animations
│   └── Compression/       # Video compression
├── Models/                # Data models
├── UI/
│   ├── Components/        # Reusable UI components
│   ├── Styles/            # Custom styles
│   └── Modifiers/         # View modifiers
├── Resources/
│   ├── Assets.xcassets/   # Images and colors
│   └── Animations/        # Lottie animations (optional)
└── Subscription/          # IAP handling

CleanupWidget/             # Widget extension
```

## Privacy

This app:
- Works completely offline
- Never uploads your data
- Stores vault content locally with encryption
- Cannot access your data server-side

## Architecture

- **SwiftUI** for all UI
- **MVVM** architecture pattern
- **Swift Concurrency** (async/await) for asynchronous operations
- **StoreKit 2** for in-app purchases
- **PhotoKit** for photo library access
- **Contacts Framework** for contact management
- **AVFoundation** for video compression
- **WidgetKit** for home screen widgets
- **LocalAuthentication** for biometric security

## Building for Release

1. Update version in `project.yml`
2. Regenerate project: `xcodegen generate`
3. Archive in Xcode: Product > Archive
4. Upload to App Store Connect

## License

Proprietary - All rights reserved
