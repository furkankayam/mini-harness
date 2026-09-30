# LLM, Agent, Harness ve Agentic Harness

> **Formül:**
> `LLM (Model) + Çalışma Ortamı (Araçlar, Hafıza, Runtime) = Agent`
> `Harness = Agent'ı çevreleyen, kontrol eden, izleyen ve yöneten dış kabuk (iskelet)`

---

## 1. Temel Kavramlar

### 1.1 LLM (Large Language Model)

İstatistiksel olarak metin üreten, bağlamı anlayan ve muhakeme yapabilen temel yapay zekâ modelidir.

- **Yapabildikleri:** Metin üretmek, soruları yanıtlamak, plan yapmak, kod yazmak, bir sonraki adımın ne olması gerektiğine karar vermek.
- **Yapamadıkları:** Kendi başına kalıcı bir hafızası yoktur. Dosya kaydedemez, komut çalıştıramaz, dış dünyayı değiştiremez.
- **Özetle:** Kendisine verilen prompt'u işleyip çıktı üreten, pasif bir "düşünme motoru"dur.

### 1.2 Tools (Araçlar) ve Ortam

Modelin dış dünyayla veri alışverişi yapabilmesi için tanımlanan harici fonksiyonlar ve arayüzlerdir.

- **Örnekler:** Web araması, veritabanı sorgusu, dosya okuma/yazma, terminal komutu çalıştırma, REST API çağrıları.
- **Nasıl çalışır:** Model bu araçların adını, açıklamasını ve parametre şemasını bilir. Gerek duyduğunda uygun parametrelerle bir araç çağrısı üretir; çağrıyı model değil, onu çalıştıran sistem yürütür ve sonucu modele geri verir.
- **Ortam:** Araçların üzerinde çalıştığı zemindir: terminal, dosya sistemi, Git reposu, veritabanı, API'ler.

### 1.3 Agent (Ajan)

LLM'i araç setiyle, hafızayla ve döngüsel bir karar mantığıyla birleştiren otonom sistemdir.

- **Döngü mantığı:** Genellikle **ReAct** (Reason + Act) veya **Planning** yaklaşımıyla çalışır:
  1. **Thought:** Hedefe ulaşmak için ne yapmalıyım?
  2. **Action:** Uygun aracı çağır.
  3. **Observation:** Aracın sonucunu değerlendir.
  4. Hedefe ulaşılana kadar tekrarla.
- **Fark:** LLM tek bir cevap üretir; agent ise bir hedefe ulaşmak için birden çok adımı kendisi planlar ve yürütür.

### 1.4 Harness (Genel Anlamıyla)

Yazılım ve test mühendisliğinde karmaşık bir bileşeni (motor, modül, test edilecek kod) içine yerleştirip çalıştırmak için kullanılan dış iskelettir.

- Bileşeni ana sistemden **izole eder**.
- **Mock girdiler** verir, **çıktıları ölçer**.
- Bir hata olduğunda ana sistemin çökmesini engeller.
- En bilinen örneği **test harness** kavramıdır.

### 1.5 Agentic Harness

Agent'ın çalışma ortamını, güvenliğini ve operasyonel sınırlarını yöneten özel altyapı katmanıdır. Klasik harness mantığının yapay zekâ ajanlarına uyarlanmış halidir.

---

## 2. Agentic Harness'ın Görevleri

| Görev                       | Ne yapar?                                                   | Neden gerekli?                                             |
| --------------------------- | ----------------------------------------------------------- | ---------------------------------------------------------- |
| **Sınırlar (Limits)**       | Adım sayısı, token bütçesi, süre (timeout) limitleri koyar. | Sonsuz döngüyü ve kontrolsüz API faturasını önler.         |
| **Güvenlik (Guardrails)**   | Tehlikeli araç çağrılarını engeller veya filtreler.         | Yetkisiz ya da zararlı komutların çalışmasını durdurur.    |
| **Sandbox / İzolasyon**     | Ajanı izole bir ortamda (konteyner, mikro-VM) çalıştırır.   | Hata olursa sadece sandbox etkilenir, ana sistem korunur.  |
| **Human-in-the-Loop**       | Kritik adımlarda akışı durdurup insan onayı ister.          | Geri alınamaz işlemler kontrolsüz yapılmaz.                |
| **Observability / Tracing** | Her adımı, araç çağrısını, token ve maliyeti loglar.        | Ajanın ne yaptığı görünür olur, hatalar teşhis edilebilir. |
| **State / Checkpoint**      | Ajanın durumunu kaydeder, gerekirse geri alır.              | Hata anında kaldığı yerden devam edilebilir.               |
| **Context Yönetimi**        | Bağlamı hazırlar, şişince özetler (compaction).             | Model doğru bilgiyle ve sınırlı pencere içinde çalışır.    |

---

## 3. Tarihçe: Kavram Nasıl Oluştu?

### 3.1 Köken: Test Harness (1970'ler – 2000'ler)

