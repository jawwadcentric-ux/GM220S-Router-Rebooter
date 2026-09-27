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
        for (String r : regexes) {
            Matcher m = Pattern.compile(r, Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html);
            if (m.find()) return m.group(1);
        }
        return null;
    }

    private static HttpURLConnection open(String url, String method, String cookie) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(12000);
        c.setReadTimeout(12000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 Android GM220S-Rebooter");
        c.setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*");
        if (cookie != null && !cookie.isEmpty()) c.setRequestProperty("Cookie", cookie);
        return c;
    }

    private static String cookies(HttpURLConnection c, String old) {
        Map<String,List<String>> h = c.getHeaderFields();
        List<String> set = h.get("Set-Cookie");
        if (set == null) set = h.get("set-cookie");
        StringBuilder b = new StringBuilder(old == null ? "" : old);
        if (set != null) for (String s : set) {
            String pair = s.split(";", 2)[0];
            if (b.length() > 0) b.append("; ");
            b.append(pair);
        }
        return b.toString();
    }

    private static String post(String url, String body, String cookie, StringBuilder cookieOut) throws Exception {
        HttpURLConnection c = open(url, "POST", cookie);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream os = c.getOutputStream()) { os.write(bytes); }
        String text = read(c);
        cookieOut.setLength(0);
        cookieOut.append(cookies(c, cookie));
        c.disconnect();
        return text;
    }

    private static Result attempt(String base, String user, String pass, boolean doReboot, boolean hashPassword) {
        try {
            if (!base.startsWith("http://") && !base.startsWith("https://")) base = "http://" + base;
            while (base.endsWith("/")) base = base.substring(0, base.length()-1);

            String cookie = "_TESTCOOKIESUPPORT=1";
            HttpURLConnection g = open(base + "/", "GET", cookie);
            String loginHtml = read(g);
            cookie = cookies(g, cookie);
            g.disconnect();

            String token = find(loginHtml,
                    "name=[\\\"']?Frm_Logintoken[\\\"']?[^>]*value=[\\\"']([^\\\"']+)",
                    "Frm_Logintoken[^0-9]{0,80}([0-9]+)");
            if (token == null) return new Result(false, "Login token not found on router page.");

            String rnd = String.valueOf(10000000 + new Random().nextInt(90000000));
            String pwd = hashPassword ? sha256(pass + rnd) : pass;
            String body = "action=login&Username=" + enc(user) + "&Password=" + enc(pwd) +
                    "&Frm_Logintoken=" + enc(token) + "&UserRandomNum=" + enc(rnd);
            StringBuilder newCookie = new StringBuilder();
            post(base + "/", body, cookie, newCookie);
            cookie = newCookie.toString();

            String rebootUrl = base + "/getpage.gch?pid=1002&nextpage=manager_dev_conf_t.gch";
            HttpURLConnection r = open(rebootUrl, "GET", cookie);
            String rebootHtml = read(r);
            cookie = cookies(r, cookie);
            r.disconnect();

            String session = find(rebootHtml,
                    "_SESSION_TOKEN[^0-9A-Za-z_-]{0,80}([0-9A-Za-z_-]+)",
                    "session_token\\s*=\\s*[\\\"']([^\\\"']+)");
            if (session == null) return new Result(false, "Login failed or reboot session token not found.");

            if (!doReboot) return new Result(true, "Connection successful. Router login and reboot page verified.");

            String rebootBody = "IF_ACTION=devrestart&IF_ERRORSTR=SUCC&IF_ERRORPARAM=SUCC&IF_ERRORTYPE=-1&flag=1&_SESSION_TOKEN=" + enc(session);
            try { post(rebootUrl, rebootBody, cookie, new StringBuilder()); }
            catch (Exception ignored) { /* Connection drop is normal during reboot */ }
            return new Result(true, "Reboot command sent successfully.");
        } catch (Exception e) {
            return new Result(false, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    public static Result test(String base, String user, String pass) {
        Result a = attempt(base, user, pass, false, true);
        if (a.ok) return a;
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
