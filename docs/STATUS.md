# İlmerya — doğrulama durumu (5 Ekim 2026)

Durumlar ayrı tutulur: kod yazıldı / derlendi / otomatik test edildi / emülatörde görüldü / gerçek cihazda test edildi / mağazaya yüklendi / incelemede / yayında.

## Doğrulananlar

| Alan | Kanıt |
|---|---|
| Ortak kurallar, kayıt/geri yükleme, lig istemcisi, ses kuyrukları | 43 JVM testi geçti (`:shared:testDebugUnitTest`), macOS CI'da da geçti |
| Sunucu | 34 test: Game Center imza doğrulaması, gerçek HTTP ile lig oyunu gönderme/tekrar/sıralama/hesap silme, PostgreSQL TLS kuralları |
| Android debug / QA / imzalı release | Derlendi. QA (R8 + test reklamı) API 36 emülatörde açıldı, test geçiş reklamı yüklendi, çökme yok. Release AAB İlmerya yükleme anahtarıyla imzalı (SHA-1 `8D:5B:7D:17:E8:2A:58:09:0A:1A:AA:9C:6B:65:8B:5C:16:9E:75:1C`) |
| Android arayüz akışı | `ExperienceTest` API 36 emülatörde geçti (rehber, yerleştirme, geri ile duraklatma, menüden devam, lig ekranı, geri) |
| Süreç ölümünden sonra geri yükleme | Emülatörde `force-stop` sonrası "Kaldığın yerden devam et" göründü |
| Zincir animasyonu | Emülatör ekran kaydında 4 dalgalı zincir ve etiketler görüldü; ilk kare sıçraması düzeltildi |
| iOS | Xcode 26 / iOS 26.2 simülatöründe derlendi, kuruldu ve ana menü doğru çizildi (CI artifact) |
| Mağaza kayıtları | Play Console: İlmerya `com.ozgames.ilmerya` oluşturuldu (taslak). Google Cloud: `ilmerya` projesi. Neon: `ilmerya-league` (Frankfurt, Free) |

## Henüz doğrulanmayanlar / eksikler

- Gerçek Android cihaz testi (telefon başka oturumda kullanımdaydı). Gerçek iPhone testi yok.
- Sesler ölçümle düzeltildi; telefon hoparlörü ve kulaklıkla dinleme testi yapılmadı.
- Lig canlı değil: Render servisi, Play Games projesi/OAuth istemcileri, Game Center App Store kaydı ve gerçek hesapla uçtan uca test bekliyor. Mağaza sürümü lig URL'si ve kimlikler girildikten sonra derlenecek.
- iOS imzası ve TestFlight: `apple-distribution` ortam gizlileri ve App Store provizyon profili bekleniyor (Issuer ID gerekli). App Store Connect uygulama kaydı yok.
- AdMob: uygulamalar mağazaya bağlanmadı; `app-ads.txt` lig sunucusunda hazır, alan adı yayında değil. UMP mesajları AdMob'da yayınlanmalı.
- Gerçek ekran görüntüleri, öne çıkan görsel, içerik derecelendirmesi, Data Safety ve App Privacy formları (taslak: `store-assets/listing.md`).
- Play üretim erişimi için 12 testçi × 14 gün kapalı test şartı.
- Uzun süreli insan oyun testleri ile denge değerlendirmesi.
