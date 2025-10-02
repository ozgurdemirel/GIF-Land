#!/bin/bash
# =====================================================
# App Store Asset Hazırlama Script'i
# =====================================================

set -e

echo "🎨 App Store Assets Hazırlama"
echo "=============================="

# Asset klasörü oluştur
ASSET_DIR="appstore-assets"
mkdir -p "$ASSET_DIR/screenshots"
mkdir -p "$ASSET_DIR/icons"
mkdir -p "$ASSET_DIR/metadata"

# 1. Screenshot boyutları
echo "📸 Screenshot Gereksinimleri:"
echo "-----------------------------"
cat > "$ASSET_DIR/screenshots/README.md" << 'EOF'
# App Store Screenshot Gereksinimleri

## Zorunlu Boyutlar (en az biri)
- 1280 x 800 px (16:10)
- 1440 x 900 px (16:10)
- 2560 x 1600 px (16:10) - Retina
- 2880 x 1800 px (16:10) - Retina

## Screenshot İpuçları
1. Uygulamanın temel özelliklerini gösterin
2. Temiz, profesyonel görünüm
3. Text overlay ekleyebilirsiniz (opsiyonel)
4. 1-10 arası screenshot yükleyebilirsiniz

## Önerilen Screenshot'ler
1. Ana ekran - boş durum
2. Kayıt başlatma
3. Kayıt sırasında kompakt görünüm
4. Ayarlar ekranı
5. Kaydedilmiş dosya önizleme

## Screenshot Alma
```bash
# Uygulamamızı çalıştır
./gradlew run

# macOS screenshot: Cmd+Shift+4 (alan seçimi)
# veya Cmd+Shift+3 (tam ekran)
```
EOF

# 2. App Icon gereksinimleri
echo "🎨 App Icon Hazırlama:"
echo "----------------------"
cat > "$ASSET_DIR/icons/README.md" << 'EOF'
# App Store Icon Gereksinimleri

## Boyutlar
- 1024 x 1024 px (App Store için)
- PNG formatında
- Transparanlık OLMAMALI
- Köşeler düz (Apple otomatik yuvarlatır)

## Icon Versiyonları (opsiyonel, uygulama içinde kullanım için)
- 16x16
- 32x32
- 64x64
- 128x128
- 256x256
- 512x512

## Icon Oluşturma
```bash
# ImageMagick ile resize (kurulu değilse: brew install imagemagick)
convert app-icon-1024.png -resize 512x512 app-icon-512.png
convert app-icon-1024.png -resize 256x256 app-icon-256.png
# ... diğer boyutlar için devam edin
```
EOF

# 3. Metadata şablonu
echo "📝 Metadata Hazırlama:"
echo "---------------------"
cat > "$ASSET_DIR/metadata/app-description.txt" << 'EOF'
# WebP Recorder - App Store Description

## Kısa Açıklama (Promotional Text - max 170 karakter)
Transform your screen recordings into efficient WebP animations and MP4 videos with just one click!

## Uzun Açıklama (Description - max 4000 karakter)
WebP Recorder is the ultimate screen recording tool for macOS, designed to create compact, high-quality WebP animations and MP4 videos effortlessly.

KEY FEATURES:

⚡ Lightning Fast Recording
Start recording instantly with customizable keyboard shortcuts. No complex setup required.

🎯 Smart Recording Modes
• Fullscreen capture for presentations
• Custom area selection for focused content
• Window-specific recording (coming soon)

📦 Multiple Output Formats
• WebP: Ultra-efficient animated images, perfect for documentation
• MP4: Universal video format with H.264 encoding
• GIF: Classic animated format (coming soon)

🎨 Intelligent Compression
Advanced compression algorithms ensure your recordings are crystal clear while maintaining minimal file sizes.

🖱️ Global Controls
Control recording from anywhere with global hotkeys:
• F9: Start/Stop recording
• F10: Pause/Resume
• Escape: Cancel recording

📊 Real-time Monitoring
Track your recording progress with:
• Live frame counter
• Duration timer
• File size estimation
• Dynamic window resizing during recording

🚀 Performance Optimized
Built with efficiency in mind:
• Low CPU usage
• Minimal memory footprint
• Hardware acceleration support

Perfect for:
• Creating software tutorials
• Bug reporting and QA
• Design feedback
• Educational content
• Social media posts

No watermarks, no time limits, no subscriptions. Just powerful screen recording when you need it.

System Requirements:
• macOS 10.15 or later
• 2GB RAM minimum
• 100MB free disk space

## Keywords (max 100 karakter, virgülle ayrılmış)
screen recorder,webp,mp4,capture,video,animation,gif,recording,screenshot,screencast

