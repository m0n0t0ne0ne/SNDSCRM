package app.myspoti.host.android

import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Sends the sign-in pages' requests (accounts.spotify.com, challenge.spotify.com) from the app
 * instead of the WebView.
 *
 * The WebView stamps every request with "X-Requested-With: <package>" and current WebViews no
 * longer let an app turn that off (see [ControlRelay]). On the sign-in that header names the app
 * to Spotify's login, which then does not take the client for the desktop Chrome it claims to be.
 *
 * Two halves, because a WebView only hands the app the body of a request its own script sends:
 * - GET requests (the pages, their scripts and API reads) are fetched here from
 *   [WebViewClient.shouldInterceptRequest][android.webkit.WebViewClient.shouldInterceptRequest] ([intercept]).
 * - Requests with a body (the password, the e-mail code, the challenge answers) are sent by the
 *   page's `fetch`, wrapped at document start ([SCRIPT]) to post them to the app ([onMessage]).
 *
 * Both carry the WebView's cookies for the address (sp_t, the CSRF cookie...) and write back every
 * cookie Spotify sets, sp_dc included, so the player page that follows is signed in.
 */
internal class LoginRelay(
    private val view: WebView,
    private val fingerprint: Fingerprint,
    private val cookies: () -> CookieManager,
) {
    private val main = Handler(Looper.getMainLooper())

    /** The guarded fallback channel of a WebView without web message listeners (see [PlayerChannel]). */
    internal var channel: PlayerChannel? = null

    private val language: String by lazy {
        Fingerprint.acceptLanguage(android.os.LocaleList.getDefault().toLanguageTags().split(','))
    }

    fun install() {
        if (!ENABLED) return
        channel = view.exposeTo(CHANNEL, ORIGINS, ::onMessage)
        val documentStart = androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT)
        if (documentStart) androidx.webkit.WebViewCompat.addDocumentStartJavaScript(view, SCRIPT, ORIGINS)
        Logbook.log("login relay installed (document start: $documentStart)")
    }

    /** Fallback for WebViews without document-start scripts; harmless when already installed. */
    fun injectNow() { if (ENABLED) view.evaluateJavascript(SCRIPT, null) }

    // ---- GET: fetched here --------------------------------------------------------------------

    /**
     * The WebView's [request] fetched by the app, or null to leave it to the WebView (not a
     * sign-in host, not a GET, or the fetch failed: the WebView then sends it as before).
     */
    fun intercept(request: WebResourceRequest): WebResourceResponse? {
        if (!ENABLED) return null
        val url = request.url.toString()
        if (!isLoginHost(url)) return null
        val method = request.method?.uppercase() ?: "GET"
        if (method != "GET") {
            // A form posted by navigation: its body never reaches the app, the WebView sends it.
            if (request.isForMainFrame) Logbook.log("login relay: $method form left to the WebView ${Logbook.noQuery(url).take(100)}")
            return null
        }
        return runCatching { fetchGet(request, url) }
            .onFailure { Logbook.log("login relay: GET ${shortPath(url)} failed, the WebView sends it: ${it.message}") }
            .getOrNull()
    }

    private fun fetchGet(request: WebResourceRequest, start: String): WebResourceResponse? {
        var url = start
        repeat(MAX_REDIRECTS) {
            val builder = Request.Builder().url(url).get()
            request.requestHeaders.forEach { (k, v) -> if (k.lowercase() !in NOT_CARRIED) builder.header(k, v) }
            browserHeaders(builder, url)
            if (request.isForMainFrame) {
                builder.header("Sec-Fetch-Site", if (url == start) "none" else "same-origin")
                builder.header("Sec-Fetch-Mode", "navigate")
                builder.header("Sec-Fetch-Dest", "document")
                builder.header("Sec-Fetch-User", "?1")
                builder.header("Upgrade-Insecure-Requests", "1")
            }
            val response = http.newCall(builder.build()).execute()
            keepCookies(url, response.headers)
            val location = response.header("Location")
            if (response.code in 300..399 && location != null) {
                response.close()
                val next = response.request.url.resolve(location)?.toString() ?: return null
                Logbook.log("login relay: GET ${shortPath(url)} -> ${response.code} ${Logbook.noQuery(next).take(100)}")
                // A page's redirect: the WebView must land on the new address itself (the address
                // bar, relative links and the app's login checks all read it), so the page that
                // comes back sends it there. Off the sign-in hosts it then goes the WebView's way.
                if (request.isForMainFrame || !isLoginHost(next)) {
                    if (!request.isForMainFrame) return null
                    return redirectPage(next)
                }
                url = next
                return@repeat
            }
            if (response.code in 300..399 || response.code == 304) {
                // 304 for a conditional request the WebView made: its cache knows what it asked for.
                response.close()
                return null
            }
            Logbook.log("login relay: GET ${shortPath(url)} -> ${response.code}")
            return toWebResponse(response)
        }
        return null
    }

    /** A page that moves the WebView on to [target], replacing itself in the history. */
    private fun redirectPage(target: String): WebResourceResponse {
        val html = "<!doctype html><meta charset=utf-8><script>location.replace(${JSONObject.quote(target)})</script>"
        return WebResourceResponse("text/html", "utf-8", 200, "OK", mapOf("Cache-Control" to "no-store"),
            ByteArrayInputStream(html.toByteArray()))
    }

    private fun toWebResponse(response: Response): WebResourceResponse {
        val body = response.body
        val type = body.contentType()
        val headers = response.headers.toMultimap()
            .filterKeys { it.lowercase() !in DROPPED }
            .mapValues { (_, values) -> values.joinToString(", ") }
        return WebResourceResponse(
            type?.let { "${it.type}/${it.subtype}" } ?: "application/octet-stream",
            type?.charset()?.name(),
            response.code,
            response.message.ifBlank { reason(response.code) },
            headers,
            ClosingBody(body.byteStream(), response),
        )
    }

    // ---- Requests with a body: from the page's wrapped fetch -----------------------------------

    /** `{id, doc, url, method, headers, body, page}` from the page's wrapped `fetch`. */
    private fun onMessage(json: String) {
        val m = runCatching { JSONObject(json) }.getOrNull() ?: return
        if (m.optString("type") == "note") {
            Logbook.log("login relay: ${m.optString("text").take(200)}")
            return
        }
        val id = m.optInt("id", -1).takeIf { it >= 0 } ?: return
        val doc = m.optString("doc")
        val url = m.optString("url")
        val page = m.optString("page")
        // Only to the sign-in hosts, from a sign-in page: never a way to send the cookies elsewhere.
        if (!isLoginHost(url) || !isLoginHost(page)) {
            reply(id, doc, 0, "{}", "", url)
            return
        }
        val method = m.optString("method", "POST").uppercase()
        val body = if (m.isNull("body")) null else m.optString("body")
        val given = m.optJSONObject("headers") ?: JSONObject()
        val builder = Request.Builder().url(url)
        given.keys().forEach { k -> if (k.lowercase() !in NOT_CARRIED) runCatching { builder.header(k, given.getString(k)) } }
        browserHeaders(builder, url)
        val pageUrl = page.toHttpUrlOrNull()
        val target = url.toHttpUrlOrNull()
        builder.header("Origin", "https://${pageUrl?.host}")
        builder.header("Referer", page)
        builder.header("Sec-Fetch-Site", if (pageUrl?.host == target?.host) "same-origin" else "same-site")
        builder.header("Sec-Fetch-Mode", "cors")
        builder.header("Sec-Fetch-Dest", "empty")
        if (given.keys().asSequence().none { it.equals("accept", true) }) builder.header("Accept", "*/*")
        // Bytes, not a String body: OkHttp would append "; charset=utf-8" to the content type.
        val payload = if (method == "GET" || method == "HEAD") null else (body ?: "").toByteArray().toRequestBody(null)
        builder.method(method, payload)
        following.newCall(builder.build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Logbook.log("login relay: $method ${shortPath(url)} failed: ${e.message}")
                reply(id, doc, 0, "{}", "", url)
            }

            override fun onResponse(call: Call, response: Response) {
                val answer = runCatching {
                    response.use {
                        val out = JSONObject()
                        it.headers.toMultimap().forEach { (k, v) -> if (k.lowercase() !in DROPPED) out.put(k, v.joinToString(", ")) }
                        Triple(it.code, out.toString(), it.body.string()) to it.request.url.toString()
                    }
                }.getOrElse { e ->
                    Logbook.log("login relay: $method ${shortPath(url)} unreadable: ${e.message}")
                    reply(id, doc, 0, "{}", "", url)
                    return
                }
                val (triple, finalUrl) = answer
                Logbook.log("login relay: $method ${shortPath(url)} -> ${triple.first}")
                if (triple.first >= 400) Logbook.log("login relay error body: ${LogMaskBody.redact(triple.third.take(300))}")
                reply(id, doc, triple.first, triple.second, triple.third, finalUrl)
            }
        })
    }

    private fun reply(id: Int, doc: String, status: Int, headersJson: String, body: String, finalUrl: String) {
        val script = "window.__mspLoginDone && window.__mspLoginDone($id, $status, ${JSONObject.quote(headersJson)}, " +
            "${JSONObject.quote(body)}, ${JSONObject.quote(finalUrl)}, ${JSONObject.quote(doc)})"
        main.post { view.evaluateJavascript(script, null) }
    }

    // ---- Shared ---------------------------------------------------------------------------------

    /** What desktop Chrome sends by itself, and the WebView's cookies for [url]. */
    private fun browserHeaders(builder: Request.Builder, url: String) {
        builder.header("User-Agent", fingerprint.userAgent)
        builder.header("Accept-Language", language)
        builder.header("sec-ch-ua", fingerprint.secChUa)
        builder.header("sec-ch-ua-mobile", "?0")
        builder.header("sec-ch-ua-platform", "\"Linux\"")
        // No Accept-Encoding: ControlRelay.DECODING writes Chrome's list and decodes the answer.
        builder.removeHeader("Cookie")
        runCatching { cookies().getCookie(url) }.getOrNull()?.takeIf { it.isNotBlank() }?.let { builder.header("Cookie", it) }
    }

    /** Every cookie Spotify set on this answer, into the WebView's jar (so the page sees it signed in). */
    private fun keepCookies(url: String, headers: Headers) {
        val set = headers.values("Set-Cookie")
        if (set.isEmpty()) return
        val jar = cookies()
        set.forEach { runCatching { jar.setCookie(url, it) } }
        jar.flush()
        Logbook.log("login relay: ${set.size} cookie(s) kept from ${shortPath(url)}: " +
            set.joinToString(",") { it.substringBefore('=').take(24) })
    }

    /** Hop by hop, so every redirect's cookies reach the jar (OkHttp follows them for [following]). */
    private val cookieHops = okhttp3.Interceptor { chain ->
        val response = chain.proceed(chain.request())
        keepCookies(chain.request().url.toString(), response.headers)
        response
    }

    private val following: OkHttpClient by lazy { http.newBuilder().followRedirects(true).addNetworkInterceptor(cookieHops).build() }

    private fun shortPath(url: String) = url.substringBefore('?').substringAfter("spotify.com").take(90)

    /** Error bodies of the sign-in can echo what was typed: only their shape is logged. */
    private object LogMaskBody {
        fun redact(text: String) = text.replace(Regex("\"[^\"]{24,}\""), "\"…\"")
    }

    companion object {
        /** Off: the sign-in goes the WebView's way, as before this relay. */
        const val ENABLED = true

        private const val CHANNEL = "MyspotiLogin"
        private val ORIGINS = setOf("https://accounts.spotify.com", "https://challenge.spotify.com")
        private const val MAX_REDIRECTS = 8

        fun isLoginHost(url: String?): Boolean {
            val u = url?.toHttpUrlOrNull() ?: return false
            return u.scheme == "https" && (u.host == "accounts.spotify.com" || u.host == "challenge.spotify.com")
        }

        /** Never copied from the WebView's or the page's request: the transport's, the cookies (from the jar), the WebView's mark. */
        private val NOT_CARRIED = setOf(
            "host", "connection", "content-length", "accept-encoding", "x-requested-with", "cookie",
            "user-agent", "accept-language", "sec-ch-ua", "sec-ch-ua-mobile", "sec-ch-ua-platform",
            "if-none-match", "if-modified-since",
        )

        /** What the hop replaces: the body is decoded here, its cookies went to the jar. */
        private val DROPPED = setOf("content-encoding", "content-length", "transfer-encoding", "connection", "keep-alive", "set-cookie")

        private fun reason(code: Int) = when (code) {
            200 -> "OK"; 201 -> "Created"; 204 -> "No Content"; 400 -> "Bad Request"; 401 -> "Unauthorized"
            403 -> "Forbidden"; 404 -> "Not Found"; 429 -> "Too Many Requests"; else -> if (code >= 500) "Server Error" else "Status"
        }

        /** Redirects handled by [fetchGet] itself; Chrome's encodings decoded ([ControlRelay.DECODING]). */
        private val http: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .addInterceptor(ControlRelay.DECODING)
                .build()
        }

        /** Wraps the sign-in page's `fetch` for requests with a body to the sign-in hosts. */
        private val SCRIPT = """
            (() => {
              if (window.__mspLoginInstalled || !window.$CHANNEL) return;
              window.__mspLoginInstalled = true;
              const hosts = /^https:\/\/(accounts|challenge)\.spotify\.com\//;
              const doc = Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 10);
              const pending = new Map();
              let seq = 0;
              const post = (m) => { try { window.$CHANNEL.postMessage(JSON.stringify(m)); } catch (e) { /* host gone */ } };
              window.__mspLoginDone = (id, status, headers, body, finalUrl, from) => {
                if (from !== doc) return;
                const p = pending.get(id);
                if (!p) return;
                pending.delete(id);
                if (!status) return p.reject(new TypeError('Failed to fetch'));
                const noBody = status === 204 || status === 205 || status === 304;
                const r = new Response(noBody ? null : body, { status, headers: JSON.parse(headers) });
                try {
                  Object.defineProperty(r, 'url', { value: finalUrl });
                  Object.defineProperty(r, 'redirected', { value: finalUrl !== p.url });
                } catch (e) { /* keeps the defaults */ }
                p.resolve(r);
              };
              // A body the host can carry as text; undefined for one it cannot (a stream, a blob).
              const asText = (b) => {
                if (b == null) return Promise.resolve([null, null]);
                if (typeof b === 'string') return Promise.resolve([b, null]);
                if (b instanceof URLSearchParams) return Promise.resolve([b.toString(), 'application/x-www-form-urlencoded;charset=UTF-8']);
                return undefined;
              };
              const original = window.fetch;
              window.fetch = function (input, init) {
                const isRequest = typeof Request !== 'undefined' && input instanceof Request;
                const url = isRequest ? input.url : (typeof input === 'string' ? input : (input instanceof URL ? input.href : null));
                const abs = url ? new URL(url, location.href).href : null;
                const method = ((init && init.method) || (isRequest ? input.method : 'GET')).toUpperCase();
                if (!abs || !hosts.test(abs) || method === 'GET' || method === 'HEAD') return original.apply(this, arguments);
                let text;
                if (init && 'body' in init) text = asText(init.body);
                else if (isRequest) text = input.clone().text().then(t => [t, null]);
                else text = Promise.resolve([null, null]);
                if (!text) {
                  post({ type: 'note', text: 'fetch ' + method + ' with a body it cannot relay, left to the WebView' });
                  return original.apply(this, arguments);
                }
                const headers = {};
                if (isRequest) input.headers.forEach((v, k) => { headers[k] = v; });
                new Headers((init && init.headers) || {}).forEach((v, k) => { headers[k] = v; });
                return text.then(([body, type]) => new Promise((resolve, reject) => {
                  if (type && !Object.keys(headers).some(k => k.toLowerCase() === 'content-type')) headers['content-type'] = type;
                  const id = ++seq;
                  pending.set(id, { resolve, reject, url: abs });
                  post({ id, doc, url: abs, method, headers, body, page: location.href });
                }));
              };
              // XMLHttpRequest is not relayed: told, so a sign-in that still fails says why.
              const open = XMLHttpRequest.prototype.open;
              XMLHttpRequest.prototype.open = function (method, url) {
                try {
                  const abs = new URL(url, location.href).href;
                  const m = String(method).toUpperCase();
                  if (hosts.test(abs) && m !== 'GET' && m !== 'HEAD') post({ type: 'note', text: 'XHR ' + m + ' ' + abs.split('?')[0] + ' left to the WebView' });
                } catch (e) { /* ignored */ }
                return open.apply(this, arguments);
              };
            })();
        """.trimIndent()
    }
}

/** Closes the whole HTTP response once Chromium has finished with the body it was handed. */
private class ClosingBody(stream: InputStream, private val response: Response) : FilterInputStream(stream) {
    override fun close() {
        runCatching { super.close() }
        runCatching { response.close() }
    }
}
