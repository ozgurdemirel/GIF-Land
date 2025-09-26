# GIF/WebP/MP4 Screen Recorder

A high-performance screen recording application for macOS built with Kotlin Multiplatform and Compose Desktop. Records screen content directly to WebP, MP4, or GIF formats using pure JVM implementation.

> **⚠️ IMPORTANT:** Currently supports **Apple Silicon Macs only** (M1/M2/M3/M4). The bundled FFmpeg is compiled for ARM64 architecture. Intel Mac support coming soon.

## ✨ Features

- **Multiple Recording Modes**: Full screen, area selection, window capture
- **Multiple Output Formats**: WebP (animated), MP4, GIF
- **Pure JVM Implementation**: No external native processes required
- **High Performance**: Optimized frame capture and encoding
- **Built-in FFmpeg**: No external dependencies needed
- **Customizable Settings**: FPS (1-60), quality, scale factor
- **Real-time Preview**: See recording area before starting
- **Pause/Resume**: Control recording without losing frames

## 🚀 Quick Start

```bash
# Run in development mode
./gradlew :composeApp:run

# Build DMG for distribution
./scripts/package/create-full-package.sh
```

## 📋 Requirements

- **macOS**: 10.15 (Catalina) or higher
- **JDK**: 17 or higher
- **Memory**: 8GB RAM recommended
- **Disk Space**: 5GB for build

## 🏗️ Architecture

- **Kotlin/Compose Desktop**: Modern UI framework with declarative UI
- **Pure JVM Encoder**: All encoding done in JVM without external processes
- **Bundled FFmpeg**: Statically linked FFmpeg for video processing
- **Zero Native Dependencies**: No Rust, C++, or other native code required

## 📂 Project Structure

```
gif-land/
├── composeApp/                 # Main application
│   └── src/jvmMain/
│       ├── kotlin/
│       │   └── club/ozgur/gifland/
│       │       ├── App.kt      # Main application entry
│       │       ├── capture/    # Screen capture logic
│       │       ├── encoder/    # Pure Kotlin encoder
│       │       ├── core/       # Core recording logic
│       │       └── ui/         # UI components
│       └── resources/
│           └── native/macos/
│               └── ffmpeg      # Bundled FFmpeg binary
├── scripts/                    # Build and utility scripts
│   ├── build/                  # Build scripts
│   ├── package/                # Packaging scripts
│   └── run/                    # Development scripts
└── docs/                       # Documentation
```

## 🛠️ Building from Source

### Development Build

```bash
# Clone the repository
git clone https://github.com/yourusername/gif-land.git
cd gif-land

# Quick start - run in development mode
./scripts/run/run-dev.sh

# Or use Gradle directly
./gradlew :composeApp:run
```

### Production Build

```bash
# Complete build - includes FFmpeg and creates DMG
./scripts/package/create-full-package.sh

# DMG will be created at:
# composeApp/build/compose/binaries/main/dmg/WebP Recorder-1.0.0.dmg

# Alternative: Build step by step
./scripts/build/build-ffmpeg-static.sh  # One-time: build FFmpeg
./gradlew :composeApp:packageDmg         # Create DMG
```

### Build Scripts

| Script | Description |
|--------|-------------|
| **Package Scripts** | |
| `./scripts/package/create-full-package.sh` | Complete build with FFmpeg and DMG packaging |
| `./scripts/package/create-dmg.sh` | Create DMG installer from built application |
| **Build Scripts** | |
| `./scripts/build/build-ffmpeg-static.sh` | Build static FFmpeg binary (one-time setup, ~30-60 min) |
| `./scripts/build/build-ffmpeg.sh` | Build FFmpeg with custom configurations |
| `./scripts/build/build-native.sh` | Build application (pure JVM, no native code) |
| `./scripts/build/build-universal.sh` | Build universal binary for Intel + Apple Silicon (TODO) |
| `./scripts/build/clean.sh` | Clean all build artifacts and caches |
| **Run Scripts** | |
| `./scripts/run/run-dev.sh` | Run application in development mode with hot reload |
| `./scripts/run/test-recording.sh` | Test recording functionality and check outputs |

## 🎮 Usage

1. **Launch the application**
2. **Select recording mode**:
   - Full Screen: Records entire display
   - Area Selection: Draw rectangle to record
   - Window Capture: Select specific window
3. **Configure settings**:
   - Format: WebP, MP4, or GIF
   - FPS: 1-60 frames per second
   - Quality: 1-100 (higher is better)
   - Scale: 0.1-1.0 (resize output)
4. **Start recording** with the record button
5. **Stop recording** to save the file to Documents folder

## ⚙️ Configuration

The application saves recordings to `~/Documents/` by default.

### Recording Settings

- **FPS**: Higher FPS = smoother video, larger file size
- **Quality**:
  - WebP: 80-100 recommended for best quality
  - MP4: 50-80 balanced quality/size
  - GIF: 30-50 for reasonable file sizes
- **Scale**: Reduce to create smaller files
- **Max Duration**: Automatic stop after specified seconds

## 🔧 Troubleshooting

### Application won't start
```bash
# Check Java version (requires 17+)
java -version

# Clean and rebuild
./gradlew clean build
```

### FFmpeg not found
```bash
# Rebuild FFmpeg
./scripts/build/build-ffmpeg-static.sh

# Or install system FFmpeg
brew install ffmpeg
```

### Out of memory errors
```bash
# Increase JVM memory
export GRADLE_OPTS="-Xmx4g"
./gradlew :composeApp:run
```

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📄 License

This project is for demonstration purposes. Commercial use requires proper licensing.

## 🙏 Acknowledgments

- [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/) - UI Framework
- [FFmpeg](https://ffmpeg.org/) - Video processing
- [Kotlin](https://kotlinlang.org/) - Programming language

## 📞 Support

For issues and questions:
- Open an issue on GitHub
- Check existing issues for solutions
- Read the [installation guide](docs/installation-tr.md) (Turkish)

---

Made with ❤️ using Kotlin/Compose Desktop