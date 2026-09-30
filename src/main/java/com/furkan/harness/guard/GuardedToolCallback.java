package com.furkan.harness.guard;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * Her tool'u saran decorator = harness'ın "hook" katmanı.
 *
 *   PreToolUse  -> izin kontrolü
 *   (tool çalışır)
 *   PostToolUse -> hata yakalama, çıktı kırpma, trace
 *
 * Önemli fikir: hatalar exception olarak fırlatılmaz, modele METİN olarak döner.
 * Böylece model hatayı görüp kendini düzeltebilir (error recovery).
 */
public class GuardedToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final PermissionGate gate;
    private final int maxOutputChars;

    public GuardedToolCallback(ToolCallback delegate, PermissionGate gate, int maxOutputChars) {
        this.delegate = delegate;
        this.gate = gate;
        this.maxOutputChars = maxOutputChars;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        String name = getToolDefinition().name();
        System.out.println("  [tool] " + name + " " + toolInput);

        // --- PreToolUse hook ---
        if (gate.check(name, toolInput) == PermissionGate.Decision.DENY) {
            return "İZİN REDDEDİLDİ: kullanıcı '" + name + "' çağrısına izin vermedi. "
                    + "Farklı bir yaklaşım dene veya kullanıcıya ne yapmak istediğini sor.";
        }

        // --- Tool çalışır ---
        String result;
        try {
            result = delegate.call(toolInput, toolContext);
        }
        catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            result = "HATA (" + root.getClass().getSimpleName() + "): " + root.getMessage();
        }

        // --- PostToolUse hook ---
        if (result != null && result.length() > maxOutputChars) {
            int dropped = result.length() - maxOutputChars;
            result = result.substring(0, maxOutputChars) + "\n...[" + dropped + " karakter kırpıldı]";
        }
        System.out.println("    -> " + preview(result));
        return result;
    }

    private static String preview(String s) {
        if (s == null) {
            return "null";
        }
        String oneLine = s.replace('\n', ' ');
        return oneLine.length() > 120 ? oneLine.substring(0, 120) + "..." : oneLine;
    }
}
