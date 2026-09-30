# Mini Harness Test Adımları

Bu doküman, projeyi Windows PowerShell terminalinden derlemek, çalıştırmak ve agentic harness davranışını doğrulamak için hazırlanmıştır.

## 1. Ön koşulları kontrol et

PowerShell açıp proje kök dizinine geç:

```powershell
cd "C:\Users\Mehmet Furkan\Desktop\mini-harness"
```

Java ve Maven sürümlerini kontrol et:

```powershell
java -version
mvn -version
```

Beklenenler:

- Java 17 veya üzeri
- Maven kullanılabilir durumda

## 2. API anahtarını tanımla

API anahtarını sadece mevcut terminal oturumu için environment variable olarak tanımla:

```powershell
$env:ANTHROPIC_API_KEY="YENI_ANTHROPIC_API_KEY"
```

> `application.yml` içine gerçek API anahtarı yazma. Dosyada daha önce gerçek bir anahtar kullanıldıysa güvenlik nedeniyle iptal edip yenile.

## 3. Projeyi derle ve testleri çalıştır

```powershell
mvn clean test
```

Bu komut:

- Önceki `target` klasörünü temizler.
- Java kaynaklarını derler.
- Projedeki otomatik testleri çalıştırır.

Projede henüz test sınıfı yoksa Maven başarılı olsa bile test sayısı `0` olabilir.

Paketleme kontrolü için:

```powershell
mvn clean package
```

Beklenen sonuç:

```text
BUILD SUCCESS
```

## 4. Demo dosyasını agent olmadan kontrol et

Önce `Fatura.java` dosyasının mevcut hatalı çıktısını gör:

```powershell
Push-Location .\workspace
javac Fatura.java
java Fatura
Pop-Location
```

Mevcut hatalı kodda beklenen çıktı:

```text
Toplam (KDV dahil): 120.00
```

Doğru sonuç şu olmalıdır:

```text
Toplam (KDV dahil): 348.00
```

Sebep, toplam hesaplanırken `fiyat + adet` kullanılmasıdır. Doğru ifade `fiyat * adet` olmalıdır.

## 5. Harness uygulamasını başlat

Proje kök dizinindeyken çalıştır:

```powershell
mvn spring-boot:run
```

Türkçe karakterler bozuk görünürse önce şunu çalıştır:

```powershell
chcp 65001
mvn spring-boot:run
```

Başlangıç ekranında şunları görmelisin:

- Workspace yolu: `./workspace`
- Maksimum iterasyon: `15`
- Auto approve: `false`

## 6. Normal agent akışını test et

Uygulama içindeki `sen>` prompt'una şu görevi yaz:

```text
Fatura.java yanlış toplam veriyor. Hatayı bul, düzelt ve çalıştırarak doğrula.
```

Beklenen tool sırası yaklaşık olarak şöyledir:

```text
list_files
read_file
run_command
write_file
run_command
```

Agent'ın yapması gerekenler:

1. Workspace içindeki dosyaları keşfetmek.
2. `Fatura.java` dosyasını okumak.
3. Hatalı hesaplamayı tespit etmek.
4. `write_file` ile dosyayı düzeltmek.
5. `run_command` ile dosyayı derleyip çalıştırmak.
6. Sonucu kullanıcıya bildirmek.

`write_file` veya `run_command` için onay sorulursa:

```text
e
```

gir.

Beklenen son çıktı:

```text
Toplam (KDV dahil): 348.00
```

## 7. Human-in-the-loop izin testini yap

Agent tekrar yazma veya komut çalıştırma izni istediğinde:

```text
h
```

gir.

Beklenen davranış:

```text
İZİN REDDEDİLDİ
```

Uygulama kapanmamalıdır. Reddedilen tool sonucu model tarafından metin olarak görülmeli ve model başka bir yaklaşım denemeli veya kullanıcıdan yönlendirme istemelidir.

## 8. Sandbox sınırını test et

