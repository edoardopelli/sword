package org.cheetah.sword.cli;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Locale;

public class ConsolePrompter {

    private final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

    public String ask(String prompt, String defaultValue) {
        try {
            System.out.print(prompt + (defaultValue != null ? " [" + defaultValue + "]" : "") + ": ");
            String line = reader.readLine();
            if (line == null) {
                return defaultValue;
            }
            String trimmed = line.trim();
            return trimmed.isEmpty() ? defaultValue : trimmed;
        } catch (Exception ex) {
            throw new IllegalStateException("Console input failed.", ex);
        }
    }

    public boolean askYesNo(String prompt, boolean defaultValue) {
        String def = defaultValue ? "Y" : "N";
        String v = ask(prompt + " [Y/N]", def);
        return v != null && v.trim().toLowerCase(Locale.ROOT).startsWith("y");
    }

    public int askInt(String prompt, int defaultValue) {
        String v = ask(prompt, Integer.toString(defaultValue));
        try {
            return Integer.parseInt(v.trim());
        } catch (Exception ex) {
            return defaultValue;
        }
    }
}