## Support URL
https://github.com/yourusername/webp-recorder/issues

## Marketing URL (opsiyonel)
https://github.com/yourusername/webp-recorder

## Privacy Policy URL
https://github.com/yourusername/webp-recorder/blob/main/PRIVACY.md
EOF

# 4. Privacy Policy şablonu
echo "🔒 Privacy Policy Oluşturuluyor..."
cat > "PRIVACY.md" << 'EOF'
# Privacy Policy for WebP Recorder

**Last Updated: October 2024**

## Overview
WebP Recorder ("we", "our", or "the app") respects your privacy and is committed to protecting your personal data.

## Data Collection
WebP Recorder does NOT collect, store, or transmit any personal information or usage data.

## Screen Recording
- All screen recordings are processed locally on your device
- Recordings are saved only to locations you specify
- No recordings are uploaded to any servers
- We have no access to your recorded content

## Permissions
The app requires the following permissions to function:
- **Screen Recording**: To capture your screen content
- **File System Access**: To save recordings to your chosen location
- **Accessibility**: To detect global keyboard shortcuts

## Third-Party Services
WebP Recorder does not use any third-party analytics, tracking, or advertising services.

## Data Storage
- All settings are stored locally on your device
- No data is synced to cloud services
- No user accounts or profiles are created

## Changes to Privacy Policy
We may update this privacy policy from time to time. Any changes will be reflected in the "Last Updated" date.

## Contact
For privacy-related questions: [your-email@example.com]
EOF

# 5. Version history
cat > "$ASSET_DIR/metadata/version-history.txt" << 'EOF'
# Version History

## Version 1.0.0
**What's New:**
- Initial release
- WebP animation recording
- MP4 video recording
- Global hotkey controls
- Custom recording area selection
- Real-time recording stats
- Automatic window resizing during recording
EOF

# 6. App Store Connect checklist
cat > "$ASSET_DIR/CHECKLIST.md" << 'EOF'
# App Store Submission Checklist

## Hesap Hazırlığı
- [ ] Apple Developer Program üyeliği ($99/yıl)
- [ ] Apple ID two-factor authentication aktif
- [ ] Developer ID sertifikası oluşturuldu
- [ ] App-specific password oluşturuldu
- [ ] Team ID alındı

## Uygulama Hazırlığı
- [ ] Bundle ID belirlendi (club.ozgur.gifland)
- [ ] Version number (1.0.0)
- [ ] Build number (1)
- [ ] Code signing yapıldı
- [ ] Notarization tamamlandı

## Assets
- [ ] App Icon (1024x1024)
- [ ] Screenshots (en az 1, max 10)
- [ ] App Store açıklaması
- [ ] Keywords
- [ ] Support URL
- [ ] Privacy Policy URL

## App Store Connect
- [ ] Yeni app oluşturuldu
- [ ] Genel bilgiler girildi
- [ ] Fiyatlandırma seçildi (Free/Paid)
- [ ] Territories seçildi
- [ ] Age rating anketi dolduruldu
- [ ] Screenshots yüklendi
- [ ] Build yüklendi (Transporter ile)

## Test
- [ ] TestFlight internal testing
- [ ] TestFlight external testing (opsiyonel)
- [ ] Crash report kontrolü
- [ ] Performance metrikleri

## Submission
- [ ] App Review notları eklendi (gerekirse)
- [ ] Contact information doğrulandı
- [ ] Submit for Review
- [ ] Review süreci (genelde 24-48 saat)

## Post-Launch
- [ ] App Analytics kontrolü
- [ ] User feedback takibi
- [ ] Crash reports kontrolü
- [ ] Version 1.0.1 planlaması
EOF

echo ""
echo "✅ Asset klasörleri ve şablonlar oluşturuldu!"
echo "============================================"
echo ""
echo "📁 Oluşturulan Dosyalar:"
echo "  $ASSET_DIR/"
echo "  ├── screenshots/README.md"
echo "  ├── icons/README.md"
echo "  ├── metadata/"
echo "  │   ├── app-description.txt"
echo "  │   └── version-history.txt"
echo "  ├── CHECKLIST.md"
echo "  └── PRIVACY.md"
echo ""
echo "📋 Yapılacaklar:"
echo "1. Screenshots alın (uygulamayı çalıştırıp Cmd+Shift+4)"
echo "2. 1024x1024 App Store icon'u hazırlayın"
echo "3. Metadata bilgilerini güncelleyin"
echo "4. Privacy Policy'yi kendi bilgilerinizle güncelleyin"