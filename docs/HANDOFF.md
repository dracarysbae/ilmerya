# İlmerya — devir notu (5 Ekim 2026, 16:30 TR)

Bu not, işi devralacak ajan (ChatGPT/Codex) içindir. Ayrıntılı kanıtlar: `docs/STATUS.md`. Mağaza metinleri ve beyan taslakları: `store-assets/listing.md`.

## Kimlikler (gizli değil)

| Şey | Değer |
|---|---|
| Depo | github.com/dracarysbae/ilmerya (herkese açık, `main`) |
| Paket / bundle | `com.ozgames.ilmerya` (Android namespace kodda `com.bloxtrix.hexdrop` olarak kaldı, sorun değil) |
| Play Console | geliştirici 6400369457258382422, uygulama 4975647079024897021, hesap zuberozcan24@gmail.com (Chrome'da `/u/1/`) |
| Dahili test | yayında, 1.0.0 (versionCode 1); liste "Ilmerya testers"; katılım https://play.google.com/apps/internaltest/4701736684417648322 |
| Lig sunucusu | https://ilmerya-league.onrender.com (Render servis `srv-db1pbi1srm7s73co188g`, Free, `server/Dockerfile`, `main`'e push edince otomatik deploy) |
| Veritabanı | Neon proje `ilmerya-league` (autumn-math-46844551), Frankfurt, Free |
| Google Cloud | proje `ilmerya` (120496089291), hesap ozgamesstudio24@gmail.com (Chrome'da `authuser=3`); OAuth "In production" |
| PGS uygulama kimliği | 120496089291 |
| OAuth istemcileri | web (sunucu) `120496089291-e755tardlg8d3r3qa2qk7c7ush6i43li`; Android yükleme anahtarı; Android Play imzalama anahtarı |
| İmza SHA-1 | yükleme `8D:5B:7D:17:E8:2A:58:09:0A:1A:AA:9C:6B:65:8B:5C:16:9E:75:1C`; Play imzalama `C6:4A:BA:79:8C:55:2D:3C:A7:B3:BC:40:71:3E:D2:6A:C2:C8:C6:C0` |
| AdMob | pub-1875904677314834; Android uygulama `~1610529280`, geçiş `/1855081025`; iOS uygulama `~6655058128`, geçiş `/2541476737` |
| App Store Connect | uygulama 6819181162, SKU `ilmerya-ios`, birincil dil tr; App ID ve "Ilmerya App Store" profili hazır (Game Center açık) |
| İletişim e-postası | ozgamesstudio24@gmail.com (her yerde bu; yalnız Play geliştirici hesabı zuberozcan24'te) |

## Gizli bilgiler nerede (değerleri asla koda/loga/sohbete yazma)

- Android yükleme anahtarı: `%USERPROFILE%\.ilmerya-signing\ilmerya-upload.jks` + `ilmerya-upload.properties` (Gradle bunları otomatik okur).
- PGS web istemci sırrı: Render ortam değişkeni `PGS_WEB_CLIENT_SECRET`; yerel kopya `%USERPROFILE%\.ilmerya-signing\pgs-web-client.json`.
- Veritabanı: Render ortam değişkeni `ILMERYA_JDBC_URL` (Neon'un `postgresql://` dizesi; sunucu verify-full'a yükseltir).
- iOS: GitHub ortamı `apple-distribution` içinde ASC API anahtarı ve profil var. **Eksik:** dağıtım sertifikası `.p12` + parolası.

## Yapılmış ve doğrulanmış

- Oyun kuralları, kayıt/geri yükleme, animasyonlar, ses, küçük ekran pencereleri, geri tuşu: testler + gerçek Galaxy A56 + CI emülatörleri.
- Testler: `:shared:testDebugUnitTest` 46/46, sunucu 35/35 (JDK 21 gerekir: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"`).
- Lig canlı: gerçek telefonda Play Games girişi → sunucu doğruladı → lig ekranı ve sıralama göründü.
- İmzalı AAB dahili teste yüklendi ve yayınlandı. `gradle.properties` içinde canlı sunucu URL'si ve PGS kimlikleri var.
- iOS: simülatör CI'ı ve imzasız Release derlemesi geçiyor; `Release.xcconfig` canlı lig URL'sini içeriyor.

## Kalan işler (öncelik sırasıyla)

1. **Play Console mağaza kaydı** (hepsi Play Console'da, zuberozcan24):
   - Ana mağaza girişi TR (varsayılan) + EN: metinler `store-assets/listing.md`; simge `store-assets/icon/ilmerya-play-icon-512.png`.
   - Öne çıkan görsel 1024×500 (henüz yok, üretilmeli).
   - Telefon ekran görüntüleri (en az 2, oran ≤ 2:1): `store-assets/raw` altındaki ham görüntülerden 1080×1920 kareler üretilmeli. Debug/QA derlemesinde `adb shell am start -n com.ozgames.ilmerya.qa/com.bloxtrix.hexdrop.MainActivity --es ilmerya.scene board|result|tutorial|settings` demo sahneleri açar.
   - Uygulama içeriği: gizlilik politikası `https://ilmerya-league.onrender.com/privacy`, reklam var, hedef kitle 13+, içerik derecelendirmesi anketi, Data Safety (taslak `listing.md`), hesap silme URL'si `/delete-account`, devlet/finans/sağlık uygulaması değil.
   - Kategori: Oyun → Bulmaca; iletişim e-postası ozgamesstudio24@gmail.com.
2. **Kapalı test**: dahili testteki sürümü kapalı teste yükselt; üretim erişimi için 12 testçi × 14 gün şartı var.
3. **Play Games Hizmetleri'ni yayınla**: mağaza girişi tamamlanınca (Play Console → Play Oyun Hizmetleri → Yayınlama). Yayınlanana kadar yalnız PGS test kullanıcıları lige girebilir.
4. **AdMob**: iki uygulamayı mağaza kayıtlarına bağla; UMP (GDPR/US) mesajlarını yayınla. `app-ads.txt` şu an sunucuda; AdMob'un okuyabilmesi için mağaza girişindeki "web sitesi" alanı `https://ilmerya-league.onrender.com` olmalı.
5. **iOS / TestFlight**:
   - Kullanıcı `.p12`'yi kendisi ekleyecek: `powershell -ExecutionPolicy Bypass -File tools\set-ios-secrets.ps1 -DistributionP12 "<gerçek yol>"`.
   - Sonra GitHub Actions → `ios-testflight.yml` → `build_number=1`, `upload=true`.
   - ASC: metadata, App Privacy (taslak `listing.md`), yaş derecelendirmesi, 6.9" ekran görüntüleri (simülatörde `-ilmeryaScene` argümanı), Game Center etkinleştirme, incelemeye gönderme.
6. Sesleri gerçek hoparlör/kulaklıkla dinleyerek kontrol et.

## Kurallar (kullanıcının koyduğu, bozma)

- Bütçe yok: hiçbir ücretli plan, kart, deneme kredisi ("$300 free trial") vb. tıklanmayacak.
- Bloxboom'un (`E:\uygulamalar\tetris-kmp`) canlı servisine, veritabanına, OAuth kimliklerine dokunulmayacak. Render'da `bloxboom-league` servisi aynı çalışma alanında; ona dokunma.
- Debug/QA derlemelerinde gerçek reklam trafiği üretme. Gerçek cihazda lig testi için `./gradlew :androidApp:assembleQa -PILMERYA_QA_LEAGUE=true` kullan (mağaza paketi + yükleme anahtarı, test reklamı).
- PostgreSQL'de sertifika/hostname doğrulaması korunacak (sunucu zorluyor).
- Yeni imza anahtarı üretme; mevcut yükleme anahtarını kullan.
- "Gönderildi" ile "onaylandı/yayında" ayrı raporlanacak; test edilmemiş şey "bitti" denmeyecek.
- Kullanıcıyla Türkçe, kısa ve net konuş.

## Kod haritası (kısa)

- `shared/` ortak Kotlin (oyun motoru `engine/`, arayüz `ui/`, lig istemcisi `competition/`, kayıt `persistence/`).
- `androidApp/` Android girişi, reklam, Play Games (`competition/AndroidLeague.kt`, `PlayGamesIdentity.kt`).
- `iosApp/` XcodeGen projesi, Swift köprüleri (Game Center, reklam).
- `server/` Kotlin/JVM lig sunucusu (`Server.kt`, `WeeklyStore.kt`, `LeagueDatabase.kt`), statik sayfalar `server/src/main/resources/public/`.
- CI: `.github/workflows/android-ci.yml`, `ios-simulator.yml`, `ios-testflight.yml`.
