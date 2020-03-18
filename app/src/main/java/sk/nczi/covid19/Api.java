/*-
 * Copyright (c) 2020 Sygic a.s.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package sk.nczi.covid19;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;

import com.google.gson.Gson;

import java.security.KeyStore;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import sk.turn.http.Http;

public class Api {
    public interface Listener {
        void onResponse(int status, String response);
    }
    public static class RequestBase {
        private Long profileId;
        private String deviceId;
        private String covidPass;
        private String nonce;
        private String status;
        public RequestBase(long profileId, String deviceId, String covidPass) {
            this.profileId = profileId;
            this.deviceId = deviceId;
            this.covidPass = covidPass;
        }
        public String getNonce() {
            return nonce;
        }
        public void setNonce(String nonce) {
            this.nonce = nonce;
        }
        public void setStatus(String status) {
            this.status = status;
        }
    }
    private static class Response {
        private int status;
        private String body;
        Response(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }

    /** String (public key for signing API requests) */
    public static final String PREF_API_PUBLIC_KEY = "apiPublicKey";

    public static LinkedHashMap<String, String> createMap(String... keyValues) {
        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            headers.put(keyValues[i * 2], keyValues[i * 2 + 1]);
        }
        return headers;
    }

    private final App app;
    public Api(Context context) {
        app = App.get(context);
    }

    public void send(String action, String method, Object request, final Listener listener) {
        send(action, method, request, null, null, listener);
    }

    public void send(String action, String method, Object request, String signingKeyAlias, final Listener listener) {
        send(action, method, request, signingKeyAlias, null, listener);
    }

    @SuppressLint("StaticFieldLeak")
    public void send(String action, String method, Object request, String signingKeyAlias, LinkedHashMap<String, String> headers, final Listener listener) {
        AsyncTask<Void, Void, Response> asyncTask = new AsyncTask<Void, Void, Response>() {
            @Override
            protected Response doInBackground(Void... voids) {
                try {
                    String data = (request == null ? null : new Gson().toJson(request));
                    Uri uri = Uri.withAppendedPath(app.apiUri(), action);
                    String versionName = app.getPackageManager().getPackageInfo(app.getPackageName(), 0).versionName;
                    String userAgent = String.format(Locale.ROOT, "%1$s(%2$s)/%3$s(anr)", app.getString(R.string.app_userAgentAppName),
                            app.getPackageName(), versionName.contains("-") ? versionName.substring(0, versionName.indexOf("-")) : versionName);
                    if (App.TEST) {
                        App.log("API > " + method + " " + uri + " (" + userAgent + ") " + data);
                    } else {
                        App.log("API > " + method + " " + uri);
                    }
                    Http http = new Http(uri.toString(), method)
                            .addHeader("Content-Type", "application/json")
                            .addHeader("Accept-Version", "2.0")
                            .addHeader("User-Agent", userAgent);
                    // Copy headers
                    if (headers != null) {
                        for (Map.Entry<String, String> header : headers.entrySet()) {
                            http.addHeader(header.getKey(), header.getValue());
                        }
                    }
                    // For GET requests with query params, set data to the query string for signing
                    if (Http.GET.equals(method) && action.contains("?")) {
                        data = action.substring(action.indexOf('?'));
                    }
                    // Sign the data and include signature in HTTP header
                    if (signingKeyAlias != null && data != null) {
                        KeyStore.PrivateKeyEntry privateKey = Security.getPrivateKey(signingKeyAlias);
                        String publicKey = app.prefs().getString(PREF_API_PUBLIC_KEY, null);
                        if (privateKey == null || publicKey == null) {
                            publicKey = Security.generateKeyPair(app, signingKeyAlias);
                            app.prefs().edit().putString(PREF_API_PUBLIC_KEY, publicKey).apply();
                            privateKey = Security.getPrivateKey(signingKeyAlias);
                        }
                        String signature = publicKey + ":" + Security.sign(data, privateKey.getPrivateKey());
                        if (App.TEST) {
                            App.log("API signature " + signature);
                        }
                        http.addHeader("X-Signature", signature);
                    }
                    if (request != null) {
                        http.setData(data);
                    }
                    int code = http.send().getResponseCode();
                    String response = http.getResponseString();
                    if (App.TEST) {
                        App.log("API < " + code + " " + response + (code == 200 ? "" : " " + http.getResponseMessage()));
                    } else {
                        App.log("API < " + code + (code == 200 ? "" : " " + http.getResponseMessage()));
                    }
                    return new Response(http.getResponseCode(), response);
                } catch (Exception e) {
                    App.log("API failed " + e);
                    return new Response(-1, e.getMessage());
                }
            }
            @Override
            protected void onPostExecute(Response response) {
                if (listener != null) {
                    listener.onResponse(response.status, response.body);
                }
            }
        };
        try {
            asyncTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } catch (Exception e) {
            listener.onResponse(-1, e.getMessage());
        }
    }
}
