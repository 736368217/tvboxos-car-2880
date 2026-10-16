package com.undcover.freedom.pyramid;

import android.util.Base64;

import org.json.JSONArray;
import java.util.Locale;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Uses the app's configured OkHttp stack for Python sites which reject the
 * embedded Python requests TLS/network fingerprint.
 */
public final class PythonHttp {
    private PythonHttp() {
    }

    public static String request(String method,
                                 String url,
                                 String headersJson,
                                 String bodyBase64,
                                 boolean followRedirects,
                                 int connectTimeoutMs,
                                 int readTimeoutMs) {
        JSONObject result = new JSONObject();
        try {
            method = method.toUpperCase(Locale.ROOT);
            Request.Builder builder = new Request.Builder().url(url);
            MediaType mediaType = null;
            if (headersJson != null && !headersJson.isEmpty()) {
                JSONObject headers = new JSONObject(headersJson);
                Iterator<String> keys = headers.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    if ("content-type".equalsIgnoreCase(key)) {
                        mediaType = MediaType.parse(headers.optString(key));
                    }
                    if (!"content-length".equalsIgnoreCase(key)) {
                        builder.header(key, headers.optString(key));
                    }
                }
            }

            RequestBody requestBody = null;
            if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
                byte[] body = bodyBase64 == null || bodyBase64.isEmpty()
                        ? new byte[0]
                        : Base64.decode(bodyBase64, Base64.DEFAULT);
                requestBody = RequestBody.create(mediaType, body);
            }
            Request request = builder.method(method, requestBody).build();
            okhttp3.OkHttpClient client = com.github.catvod.net.OkHttp.client()
                    .newBuilder()
                    .connectTimeout(Math.max(0, connectTimeoutMs), TimeUnit.MILLISECONDS)
                    .readTimeout(Math.max(0, readTimeoutMs), TimeUnit.MILLISECONDS)
                    .writeTimeout(Math.max(0, readTimeoutMs), TimeUnit.MILLISECONDS)
                    .followRedirects(followRedirects)
                    .followSslRedirects(followRedirects)
                    .build();
            try (Response response = client.newCall(request).execute()) {
                result.put("status_code", response.code());
                result.put("reason", response.message());
                result.put("url", response.request().url().toString());
                JSONObject responseHeaders = new JSONObject();
                for (String name : response.headers().names()) {
                    responseHeaders.put(name, response.header(name));
                }
                result.put("headers", responseHeaders);
                JSONArray headerPairs = new JSONArray();
                for (int i = 0; i < response.headers().size(); i++) {
                    JSONArray pair = new JSONArray();
                    pair.put(response.headers().name(i));
                    pair.put(response.headers().value(i));
                    headerPairs.put(pair);
                }
                result.put("header_pairs", headerPairs);
                byte[] content = response.body() == null ? new byte[0] : response.body().bytes();
                result.put("body_base64", Base64.encodeToString(content, Base64.NO_WRAP));
            }
        } catch (Throwable error) {
            result = new JSONObject();
            try {
                result.put("error", error.getClass().getSimpleName());
            } catch (Exception ignored) {
            }
        }
        return result.toString();
    }
}
