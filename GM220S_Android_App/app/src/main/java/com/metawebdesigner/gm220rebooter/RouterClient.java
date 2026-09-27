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

    private static String read(HttpURLConnection c) throws Exception {
        InputStream in;
        try { in = c.getInputStream(); }
        catch (IOException e) { in = c.getErrorStream(); if (in == null) throw e; }
        if (in == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096]; int n;
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

    private static String get(String url, String cookie, StringBuilder cookieOut) throws Exception {
        HttpURLConnection c = open(url, "GET", cookie, true);
        String text = read(c);
        cookieOut.setLength(0);
        cookieOut.append(mergeCookies(c, cookie));
        c.disconnect();
        return text;
    }

    private static String post(String url, String body, String cookie, StringBuilder cookieOut, String base) throws Exception {
        HttpURLConnection c = open(url, "POST", cookie, false);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        c.setRequestProperty("Origin", base);
        c.setRequestProperty("Referer", base + "/");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream os = c.getOutputStream()) { os.write(bytes); }
        int code = c.getResponseCode();
        String text = read(c);
        String merged = mergeCookies(c, cookie);
        String location = c.getHeaderField("Location");
        c.disconnect();

        // ZTE login commonly responds with HTTP 302 and sets SID on that response.
        if (code >= 300 && code < 400 && location != null && !location.isEmpty()) {
            URL target = new URL(new URL(url), location);
            StringBuilder tmp = new StringBuilder();
            try { text = get(target.toString(), merged, tmp); merged = tmp.toString(); }
            catch (Exception ignored) { }
        }
        cookieOut.setLength(0);
        cookieOut.append(merged);
        return text;
    }

    private static String sessionToken(String html) {
        return find(html,
                "var\\s+session_token\\s*=\\s*[\\\"']([^\\\"']+)",
                "session_token\\s*=\\s*[\\\"']([^\\\"']+)",
                "_SESSION_TOKEN[^0-9A-Za-z_-]{0,80}([0-9A-Za-z_-]+)");
    }

    private static Result attempt(String base, String user, String pass, boolean doReboot, boolean hashPassword) {
        try {
            if (!base.startsWith("http://") && !base.startsWith("https://")) base = "http://" + base;
            while (base.endsWith("/")) base = base.substring(0, base.length()-1);

            String cookie = "_TESTCOOKIESUPPORT=1";
            StringBuilder co = new StringBuilder();
            String loginHtml = get(base + "/", cookie, co);
            cookie = co.toString();

            String token = find(loginHtml,
                    "createHiddenInput\\(\\s*[\\\"']Frm_Logintoken[\\\"']\\s*,\\s*[\\\"']([^\\\"']+)",
                    "name=[\\\"']?Frm_Logintoken[\\\"']?[^>]*value=[\\\"']([^\\\"']+)",
                    "Frm_Logintoken[^0-9]{0,100}([0-9]+)");

            String checkToken = find(loginHtml,
                    "createHiddenInput\\(\\s*[\\\"']Frm_Loginchecktoken[\\\"']\\s*,\\s*[\\\"']([^\\\"']+)",
                    "name=[\\\"']?Frm_Loginchecktoken[\\\"']?[^>]*value=[\\\"']([^\\\"']+)",
                    "Frm_Loginchecktoken[^0-9]{0,120}([0-9]+)");

            if (token == null) return new Result(false, "Frm_Logintoken not found on router login page.");
            if (checkToken == null) return new Result(false, "Frm_Loginchecktoken not found on router login page.");

            String rnd = String.valueOf(10000000 + new Random().nextInt(90000000));
            String pwd = hashPassword ? sha256(pass + rnd) : pass;
            String body = "Frm_Logintoken=" + enc(token) +
                    "&Frm_Loginchecktoken=" + enc(checkToken) +
                    "&Right=&Username=" + enc(user) +
                    "&UserRandomNum=" + enc(rnd) +
                    "&Password=" + enc(pwd) +
                    "&action=login";

            StringBuilder newCookie = new StringBuilder();
            post(base + "/", body, cookie, newCookie, base);
            cookie = newCookie.toString();

            String rebootUrl = base + "/getpage.gch?pid=1002&nextpage=manager_dev_conf_t.gch";
            String rebootHtml = get(rebootUrl, cookie, newCookie);
            cookie = newCookie.toString();
            String session = sessionToken(rebootHtml);

            // Some ZTE firmware exposes the token from template.gch instead of getpage.gch.
            if (session == null) {
                try {
                    String templateUrl = base + "/template.gch?pid=1002&nextpage=manager_dev_conf_t.gch";
                    String templateHtml = get(templateUrl, cookie, newCookie);
                    cookie = newCookie.toString();
                    session = sessionToken(templateHtml);
                } catch (Exception ignored) { }
            }

            if (session == null) {
                return new Result(false, "Login did not produce a reboot session token. Username/password may be wrong or firmware login flow differs.");
            }

            if (!doReboot) return new Result(true, "Connection successful. Router login and reboot token verified.");

            String rebootBody = "IF_ACTION=devrestart&IF_ERRORSTR=SUCC&IF_ERRORPARAM=SUCC&IF_ERRORTYPE=-1&flag=1&_SESSION_TOKEN=" + enc(session);
            try { post(rebootUrl, rebootBody, cookie, new StringBuilder(), base); }
            catch (Exception ignored) { /* Connection drop is normal during reboot */ }
            return new Result(true, "Reboot command sent successfully.");
        } catch (Exception e) {
            return new Result(false, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    public static Result test(String base, String user, String pass) {
        Result a = attempt(base, user, pass, false, true);
        if (a.ok) return a;
        // Kept only for older firmware that submits plain password.
        Result b = attempt(base, user, pass, false, false);
        if (b.ok) return b;
        return new Result(false, a.message + " / Plain-password fallback: " + b.message);
    }

    public static Result reboot(String base, String user, String pass) {
        Result a = attempt(base, user, pass, true, true);
        if (a.ok) return a;
        Result b = attempt(base, user, pass, true, false);
        if (b.ok) return b;
        return new Result(false, a.message + " / Plain-password fallback: " + b.message);
    }
}
