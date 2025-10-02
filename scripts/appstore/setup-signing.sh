#!/bin/bash
# =====================================================
# Apple Code Signing ve Notarization Kurulumu
# =====================================================

set -e

echo "🔐 Apple Code Signing Kurulumu"
echo "================================"

# Renklendirme
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 1. Developer Certificate kontrolü
echo -e "\n${YELLOW}1. Developer Certificate Kontrolü${NC}"
echo "-----------------------------------"

# Mevcut sertifikaları listele
echo "Mevcut Code Signing Sertifikaları:"
security find-identity -v -p codesigning | grep "Developer ID Application" || echo "❌ Developer ID Application sertifikası bulunamadı!"

echo -e "\n${YELLOW}Sertifika Kurulumu:${NC}"
echo "1. developer.apple.com > Account > Certificates, IDs & Profiles"
echo "2. 'Certificates' > '+' > 'Developer ID Application'"
echo "3. İndirilen sertifikayı çift tıklayarak Keychain'e ekleyin"

# 2. App-specific password oluşturma
echo -e "\n${YELLOW}2. App-Specific Password Oluşturma${NC}"
echo "------------------------------------"
echo "1. appleid.apple.com > Sign In > Security"
echo "2. 'App-Specific Passwords' > 'Generate Password'"
echo "3. 'WebP Recorder Notarization' gibi bir isim verin"
echo "4. Oluşturulan şifreyi kaydedin (xxxx-xxxx-xxxx-xxxx formatında)"

# 3. Environment dosyası oluştur
echo -e "\n${YELLOW}3. Environment Değişkenleri${NC}"
echo "----------------------------"

if [ ! -f .env.appstore ]; then
    cat > .env.appstore << 'EOF'
# Apple Developer Bilgileri
# ⚠️ BU DOSYAYI GIT'E COMMIT ETMEYİN!

# Apple ID (developer account email)
APPLE_ID="your-email@example.com"

# App-specific password (xxxx-xxxx-xxxx-xxxx formatında)
APPLE_PASSWORD="xxxx-xxxx-xxxx-xxxx"

# Team ID (developer.apple.com'dan alın)
TEAM_ID="XXXXXXXXXX"

# Signing Identity (genelde "Developer ID Application: Your Name (TEAMID)")
SIGNING_IDENTITY="Developer ID Application: Your Name (XXXXXXXXXX)"

# Bundle ID
BUNDLE_ID="club.ozgur.gifland"

# Keychain şifresi (notarization için)
KEYCHAIN_PASSWORD="your-keychain-password"
EOF

    echo -e "${GREEN}✅ .env.appstore dosyası oluşturuldu${NC}"
    echo "⚠️  Lütfen dosyayı gerçek bilgilerinizle güncelleyin"
else
    echo -e "${YELLOW}📁 .env.appstore dosyası zaten mevcut${NC}"
fi

# 4. .gitignore'a ekle
if ! grep -q ".env.appstore" .gitignore 2>/dev/null; then
    echo ".env.appstore" >> .gitignore
    echo -e "${GREEN}✅ .env.appstore .gitignore'a eklendi${NC}"
fi

# 5. Notarization tool kurulumu
echo -e "\n${YELLOW}4. Notarization Tool Kontrolü${NC}"
echo "------------------------------"

if ! command -v xcrun &> /dev/null; then
    echo -e "${RED}❌ Xcode Command Line Tools kurulu değil${NC}"
    echo "Kurmak için: xcode-select --install"
else
    echo -e "${GREEN}✅ Xcode Command Line Tools kurulu${NC}"
fi

# Team ID'yi kontrol et
echo -e "\n${YELLOW}5. Team ID Bulma${NC}"
echo "----------------"
echo "Team ID'nizi bulmak için:"
echo "1. developer.apple.com > Account"
echo "2. Sağ üstte 'Team ID' görünecektir"
echo "3. Veya terminal: security find-identity -v | grep 'Developer ID'"

echo -e "\n${YELLOW}6. Sertifika Doğrulama${NC}"
echo "----------------------"
echo "Sertifikanızı test etmek için:"
echo "codesign --verify --deep --strict --verbose=2 'path/to/your.app'"

echo -e "\n${GREEN}📋 Yapılacaklar Listesi:${NC}"
echo "------------------------"
echo "[ ] Apple Developer Program'a kayıt ($99/yıl)"
echo "[ ] Developer ID Application sertifikası oluştur"
echo "[ ] App-specific password oluştur"
echo "[ ] .env.appstore dosyasını güncelle"
echo "[ ] Xcode Command Line Tools kurulu"
echo "[ ] Team ID'yi al"

echo -e "\n${YELLOW}📚 Faydalı Linkler:${NC}"
echo "-------------------"
echo "• Developer Portal: https://developer.apple.com"
echo "• Apple ID: https://appleid.apple.com"
echo "• Notarization Docs: https://developer.apple.com/documentation/security/notarizing_macos_software_before_distribution"
echo "• App Store Guidelines: https://developer.apple.com/app-store/review/guidelines/"