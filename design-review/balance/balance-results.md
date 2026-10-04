# Oyun dengesi ölçümü

## Sonuç ve karar

Eski dağılımda rastgele yerleştiren stratejinin 32 koşusunun tamamı 100 bırakışı,
31'i 200 bırakışı geçti; medyan 400 bırakışlık deney sınırına ulaştı. Kullanıcının
oyunu kolay bulması bu ölçümle desteklendi. **EarlyPressure** dağılımı seçildi:
rastgele oyunun medyanı 90, yalnızca bir hamle hesaplayan stratejinin medyanı
300,5 bırakış oldu. Rastgele oyuna göre yaklaşık 3,3 katlık fark, planlamanın
değerini artırırken temel üçlü birleşme kuralını koruyor.

Birincil veri: [balance-results.csv](balance-results.csv).
Ölçüm kodu: `shared/src/commonTest/kotlin/com/bloxtrix/hexdrop/BalanceAuditTest.kt`.

## Üretime alınan dağılım

Her torba 12 taştır. Torba açılırken tamamı karıştırılır; tahtanın durumuna göre
taş seçilmez. Yeni aşama eldeki torbayı veya sıradaki üç taşı değiştirmez.
Dolayısıyla eşikte hemen yeni değer gelmesi zorunlu değildir. Küçük taşlar hiçbir
aşamada kaldırılmaz; önceden yerleştirilmiş küçük taşlar eşleşebilir kalır.

| Aşama | Tamamlanan bırakış | Seviye | 2 | 4 | 8 | 16 | 32 | 64 |
|---|---|---|---:|---:|---:|---:|---:|---:|
| 1 | 0–17 | 1 | 4 | 3 | 3 | 2 | 0 | 0 |
| 2 | 18–53 | 2–3 | 3 | 3 | 2 | 2 | 2 | 0 |
| 3 | 54+ | 4+ | 2 | 2 | 2 | 2 | 2 | 2 |

Üçlü birleşme, altı komşuluk, 5×7 tahta, zincir puanı, Devir'in üç enerji bedeli
ve her birleşme dalgasının bir enerji vermesi değişmedi. Dingin süre sınırı içermez.

## Yöntem

- Üç dağılım × üç strateji × aynı 32 tohum (`0..31`): toplam **288 koşu**.
- En fazla **400 bırakış veya 500 eylem**. Devir de eylem sayılır.
- Gerçek motorun `dropPiece`, `resolveTurn`, `cycleColumn` işlevleri çalıştırıldı.
- Başlangıç taşı, üç taşlık önizleme, kuyruktan tüketim ve yeni torbanın seviye
  seçimi ViewModel ile aynı sırada modellendi. Torba ve hamle rastgelelikleri ayrıdır.
- Rastgele strateji yalnızca boş sütunlardan seçer. Sırayla sütun stratejisi
  0→1→2→3→4 döner, dolu sütunu atlar. İkisi de Devir'i yalnızca tahta doluyken kullanır.
- Hesaplayan strateji tek hamle ilerisini değerlendirir: az doluluk, düşük sütunlar
  ve eş değerde komşuluk tercih edilir. Devir'in gerçek enerji bedeli değerlendirmeye
  katılır; boş yer varsa sırf bırakışı ertelemek için çevrim yapamaz. Üç taşlık
  önizlemeyi daha ileri arama için kullanmaz.
- Her eylemden sonra yerçekimi düzeni, birleşmelerin tamamlanması, enerji sınırları
  ve oyun sonu koşulu kontrol edilir.
- `Baseline` eski torbanın karıştırma öncesi sırası dahil dondurulmuş kopyasıdır.
  `EarlyPressure` artık doğrudan üretimdeki `StoneBag` sınıfını kullanır.

## Karşılaştırma

| Dağılım | Strateji | P10 / Medyan / P90 bırakış | 100'e ulaşma | 200'e ulaşma | İlk 40 hamlede ortalama doluluk |
|---|---|---|---:|---:|---:|
| Eski | Rastgele | 285 / 400 / 400 | %100 | %96,8 | %27,3 |
| Eski | Sırayla sütun | 228 / 400 / 400 | %100 | %93,7 | %24,1 |
| Eski | Tek hamle hesaplayan | 400 / 400 / 400 | %100 | %100 | %15,5 |
| Yumuşak kademeli | Rastgele | 102 / 146 / 168 | %93,7 | %3,1 | %27,9 |
| Yumuşak kademeli | Sırayla sütun | 83 / 128,5 / 171 | %78,1 | %0 | %26,8 |
| Yumuşak kademeli | Tek hamle hesaplayan | 329 / 400 / 400 | %100 | %100 | %16,2 |
| Seçilen erken baskı | Rastgele | 65 / 90 / 122 | %34,3 | %0 | %37,1 |
| Seçilen erken baskı | Sırayla sütun | 69 / 81 / 110 | %21,8 | %0 | %33,2 |
| Seçilen erken baskı | Tek hamle hesaplayan | 174 / 300,5 / 400 | %100 | %78,1 | %19,3 |

Yumuşak kademeli adayın torbaları sırasıyla `[5,4,3]`, `[4,3,3,2]`,
`[3,3,2,2,2]`, `[3,2,2,2,2,1]`; başlangıç seviyeleri 1, 2, 4 ve 7 idi.

## Yorumlama sınırları ve yeniden çalıştırma

400 ve eylem sınırına ulaşan sonuçlar gerçek oyun ömrünün alt sınırıdır; bu
koşular oyun kaybedildiği için bitmemiştir. `cap_pct` her iki deney sınırını kapsar.
Yüzdeler tek ondalığa kırpılmıştır. İlk 40 hamlenin doluluğu her koşunun mevcut
ilk 40 bırakışından hesaplanır. P10 ve P90 sıralanmış 32 koşunun sırasıyla 4. ve
29. değeridir. Medyan 16. ve 17. değerin ortalamasıdır.

Bunlar insan oynanabilirlik testleri değildir; Akış modunun süre baskısı da
modellenmez. Yeni dağılım büyük değerleri daha erken verdiğinden, sürümler arasında
ham puan tek başına zorluk karşılaştırması değildir. Örneğin hesaplayan stratejinin
medyan puanı eskide 13.452, seçilen dağılımda 28.710'dur.

Kaydedilen CSV ilk aday ölçümünün çıktısıdır. Aynı seçilen dağılım üretime alındı;
sonraki regresyon çalıştırmaları `EarlyPressure` satırını gerçek üretim torbasından
yeniden üretir. Geniş regresyon sınırları rastgele strateji medyanının 50–160 arasında
olmasını ve hesaplayan stratejinin en az iki katına ulaşmasını denetler.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:GRADLE_USER_HOME = 'C:\Users\ahmet\.gradle'
.\gradlew.bat :shared:testDebugUnitTest --tests '*BalanceAuditTest' --console=plain
```

Yeni CSV satırları JUnit XML çıktısının `system-out` bölümünde `BALANCE_CSV` ön ekiyle
bulunur: `shared/build/test-results/testDebugUnitTest/TEST-com.bloxtrix.hexdrop.BalanceAuditTest.xml`.
