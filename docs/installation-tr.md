# GIF Land - WebP/MP4 Screen Recorder Kurulum Kılavuzu

Bu kılavuz, WebP/MP4 Screen Recorder uygulamasını başka bir bilgisayarda nasıl kuracağınızı ve çalıştıracağınızı detaylı olarak açıklamaktadır.

## 📋 İçindekiler
- [Sistem Gereksinimleri](#sistem-gereksinimleri)
- [Gerekli Yazılımlar](#gerekli-yazılımlar)
- [Detaylı Kurulum Adımları](#detaylı-kurulum-adımları)
- [Proje Yapısı](#proje-yapısı)
- [Build İşlemleri](#build-işlemleri)
- [Sorun Giderme](#sorun-giderme)
- [Hızlı Kurulum](#hızlı-kurulum-tldr)

## 🖥️ Sistem Gereksinimleri

### Minimum Gereksinimler
- **İşletim Sistemi**: macOS 10.15 (Catalina) veya üzeri
- **İşlemci**: Intel x86_64 veya Apple Silicon (M1/M2/M3)
- **RAM**: En az 8GB (önerilen)
- **Disk Alanı**: En az 5GB boş alan
- **İnternet**: Bağımlılıkları indirmek için gerekli

### Desteklenen Platformlar
- macOS Intel (x86_64)
- macOS Apple Silicon (arm64/aarch64)

## 🛠️ Gerekli Yazılımlar

### 1. Xcode Command Line Tools
macOS'ta native kod derlemek için gereklidir.

```bash
# Kurulu olup olmadığını kontrol et
xcode-select -p

# Kurulu değilse kur (yaklaşık 15 dakika sürer)
xcode-select --install

# Kurulum penceresinde "Install" butonuna tıklayın ve bekleyin
```

### 2. Homebrew Paket Yöneticisi
macOS için paket yöneticisi, diğer araçları kurmak için gerekli.

```bash
# Homebrew kurulu mu kontrol et
which brew

# Kurulu değilse kur
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# Apple Silicon Mac için PATH'e ekle
echo 'eval "$(/opt/homebrew/bin/brew shellenv)"' >> ~/.zprofile
eval "$(/opt/homebrew/bin/brew shellenv)"

# Intel Mac için PATH'e ekle (genelde otomatik yapılır)
echo 'export PATH="/usr/local/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc

# Kurulumu doğrula
brew --version
```

### 3. Java Development Kit (JDK) 17 veya üzeri
Kotlin/Compose Desktop için gereklidir.

```bash
# Java kurulu mu kontrol et
java -version

# JDK 17'yi Homebrew ile kur
brew install openjdk@17

# JDK'yı sistem Java'sı olarak ayarla
sudo ln -sfn /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-17.jdk

# PATH'e ekle
echo 'export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc

# Kurulumu doğrula
java -version  # "openjdk version 17.x.x" göstermeli
```

### 4. FFmpeg (Opsiyonel)
Uygulama kendi içinde FFmpeg barındırır, ancak sistem genelinde kullanmak isterseniz:

```bash
# FFmpeg kurulu mu kontrol et
which ffmpeg

# Kurulu değilse kur
brew install ffmpeg

# Kurulumu doğrula
ffmpeg -version
```

## 📥 Detaylı Kurulum Adımları

### Adım 1: Projeyi Klonla veya İndir

```bash
# Git ile klonla (önerilen)
git clone https://github.com/username/gif-land.git
cd gif-land

# VEYA: ZIP olarak indir
# GitHub'dan ZIP indir ve aç
unzip gif-land-main.zip
cd gif-land-main
```

### Adım 2: Dizin Yapısını Kontrol Et

```bash
# Proje dizininde olduğundan emin ol
ls -la

# Şu dosyaları görmeli:
# - gradlew (çalıştırılabilir)
# - settings.gradle.kts
# - composeApp/ dizini
# - scripts/ dizini
```

### Adım 3: FFmpeg'i Derle (İsteğe Bağlı)
Uygulama içinde gömülü FFmpeg kullanmak için:

```bash
# FFmpeg'i static olarak derle (30-60 dakika sürebilir)
./scripts/build/build-ffmpeg-static.sh

# Başarılı olup olmadığını kontrol et
ls -la composeApp/src/jvmMain/resources/native/macos/ffmpeg
```

### Adım 4: Uygulamayı Derle ve Çalıştır

```bash
# Gradle wrapper'a çalıştırma izni ver
chmod +x gradlew

# Bağımlılıkları indir ve derle (ilk seferde uzun sürer)
./gradlew build

# Uygulamayı çalıştır
./gradlew :composeApp:run
```

## 📦 Build İşlemleri

### A. Hızlı Build (Geliştirme)

```bash
# Kotlin/Compose uygulamasını derle ve çalıştır
./gradlew :composeApp:run
```

### B. Tam Build (Dağıtım)

```bash
# 1. FFmpeg'i derle (bir kere yapılır, uzun sürer)
./scripts/build/build-ffmpeg-static.sh

# 2. DMG oluştur
./gradlew :composeApp:packageDmg
```

### C. Script ile Otomatik Build

```bash
# Tüm bileşenleri derle ve DMG oluştur
./scripts/package/create-full-package.sh

# Sadece DMG oluştur (FFmpeg zaten derlenmişse)
./scripts/package/create-dmg.sh
```

## 📂 Proje Yapısı

```
gif-land/
├── composeApp/                        # Ana uygulama
│   ├── src/
│   │   └── jvmMain/
│   │       ├── kotlin/                # Kotlin kaynak kodları
│   │       │   └── club/ozgur/gifland/
│   │       │       ├── App.kt         # Ana uygulama
│   │       │       ├── capture/       # Ekran yakalama
│   │       │       ├── encoder/       # Pure Kotlin encoder
│   │       │       │   ├── KotlinEncoder.kt     # JVM encoder
│   │       │       │   └── NativeRecorderClient.kt
│   │       │       └── ui/            # Kullanıcı arayüzü
│   │       └── resources/
│   │           └── native/macos/
│   │               └── ffmpeg         # Gömülü FFmpeg (8MB)
│   └── build.gradle.kts               # Build yapılandırması
├── scripts/                           # Yardımcı scriptler
│   ├── build/                         # Build scriptleri
│   ├── package/                       # Paketleme scriptleri
│   └── run/                           # Çalıştırma scriptleri
├── gradle/                            # Gradle wrapper
├── gradlew                            # Unix/Mac Gradle wrapper
├── gradlew.bat                        # Windows Gradle wrapper
└── settings.gradle.kts                # Proje ayarları
```

## 🔧 Sorun Giderme

### Sorun 1: Java Sürüm Hatası
```
Error: Java 11 has been deprecated. Java 17 or above is required.
```

**Çözüm:**
```bash
# JDK 17 kur
brew install openjdk@17

# Varsayılan yap
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

### Sorun 2: Gradle İzin Hatası
```
Permission denied: ./gradlew
```

**Çözüm:**
```bash
chmod +x gradlew
```

### Sorun 3: FFmpeg Bulunamadı
```
FFmpeg not found
```

**Çözüm:**
```bash
# Static FFmpeg derle
./scripts/build/build-ffmpeg-static.sh

# VEYA sistem FFmpeg kur
brew install ffmpeg
```

### Sorun 4: Out of Memory Hatası

**Çözüm:**
```bash
# Gradle'a daha fazla bellek ayır
export GRADLE_OPTS="-Xmx4g -Xms512m"

# Veya gradle.properties dosyasına ekle
echo "org.gradle.jvmargs=-Xmx4g -Xms512m" >> gradle.properties
```

## 🚀 Hızlı Kurulum (TL;DR)

Deneyimli kullanıcılar için hızlı kurulum:

```bash
# 1. Gerekli araçları kur
xcode-select --install
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
brew install openjdk@17 git

# 2. Projeyi klonla
git clone https://github.com/username/gif-land.git
cd gif-land

# 3. Derle ve çalıştır
chmod +x gradlew
./gradlew :composeApp:run

# 4. DMG oluştur (opsiyonel)
./scripts/package/create-full-package.sh
```

## 📊 Performans İpuçları

### Build Hızlandırma
```bash
# Gradle daemon kullan
./gradlew --daemon :composeApp:run

# Paralel derleme
./gradlew --parallel :composeApp:run

# Gradle cache kullan
./gradlew --build-cache :composeApp:packageDmg
```

### Bellek Optimizasyonu
```bash
# gradle.properties dosyası oluştur
cat > gradle.properties << EOF
org.gradle.jvmargs=-Xmx4g -Xms1g -XX:+UseG1GC
org.gradle.parallel=true
org.gradle.daemon=true
org.gradle.caching=true
kotlin.incremental=true
EOF
```

## 📝 Notlar

- İlk derleme internet bağlantısı gerektirir ve 10-20 dakika sürebilir
- FFmpeg derleme işlemi bir kere yapılır ve 30-60 dakika sürebilir
- DMG dosyası `composeApp/build/compose/binaries/main/dmg/` altında oluşur
- Uygulama pure JVM implementation kullanır, harici native process gerekmez

## 🆘 Yardım

Sorun yaşarsanız:
1. `./gradlew clean` ile temiz build deneyin
2. `rm -rf .gradle build` ile cache temizleyin
3. GitHub Issues'da sorun bildirin

## 📄 Lisans

Bu proje demo amaçlıdır. Ticari kullanım için lisans gereklidir.