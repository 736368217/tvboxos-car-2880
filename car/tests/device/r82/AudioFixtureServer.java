package com.tvbox.car.r82test;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Local fixture server for the real config/detail/player paths, not a production APK component. */
final class AudioFixtureServer implements Closeable {
    private final ServerSocket server;
    private final ExecutorService requests = Executors.newFixedThreadPool(3);
    private final File media;
    private final String origin;
    private final byte[] config, entries;

    AudioFixtureServer(File media, int port) throws Exception {
        this.media = media;
        origin = "http://127.0.0.1:" + port;
        config = new JSONObject().put("sites", new JSONArray().put(new JSONObject()
                .put("key", "audio_qa").put("name", "Audio QA").put("type", 1)
                .put("api", origin + "/api").put("searchable", 1).put("quickSearch", 1)))
                .put("parses", new JSONArray()).toString().getBytes(StandardCharsets.UTF_8);
        entries = new JSONObject().put("code", 1).put("page", 1).put("pagecount", 1).put("limit", 20).put("total", 1)
                .put("class", new JSONArray().put(new JSONObject().put("type_id", "1").put("type_name", "Audio QA")))
                .put("list", new JSONArray().put(new JSONObject().put("vod_id", "ab-series")
                        .put("vod_name", "Audio A/B QA").put("vod_pic", "").put("vod_content", "Local 48 kHz fixture")
                        .put("type_id", "1").put("type_name", "Audio QA").put("vod_remarks", "2 episodes")
                        .put("vod_play_from", "local").put("vod_play_url",
                                "Episode 1$" + origin + "/episode-1.mp4#Episode 2$" + origin + "/episode-2.mp4")))
                .toString().getBytes(StandardCharsets.UTF_8);
        for (int i = 1; i <= 2; i++) {
            File file = new File(media, "episode-" + i + ".mp4");
            if (!file.isFile() || !file.canRead()) throw new FileNotFoundException(file.toString());
        }
        server = new ServerSocket();
        server.setReuseAddress(true);
        server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port));
        Thread accept = new Thread(() -> {
            while (!server.isClosed()) {
                try {
                    Socket socket = server.accept();
                    requests.execute(() -> handle(socket));
                } catch (IOException closed) { return; }
            }
        }, "audio-qa-http");
        accept.setDaemon(true);
        accept.start();
    }

    String configUrl() { return origin + "/config.json"; }

    private void handle(Socket socket) {
        try (Socket client = socket) {
            client.setSoTimeout(5000);
            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII));
            String line = in.readLine();
            if (line == null) return;
            String[] request = line.split(" ", 3);
            if (request.length != 3) return;
            boolean head = "HEAD".equals(request[0]);
            String path = request[1].split("\\?", 2)[0];
            String range = null;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                if (line.regionMatches(true, 0, "Range: bytes=", 0, 13)) range = line.substring(13);
            }
            OutputStream output = client.getOutputStream();
            switch (path) {
                case "/config.json": send(output, config, head); break;
                case "/api": send(output, entries, head); break;
                case "/episode-1.mp4": case "/episode-2.mp4":
                    try (RandomAccessFile file = new RandomAccessFile(new File(media, path.substring(1)), "r")) {
                        long size = file.length(), start = 0, end = size - 1;
                        if (range != null) {
                            String[] offsets = range.trim().split("-", -1);
                            start = Long.parseLong(offsets[0]);
                            if (!offsets[1].isEmpty()) end = Math.min(end, Long.parseLong(offsets[1]));
                        }
                        if (start < 0 || end < start) {
                            header(output, "416 Range Not Satisfiable", "video/mp4", 0, "Content-Range: bytes */" + size + "\r\n");
                            break;
                        }
                        long count = end - start + 1;
                        String extra = "Accept-Ranges: bytes\r\n";
                        if (range != null) extra += "Content-Range: bytes " + start + "-" + end + "/" + size + "\r\n";
                        header(output, range == null ? "200 OK" : "206 Partial Content", "video/mp4", count, extra);
                        if (!head) {
                            byte[] bytes = new byte[65536];
                            file.seek(start);
                            while (count > 0) {
                                int read = file.read(bytes, 0, (int) Math.min(count, bytes.length));
                                if (read < 0) break;
                                output.write(bytes, 0, read);
                                count -= read;
                            }
                        }
                    }
                    break;
                default: header(output, "404 Not Found", "text/plain", 0, "");
            }
        } catch (IOException | RuntimeException ignored) { /* Player may close a range when seeking. */ }
    }

    private static void send(OutputStream out, byte[] body, boolean head) throws IOException {
        header(out, "200 OK", "application/json; charset=utf-8", body.length, "");
        if (!head) out.write(body);
    }
    private static void header(OutputStream out, String status, String mime, long size, String extra) throws IOException {
        out.write(("HTTP/1.1 " + status + "\r\nConnection: close\r\nContent-Type: " + mime
                + "\r\nContent-Length: " + size + "\r\n" + extra + "\r\n").getBytes(StandardCharsets.US_ASCII));
    }

    @Override public void close() throws IOException {
        server.close();
        requests.shutdownNow();
    }
}
