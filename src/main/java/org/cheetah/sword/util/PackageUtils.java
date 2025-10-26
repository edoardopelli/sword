package org.cheetah.sword.util;

public final class PackageUtils {
	


	public static String normalizePackage(String raw) {
		if (raw == null)
			return "";
		String p = raw.replace('/', '.').replace('\\', '.').trim();
		while (p.contains("..")) {
			p = p.replace("..", ".");
		}
		if (p.startsWith("."))
			p = p.substring(1);
		if (p.endsWith("."))
			p = p.substring(0, p.length() - 1);
		return p;
	}

	public static String siblingPackage(String entityPackage, String name) {

		if (entityPackage == null || entityPackage.isBlank()) {
			return name;
		}
		int idx = entityPackage.lastIndexOf('.');
		if (idx <= 0) {
			return name;
		}
		String parent = entityPackage.substring(0, idx);
		return parent + "." + name;
	}

}
