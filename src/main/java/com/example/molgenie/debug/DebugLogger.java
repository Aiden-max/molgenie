package com.example.molgenie.debug;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

public final class DebugLogger {

    private static final String LOG_PATH = "debug-cdcc17.log";

    private DebugLogger() {
    }

    // #region agent log
    public static void log(String location, String message, String hypothesisId, String runId, Map<String, Object> data) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append('{');
            sb.append("\"id\":\"").append("log_").append(System.currentTimeMillis()).append('"');
            sb.append(",\"timestamp\":").append(System.currentTimeMillis());
            sb.append(",\"sessionId\":\"cdcc17\"");
            sb.append(",\"runId\":\"").append(escape(runId)).append('"');
            sb.append(",\"hypothesisId\":\"").append(escape(hypothesisId)).append('"');
            sb.append(",\"location\":\"").append(escape(location)).append('"');
            sb.append(",\"message\":\"").append(escape(message)).append('"');
            sb.append(",\"data\":");
            if (data == null || data.isEmpty()) {
                sb.append("{}");
            } else {
                sb.append('{');
                boolean first = true;
                for (Map.Entry<String, Object> e : data.entrySet()) {
                    if (!first) {
                        sb.append(',');
                    }
                    first = false;
                    sb.append('"').append(escape(e.getKey())).append("\":\"")
                            .append(escape(String.valueOf(e.getValue()))).append('"');
                }
                sb.append('}');
            }
            sb.append('}');
            sb.append('\n');

            Files.writeString(
                    Path.of(LOG_PATH),
                    sb.toString(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException ignored) {
            // logging failure should never break main logic
        }
    }
    // #endregion agent log

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }
}

