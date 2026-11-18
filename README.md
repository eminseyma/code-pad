# Code Pad

Yazılım notların için yapay zeka destekli Android not defteri. Kotlin + Jetpack Compose, tek Activity.

## Özellikler (v0.1)

- Çoklu defter, defter içinde notlar, arama
- Editör: metin + ``` ile kod blokları, sözdizimi renklendirme, tek dokunuşla kopyalama, önizleme, otomatik kayıt
- Editörde AI eylemleri: Açıkla, Özetle, Örnek ekle, Hata bul (sonucu nota ekleyebilirsin)
- Asistan sekmesi: notlarına dayanarak sohbet (en ilgili 5 notu bağlam olarak Gemini'ye gönderir)
- Ayarlar: Gemini API anahtarı (cihazda şifreli saklanır) ve model adı
- Veriler cihazda (Room/SQLite); internet sadece AI çağrılarında kullanılır

## Çalıştırma

1. Android Studio'yu (güncel sürüm) aç, **Open** ile bu klasörü seç. Gradle wrapper dosyalarını ve SDK'yı Android Studio kendisi kurar/önerir.
2. Gradle sync bittikten sonra telefon ya da emülatörde **Run**.
3. Uygulamada **Ayarlar** sekmesine git, https://aistudio.google.com/apikey adresinden aldığın Gemini API anahtarını yapıştır, kaydet.

Sürüm numaraları (AGP, Kotlin, Compose BOM, Room) bilerek biraz temkinli seçildi; Android Studio güncelleme önerirse kabul edebilirsin.

## Yapı

```
app/src/main/java/com/codepad/app/
  MainActivity.kt        gezinme (alt menü + ekranlar)
  AppViewModel.kt        durum, AI eylemleri, sohbet, not arama
  ai/GeminiClient.kt     Gemini REST çağrısı (ek kütüphane yok)
  data/Database.kt       Room: Notebook, Note, DAO
  data/SettingsStore.kt  şifreli API anahtarı deposu
  ui/                    tema, bileşenler, ekranlar
```

## Bilinen sınırlar / sıradaki adımlar

- Bu proje yazıldıktan sonra derlenip test edilmedi; ilk açılışta küçük derleme uyarıları/hataları çıkabilir.
- Model adı Ayarlar'dan değiştirilebilir (varsayılan `gemini-3.6-flash`); Google adları güncellerse oradan düzelt.
- Notlar arası arama basit anahtar kelime puanlaması kullanıyor; sonra gömme (embedding) ile geliştirilebilir.
- Tasarımdaki "Parçalar" sekmesi (tüm kod blokları) henüz yok.
- Bulut yedek/senkron yok; istenirse Firebase ya da kendi sunucun eklenebilir.
- AI kullanıldığında not içeriği Google'a gönderilir.

## Güncelleme (v0.2)

- **Silmeden güncelleme:** Her derleme sabit `app/codepad.keystore` ile imzalanır ve `versionCode` derleme numarasıyla artar. APK'yı eski sürümün üzerine kurmak yeterli.
- **Google Drive yedeği:** Ayarlar > "Google Drive'a bağlan". Yedek Drive'ın uygulamaya özel gizli klasöründe (`appDataFolder`) tutulur. Çalışması için Google Cloud'da Android OAuth istemcisi gerekir (paket adı `com.codepad.app` + aşağıdaki SHA-1).
- **Gemini anahtarı:** Cihazda şifreli saklanır; isteğe bağlı olarak Drive yedeğine de girer.

İmza SHA-1: `15:72:9D:C8:A7:33:04:75:4B:50:17:28:F5:D0:04:0B:4B:D7:E4:3C`

> Not: Bu depodaki anahtar yalnızca kişisel kurulum içindir. Depoyu özel tut.
