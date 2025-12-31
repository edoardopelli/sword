package org.cheetah.sword.cli;

import java.io.Console;
import java.util.Scanner;

public final class ConsoleInput {

    private ConsoleInput() {
    }

    public static String readLine(Scanner in, String prompt, String def) {
        System.out.print(prompt);
        String s = in.nextLine();
        if (s == null || s.isBlank()) {
            return def;
        }
        return s.trim();
    }

    public static String readPassword(Scanner in, String prompt) {
        Console console = System.console();
        if (console != null) {
            char[] pwd = console.readPassword(prompt);
            return pwd == null ? "" : new String(pwd);
        }

        // Fallback: visible input (console not available, e.g. IDE run)
        System.out.print(prompt);
        String s = in.nextLine();
        return s == null ? "" : s;
    }
}