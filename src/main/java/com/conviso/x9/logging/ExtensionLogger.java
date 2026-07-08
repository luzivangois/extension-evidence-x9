package com.conviso.x9.logging;

import java.io.PrintWriter;

/**
 * Thin wrapper around Burp's stdout stream so call sites depend on an
 * interface instead of a raw {@link PrintWriter}.
 */
public final class ExtensionLogger {

    private final PrintWriter stdout;

    public ExtensionLogger(PrintWriter stdout) {
        this.stdout = stdout;
    }

    public void info(String message) {
        if (stdout != null) {
            stdout.println(message);
        }
    }
}
