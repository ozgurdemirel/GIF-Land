#!/bin/bash
# =====================================================
# App Store için Build ve Notarization Script
# =====================================================

set -e

# Environment değişkenlerini yükle
if [ -f .env.appstore ]; then
    export $(cat .env.appstore | grep -v '^#' | xargs)
else
    echo "❌ .env.appstore dosyası bulunamadı!"
    echo "Önce ./scripts/appstore/setup-signing.sh scriptini çalıştırın"
    exit 1
fi

echo "🍎 App Store Build Başlatılıyor..."
echo "=================================="

# 1. Temizlik
echo "🧹 Önceki build'leri temizliyoruz..."
./gradlew clean

# 2. DMG oluştur (signed)
echo "📦 Signed DMG oluşturuluyor..."
./gradlew packageDmg \
    -Pcompose.desktop.mac.sign=true \
    -Pcompose.desktop.mac.signing.identity="$SIGNING_IDENTITY" \
    -Pcompose.desktop.mac.notarization.appleID="$APPLE_ID" \
    -Pcompose.desktop.mac.notarization.password="$APPLE_PASSWORD" \
    -Pcompose.desktop.mac.notarization.teamID="$TEAM_ID"

DMG_PATH="composeApp/build/compose/binaries/main/dmg/WebP Recorder-1.0.0.dmg"

if [ ! -f "$DMG_PATH" ]; then
    echo "❌ DMG dosyası oluşturulamadı!"
    exit 1
fi

echo "✅ DMG oluşturuldu: $DMG_PATH"

# 3. Code Signing Doğrulama
echo "🔐 Code signing doğrulanıyor..."
spctl -a -t open --context context:primary-signature -v "$DMG_PATH" || {
    echo "⚠️  Code signing doğrulaması başarısız!"
}

# 4. Notarization
echo "📤 Apple Notarization başlatılıyor..."
echo "Bu işlem 5-30 dakika sürebilir..."

# Notarization submit
xcrun notarytool submit "$DMG_PATH" \
    --apple-id "$APPLE_ID" \
    --password "$APPLE_PASSWORD" \
    --team-id "$TEAM_ID" \
    --wait || {
    echo "❌ Notarization başarısız!"
    exit 1
}

# 5. Staple the notarization
echo "📌 Notarization stapling..."
xcrun stapler staple "$DMG_PATH"

# 6. Final doğrulama
echo "✅ Final doğrulama..."
spctl -a -t open --context context:primary-signature -v "$DMG_PATH"
xcrun stapler validate "$DMG_PATH"

echo ""
echo "🎉 Build ve Notarization Tamamlandı!"
echo "====================================="
echo "📦 DMG: $DMG_PATH"
echo ""
echo "📱 App Store Connect'e Yükleme:"
echo "--------------------------------"
echo "1. https://appstoreconnect.apple.com giriş yapın"
echo "2. 'My Apps' > '+' > 'New App'"
echo "3. Uygulama bilgilerini girin:"
echo "   - Platform: macOS"
echo "   - Name: WebP Recorder"
echo "   - Primary Language: English"
echo "   - Bundle ID: $BUNDLE_ID"
echo "   - SKU: webp-recorder-001"
echo "4. 'Transporter' uygulamasını indirin (Mac App Store'dan)"
echo "5. Transporter ile DMG'yi yükleyin"
echo ""
echo "📝 Gerekli App Store Bilgileri:"
echo "--------------------------------"
echo "• Açıklama (min 10 karakter)"
echo "• Keywords (100 karakter)"
echo "• Support URL"
echo "• Marketing URL (opsiyonel)"
echo "• Privacy Policy URL"
echo "• Screenshots (min 1, max 10)"
echo "  - 1280x800 veya 1440x900 (16:10)"
echo "  - 2560x1600 veya 2880x1800 (16:10)"
echo "• App Icon (1024x1024)"
echo "• Kategori seçimi"
echo "• Age Rating anketi"