Yazılım ve donanım mühendisliğinde karmaşık bir modülü test etmek için onu ana sistemden izole eden, mock girdi veren, çıktıyı ölçen ve hata durumunda sistemi çökertmeyen "çevreleyici iskelet"e **harness** denirdi.

### 3.2 Kıvılcım: AutoGPT, BabyAGI ve Kaos Dönemi (Mart – Nisan 2023)

ChatGPT API'sinin yaygınlaşmasıyla Toran Bruce Richards (**AutoGPT**) ve Yohei Nakajima (**BabyAGI**) gibi geliştiriciler, LLM'leri kendi kendine prompt üreten sonsuz döngülerde çalıştırdı.

- **Sorunlar:** Sonsuz döngüler, tek görev için yüzlerce dolarlık API faturası, kendini kilitleyen ajanlar, kullanıcının yerel dosyalarını silen ajanlar.
- **Ders:** "Model tek başına yetersiz; etrafına çok katı bir denetim ve çalışma iskeleti örmeliyiz."

### 3.3 Kavramın Oturması: SWE-bench (Ekim 2023)

Princeton Üniversitesi araştırmacıları, LLM'lerin gerçek GitHub issue'larını çözüp çözemeyeceğini ölçmek için **SWE-bench**'i yayınladı.

- Modeli doğrudan GitHub'a salamazlardı; bunun yerine bir **Evaluation / Execution Harness** kurdular:
  - İzole Docker konteyneri
  - Kod çalıştırma limitleri
  - Git commit kontrolü
  - Otomatik birim testleri
- **Ders:** Ajanın başarısı sadece modelin zekâsına değil, içinde çalıştığı harness'ın sağlamlığına da bağlıdır.

### 3.4 Kurumsallaşma ve Ekosistem (2024 – Günümüz)

- **LangChain ekibi → LangGraph:** Lineer zincirlerin ajanlar için yetersiz olduğunu görüp döngü, state yönetimi ve human-in-the-loop destekleyen LangGraph'ı çıkardı.
- **Anthropic → Computer Use:** 2024 sonunda modelin ekranı ve terminali kontrol ettiği yeteneği duyururken etrafındaki sandbox ve güvenlik iskeletini harness disipliniyle açıkladı.
- **Observability şirketleri:** Arize AI (Phoenix), Braintrust, LangSmith gibi platformlar ajan izleme ve telemetriyi standartlaştırdı.

> **Sonuç:** Kavramı tek bir kişi icat etmedi. Kontrolsüz ajanların yarattığı güvenlik ve maliyet krizlerinden sonra akademik benchmark'lar (SWE-bench) ve modern framework'ler (LangGraph vb.), klasik test harness mantığını yapay zekâ ajanlarına uyarlayarak **Agentic Harness** standardını doğurdu.

---

## 4. Harness Türleri ve Örnek Araçlar

### 4.1 State & Yaşam Döngüsü Harness'ları

Sonsuz döngüyü önler, akış adımlarını denetler, gerektiğinde duraklatıp onay alır.

| Araç                    | Öne çıkan özellik                                                                                                     |
| ----------------------- | --------------------------------------------------------------------------------------------------------------------- |
| **LangGraph**           | Ajanı döngüsel bir çizge olarak modeller; checkpointing, time-travel (adım geri alma), human-in-the-loop.             |
| **AutoGen (Microsoft)** | Birden fazla ajanın birbirini denetlediği, konuşma adımlarına tavan limit konan çoklu ajan ortamı.                    |
| **CrewAI**              | Ajanlara rol, görev ve süreç kısıtları (sıralı / hiyerarşik) tanımlayarak görev sınırlarının dışına çıkmayı engeller. |

### 4.2 Sandbox & İzolasyon Harness'ları

Kod çalıştırma, dosya değiştirme ve komut yürütmeyi fiziksel sunucudan izole eder.

| Araç                        | Öne çıkan özellik                                                                                  |
| --------------------------- | -------------------------------------------------------------------------------------------------- |
| **E2B**                     | AI ajanları için izole bulut mikro-VM'leri; sistem çökerse sadece sandbox ölür.                    |
| **Modal / Fly.io / Docker** | Saniyeler içinde açılıp kapanan geçici konteynerler; ana sisteme ve veritabanına erişimi engeller. |
| **Daytona / Gitpod**        | Kod yazan ve test koşan ajanlara hazır, sınırlandırılmış geliştirme ortamları.                     |

### 4.3 Observability & Telemetri Harness'ları

Token kullanımını, araç çağrılarını ve döngü hatalarını izler.

| Araç                              | Öne çıkan özellik                                                                                         |
| --------------------------------- | --------------------------------------------------------------------------------------------------------- |
| **LangSmith**                     | Her `Thought → Action → Observation` adımını ağaç yapısında gösterir; maliyet ve gecikme uyarıları verir. |
| **Arize Phoenix / OpenInference** | Açık kaynak; OpenTelemetry standartlarında izleme, döngüde sıkışma tespiti.                               |
| **Braintrust**                    | Log toplamanın yanında üretimde güvenlik kurallarını ve performans skorlarını anlık denetler.             |

