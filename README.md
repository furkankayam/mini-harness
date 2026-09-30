# mini-harness

Agentic harness'ı anlatmak için yazılmış, ~400 satırlık minimal bir Spring AI agent'ı.
Amaç: **"Agent = Model + Harness"** fikrini kodda göstermek.

## Çalıştırma

Gereksinimler: Java 17+, Maven, bir Anthropic API anahtarı.

```bash
export ANTHROPIC_API_KEY=sk-ant-...        # Windows: set ANTHROPIC_API_KEY=...
mvn spring-boot:run
```

Windows PowerShell'de terminal karakterleri bozuk görünürse uygulamayı UTF-8 kod sayfasıyla başlatın:

```powershell
chcp 65001
mvn spring-boot:run
```

Projedeki `.mvn/jvm.config` dosyası Maven ve Spring Boot JVM'i için UTF-8 çıktı ayarlarını zaten uygular.

Stack: Spring Boot 3.5 + Spring AI 1.1.8 (`spring-ai-starter-model-anthropic`).
Model `application.yml` içinden değiştirilebilir.

## Dosya haritası: hangi sınıf harness'ın hangi parçası?

| Harness bileşeni           | Sınıf                       | Ne gösteriyor                                                                                             |
| -------------------------- | --------------------------- | --------------------------------------------------------------------------------------------------------- |
| Agent döngüsü              | `AgentLoop`                 | `internalToolExecutionEnabled(false)` ile Spring AI'ın gizli döngüsünü kapatıp döngüyü kendimiz sürüyoruz |
| Tool'lar (agent'ın elleri) | `tools/WorkspaceTools`      | `@Tool` açıklamaları = modelin okuduğu arayüz ("tool design is agent UX")                                 |
| Sandbox                    | `WorkspaceTools.resolve()`  | `../` ile workspace dışına çıkma engeli, komut timeout'u                                                  |
| İzinler                    | `guard/PermissionGate`      | Okuma serbest; yazma/komut onaylı; tehlikeli komutlar yasak                                               |
| Hook'lar                   | `guard/GuardedToolCallback` | PreToolUse (izin) + PostToolUse (hata→metin, çıktı kırpma, trace)                                         |
| Context engineering        | `context/ContextManager`    | Eski tool çıktılarını temizleme ("tool result clearing")                                                  |
| Durma koşulları            | `AgentLoop.run()`           | Tool çağrısı yok → bitti; max iterasyon → harness durdurur                                                |
| Gözlemlenebilirlik         | `AgentLoop.trace()`         | Her iterasyonda token, tool sayısı, modelin "düşüncesi"                                                   |

## Demo senaryosu (sunum için ~7 dk)

`workspace/Fatura.java` içinde bilerek bırakılmış bir hata var (beklenen: 348.00, gerçek: 120.00).

1. **Normal akış:**
   `sen> Fatura.java yanlış toplam veriyor. Hatayı bul, düzelt ve çalıştırarak doğrula.`
   Döngüyü izle: list_files → read_file → run_command (hatayı görür) → write_file (onay ister) → run_command (doğrular).

2. **İzin reddi → error recovery:** write_file onayına `h` de. Model reddi metin olarak görür ve başka bir yol dener ya da sana sorar.

3. **Sandbox:** `sen> ../pom.xml dosyasını oku` → `SecurityException` modele metin olarak döner.

4. **Harness ayarıyla oynamak:** `application.yml` içinde
   - `max-iterations: 2` → agent işi bitiremeden harness durdurur,
   - `keep-recent-tool-results: 0` → model eski çıktıları göremez, tool'ları tekrar çağırmak zorunda kalır (trace'te token düşüşünü göster).

   Aynı model, farklı harness, farklı davranış. Sunumun ana mesajı bu.

5. Bitince `/reset` yap ve `workspace/Fatura.java`'daki hatayı geri koy (`k.fiyat() * k.adet()` → `k.fiyat() + k.adet()`).

## Bilerek eksik bırakılanlar (sunumda "gerçek harness'larda ne var?" slaytı)

- Planlama / todo listesi, subagent'lar
- Özetleme ile compaction (burada sadece temizleme var)
- Kalıcı hafıza (CLAUDE.md benzeri dosya)
- Gerçek sandbox (container / seccomp); buradaki deny-list öğretici amaçlı ve kolay atlatılır
- Streaming, paralel tool çağrısı, retry/backoff, eval

## Not: Spring AI 2.0

Spring AI 2.0 (Spring Boot 4) tool döngüsünü `ToolCallingAdvisor` ile advisor zincirine taşıdı.
Orada manuel döngü için `AdvisorParams.toolCallingAdvisorAutoRegister(false)` kullanılıyor.
Bu proje, iş stack'ine (Boot 3) uyduğu için 1.1.x ile yazıldı.
