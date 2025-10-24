package org.cheetah.sword.util;

public final class Closeables {
    private Closeables() {}
    public static void quiet(AutoCloseable c) {
        if (c != null) try { c.close(); } catch (Exception ignored) {}
    }
}