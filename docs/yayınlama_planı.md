# 📱 WebP Recorder - Apple App Store Yayınlama Planı

## 📋 İçindekiler
- [Genel Bakış](#genel-bakış)
- [Zaman Çizelgesi](#zaman-çizelgesi)
- [Maliyet Analizi](#maliyet-analizi)
- [Teknik Hazırlıklar](#teknik-hazırlıklar)
- [Script Kullanım Rehberi](#script-kullanım-rehberi)
- [App Store Gereksinimleri](#app-store-gereksinimleri)
- [Yayın Sonrası Plan](#yayın-sonrası-plan)

---

## 🎯 Genel Bakış

WebP Recorder uygulamasının Apple App Store'da yayınlanması için kapsamlı bir plan ve otomatik script'ler hazırlandı.

### Proje Durumu
- ✅ Uygulama geliştirme tamamlandı
- ✅ App Store konfigürasyonları eklendi
- ✅ Build ve paketleme script'leri hazır
- ⏳ Apple Developer hesabı açılması gerekiyor
- ⏳ Code signing sertifikaları alınması gerekiyor

### Hedef Kitle
- **Birincil**: İçerik üreticileri, yazılım geliştiriciler
- **İkincil**: Eğitimciler, tasarımcılar
- **Platform**: macOS 10.15+

---

## ⏰ Zaman Çizelgesi

### Hafta 1: Hesap ve Sertifikalar
| Gün | Görev | Tahmini Süre | Sorumlu |
|-----|-------|--------------|---------|
| 1 | Apple Developer Program kaydı | 2-3 saat | Hesap sahibi |
| 2-3 | Hesap onayı bekleme | 24-48 saat | Apple |
| 4 | Developer ID sertifikası oluşturma | 1 saat | Geliştirici |
| 5 | App-specific password kurulumu | 30 dakika | Geliştirici |
| 5 | Team ID ve signing identity ayarları | 30 dakika | Geliştirici |

### Hafta 2: Asset ve Build Hazırlığı
| Gün | Görev | Tahmini Süre | Sorumlu |
|-----|-------|--------------|---------|
| 6-7 | App Icon tasarımı (1024x1024) | 2-3 saat | Tasarımcı |
| 8 | Screenshot'ların alınması | 2 saat | Geliştirici |
| 9 | App Store açıklamaları yazımı | 2 saat | İçerik |
| 10 | Privacy Policy güncelleme | 1 saat | Hukuk/İçerik |
| 11 | Build ve code signing | 1 saat | Geliştirici |
| 12 | Notarization işlemi | 2-3 saat | Otomatik |

### Hafta 3: App Store Connect
| Gün | Görev | Tahmini Süre | Sorumlu |
|-----|-------|--------------|---------|
| 13 | App Store Connect'te app oluşturma | 1 saat | Geliştirici |
| 14 | Metadata ve görsellerin yüklenmesi | 2 saat | Geliştirici |
| 15 | Build yükleme (Transporter) | 1 saat | Geliştirici |
| 16 | Internal TestFlight | 1 gün | Test ekibi |
| 17-18 | Bug düzeltmeleri (varsa) | Değişken | Geliştirici |
| 19 | Submit for Review | 30 dakika | Geliştirici |

### Hafta 4: Review ve Yayın
| Gün | Görev | Tahmini Süre | Sorumlu |
|-----|-------|--------------|---------|
| 20-22 | Apple Review süreci | 24-72 saat | Apple |
| 23 | Review feedback (varsa düzeltme) | Değişken | Geliştirici |
| 24 | Yayın onayı | Otomatik | Apple |
| 25+ | Launch ve marketing | Sürekli | Pazarlama |

---

## 💰 Maliyet Analizi

### Zorunlu Maliyetler
| Kalem | Maliyet | Periyot | Not |
|-------|---------|---------|-----|
| Apple Developer Program | $99 | Yıllık | ~3,300 TL |
| Code Signing Sertifikası | Dahil | - | Developer Program'a dahil |

### Opsiyonel Maliyetler
| Kalem | Maliyet | Not |
|-------|---------|-----|
| Icon tasarım | $50-200 | Profesyonel tasarımcı |
| Screenshot düzenleme | $30-100 | Canva Pro veya benzeri |
| App Store Optimization (ASO) | $100-500/ay | Keyword araştırması |
| Apple Search Ads | $50-500/ay | Reklam bütçesi |
| Transporter | Ücretsiz | Apple'ın yükleme aracı |

### Gelir Projeksiyonu
```
Senaryo 1 - Ücretsiz Model:
- İndirme hedefi: 1,000/ay
- Gelir: $0
- Amaç: Kullanıcı tabanı oluşturma

Senaryo 2 - Ücretli Model ($4.99):
- İndirme hedefi: 100/ay
- Brüt gelir: $499/ay
- Apple komisyonu (%30): $150
- Net gelir: $349/ay (~11,500 TL)

Senaryo 3 - Freemium Model:
- Ücretsiz indirme: 1,000/ay
- Pro dönüşüm (%5): 50 kullanıcı
- Pro fiyat: $9.99
- Net gelir: $349/ay
```

---

## 🛠 Teknik Hazırlıklar

### Dosya Yapısı
```
gif-land/
├── composeApp/
│   ├── build.gradle.kts         # ✅ App Store konfigürasyonu eklendi
│   ├── entitlements.plist       # ✅ Sandbox ve izinler
│   └── src/jvmMain/resources/
│       ├── Info.plist            # ✅ App metadata
│       └── icons/
│           └── app-icon.icns    # ✅ macOS icon
├── scripts/
│   ├── appstore/
│   │   ├── setup-signing.sh     # ✅ Sertifika kurulumu
│   │   ├── build-for-appstore.sh # ✅ Build ve notarization
│   │   └── prepare-assets.sh    # ✅ Asset hazırlama
│   └── package/
│       └── create-full-package.sh # ✅ DMG oluşturma
├── appstore-assets/              # 📁 Oluşturulacak
│   ├── screenshots/
│   ├── icons/
│   └── metadata/
├── docs/
│   └── yayınlama_planı.md       # 📍 Bu dosya
└── PRIVACY.md                    # ✅ Gizlilik politikası
```

### Teknik Gereksinimler
- ✅ macOS 10.15+ desteği
- ✅ Code signing altyapısı
- ✅ Notarization desteği
- ✅ Sandbox uyumluluğu
- ✅ FFmpeg bundled (24MB)
- ✅ JVM Runtime bundled

---

## 📜 Script Kullanım Rehberi

### 1. setup-signing.sh
**Amaç**: Apple sertifikalarını ve environment değişkenlerini ayarlar

```bash
# Kullanım
./scripts/appstore/setup-signing.sh

# Ne yapar:
# - Mevcut sertifikaları kontrol eder
# - .env.appstore dosyasını oluşturur
# - Team ID bulma yöntemlerini gösterir
# - Kurulum adımlarını detaylıca açıklar

# Çıktılar:
# - .env.appstore (doldurulması gereken)
# - Kurulum kontrol listesi
```

**Manuel Adımlar**:
1. developer.apple.com'dan Developer ID Certificate indir
2. appleid.apple.com'dan app-specific password oluştur
3. .env.appstore dosyasını gerçek bilgilerle doldur

### 2. prepare-assets.sh
**Amaç**: App Store için gerekli görselleri ve metadataları hazırlar

```bash
# Kullanım
./scripts/appstore/prepare-assets.sh

# Ne yapar:
# - appstore-assets/ klasörünü oluşturur
# - README dosyaları ile gereksinimleri belirtir
# - Metadata şablonları oluşturur
# - Kontrol listesi sağlar

# Çıktılar:
# - appstore-assets/screenshots/README.md
# - appstore-assets/icons/README.md
# - appstore-assets/metadata/app-description.txt
# - appstore-assets/CHECKLIST.md
# - PRIVACY.md
```

**Manuel Adımlar**:
1. 1024x1024 App Icon hazırla (Figma, Sketch, Photoshop)
2. En az 1 screenshot al (1280x800 veya 2560x1600)
3. Metadata dosyalarını düzenle

### 3. build-for-appstore.sh
**Amaç**: Uygulamayı imzalar, paketler ve Apple'a notarization için gönderir

```bash
# Ön koşul: .env.appstore dolu olmalı

# Kullanım
./scripts/appstore/build-for-appstore.sh

# Ne yapar:
# 1. Gradle clean
# 2. Signed DMG oluşturur
# 3. Code signing doğrular
# 4. Apple'a notarization için gönderir (5-30 dk)
# 5. Notarization ticket'ını DMG'ye "staple" eder
# 6. Final doğrulama yapar

# Çıktılar:
# - composeApp/build/compose/binaries/main/dmg/WebP Recorder-1.0.0.dmg (signed & notarized)
# - Yükleme talimatları
```

**Sorun Giderme**:
- "Identity not found": Sertifika kurulu değil
- "Invalid password": App-specific password yanlış
- "Team ID not found": .env.appstore kontrol et
- Notarization fail: Apple Status sayfasını kontrol et

### 4. create-full-package.sh
**Amaç**: Geliştirme/test için hızlı DMG build'i (signing olmadan)

```bash
# Kullanım
./scripts/package/create-full-package.sh

# Ne yapar:
# - FFmpeg varlığını kontrol eder
# - Gradle clean & packageDmg
# - DMG boyutunu raporlar

# Çıktılar:
# - Unsigned DMG (test için)
```

---

## 📱 App Store Gereksinimleri

### Zorunlu Bilgiler
| Alan | Gereksinim | Durum |
|------|------------|-------|
| App Name | Max 30 karakter | ✅ WebP Recorder |
| Subtitle | Max 30 karakter | ⏳ "Screen to WebP/MP4 Converter" |
| Bundle ID | Unique | ✅ club.ozgur.gifland |
| SKU | Unique | ⏳ webp-recorder-001 |
| Primary Category | Seçim | ✅ Productivity |
| Secondary Category | Opsiyonel | ⏳ Photo & Video |
| Age Rating | Anket | ⏳ 4+ |

### İçerik Gereksinimleri
| İçerik | Min | Max | Durum |
|--------|-----|-----|-------|
| Screenshots | 1 | 10 | ⏳ |
| App Preview Video | 0 | 3 | Opsiyonel |
| Description | 10 | 4000 karakter | ⏳ |
| Keywords | 0 | 100 karakter | ⏳ |
| Promotional Text | 0 | 170 karakter | ⏳ |

### Teknik Gereksinimler
- ✅ 64-bit binary
- ✅ macOS 10.15+ SDK
- ✅ Hardened Runtime
- ✅ Notarization
- ✅ Sandbox entitlements
- ✅ Info.plist tam

---

## 📈 Yayın Sonrası Plan

### İlk Hafta
- [ ] Launch duyurusu (Twitter, LinkedIn)
- [ ] ProductHunt submission
- [ ] Reddit r/macapps paylaşımı
- [ ] GitHub README güncelleme
- [ ] App Store linki ekleme

### İlk Ay
- [ ] Kullanıcı geri bildirimlerini topla
- [ ] App Store Review'larına cevap ver
- [ ] Crash report'ları analiz et
- [ ] Version 1.0.1 bug fix planı
- [ ] App Analytics'i incele

### 3 Aylık Plan
- [ ] Feature request'leri değerlendir
- [ ] Version 1.1.0 major update
- [ ] App Store Optimization (ASO)
- [ ] Lokalizasyon (Türkçe, Almanca, Fransızca)
- [ ] Pro version değerlendirmesi

### Metrikler
```yaml
Başarı Kriterleri:
  İndirme Sayısı:
    - Hedef: 100/hafta
    - Min: 50/hafta
  
  Rating:
    - Hedef: 4.5+
    - Min: 4.0+
  
  Crash Rate:
    - Hedef: <%1
    - Max: %2
  
  Retention:
    - 7-day: %40
    - 30-day: %20
```

---

## 🚀 Hızlı Başlangıç

### Acil Yapılacaklar (Sırayla)

1. **Bugün**:
   ```bash
   # Asset'leri hazırla
   ./scripts/appstore/prepare-assets.sh
   
   # 1024x1024 icon tasarla
   # Screenshot'lar al
   ```

2. **Apple Developer Hesabı Açılınca**:
   ```bash
   # Sertifika kurulumu
   ./scripts/appstore/setup-signing.sh
   
   # .env.appstore dosyasını doldur
   nano .env.appstore
   ```

3. **Build ve Yükleme**:
   ```bash
   # Signed build oluştur
   ./scripts/appstore/build-for-appstore.sh
   
   # Transporter ile yükle
   # App Store Connect'te submit
   ```

---

## 📞 Destek ve İletişim

### Teknik Sorunlar
- GitHub Issues: `github.com/yourusername/webp-recorder/issues`
- Email: `support@example.com`

### App Store Review Sorunları
- Review notlarına detaylı açıklama ekle
- Demo video hazırla (gerekirse)
- Appeal process kullan

### Topluluk
- Discord/Slack kanalı aç
- Twitter hesabı oluştur
- Dökümentasyon wiki'si

---

## 📚 Kaynaklar

### Resmi Dökümanlar
- [Apple Developer Program](https://developer.apple.com/programs/)
- [App Store Review Guidelines](https://developer.apple.com/app-store/review/guidelines/)
- [Notarization Documentation](https://developer.apple.com/documentation/security/notarizing_macos_software_before_distribution)
- [App Store Connect Help](https://help.apple.com/app-store-connect/)

### Faydalı Araçlar
- [Transporter](https://apps.apple.com/us/app/transporter/id1450874784) - Build yükleme
- [CreateML](https://developer.apple.com/machine-learning/create-ml/) - Icon oluşturma
- [Reality Converter](https://developer.apple.com/augmented-reality/tools/) - 3D preview (opsiyonel)

### Topluluk Kaynakları
- [r/macapps](https://reddit.com/r/macapps) - Tanıtım
- [ProductHunt](https://producthunt.com) - Launch
- [MacUpdate](https://macupdate.com) - Listeleme
- [AlternativeTo](https://alternativeto.net) - Karşılaştırma

---

## ✅ Son Kontrol Listesi

Yayına hazır mısınız? Tüm maddeleri kontrol edin:

- [ ] Apple Developer Program üyeliği aktif
- [ ] Developer ID sertifikası Keychain'de
- [ ] App-specific password oluşturuldu
- [ ] .env.appstore dosyası dolu
- [ ] 1024x1024 App Icon hazır
- [ ] En az 1 screenshot hazır
- [ ] App Store description yazıldı
- [ ] Privacy Policy yayında
- [ ] Build notarized
- [ ] TestFlight'ta test edildi
- [ ] Crash report'lar temiz
- [ ] Performance metrikleri iyi
- [ ] Review notları hazır
- [ ] Support email aktif
- [ ] Backup plan hazır

---

*Son Güncelleme: Ekim 2024*
*Versiyon: 1.0.0*