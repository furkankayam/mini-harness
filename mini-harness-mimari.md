# Mini Harness: Mimari ve Design Pattern Analizi

> **Özet:** Bu proje tek bir design pattern kullanmıyor. Ana yapı şunların birleşiminden oluşuyor:
> **ReAct Agent Loop + Decorator tabanlı Tool Guard + Policy tabanlı izin sistemi + Context Compaction + Sandbox sınırı**

---

## 1. Sınıflar ve Karşıladıkları Pattern'ler

| Sınıf                         | Karşıladığı pattern / mantık                           | Rolü                                                                                                                            |
| ----------------------------- | ------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------- |
| `AgentLoop.java`              | ReAct Agent Loop, Orchestrator, kısmen Template Method | Modelden karar alır, tool çağrısını çalıştırır, sonucu tekrar modele verir. `Thought → Action → Observation` döngüsünü yönetir. |
| `HarnessRunner.java`          | REPL, Command Dispatcher                               | Kullanıcıdan sürekli komut alır. `/reset` ve `/exit` gibi komutları yönlendirir.                                                |
| `WorkspaceTools.java`         | Tool/Command Pattern, Facade, Sandbox Boundary         | Modelin dış dünyaya eriştiği araçları sağlar: dosya listeleme, okuma, yazma ve komut çalıştırma.                                |
| `GuardedToolCallback.java`    | Decorator Pattern, Interceptor, Middleware             | Her tool'u sarar. Tool çalışmadan önce izin kontrolü, çalıştıktan sonra hata yakalama ve çıktı kırpma yapar.                    |
| `PermissionGate.java`         | Policy Object, Chain/Rule-based Guard, kısmen Strategy | Tool çağrısının serbest mi, onay gerektiren mi, yasak mı olduğuna karar verir.                                                  |
| `ContextManager.java`         | Context Compaction, Projection/View, kısmen Memento    | Ham geçmişi değiştirmeden modele gönderilecek daha küçük bir görünüm üretir. Eski tool çıktılarını temizler.                    |
| `HarnessProperties.java`      | Configuration Object, Parameter Object                 | Workspace, timeout, iterasyon ve onay ayarlarını tek bir immutable `record` içinde taşır.                                       |
| `ConsoleIO.java`              | Adapter, I/O Abstraction                               | `System.in` ve `System.out` erişimini tek sınıf arkasında toplar.                                                               |
| `MiniHarnessApplication.java` | Bootstrap / Composition Root                           | Spring uygulamasını başlatır ve dependency injection yapısını kurar.                                                            |
| `ToolCallingManager`          | Framework Adapter / Executor                           | Spring AI'ın tool çağrılarını gerçek callback'lere bağlar.                                                                      |
| `ToolCallbacks.from(tools)`   | Reflection-based Adapter                               | `@Tool` anotasyonlu Java metotlarını modelin anlayacağı tool tanımlarına dönüştürür.                                            |

---

## 2. Genel Akış

| Adım | Bileşen               | Ne olur?                                                             |
| ---- | --------------------- | -------------------------------------------------------------------- |
| 1    | `HarnessRunner`       | Kullanıcı komutu alınır; `/reset` veya `/exit` ise doğrudan işlenir. |
| 2    | `AgentLoop`           | Kullanıcı mesajı `history`'ye eklenir, ReAct döngüsü başlar.         |
| 3    | `ContextManager`      | Ham geçmişten modele gidecek küçültülmüş görünüm üretilir.           |
| 4    | LLM (Spring AI)       | Model ya nihai cevap ya da tool çağrısı üretir.                      |
| 5    | `ToolCallingManager`  | Tool çağrısı ilgili callback'e yönlendirilir.                        |
| 6    | `GuardedToolCallback` | Çağrı sarılır; önce `PermissionGate`'e sorulur.                      |
| 7    | `PermissionGate`      | Çağrı serbest, onaylı veya yasak olarak sınıflandırılır.             |
| 8    | `WorkspaceTools`      | İzin verildiyse asıl iş yapılır (dosya / komut).                     |
| 9    | `GuardedToolCallback` | Hata yakalanır, çıktı kırpılır, sonuç `history`'ye döner.            |
| 10   | `ConsoleIO`           | Nihai cevap kullanıcıya yazdırılır.                                  |

---

## 3. Ana Mimari Kararlar

### 3.1 ReAct Loop (En önemli pattern)

