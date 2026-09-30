package com.furkan.harness.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.furkan.harness.HarnessProperties;

/**
 * Agent'ın "elleri". Tool açıklamaları modelin okuduğu arayüzdür:
 * ne zaman, nasıl ve hangi kısıtlarla kullanılacağını net yazmak "agent UX"tir.
 *
 * Sandbox: tüm yollar workspace köküne göre çözülür, dışarı çıkmak yasaktır.
 */
@Component
public class WorkspaceTools {

    private final Path root;
    private final int commandTimeoutSeconds;

    public WorkspaceTools(HarnessProperties props) throws IOException {
        this.root = Path.of(props.workspace()).toAbsolutePath().normalize();
        this.commandTimeoutSeconds = props.commandTimeoutSeconds();
        Files.createDirectories(root);
    }

    @Tool(name = "list_files", description = """
            Workspace içindeki dosya ve klasörleri özyinelemeli listeler (en fazla 3 seviye).
            Bir işe başlamadan önce projede ne olduğunu görmek için kullan.""")
    public String listFiles(@ToolParam(description = "Workspace'e göre göreli klasör yolu. Kök için '.'") String path)
            throws IOException {
        Path dir = resolve(path);
        try (Stream<Path> s = Files.walk(dir, 3)) {
            String out = s.filter(p -> !p.equals(dir))
                .map(p -> root.relativize(p) + (Files.isDirectory(p) ? "/" : ""))
                .sorted()
                .collect(Collectors.joining("\n"));
            return out.isEmpty() ? "(boş klasör)" : out;
        }
    }

    @Tool(name = "read_file", description = """
            Bir metin dosyasının içeriğini okur. Bir dosyayı değiştirmeden önce mutlaka oku.""")
    public String readFile(@ToolParam(description = "Workspace'e göre göreli dosya yolu") String path)
            throws IOException {
        return Files.readString(resolve(path), StandardCharsets.UTF_8);
    }

    @Tool(name = "write_file", description = """
            Dosyayı verilen içerikle TAMAMEN yazar (yoksa oluşturur, varsa üzerine yazar).
            Kullanıcı onayı gerektirir; reddedilirse başka bir yol dene veya kullanıcıya sor.""")
    public String writeFile(@ToolParam(description = "Workspace'e göre göreli dosya yolu") String path,
            @ToolParam(description = "Dosyanın yeni tam içeriği") String content) throws IOException {
        Path file = resolve(path);
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return "Yazıldı: " + root.relativize(file) + " (" + content.length() + " karakter)";
    }

    @Tool(name = "run_command", description = """
            Workspace kökünde bir shell komutu çalıştırır (ör. derleme, test, 'java Main.java').
            Çıktı ve exit code döner. Zaman aşımı vardır. Kullanıcı onayı gerektirir.""")
    public String runCommand(@ToolParam(description = "Çalıştırılacak komut") String command) throws Exception {
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        ProcessBuilder pb = windows ? new ProcessBuilder("cmd", "/c", "chcp 65001>nul && " + command)
                : new ProcessBuilder("bash", "-c", command);
        pb.directory(root.toFile()).redirectErrorStream(true);
        if (windows) {
            pb.environment().merge(
                "JAVA_TOOL_OPTIONS",
                "-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8",
                (existing, utf8Options) -> utf8Options + " " + existing);
        }

        Process process = pb.start();
        if (!process.waitFor(commandTimeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            return "ZAMAN AŞIMI: komut " + commandTimeoutSeconds + " sn içinde bitmedi.";
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return "exit=" + process.exitValue() + "\n" + output;
    }

    /** Sandbox kontrolü: "../../etc/passwd" gibi kaçış denemelerini engeller. */
    private Path resolve(String relative) {
        Path p = root.resolve(relative == null || relative.isBlank() ? "." : relative).normalize();
        if (!p.startsWith(root)) {
            throw new SecurityException("Workspace dışına erişim yasak: " + relative);
        }
        return p;
    }
}
