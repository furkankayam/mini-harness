package com.furkan.harness;

import com.furkan.harness.console.ConsoleIO;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/** Basit REPL: görev yaz, agent çalışsın. */
@Component
public class HarnessRunner implements CommandLineRunner {

    private final AgentLoop agent;
    private final ConsoleIO console;
    private final HarnessProperties props;

    public HarnessRunner(AgentLoop agent, ConsoleIO console, HarnessProperties props) {
        this.agent = agent;
        this.console = console;
        this.props = props;
    }

    @Override
    public void run(String... args) {
        console.print("""

                +---------------------------------------------+
                |  mini-harness  -  model + harness = agent   |
                +---------------------------------------------+
                workspace: %s  |  max iterasyon: %d  |  auto-approve: %s
                Komutlar: /reset (geçmişi temizle), /exit
                """.formatted(props.workspace(), props.maxIterations(), props.autoApprove()));

        while (true) {
            String line = console.ask("\nsen> ");
            if (line == null || line.isBlank()) {
                if (line == null) {
                    return;
                }
                continue;
            }
            switch (line.trim()) {
                case "/exit" -> {
                    return;
                }
                case "/reset" -> {
                    agent.reset();
                    console.print("Geçmiş temizlendi.");
                }
                default -> {
                    try {
                        console.print("\nagent> " + agent.run(line));
                    }
                    catch (Exception e) {
                        console.print("HARNESS HATASI: " + e.getMessage());
                    }
                }
            }
        }
    }
}
