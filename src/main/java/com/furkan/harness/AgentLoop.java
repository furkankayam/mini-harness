package com.furkan.harness;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.furkan.harness.context.ContextManager;
import com.furkan.harness.guard.GuardedToolCallback;
import com.furkan.harness.guard.PermissionGate;
import com.furkan.harness.tools.WorkspaceTools;

import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

/**
 * HARNESS'IN KALBİ: agent döngüsü.
 *
 *   while (iterasyon < limit):
 *       görünüm = context.compact(geçmiş)        // context engineering
 *       yanıt   = model(görünüm)                  // karar veren: model
 *       tool çağrısı yoksa -> bitti               // durma koşulu
 *       sonuç   = tool'ları çalıştır (hook'larla) // eylemi yapan: harness
 *       geçmiş += yanıt + sonuç
 *
 * Spring AI normalde bu döngüyü kendi içinde gizli çalıştırır.
 * internalToolExecutionEnabled(false) ile döngüyü kendimiz sürüyoruz — sunumun ana fikri bu.
 */
@Component
public class AgentLoop {

    private static final String SYSTEM_PROMPT = """
            Sen bir yazılım geliştirme agent'ısın. Kullanıcının workspace klasöründe çalışırsın.

            Çalışma kuralların:
            1. Önce keşfet: list_files ve read_file ile ne olduğunu gör.
            2. Sonra planla: ne yapacağını 1-3 cümleyle söyle.
            3. Sonra uygula: write_file ile değiştir, run_command ile DOĞRULA (derle/çalıştır/test et).
            4. Bir tool hata dönerse hatayı oku, düzelt ve tekrar dene.
            5. İş bitince ne yaptığını ve nasıl doğruladığını kısaca özetle.
            Türkçe yanıt ver.""";

    private final ChatModel chatModel;
    private final ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();
    private final ContextManager contextManager;
    private final HarnessProperties props;
    private final AnthropicChatOptions options;

    /** Ham, hiç kırpılmamış konuşma geçmişi (oturum boyunca korunur). */
    private final List<Message> history = new ArrayList<>();

    public AgentLoop(ChatModel chatModel, WorkspaceTools tools, PermissionGate gate,
            ContextManager contextManager, HarnessProperties props) {
        this.chatModel = chatModel;
        this.contextManager = contextManager;
        this.props = props;

        List<ToolCallback> guarded = Arrays.stream(ToolCallbacks.from(tools))
            .map(cb -> (ToolCallback) new GuardedToolCallback(cb, gate, props.maxToolOutputChars()))
            .toList();

        this.options = AnthropicChatOptions.builder()
            .toolCallbacks(guarded)
            .internalToolExecutionEnabled(false) // döngü artık bizim
            .build();

        history.add(new SystemMessage(SYSTEM_PROMPT));
    }

    public String run(String task) {
        int checkpoint = history.size();
        try {
            return loop(task);
        }
        catch (RuntimeException e) {
            // Yarım kalan turu geri al; bozuk geçmiş sonraki görevi de bozmasın
            history.subList(checkpoint, history.size()).clear();
            throw e;
        }
    }

    private String loop(String task) {
        history.add(new UserMessage(task));

        for (int i = 1; i <= props.maxIterations(); i++) {
            // 1) Context engineering: modele gidecek görünümü hazırla
            List<Message> view = contextManager.compact(history);
            Prompt prompt = new Prompt(view, options);

            // 2) Model karar verir
            ChatResponse response = chatModel.call(prompt);
            trace(i, view, response);

            // 3) Durma koşulu: tool çağrısı yoksa model işi bitirmiştir
            if (!response.hasToolCalls()) {
                AssistantMessage answer = response.getResult().getOutput();
                history.add(answer);
                return answer.getText();
            }

            // 4) Harness eylemi yapar (GuardedToolCallback -> izin, hata, kırpma)
            ToolExecutionResult result = toolCallingManager.executeToolCalls(prompt, response);

            // 5) Ham geçmişe ekle (kırpılmış görünüme değil!)
            // DİKKAT: Anthropic yanıtı "metin + tool_use" içerince Spring AI bunu birden fazla
            // Generation'a böler; getResult() sadece ilkini (metni) verir. tool_use bloğunu taşıyan
            // asistan mesajını ToolCallingManager'ın ürettiği geçmişten alıyoruz. Aksi halde API
            // "tool_result'ın karşılığı olan tool_use yok" diye 400 döner.
            List<Message> conversation = result.conversationHistory();
            history.add(conversation.get(conversation.size() - 2)); // asistan mesajı (tool_use)
            history.add(conversation.get(conversation.size() - 1)); // tool_result'lar
        }

        return "[!] " + props.maxIterations() + " iterasyon limitine ulaşıldı, agent durduruldu. "
                + "(Bu bir harness kararıdır: sonsuz döngü ve maliyet koruması.)";
    }

    public void reset() {
        history.subList(1, history.size()).clear(); // system prompt kalır
    }

    private void trace(int iteration, List<Message> view, ChatResponse response) {
        // Yanıt birden fazla Generation'a bölünmüş olabilir: hepsini topla
        StringBuilder text = new StringBuilder();
        int toolCalls = 0;
        for (Generation g : response.getResults()) {
            AssistantMessage m = g.getOutput();
            if (m.getText() != null && !m.getText().isBlank()) {
                text.append(m.getText().strip()).append(' ');
            }
            toolCalls += m.getToolCalls().size();
        }
        String thought = text.toString().strip();
        if (thought.length() > 200) {
            thought = thought.substring(0, 200) + "...";
        }
        Integer inputTokens = response.getMetadata().getUsage() == null ? null
                : response.getMetadata().getUsage().getPromptTokens();
        System.out.printf("%n-- iterasyon %d | ~%d token (tahmini) | %s gerçek input token | %d tool çağrısı%n",
                iteration, ContextManager.estimateTokens(view), inputTokens == null ? "?" : inputTokens,
                toolCalls);
        if (!thought.isEmpty()) {
            System.out.println("  [düşünce] " + thought.replace('\n', ' '));
        }
    }
}
