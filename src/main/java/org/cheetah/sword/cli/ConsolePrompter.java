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

	public String askPassword(String prompt, String defaultValue) {
		try {
			String defaultPart = (defaultValue != null ? " [" + defaultValue + "]" : "");
			java.io.Console console = System.console();

			if (console != null) {
				char[] pwd = console.readPassword("%s%s: ", prompt, defaultPart);
				if (pwd == null) {
					return defaultValue;
				}
				String trimmed = new String(pwd).trim();
				return trimmed.isEmpty() ? defaultValue : trimmed;
			}

			// Fallback: console not available (e.g., IDE). Input will be visible.
			System.out.print(prompt + defaultPart + ": ");
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