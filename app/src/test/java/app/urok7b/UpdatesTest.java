package app.urok7b;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Updates.get: файл с сайта качается один раз, пока он не изменился (ETag и ответ 304 без тела). */
public class UpdatesTest {

    @Test
    public void etag() throws Exception {
        String[] tag = {"\"v1\""}, body = {"{\"days\":{}}"};
        int[] sent = {0};
        // крошечный HTTP-сервер на сокете: модуля com.sun.net.httpserver в юнит-тестах Android нет
        ServerSocket server = new ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"));
        Thread th = new Thread(() -> {
            while (!server.isClosed()) {
                try (Socket s = server.accept()) {
                    BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                    String line, match = null;
                    while ((line = r.readLine()) != null && !line.isEmpty())
                        if (line.toLowerCase(Locale.ROOT).startsWith("if-none-match:")) match = line.substring(14).trim();
                    OutputStream o = s.getOutputStream();
                    String head = "ETag: " + tag[0] + "\r\nConnection: close\r\n";
                    if (tag[0].equals(match)) {
                        o.write(("HTTP/1.1 304 Not Modified\r\n" + head + "\r\n").getBytes(StandardCharsets.UTF_8)); // без тела
                    } else {
                        byte[] b = body[0].getBytes(StandardCharsets.UTF_8);
                        o.write(("HTTP/1.1 200 OK\r\n" + head + "Content-Length: " + b.length + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                        o.write(b);
                        sent[0]++;
                    }
                    o.flush();
                } catch (IOException e) {
                    // сервер закрыт: тест закончился
                }
            }
        });
        th.setDaemon(true);
        th.start();
        try {
            String url = "http://127.0.0.1:" + server.getLocalPort() + "/changes.json";
            assertArrayEquals("первый раз: файл и его ETag", new String[]{body[0], "\"v1\""}, Updates.get(url, 512_000, null));
            assertArrayEquals("тот же ETag: 304, ничего не качаем", new String[]{null, "\"v1\""}, Updates.get(url, 512_000, "\"v1\""));
            tag[0] = "\"v2\"";
            body[0] = "{\"days\":{\"2026-11-10\":{\"off\":\"Актированный день\"}}}";
            assertArrayEquals("файл изменился: новый текст и новый ETag", new String[]{body[0], "\"v2\""}, Updates.get(url, 512_000, "\"v1\""));
            assertEquals("тело ушло только два раза из трёх", 2, sent[0]);
            assertEquals("ответ длиннее лимита обрезается", 8, Updates.get(url, 8, null)[0].length());
        } finally {
            server.close();
        }
    }
}
