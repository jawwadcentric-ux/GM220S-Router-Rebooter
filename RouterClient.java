package com.metawebdesigner.gm220rebooter;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;

public class RouterClient {
    public static class Result {
        public final boolean ok;
        public final String message;
        Result(boolean ok, String message) { this.ok = ok; this.message = message; }
    }

    private static class Response {
        int code;
        String body;
        String cookie;
        String location;
    }

    private static String read(HttpURLConnection c) throws Exception {
        InputStream in;
        try { in = c.getInputStream(); }
        catch (IOException e) {
            in = c.getErrorStream();
            if (in == null) throw e;
        }

        if (in == null) return "";

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toString("UTF-8");
    }

    private static String sha256(String s) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder b = new StringBuilder();
        for (byte x : d) b.append(String.format(Locale.US, "%02x", x));
        return b.toString();
    }

    private static String enc(String s) throws Exception {
        return URLEncoder.encode(s == null ? "" : s, "UTF-8");
    }

    private static String find(String html, String... regexes) {
        if (html == null) return null;
        for (String r : regexes) {
            Matcher m = Pattern.compile(r, Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html);
            if (m.find()) return m.group(1);
        }
        return null;
    }

    private static HttpURLConnection open(String url, String method, String cookie, boolean redirects) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(12000);
        c.setReadTimeout(12000);
        c.setInstanceFollowRedirects(redirects);
        c.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Safari/537.36");
        c.setRequestProperty("Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        c.setRequestProperty("Accept-Language", "en-US,en;q=0.8");
        c.setRequestProperty("Connection", "keep-alive");
        if (cookie != null && !cookie.isEmpty()) c.setRequestProperty("Cookie", cookie);
        return c;
    }

    private static String mergeCookies(HttpURLConnection c, String old) {
        LinkedHashMap<String,String> jar = new LinkedHashMap<>();

        if (old != null && !old.isEmpty()) {
            for (String piece : old.split(";\\s*")) {
                int eq = piece.indexOf('=');
                if (eq > 0) {
                    jar.put(piece.substring(0,eq).trim(), piece.substring(eq+1).trim());
                }
            }
        }

        Map<String,List<String>> h = c.getHeaderFields();
        for (Map.Entry<String,List<String>> e : h.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase("Set-Cookie")) {
                for (String s : e.getValue()) {
                    String pair = s.split(";", 2)[0];
                    int eq = pair.indexOf('=');
                    if (eq > 0) {
                        jar.put(pair.substring(0,eq).trim(), pair.substring(eq+1).trim());
                    }
                }
            }
        }

        StringBuilder b = new StringBuilder();
        for (Map.Entry<String,String> e : jar.entrySet()) {
            if (b.length() > 0) b.append("; ");
            b.append(e.getKey()).append('=').append(e.getValue());
        }
        return b.toString();
    }

    private static Response request(
            String url,
            String method,
            String body,
            String cookie,
            String origin,
            String referer,
            boolean followRedirects
    ) throws Exception {

        HttpURLConnection c = open(url, method, cookie, followRedirects);

        if ("POST".equals(method)) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            if (origin != null) c.setRequestProperty("Origin", origin);
            if (referer != null) c.setRequestProperty("Referer", referer);

            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(bytes.length);

            try (OutputStream os = c.getOutputStream()) {
                os.write(bytes);
            }
        }

        Response r = new Response();
        r.code = c.getResponseCode();
        r.body = read(c);
        r.cookie = mergeCookies(c, cookie);
        r.location = c.getHeaderField("Location");
        c.disconnect();
        return r;
    }

    private static String sessionToken(String html) {
        if (html == null) return null;

        String token = find(html,
                // hidden/input forms
                "name\\s*=\\s*[\"']?_SESSION_TOKEN[\"']?[^>]*value\\s*=\\s*[\"']?([0-9A-Za-z_-]+)",
                "id\\s*=\\s*[\"']?_SESSION_TOKEN[\"']?[^>]*value\\s*=\\s*[\"']?([0-9A-Za-z_-]+)",
                "value\\s*=\\s*[\"']?([0-9A-Za-z_-]+)[\"']?[^>]*(?:name|id)\\s*=\\s*[\"']?_SESSION_TOKEN[\"']?",

                // JS variable forms
                "(?:var\\s+)?session_token\\s*=\\s*[\"']?([0-9A-Za-z_-]+)",
                "(?:var\\s+)?_SESSION_TOKEN\\s*=\\s*[\"']?([0-9A-Za-z_-]+)",
                "session_token\\s*:\\s*[\"']?([0-9A-Za-z_-]+)",
                "_SESSION_TOKEN\\s*:\\s*[\"']?([0-9A-Za-z_-]+)",

                // generic near-token fallbacks
                "_SESSION_TOKEN[^0-9A-Za-z_-]{0,120}([0-9]{8,})",
                "session_token[^0-9A-Za-z_-]{0,120}([0-9]{8,})"
        );

        if (token != null && token.length() >= 8) return token;
        return null;
    }

    private static boolean looksLoggedIn(String html, String location) {
        String x = html == null ? "" : html.toLowerCase(Locale.US);
        String l = location == null ? "" : location.toLowerCase(Locale.US);

        return x.contains("logout")
                || x.contains("device information")
                || x.contains("start.ghtml")
                || l.contains("start.ghtml")
                || l.contains("getpage.gch");
    }

    private static class LoginSession {
        boolean ok;
        String message;
        String base;
        String cookie;
    }

    private static LoginSession login(String base, String user, String pass, int passwordMode) {
        LoginSession out = new LoginSession();

        try {
            if (!base.startsWith("http://") && !base.startsWith("https://")) {
                base = "http://" + base;
            }
            while (base.endsWith("/")) {
                base = base.substring(0, base.length() - 1);
            }

            out.base = base;

            String cookie = "_TESTCOOKIESUPPORT=1";

            Response loginPage = request(
                    base + "/",
                    "GET",
                    null,
                    cookie,
                    null,
                    null,
                    true
            );
            cookie = loginPage.cookie;

            String token = find(loginPage.body,
                    "name\\s*=\\s*[\"']?Frm_Logintoken[\"']?[^>]*value\\s*=\\s*[\"']([^\"']*)",
                    "name\\s*=\\s*[\"']?Frm_Logintoken[\"']?[^>]*value\\s*=\\s*([0-9]+)",
                    "Frm_Logintoken[^0-9]{0,100}([0-9]+)"
            );

            // Actual browser capture on this GM220-S showed 0.
            if (token == null || token.isEmpty()) token = "0";

            String passwordToSend;
            if (passwordMode == 0) {
                passwordToSend = pass;
            } else if (passwordMode == 1) {
                passwordToSend = sha256(pass);
            } else {
                String rnd = String.valueOf(10000000 + new Random().nextInt(90000000));
                passwordToSend = sha256(pass + rnd);
            }

            // Exact observed login form:
            // frashnum=
            // action=login
            // Frm_Logintoken=0
            // username=...
            // Password=...
            String body =
                    "frashnum=" +
                    "&action=login" +
                    "&Frm_Logintoken=" + enc(token) +
                    "&username=" + enc(user) +
                    "&Password=" + enc(passwordToSend);

            Response login = request(
                    base + "/",
                    "POST",
                    body,
                    cookie,
                    base,
                    base + "/",
                    false
            );
            cookie = login.cookie;

            String loginBody = login.body;
            String location = login.location;

            if (login.code >= 300 && login.code < 400
                    && location != null && !location.isEmpty()) {

                URL target = new URL(new URL(base + "/"), location);

                Response redirected = request(
                        target.toString(),
                        "GET",
                        null,
                        cookie,
                        null,
                        null,
                        true
                );

                cookie = redirected.cookie;
                loginBody = redirected.body;
                location = target.toString();
            }

            boolean loggedIn = looksLoggedIn(loginBody, location);

            if (!loggedIn) {
                try {
                    Response start = request(
                            base + "/start.ghtml",
                            "GET",
                            null,
                            cookie,
                            null,
                            null,
                            true
                    );
                    cookie = start.cookie;
                    loggedIn = looksLoggedIn(start.body, "/start.ghtml");
                } catch (Exception ignored) {}
            }

            out.cookie = cookie;
            out.ok = loggedIn;
            out.message = loggedIn
                    ? "Login successful."
                    : "Router rejected login.";

            return out;

        } catch (Exception e) {
            out.ok = false;
            out.message = e.getClass().getSimpleName() + ": " + e.getMessage();
            return out;
        }
    }

    private static LoginSession loginWithFallbacks(String base, String user, String pass) {
        LoginSession plain = login(base, user, pass, 0);
        if (plain.ok) return plain;

        LoginSession sha = login(base, user, pass, 1);
        if (sha.ok) return sha;

        LoginSession legacy = login(base, user, pass, 2);
        if (legacy.ok) return legacy;

        LoginSession fail = new LoginSession();
        fail.ok = false;
        fail.message =
                "Plain: " + plain.message +
                " / SHA256: " + sha.message +
                " / Legacy: " + legacy.message;
        return fail;
    }

    private static class TokenResult {
        String token;
        String cookie;
        String source;
    }

    private static TokenResult discoverRebootToken(String base, String cookie) {
        TokenResult tr = new TokenResult();
        tr.cookie = cookie;

        String[] urls = new String[] {
                base + "/template.gch?pid=1002&nextpage=manager_dev_conf_t.gch",
                base + "/getpage.gch?pid=1002&nextpage=manager_dev_conf_t.gch",
                base + "/top.gch",
                base + "/start.ghtml",
                base + "/keepAlive.gch"
        };

        for (String url : urls) {
            try {
                Response r = request(
                        url,
                        "GET",
                        null,
                        tr.cookie,
                        null,
                        null,
                        true
                );

                tr.cookie = r.cookie;

                String t = sessionToken(r.body);
                if (t != null) {
                    tr.token = t;
                    tr.source = url;
                    return tr;
                }

            } catch (Exception ignored) {}
        }

        return tr;
    }

    public static Result test(String base, String user, String pass) {
        LoginSession ls = loginWithFallbacks(base, user, pass);

        if (!ls.ok) {
            return new Result(false, "Login failed. " + ls.message);
        }

        TokenResult tr = discoverRebootToken(ls.base, ls.cookie);

        if (tr.token == null) {
            return new Result(
                    false,
                    "LOGIN OK, but reboot session token still was not found in template/getpage/top/start/keepAlive pages."
            );
        }

        return new Result(
                true,
                "Connection successful. Login verified and reboot token found."
        );
    }

    public static Result reboot(String base, String user, String pass) {
        LoginSession ls = loginWithFallbacks(base, user, pass);

        if (!ls.ok) {
            return new Result(false, "Login failed. " + ls.message);
        }

        TokenResult tr = discoverRebootToken(ls.base, ls.cookie);

        if (tr.token == null) {
            return new Result(
                    false,
                    "LOGIN OK, but reboot session token could not be found."
            );
        }

        try {
            String rebootUrl =
                    ls.base + "/getpage.gch?pid=1002&nextpage=manager_dev_conf_t.gch";

            String referer =
                    ls.base + "/template.gch?pid=1002&nextpage=manager_dev_conf_t.gch";

            // EXACT reboot payload captured from your GM220-S browser:
            // IF_ACTION=devrestart
            // IF_ERRORSTR=SUCC
            // IF_ERRORPARAM=SUCC
            // IF_ERRORTYPE=-1281035768
            // flag=1
            // _SESSION_TOKEN=<dynamic token>
            String rebootBody =
                    "IF_ACTION=devrestart" +
                    "&IF_ERRORSTR=SUCC" +
                    "&IF_ERRORPARAM=SUCC" +
                    "&IF_ERRORTYPE=-1281035768" +
                    "&flag=1" +
                    "&_SESSION_TOKEN=" + enc(tr.token);

            try {
                request(
                        rebootUrl,
                        "POST",
                        rebootBody,
                        tr.cookie,
                        ls.base,
                        referer,
                        false
                );
            } catch (Exception ignored) {
                // Normal: connection can drop immediately when reboot begins.
            }

            return new Result(true, "Reboot command sent successfully.");

        } catch (Exception e) {
            return new Result(false, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