| Aşama           | `AgentLoop` içindeki karşılığı                                           |
| --------------- | ------------------------------------------------------------------------ |
| **Thought**     | Model güncel context ile çağrılır, bir sonraki adıma karar verir.        |
| **Action**      | Model tool çağrısı ürettiyse ilgili tool çalıştırılır.                   |
| **Observation** | Tool sonucu `history`'ye eklenir ve tekrar modele verilir.               |
| **Bitiş**       | Model tool çağrısı üretmezse cevap nihai kabul edilir.                   |
| **Limit**       | `HarnessProperties` içindeki maksimum iterasyon sonsuz döngüyü engeller. |

### 3.2 Decorator: Tool Guard

| Katman                            | Görev                                                                                                    |
| --------------------------------- | -------------------------------------------------------------------------------------------------------- |
| `GuardedToolCallback` (dış kabuk) | İzin kontrolü, hata yakalama, çıktı kırpma                                                               |
| `WorkspaceTools` (asıl nesne)     | Dosya listeleme, okuma, yazma, komut çalıştırma                                                          |
| **Sonuç**                         | Tool'un iş mantığı değişmeden güvenlik ve gözlemleme eklenir. Doğrudan bir **Decorator Pattern** örneği. |

### 3.3 Policy ve Guardrail

| Karar               | Anlamı                                   |
| ------------------- | ---------------------------------------- |
| **Serbest**         | Tool doğrudan çalışır.                   |
| **Onay gerektiren** | Çalışmadan önce kullanıcı onayı istenir. |
| **Yasak**           | Tool çalıştırılmaz.                      |

`PermissionGate` küçük bir **policy engine** gibi davranır. Gerçek projelerde yerini RBAC, capability sistemi, sandbox veya container güvenliği alabilir.

### 3.4 Sandbox Sınırı

| Konu        | Açıklama                                                                                                                 |
| ----------- | ------------------------------------------------------------------------------------------------------------------------ |
| Nerede?     | `WorkspaceTools.resolve()`                                                                                               |
| Ne yapar?   | Dosya yollarının workspace dizini içinde kalmasını sağlar.                                                               |
| Seviyesi    | Uygulama seviyesinde basit bir sandbox sınırı                                                                            |
| Sınırlaması | Gerçek bir işletim sistemi sandbox'ı değildir; deny-list ve path kontrolü öğretici amaçlıdır (README'de de belirtilmiş). |

### 3.5 State ve Checkpoint

| Konu        | Açıklama                                                                                  |
| ----------- | ----------------------------------------------------------------------------------------- |
| State       | `AgentLoop.history`, agent'ın oturum state'idir.                                          |
| Hata durumu | Yarım kalan konuşma geçmişten silinir, son tutarlı duruma dönülür.                        |
| Pattern     | Tam bir event sourcing değildir; checkpoint/rollback ve kısmen **Memento** mantığı taşır. |

### 3.6 Context Yönetimi

| State              | İçerik                                                              |
| ------------------ | ------------------------------------------------------------------- |
| **Ham geçmiş**     | Oturumun tam ve değişmeyen kaydı                                    |
| **Model görünümü** | Modele gönderilen küçültülmüş hali; eski tool çıktıları temizlenmiş |
| **Tasarım kararı** | Modelin context'i küçültülürken gerçek oturum geçmişi kaybedilmez.  |

---

## 4. Agentic Harness Kavramlarıyla Eşleşme

| Harness görevi                | Projedeki karşılığı                          |
| ----------------------------- | -------------------------------------------- |
| Döngü yönetimi ve limitler    | `AgentLoop` + `HarnessProperties`            |
| Araç seti                     | `WorkspaceTools` + `ToolCallbacks.from(...)` |
| Guardrail / Human-in-the-Loop | `PermissionGate` + `GuardedToolCallback`     |
| Sandbox                       | `WorkspaceTools.resolve()`                   |
| State / Checkpoint            | `AgentLoop.history` + hata anında rollback   |
| Context compaction            | `ContextManager`                             |
| Kullanıcı arayüzü             | `HarnessRunner` + `ConsoleIO`                |

---

## 5. Pattern Özeti

| Pattern                          | Nerede?                                                 |
| -------------------------------- | ------------------------------------------------------- |
| ReAct Loop / Orchestrator        | `AgentLoop`                                             |
| Decorator / Interceptor          | `GuardedToolCallback`                                   |
| Policy Object / Strategy         | `PermissionGate`                                        |
| Facade / Command                 | `WorkspaceTools`                                        |
| Projection / Memento             | `ContextManager`, `AgentLoop.history`                   |
| Adapter                          | `ConsoleIO`, `ToolCallingManager`, `ToolCallbacks.from` |
| REPL / Command Dispatcher        | `HarnessRunner`                                         |
| Configuration / Parameter Object | `HarnessProperties`                                     |
| Composition Root                 | `MiniHarnessApplication`                                |
