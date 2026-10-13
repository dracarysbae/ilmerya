# İlmerya — doğrulama durumu (5 Ekim 2026)

Durumlar ayrı tutulur: kod yazıldı / derlendi / otomatik test edildi / emülatörde görüldü / gerçek cihazda test edildi / mağazaya yüklendi / incelemede / yayında.

## Doğrulananlar

| Alan | Kanıt |
|---|---|
| Ortak kurallar, kayıt/geri yükleme, lig istemcisi, ses kuyrukları | 46 JVM testi geçti (`:shared:testDebugUnitTest`), macOS CI'da da geçti |
| Sunucu | 35 test (sağlayıcının `postgresql://` dizesi de verify-full ile kabul ediliyor): Game Center imza doğrulaması, gerçek HTTP ile lig oyunu gönderme/tekrar/sıralama/hesap silme, PostgreSQL TLS kuralları |
| Android debug / QA / imzalı release | Derlendi. QA (R8 + test reklamı) API 36 emülatörde açıldı, test geçiş reklamı yüklendi, çökme yok. Release AAB İlmerya yükleme anahtarıyla imzalı (SHA-1 `8D:5B:7D:17:E8:2A:58:09:0A:1A:AA:9C:6B:65:8B:5C:16:9E:75:1C`) |
| Android arayüz akışı | `ExperienceTest` API 36 emülatörde geçti (rehber, yerleştirme, geri ile duraklatma, menüden devam, lig ekranı, geri) |
| Android CI | GitHub Actions run 37236964802: birim + sunucu testleri, debug/QA (R8) derlemeleri, API 35 (Pixel 7) ve API 30 (Nexus 5X) emülatörlerinde iki arayüz testi (ilk açılış rehberi, oynanış, geri ile duraklatma, menüden devam, lig ekranı; etkinlik yeniden oluşturulduğunda tahta ve dil ayarı korunuyor) geçti |
| Süreç ölümünden sonra geri yükleme | Emülatörde `force-stop` sonrası "Kaldığın yerden devam et" göründü |
| Zincir animasyonu | Emülatör ekran kaydında 4 dalgalı zincir ve etiketler görüldü; ilk kare sıçraması düzeltildi |
| iOS | GitHub Actions run 37234212393: Xcode 26 / iOS 26.2 simülatöründe derlendi, kuruldu, iki açılışta 25 sn sonra çalışıyordu, çökme raporu yok; imzasız Release cihaz derlemesi (üretim reklam kimliği doğrulamasıyla) geçti. Oynanış, reklam, Game Center ve ses henüz iOS'ta test edilmedi |
| Mağaza kayıtları | Play Console: İlmerya `com.ozgames.ilmerya` (taslak, uygulama kimliği 4975647079024897021). App Store Connect: İlmerya, Apple ID 6819181162, SKU `ilmerya-ios`, birincil dil tr. Apple Developer: App ID `com.ozgames.ilmerya` (Game Center açık), "Ilmerya App Store" profili (mevcut dağıtım sertifikasıyla, 28.09.2027). Google Cloud: `ilmerya` projesi (120496089291). Neon: `ilmerya-league` (Frankfurt, Free) |
| Gerçek cihaz (Galaxy A56, Android 16) | Arayüz testleri geçti; QA (R8) derlemesiyle tam oyun → test geçiş reklamı → "Yeni rekor" sonucu; süreç öldürülünce oyun aynı tahtayla geri geldi; müzik AudioTrack ile çalıyor (cihaz medya sesi sıfırdı) |
| Lig sunucusu (canlı) | https://ilmerya-league.onrender.com — Render Free, ayrı servis `ilmerya-league` (Bloxboom'dan bağımsız), veritabanı Neon `ilmerya-league`. `/health` → `{"game":"ilmerya","ruleset":1,"ok":true}`; `/privacy`, `/support`, `/delete-account`, `/app-ads.txt` 200; misafir 403; geçersiz kod 400 |
| Play Games / OAuth | Google Auth Platform "In production" (External, ozgamesstudio24). OAuth istemcileri: Android yükleme anahtarı, Android Play imzalama anahtarı (SHA-1 `C6:4A:BA:79:8C:55:2D:3C:A7:B3:BC:40:71:3E:D2:6A:C2:C8:C6:C0`), web (sunucu; sır yalnızca Render'da). PGS projesi 120496089291'e üç kimlik bilgisi bağlandı; PGS test kullanıcıları + dahili test kanalı eklendi; PGS henüz yayınlanmadı |
| Gerçek hesapla lig girişi | Galaxy A56'da lig-QA derlemesi (`-PILMERYA_QA_LEAGUE=true`: mağaza paketi + yükleme anahtarı, yalnız test reklamı) ile Google Play Oyunlar girişi → canlı sunucu doğruladı → lig ekranında oyuncu adı ve haftalık sıralama görüldü |
| Google Play dahili test | 1.0.0 (versionCode 1) AAB yüklendi ve dahili test kanalında yayınlandı; "Ilmerya testers" listesi (4 hesap); katılım: https://play.google.com/apps/internaltest/4701736684417648322 |
| Küçük ekran | 360×640 dp: tüm pencereler kaydırmasız sığıyor, dış dokunuşla kapanıyor; kompakt oyun düzeni; CI'da Nexus 5 emülatörü |


### 13 Ekim 2026 — Claude: mağaza ve Senior Oz

- **Google Play:** İçerik derecelendirmesi tamamlandı (IARC: PEGI 3, ESRB Tüm yaşlar, USK Tüm yaşlar). Kapalı test (Alpha) kanalı: 177 ülke, "Ilmerya testers" listesi, geri bildirim ozgamesstudio24@gmail.com, sürüm 1.0.0 (1) ve TR/EN notlar kaydedildi; **incelemeye gönderilmedi**. Engel: "Oturum açma bilgileri" beyanı için Google, ligdeki Google Play Oyunlar girişi yüzünden ayrı bir inceleme hesabı istiyor (kullanıcı açacak ve parolayı kendisi girecek). Hedef kitle ve veri güvenliği nihai kaydı bu beyana bağlı. Mağaza açıklamalarına (TR/EN) Senior Oz satırı eklendi (taslak kayıt).
- **App Store:** Yeni İlmerya Apple Distribution sertifikası (3WGDMHC358, 13.10.2027) web'den CSR ile oluşturuldu, özel anahtar `%USERPROFILE%\.ilmerya-signing` ve GitHub `apple-distribution` ortamında; "Ilmerya App Store" profili bu sertifikayla yenilendi. TestFlight: **build 1 yüklendi ve işlendi (VALID)**; build 2 (Senior Oz) derleniyor. ASC (iris oturumu): TR/EN ad, alt başlık, açıklama, anahtar kelimeler, destek/pazarlama/gizlilik URL'leri, kategori Oyunlar › Bulmaca + Masa, ücretsiz fiyat, 174 bölge (Çin anakarası kapalı: oyun onay numarası gerekir), yaş derecelendirmesi, içerik hakları, Game Center (detay + sürüm), App Privacy yayınlandı, 6,9" TR/EN 5'er ekran görüntüsü yüklendi, sürüm 1.0.0, dahili TestFlight grubu "OzGAMES ekip" + hesap sahibi davet edildi. Eksik: App Review iletişim telefonu (kullanıcıdan), build seçimi ve incelemeye gönderme.
- **Senior Oz (OzGAMES maskotu):** Bloxboom'daki onaylı karakter referansından Codex görsel üretimiyle (ChatGPT aboneliği) üç poz (taşlarla, işaret eden, sevinen) ve Play öne çıkan görsel sahnesi üretildi (`store-assets/ai-oz`). Oyunda: ana menüde taş halkasını gösteren Oz (nefes alma animasyonu), 3+ dalgalı zincirde / 128+ yeni taşta / oyun içinde rekor geçilince köşeden çıkan kutlama (parıltılar, balon; dokunmayı engellemez), sonuç penceresinde rekor kutlaması, haftalık ligde Oz. "Hareketi azalt" açıkken animasyonsuz. 360×640 dp emülatörde doğrulandı. Mağaza görselleri Oz ile yeniden kuruldu (`tools/compose_store_oz.py`).

## Henüz doğrulanmayanlar / eksikler

### 5 Ekim 2026 — Codex devralma ilerlemesi

- `docs/HANDOFF.md` kuralları okundu; kalan işler 1. maddeden sürdürülüyor.
- Play Console'a `zuberozcan24@gmail.com` / OzGAMES hesabıyla erişildi. Yerleşik tarayıcıda bu hesap `/u/0/`; devir notundaki Chrome `/u/1/` indeksi bu tarayıcıda geçerli değil.
- Varsayılan mağaza girişinin TR ve EN metinleri kaydedildi. Yeni AI simge (512×512), öne çıkan görsel (1024×500) ve dört gerçek oyun ekranını içeren AI sunum görseli (1080×1920) yüklendi ve kaydedildi. Console başarı mesajı: “Değişiklik kaydedildi. Yayınlama özeti sayfasından incelemeye gönderin.” **Henüz incelemeye gönderilmedi/yayınlanmadı.**
- Altı yeni görsel Play Console'da AI ile oluşturulmuş/düzenlenmiş olarak etiketlendi. Oynanış, rehber, menü ve sonuç sırası kullanılıyor. EN metni ayrı; görseller varsayılan TR kaydından kullanılıyor.
- Kategori Oyun → Bulmaca olarak kaydedildi (inceleme bekleyen değişiklik). İletişim e-postası `ozgamesstudio24@gmail.com` ve web sitesi `https://ilmerya-league.onrender.com` kaydedildi; iletişim formunun yayınlama onayı tamamlandı.
- Gizlilik politikası URL'si `/privacy`; reklam var; resmi kurum/finans/sağlık özelliği yok beyanları kaydedildi. Reklam kimliği kullanımı ve analiz/reklam/sahtekarlık önleme amaçları kaydedildi. Release birleşik manifestinde `com.google.android.gms.permission.AD_ID` doğrulandı. Bu içerik değişiklikleri henüz incelemeye gönderilmedi.
- Veri güvenliği formu önizlemeye kadar dolduruldu ve **taslak olarak** kaydedildi: OAuth, HTTPS, `/delete-account`, yaklaşık konum, ad, kullanıcı kimliği, uygulama etkileşimleri, lig oynanış verileri, SDK kilitlenme/teşhis verileri, cihaz kimlikleri. İsteğe bağlı lig verileri ve reklam SDK'sı ayrımı yapıldı; kamuya açık lig sıralaması paylaşım olarak da beyan edildi. Referanslar: Google Mobile Ads Android veri açıklaması, Play Games SDK veri açıklaması ve Play Data Safety tanımları. Hedef kitle tamamlanmadığı için nihai Kaydet düğmesi devre dışı.
- **Kullanıcı girdisi gereken bağımlılık:** Play Console'un yeni Oturum açma bilgileri formu, incelemecinin kendi hesabını kullanmasını yasaklıyor ve tüm özelliklere erişen inceleme hesabı istiyor. Lig Google Play Oyunlar gerektirdiği için "erişim kısıtı yok" seçilmedi; erişim beyanı kaydedilmedi. Kullanıcıdan ayrılmış test hesabının Console'a kendisi girilmesi istendi; parola sohbetten istenmedi. Hedef kitle formu bu beyan tamamlanana kadar açılmıyor.
- **Onay bekleyen işlem:** IARC anketi şart kabulünü gerektiriyor. Tarayıcı işlem-anı onay kuralı nedeniyle kullanıcıdan IARC şartlarının kabulü için onay istendi; anket henüz doldurulmadı/kabul edilmedi.
- Görseller: `store-assets/play/ai/`, `store-assets/play/feature/`, `store-assets/play/screenshots-tr/`. Kaynak gerçek çekimler `store-assets/raw/android-tr/`; oynanış AI ile yeniden çizilmedi. Android sistem çubukları çıkarıldı ve oran korunarak sunum arka planına yerleştirildi.
- Görsel kontrolü yapıldı; boyut, saydamlık ve dosya sınırları denetlendi. Yeniden üretim: `node tools/compose-store-art.cjs <sharp-içeren-node_modules-yolu>`. `manifest.json` kaynak ve çıktı bilgilerini içerir.
- Bu değişiklikler oyun ikonu/oynanışı/derleme dosyalarını değiştirmedi; mevcut imzalı AAB korunuyor. Bloxboom kaynaklarına veya servislerine müdahale edilmedi.
- Sıradaki iş: inceleme hesabı → erişim beyanı → hedef kitle 13+ → Data Safety nihai kayıt; IARC onayı → derecelendirme. Sonra kapalı test. Sonraki önceliklere geçilmedi.

- Gerçek iPhone testi yok. Canlı ligde tamamlanmış bir lig oyununun gönderimi gerçek cihazda henüz denenmedi (sunucu testlerinde HTTP üzerinden doğrulandı).
- Sesler ölçümle düzeltildi; telefon hoparlörü ve kulaklıkla dinleme testi yapılmadı.
- Play Games Hizmetleri yayınlanmadı (mağaza girişi tamamlanınca yayınlanacak); o zamana kadar yalnız PGS test kullanıcıları lige girebilir. Game Center tarafı iOS derlemesi bekliyor.
- Render Free örneği 15 dk boşta kalınca uyur; ilk istek ~50 sn gecikebilir (uygulama zaman aşımı 70 sn).
- iOS imzası ve TestFlight: profil ve App Store Connect API gizlileri GitHub `apple-distribution` ortamında. Eksik: mevcut dağıtım sertifikasının .p12 dosyası (korumalı klasörde; `tools/set-ios-secrets.ps1` ile kullanıcı ekler).
- GitHub Actions: özel depo kotası dolduğu için kullanıcı onayıyla depo herkese açıldı (gizli bilgi taraması temiz).
- AdMob: uygulamalar mağazaya bağlanmadı; `app-ads.txt` lig sunucusunda hazır, Play web sitesi HTTPS sunucu köküne kaydedildi. Mağaza kaydı henüz incelemeye gönderilmedi. UMP mesajları AdMob'da yayınlanmalı.
- İçerik derecelendirmesi, erişim ve hedef kitle beyanları ile Data Safety nihai kaydı eksik; App Privacy henüz doldurulmadı (taslak: `store-assets/listing.md`). Play görselleri ve TR/EN metinleri hazır/yüklendi; Apple ekran görüntüleri ayrı hazırlanacak.
- Play üretim erişimi için 12 testçi × 14 gün kapalı test şartı.
- Uzun süreli insan oyun testleri ile denge değerlendirmesi.
