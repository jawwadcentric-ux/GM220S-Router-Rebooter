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
        catch (IOException e) { in = c.getErrorStream(); if (in == null) throw e; }
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
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Safari/537.36");
        c.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
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
                if (eq > 0) jar.put(piece.substring(0,eq).trim(), piece.substring(eq+1).trim());
            }
        }
        Map<String,List<String>> h = c.getHeaderFields();
        for (Map.Entry<String,List<String>> e : h.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase("Set-Cookie")) {
                for (String s : e.getValue()) {
                    String pair = s.split(";", 2)[0];
                    int eq = pair.indexOf('=');
                    if (eq > 0) jar.put(pair.substring(0,eq).trim(), pair.substring(eq+1).trim());
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

    private static Response request(String url, String method, String body, String cookie, String base, boolean followRedirects) throws Exception {
        HttpURLConnection c = open(url, method, cookie, followRedirects);
        if ("POST".equals(method)) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            c.setRequestProperty("Origin", base);
            c.setRequestProperty("Referer", base + "/");
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream os = c.getOutputStream()) { os.write(bytes); }
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
        return find(html,
                "var\\s+session_token\\s*=\\s*[\\\"']([^\\\"']+)",
                "session_token\\s*=\\s*[\\\"']([^\\\"']+)",
                "name=[\\\"']?_SESSION_TOKEN[\\\"']?[^>]*value=[\\\"']([^\\\"']+)",
                "_SESSION_TOKEN[^0-9A-Za-z_-]{0,100}([0-9A-Za-z_-]+)");
    }

    private static boolean looksLoggedIn(String html, String location) {
        String x = html == null ? "" : html.toLowerCase(Locale.US);
        String l = location == null ? "" : location.toLowerCase(Locale.US);
        return x.contains("logout")
                || x.contains("start.ghtml")
                || x.contains("device information")
                || l.contains("start.ghtml")
                || l.contains("getpage.gch");
    }

    private static Result attempt(String base, String user, String pass, boolean doReboot, int passwordMode) {
        try {
            if (!base.startsWith("http://") && !base.startsWith("https://")) base = "http://" + base;
            while (base.endsWith("/")) base = base.substring(0, base.length()-1);

            String cookie = "_TESTCOOKIESUPPORT=1";

            // Exact form observed on this GM220-S V9.0.10P1T1:
            // frashnum=
            // action=login
            // Frm_Logintoken=0 (or current token)
            // username=<user>
            // Password=<password>
            Response loginPage = request(base + "/", "GET", null, cookie, base, true);
            cookie = loginPage.cookie;

            String token = find(loginPage.body,
                    "name=[\\\"']?Frm_Logintoken[\\\"']?[^>]*value=[\\\"']([^\\\"']*)",
                    "Frm_Logintoken[^0-9]{0,100}([0-9]+)");
            if (token == null || token.isEmpty()) token = "0";

            String passwordToSend;
            if (passwordMode == 0) {
                passwordToSend = pass; // exact browser-observed form first
            } else if (passwordMode == 1) {
                passwordToSend = sha256(pass);
            } else {
                String rnd = String.valueOf(10000000 + new Random().nextInt(90000000));
                passwordToSend = sha256(pass + rnd);
            }

            String body = "frashnum=" +
                    "&action=login" +
                    "&Frm_Logintoken=" + enc(token) +
                    "&username=" + enc(user) +
                    "&Password=" + enc(passwordToSend);

            Response login = request(base + "/", "POST", body, cookie, base, false);
            cookie = login.cookie;

            String loginBody = login.body;
            String location = login.location;

            // Follow login redirect manually so cookies are retained.
            if (login.code >= 300 && login.code < 400 && location != null && !location.isEmpty()) {
                URL target = new URL(new URL(base + "/"), location);
                Response redirected = request(target.toString(), "GET", null, cookie, base, true);
                cookie = redirected.cookie;
                loginBody = redirected.body;
                location = target.toString();
            }

            // If response itself is minimal, verify with start.ghtml.
            boolean loggedIn = looksLoggedIn(loginBody, location);
            if (!loggedIn) {
                try {
                    Response start = request(base + "/start.ghtml", "GET", null, cookie, base, true);
                    cookie = start.cookie;
                    loggedIn = looksLoggedIn(start.body, "/start.ghtml");
                    if (loggedIn) loginBody = start.body;
                } catch (Exception ignored) {}
            }

            if (!loggedIn) {
                return new Result(false, "Router rejected login. Exact GM220-S form was sent (frashnum/action/Frm_Logintoken/username/Password).");
            }

            String rebootUrl = base + "/getpage.gch?pid=1002&nextpage=manager_dev_conf_t.gch";
            Response rebootPage = request(rebootUrl, "GET", null, cookie, base, true);
            cookie = rebootPage.cookie;

            String session = sessionToken(rebootPage.body);
            if (session == null) {
                try {
                    Response template = request(base + "/template.gch?pid=1002&nextpage=manager_dev_conf_t.gch", "GET", null, cookie, base, true);
                    cookie = template.cookie;
                    session = sessionToken(template.body);
                } catch (Exception ignored) {}
            }

            if (session == null) {
                // Login is now proven; this diagnostic is much more useful than saying login failed.
                return new Result(false, "LOGIN OK, but reboot _SESSION_TOKEN was not found. Next we need the browser Network payload for the Reboot button.");
            }

            if (!doReboot) {
                return new Result(true, "Connection successful. Login verified and reboot token found.");
            }

            String rebootBody =
                    "IF_ACTION=devrestart" +
                    "&IF_ERRORSTR=SUCC" +
                    "&IF_ERRORPARAM=SUCC" +
                    "&IF_ERRORTYPE=-1" +
                    "&flag=1" +
                    "&_SESSION_TOKEN=" + enc(session);

            try {
                request(rebootUrl, "POST", rebootBody, cookie, base, false);
            } catch (Exception ignored) {
                // Router dropping the connection is normal once reboot begins.
            }
            return new Result(true, "Reboot command sent successfully.");

        } catch (Exception e) {
            return new Result(false, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    public static Result test(String base, String user, String pass) {
        Result plain = attempt(base, user, pass, false, 0);
        if (plain.ok || plain.message.startsWith("LOGIN OK")) return plain;

        Result sha = attempt(base, user, pass, false, 1);
        if (sha.ok || sha.message.startsWith("LOGIN OK")) return sha;

        Result legacy = attempt(base, user, pass, false, 2);
        if (legacy.ok || legacy.message.startsWith("LOGIN OK")) return legacy;

        return new Result(false,
                "Plain: " + plain.message +
                " / SHA256: " + sha.message +
                " / Legacy SHA256(pass+random): " + legacy.message);
    }

    public static Result reboot(String base, String user, String pass) {
        Result plain = attempt(base, user, pass, true, 0);
        if (plain.ok || plain.message.startsWith("LOGIN OK")) return plain;

        Result sha = attempt(base, user, pass, true, 1);
        if (sha.ok || sha.message.startsWith("LOGIN OK")) return sha;

        Result legacy = attempt(base, user, pass, true, 2);
        if (legacy.ok || legacy.message.startsWith("LOGIN OK")) return legacy;

        return new Result(false,
                "Plain: " + plain.message +
                " / SHA256: " + sha.message +
                " / Legacy: " + legacy.message);
    }
}
