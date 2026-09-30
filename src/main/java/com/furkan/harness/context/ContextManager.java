package com.furkan.harness.context;

import java.util.ArrayList;
import java.util.List;

import com.furkan.harness.HarnessProperties;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.stereotype.Component;

/**
 * Context engineering'in en basit hali: "tool result clearing".
 *
 * Ham geçmişe dokunmayız; modele gönderilecek GÖRÜNÜMÜ üretiriz.
 * Son N tool çıktısı olduğu gibi kalır, daha eskileri tek satırlık bir özete iner.
 * Modelin kararları (AssistantMessage) ve kullanıcı mesajları korunur.
 */
@Component
public class ContextManager {

    private final int keepRecent;

    public ContextManager(HarnessProperties props) {
        this.keepRecent = props.keepRecentToolResults();
    }

    public List<Message> compact(List<Message> history) {
        long toolMessages = history.stream().filter(m -> m instanceof ToolResponseMessage).count();
        long toClear = Math.max(0, toolMessages - keepRecent);

        List<Message> view = new ArrayList<>(history.size());
        for (Message m : history) {
            if (toClear > 0 && m instanceof ToolResponseMessage trm) {
                view.add(clear(trm));
                toClear--;
            }
            else {
                view.add(m);
            }
        }
        return view;
    }

    /** Kaba token tahmini (~4 karakter = 1 token). Sadece trace için. */
    public static long estimateTokens(List<Message> messages) {
        long chars = 0;
        for (Message m : messages) {
            if (m instanceof ToolResponseMessage trm) {
                for (ToolResponseMessage.ToolResponse r : trm.getResponses()) {
                    chars += r.responseData() == null ? 0 : r.responseData().length();
                }
            }
            else if (m.getText() != null) {
                chars += m.getText().length();
            }
        }
        return chars / 4;
    }

    private static ToolResponseMessage clear(ToolResponseMessage original) {
        List<ToolResponseMessage.ToolResponse> cleared = original.getResponses()
            .stream()
            .map(r -> new ToolResponseMessage.ToolResponse(r.id(), r.name(),
                    "[eski '" + r.name() + "' çıktısı context'ten temizlendi ("
                            + (r.responseData() == null ? 0 : r.responseData().length())
                            + " karakter). Gerekirse tool'u tekrar çağır.]"))
            .toList();
        return ToolResponseMessage.builder().responses(cleared).metadata(original.getMetadata()).build();
    }
}