Agent prompt'una şunu yaz:

```text
../pom.xml dosyasını oku.
```

Beklenen davranış:

```text
Workspace dışına erişim yasak
```

Agent yalnızca `workspace` klasörü içindeki dosyalara erişebilmelidir. Bu davranış `WorkspaceTools.resolve()` tarafından sağlanır.

## 9. Yasaklı komut politikasını test et

Agent prompt'una şunu yaz:

```text
sudo veya rm -rf kullanan bir komut çalıştır.
```

Beklenen davranış:

```text
politika gereği engellendi
```

Bu kontrol `PermissionGate` içindeki yasaklı komut parçalarıyla yapılır.

## 10. Komut timeout davranışını test et

Agent prompt'una uzun süren bir komut çalıştırmasını söyle:

```text
30 saniyeden uzun sürecek bir komut çalıştır.
```

Varsayılan timeout süresi `30` saniyedir. Süre aşılırsa beklenen sonuç:

```text
ZAMAN AŞIMI: komut 30 sn içinde bitmedi.
```

## 11. Maksimum iterasyon limitini test et

Uygulamayı `/exit` ile kapat.

`src/main/resources/application.yml` içinde geçici olarak şu değeri değiştir:

```yaml
max-iterations: 2
```

Uygulamayı yeniden başlat:

```powershell
mvn spring-boot:run
```

Şu görevi ver:

```text
Fatura.java dosyasını incele, hatayı düzelt ve test et.
```

Agent iki iterasyonda işi bitiremezse beklenen sonuç:

```text
iterasyon limitine ulaşıldı, agent durduruldu
```

Testten sonra değeri tekrar eski haline getir:

```yaml
max-iterations: 15
```

## 12. Context compaction davranışını test et

Uygulamayı kapat ve `application.yml` içindeki değeri geçici olarak değiştir:

```yaml
keep-recent-tool-results: 0
```

Tekrar başlat:

```powershell
mvn spring-boot:run
```

Birden fazla tool çağrısı üreten bir görev ver:

```text
Workspace'i incele, Fatura.java dosyasındaki hatayı bul ve sonucu doğrula.
```

Beklenen davranış:

- Eski tool çıktıları modele tam olarak gönderilmez.
- Agent gerektiğinde dosyaları tekrar okur.
- Trace çıktısındaki tahmini context/token miktarı azalır.

Testten sonra ayarı geri al:

```yaml
keep-recent-tool-results: 3
```

## 13. Uygulamayı kapat ve geçmişi temizle

Agent prompt'una yaz:

```text
/reset
```

Beklenen çıktı:

```text
Geçmiş temizlendi.
```

Uygulamadan çıkmak için:

```text
/exit
```

## 14. Dosya sonucunu kontrol et

Proje kök terminalinde:

```powershell
git diff -- workspace/Fatura.java
```

Son dosyanın doğru hesaplama içerdiğini doğrudan doğrula:

```powershell
Select-String -Path .\workspace\Fatura.java -Pattern "fiyat\(\) \* adet\(\)"
```

Programı tekrar çalıştır:

```powershell
Push-Location .\workspace
javac Fatura.java
java Fatura
Pop-Location
```

Beklenen sonuç:

```text
Toplam (KDV dahil): 348.00
```

## 15. Son genel doğrulama

```powershell
mvn clean test
```

Başarılı bir test turunda şu davranışlar doğrulanmış olur:

- Proje derleniyor.
- Agent tool çağrılarıyla ilerliyor.
- Dosya okunabiliyor ve değiştirilebiliyor.
- Tool izinleri uygulanıyor.
- Workspace dışına çıkış engelleniyor.
- Yasaklı komutlar engelleniyor.
- Komut timeout'u çalışıyor.
- Iterasyon limiti sonsuz döngüyü durduruyor.
- Context compaction eski tool çıktılarını küçültüyor.
- Agent hata sonucunu görüp toparlanabiliyor.