### 4.4 Değerlendirme & Test Harness'ları

Ajanların başarısını kontrollü ortamda ölçer.

| Araç                            | Öne çıkan özellik                                                                                           |
| ------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| **SWE-bench Execution Harness** | Gerçek GitHub hatasını verir, Docker'da repoyu kurar, ajanın commit'ini alıp testleri koşar ve skor üretir. |
| **InterCode / WebArena**        | Ajanı web sitesi veya SQL ortamında izole simülasyonla test eder.                                           |

### 4.5 Guardrail & Güvenlik Harness'ları

Araç çağrılarını çalıştırılmadan önce denetler.

| Araç                         | Öne çıkan özellik                                                                            |
| ---------------------------- | -------------------------------------------------------------------------------------------- |
| **NeMo Guardrails (NVIDIA)** | Konu dışına çıkmayı, yasaklı komutları ve yetkisiz araç parametrelerini kurallarla engeller. |
| **Guardrails AI**            | JSON çıktısını ve araç girdi şemalarını doğrular; uymayan veya riskli çağrıları reddeder.    |

---

## 5. Somut Örnek: Claude Code / OpenCode

Claude Code, OpenCode, Cursor Agent, Aider ve Devin gibi araçlar birer **Agentic Harness** örneğidir; hatta terminolojinin en somut kullanım alanıdır. Çoğu kişi bu araçları "bir yapay zekâ" sanır; oysa arkadaki model tek başına sadece bir API'dir. Bu araçlar, o modelin etrafına giydirilmiş harness'ın kendisidir.

### Claude Code neden bir harness?

1. **Bağlam ve ortam hazırlığı (Context Injection):** Repoyu tarar, `.gitignore` kurallarına uyar, dosya ağacını çıkarıp modele uygun formatta verir.
2. **Araç seti ve yürütme motoru (Tool Execution):** Model kendi başına dosya kaydedemez veya `npm test` çalıştıramaz. Claude Code modele `grep`, `file_edit`, `bash` gibi araçlar tanımlar ve modelden gelen komutları işletim sisteminde güvenle yürütür.
3. **Güvenlik ve onay freni (Human-in-the-Loop):** Zararsız dosya okumalarında sessizce devam eder; `rm -rf`, kritik bir `git push` gibi riskli işlemlerde durup "Bunu çalıştırayım mı? (y/n)" diye sorar.
4. **Döngü yönetimi (Loop Control & Budget):** Bug düzeltirken testi çalıştırır, hata alırsa tekrar dener. Sonsuz döngüye girmemesi için adım ve token sınırlarını, context penceresi dolunca özetleme (compaction) işlemini yönetir.

### Bileşen ayrımı

| Bileşen             | Karşılığı                                                                                                                                    |
| ------------------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| **Model (LLM)**     | Anthropic'in API arkasındaki Claude modeli                                                                                                   |
| **Araçlar & Ortam** | Yerel terminal, Git reposu, dosya sistemi, derleyici                                                                                         |
| **Agentic Harness** | Claude Code / OpenCode CLI uygulamasının kendisi: modeli terminale bağlayan, araçları veren, onay alan, log tutan ve döngüyü yöneten iskelet |

> Claude Code olmasaydı, model yalnızca "şu dosyayı aç, şurayı sil, terminale şunu yaz" diye tarif eden pasif bir sohbet robotu olurdu. Onu yetkileri sınırlandırılmış otonom bir mühendise dönüştüren kabuk, harness'tır.

---

## 6. Karşılaştırmalı Özet

|                      | LLM                   | Agent                               | Agentic Harness                        |
| -------------------- | --------------------- | ----------------------------------- | -------------------------------------- |
| **Rolü**             | Düşünür, karar verir  | Planlar, araç çağırır, hedefe koşar | Sınırlar, izler, korur, yönetir        |
| **Hafıza**           | Yok (sadece bağlam)   | Var                                 | Durumu kaydeder (checkpoint)           |
| **Dış dünyaya etki** | Yok                   | Araçlar üzerinden var               | Bu etkiyi denetler ve onaylar          |
| **Örnek**            | Claude, GPT modelleri | ReAct döngüsüyle çalışan bir ajan   | Claude Code, LangGraph, E2B, LangSmith |

---

## 7. Benzetme: Köpek ve Akıllı Tasma

- **LLM → Köpeğin beyni:** Koku alır, düşünür, karar verir.
- **Araçlar & Ortam → Ayaklar ve sokak:** Beynin dünyayla temas ettiği uzuvlar ve zemin.
- **Agent → Tasmasız köpek:** Beyin ve ayaklar birleşmiş, hedefine kendi kararlarıyla koşuyor.
- **Agentic Harness → Akıllı tasma:** Sanal çit (güvenlik sınırları), haritada canlı takip (observability) ve tehlikeli yola atlamasını önleyen acil fren (kontrol mekanizması).

> **Tek cümlede:** LLM düşünür, araçlar dokunur, agent karar verip koşar; agentic harness ise bu koşuyu sınırlar, izler ve gerektiğinde frene basar.

---

