package app.urok7b;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import com.sun.net.httpserver.HttpServer;

import org.junit.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/** Updates.get: файл с сайта качается один раз, пока он не изменился (ETag и ответ 304 без тела). */
public class UpdatesTest {

    @Test
    public void etag() throws Exception {
        String[] tag = {"\"v1\""}, body = {"{\"days\":{}}"};
        int[] sent = {0};
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/changes.json", x -> {
            x.getResponseHeaders().add("ETag", tag[0]);
            if (tag[0].equals(x.getRequestHeaders().getFirst("If-None-Match"))) {
                x.sendResponseHeaders(304, -1); // не изменилось: без тела
            } else {
                byte[] b = body[0].getBytes(StandardCharsets.UTF_8);
                x.sendResponseHeaders(200, b.length);
                x.getResponseBody().write(b);
                sent[0]++;
            }
            x.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/changes.json";
            assertArrayEquals("первый раз: файл и его ETag", new String[]{body[0], "\"v1\""}, Updates.get(url, 512_000, null));
            assertArrayEquals("тот же ETag: 304, ничего не качаем", new String[]{null, "\"v1\""}, Updates.get(url, 512_000, "\"v1\""));
            tag[0] = "\"v2\"";
            body[0] = "{\"days\":{\"2026-11-10\":{\"off\":\"Актированный день\"}}}";
            assertArrayEquals("файл изменился: новый текст и новый ETag", new String[]{body[0], "\"v2\""}, Updates.get(url, 512_000, "\"v1\""));
            assertEquals("тело ушло только два раза из трёх", 2, sent[0]);
            assertEquals("ответ длиннее лимита обрезается", 8, Updates.get(url, 8, null)[0].length());
        } finally {
            server.stop(0);
        }
    }
}
