package com.furkan.harness.console;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;

/** REPL ve izin onayı aynı stdin'i paylaştığı için tek bir okuyucu. */
@Component
public class ConsoleIO {

    private final BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    private final PrintWriter out = new PrintWriter(System.out, true, StandardCharsets.UTF_8);

    public String ask(String prompt) {
        out.print(prompt);
        out.flush();
        try {
            return in.readLine();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void print(String text) {
        out.println(text);
    }
}
