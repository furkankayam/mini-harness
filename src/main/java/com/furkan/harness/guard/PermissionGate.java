package com.furkan.harness.guard;

import java.util.Set;

import com.furkan.harness.HarnessProperties;
import com.furkan.harness.console.ConsoleIO;

import org.springframework.stereotype.Component;

/**
 * İzin politikası. Claude Code'daki izin modlarının en basit hali:
 * okuma serbest, yan etkisi olan her şey onaya tabi, bazı şeyler her zaman yasak.
 */
@Component
public class PermissionGate {

    private static final Set<String> READ_ONLY = Set.of("list_files", "read_file");

    /** Kaba ama öğretici bir deny-list. Gerçek harness'larda bu iş sandbox'ındır. */
    private static final Set<String> FORBIDDEN_FRAGMENTS = Set.of("rm -rf", "sudo", "curl ", "wget ", "shutdown");

    public enum Decision { ALLOW, DENY }

    private final ConsoleIO console;
    private final boolean autoApprove;

    public PermissionGate(ConsoleIO console, HarnessProperties props) {
        this.console = console;
        this.autoApprove = props.autoApprove();
    }

    public Decision check(String toolName, String input) {
        if (READ_ONLY.contains(toolName)) {
            return Decision.ALLOW;
        }
        if ("run_command".equals(toolName)
                && FORBIDDEN_FRAGMENTS.stream().anyMatch(f -> input.toLowerCase().contains(f))) {
            console.print("  [ENGEL] politika gereği engellendi: " + input);
            return Decision.DENY;
        }
        if (autoApprove) {
            return Decision.ALLOW;
        }
        String answer = console.ask("  [ONAY?] " + toolName + " " + abbreviate(input) + "  izin ver? [e/h] ");
        return answer != null && answer.trim().toLowerCase().startsWith("e") ? Decision.ALLOW : Decision.DENY;
    }

    private static String abbreviate(String s) {
        return s.length() > 160 ? s.substring(0, 160) + "..." : s;
    }
}
