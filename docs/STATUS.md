# İlmerya — doğrulama durumu (5 Ekim 2026)

Durumlar ayrı tutulur: kod yazıldı / derlendi / otomatik test edildi / emülatörde görüldü / gerçek cihazda test edildi / mağazaya yüklendi / incelemede / yayında.

## Doğrulananlar

| Alan | Kanıt |
|---|---|
| Ortak kurallar, kayıt/geri yükleme, lig istemcisi, ses kuyrukları | 43 JVM testi geçti (`:shared:testDebugUnitTest`), macOS CI'da da geçti |
| Sunucu | 34 test: Game Center imza doğrulaması, gerçek HTTP ile lig oyunu gönderme/tekrar/sıralama/hesap silme, PostgreSQL TLS kuralları |
| Android debug / QA / imzalı release | Derlendi. QA (R8 + test reklamı) API 36 emülatörde açıldı, test geçiş reklamı yüklendi, çökme yok. Release AAB İlmerya yükleme anahtarıyla imzalı (SHA-1 `8D:5B:7D:17:E8:2A:58:09:0A:1A:AA:9C:6B:65:8B:5C:16:9E:75:1C`) |
| Android arayüz akışı | `ExperienceTest` API 36 emülatörde geçti (rehber, yerleştirme, geri ile duraklatma, menüden devam, lig ekranı, geri) |
| Android CI | GitHub Actions run 37236964802: birim + sunucu testleri, debug/QA (R8) derlemeleri, API 35 (Pixel 7) ve API 30 (Nexus 5X) emülatörlerinde iki arayüz testi (ilk açılış rehberi, oynanış, geri ile duraklatma, menüden devam, lig ekranı; etkinlik yeniden oluşturulduğunda tahta ve dil ayarı korunuyor) geçti |
| Süreç ölümünden sonra geri yükleme | Emülatörde `force-stop` sonrası "Kaldığın yerden devam et" göründü |
| Zincir animasyonu | Emülatör ekran kaydında 4 dalgalı zincir ve etiketler görüldü; ilk kare sıçraması düzeltildi |
| iOS | GitHub Actions run 37234212393: Xcode 26 / iOS 26.2 simülatöründe derlendi, kuruldu, iki açılışta 25 sn sonra çalışıyordu, çökme raporu yok; imzasız Release cihaz derlemesi (üretim reklam kimliği doğrulamasıyla) geçti. Oynanış, reklam, Game Center ve ses henüz iOS'ta test edilmedi |
| Mağaza kayıtları | Play Console: İlmerya `com.ozgames.ilmerya` (taslak, uygulama kimliği 4975647079024897021). App Store Connect: İlmerya, Apple ID 6819181162, SKU `ilmerya-ios`, birincil dil tr. Apple Developer: App ID `com.ozgames.ilmerya` (Game Center açık), "Ilmerya App Store" profili (mevcut dağıtım sertifikasıyla, 28.09.2027). Google Cloud: `ilmerya` projesi (120496089291). Neon: `ilmerya-league` (Frankfurt, Free) |
| Gerçek cihaz (Galaxy A56, Android 16) | Arayüz testleri geçti; QA (R8) derlemesiyle tam oyun → test geçiş reklamı → "Yeni rekor" sonucu; süreç öldürülünce oyun aynı tahtayla geri geldi; müzik AudioTrack ile çalıyor (cihaz medya sesi sıfırdı) |
| Küçük ekran | 360×640 dp: tüm pencereler kaydırmasız sığıyor, dış dokunuşla kapanıyor; kompakt oyun düzeni; CI'da Nexus 5 emülatörü |

## Henüz doğrulanmayanlar / eksikler

- Gerçek Android cihaz testi (telefon başka oturumda kullanımdaydı). Gerçek iPhone testi yok.
- Sesler ölçümle düzeltildi; telefon hoparlörü ve kulaklıkla dinleme testi yapılmadı.
- Lig canlı değil: Render servisi, Play Games projesi/OAuth istemcileri, Game Center App Store kaydı ve gerçek hesapla uçtan uca test bekliyor. Mağaza sürümü lig URL'si ve kimlikler girildikten sonra derlenecek.
- iOS imzası ve TestFlight: profil ve App Store Connect API gizlileri GitHub `apple-distribution` ortamında. Eksik: mevcut dağıtım sertifikasının .p12 dosyası (korumalı klasörde; `tools/set-ios-secrets.ps1` ile kullanıcı ekler).
- GitHub Actions: özel depo kotası dolduğu için kullanıcı onayıyla depo herkese açıldı (gizli bilgi taraması temiz).
- AdMob: uygulamalar mağazaya bağlanmadı; `app-ads.txt` lig sunucusunda hazır, alan adı yayında değil. UMP mesajları AdMob'da yayınlanmalı.
- Gerçek ekran görüntüleri, öne çıkan görsel, içerik derecelendirmesi, Data Safety ve App Privacy formları (taslak: `store-assets/listing.md`).
- Play üretim erişimi için 12 testçi × 14 gün kapalı test şartı.
- Uzun süreli insan oyun testleri ile denge değerlendirmesi.
