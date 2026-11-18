package me.elizarov.weather;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

public final class ForecastHttp {
    private ForecastHttp() {
    }

    public static String read(HttpURLConnection connection) throws IOException, InterruptedException {
        try (InputStream in = connection.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            for (int count; (count = in.read(buffer)) != -1; ) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                if (bytes.size() + count > 262144) throw new IOException("Forecast response too large");
                bytes.write(buffer, 0, count);
            }
            return bytes.toString(StandardCharsets.UTF_8.name());
        }
    }
}
