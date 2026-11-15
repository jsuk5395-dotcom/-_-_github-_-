import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * 앗차! 주변 안전 알림 중계 서버 (의존성 없는 단일 파일, JDK 17+).
 *
 * 실행:  java backend/hazard-relay/HazardRelayServer.java [port]
 * 선택:  환경변수 RELAY_TOKEN 이 있으면 모든 /v1 요청에 X-Relay-Token 헤더를 요구한다.
 *
 * 설계 원칙
 * - 저장소는 메모리뿐이다. 재시작하면 모두 사라지고 디스크에 기록하지 않는다.
 * - 필드 화이트리스트: 차량번호·차종·신고자 식별자 등 허용되지 않은 키가 있으면 거절한다.
 * - 서버 시계 기준 TTL 최대 90초. 클라이언트가 보낸 만료 시각을 믿지 않는다.
 * - 수신자는 약 1km 격자 셀만 보내고 정밀 위치는 보내지 않는다. 정밀 거리·방향 판단은 수신 기기에서 한다.
 * - 응답에는 절대 시각 대신 ageMillis/remainingMillis 를 내려 기기 간 시계 차이에 영향받지 않게 한다.
 * - 같은 IP의 발행은 30초에 1회로 제한한다(허위·폭주 제보 완화).
 */
public final class HazardRelayServer {
    static final long TTL_MILLIS = 90_000L;
    static final long PUBLISH_INTERVAL_MILLIS = 30_000L;
    static final int MAX_BODY_BYTES = 2_048;
    static final int MAX_STORED_PER_KIND = 1_000;
    static final int MAX_QUERY_CELLS = 25;
    static final Pattern ALERT_ID = Pattern.compile("[A-Za-z0-9-]{8,64}");
    static final Pattern CELL = Pattern.compile("-?\\d{1,5}:-?\\d{1,5}");

    static final Set<String> VEHICLE_KEYS = Set.of("alertId", "latitude", "longitude", "headingDegrees", "level", "isDemo");
    static final Set<String> PEDESTRIAN_KEYS = Set.of("alertId", "latitude", "longitude", "isDemo");

    record StoredAlert(Map<String, Object> fields, String cell, long receivedAt, long expiresAt) {}

    static final class AlertStore {
        final Map<String, StoredAlert> byId = new ConcurrentHashMap<>();
        final Map<String, Long> lastPublishByClient = new ConcurrentHashMap<>();

        synchronized void prune(long now) {
            byId.values().removeIf(a -> a.expiresAt() <= now);
            lastPublishByClient.values().removeIf(t -> now - t > PUBLISH_INTERVAL_MILLIS);
        }
    }

    private final AlertStore vehicleStore = new AlertStore();
    private final AlertStore pedestrianStore = new AlertStore();
    private final String token = System.getenv("RELAY_TOKEN");

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8787;
        new HazardRelayServer().start(port);
    }

    void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health", ex -> respond(ex, 200, "{\"ok\":true}"));
        server.createContext("/v1/vehicle-alerts", ex -> handle(ex, vehicleStore, VEHICLE_KEYS, true));
        server.createContext("/v1/pedestrian-alerts", ex -> handle(ex, pedestrianStore, PEDESTRIAN_KEYS, false));
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        System.out.println("HazardRelayServer listening on :" + port + (token != null ? " (token required)" : ""));
    }

    private void handle(HttpExchange ex, AlertStore store, Set<String> allowedKeys, boolean vehicle) throws IOException {
        try {
            if (token != null && !token.equals(ex.getRequestHeaders().getFirst("X-Relay-Token"))) {
                respond(ex, 401, error("unauthorized"));
                return;
            }
            long now = System.currentTimeMillis();
            store.prune(now);
            switch (ex.getRequestMethod()) {
                case "POST" -> publish(ex, store, allowedKeys, vehicle, now);
                case "GET" -> query(ex, store, now);
                default -> respond(ex, 405, error("method not allowed"));
            }
        } catch (RuntimeException e) {
            respond(ex, 400, error("bad request"));
        } finally {
            ex.close();
        }
    }

    private void publish(HttpExchange ex, AlertStore store, Set<String> allowedKeys, boolean vehicle, long now) throws IOException {
        byte[] body = readLimited(ex.getRequestBody());
        if (body == null) {
            respond(ex, 413, error("body too large"));
            return;
        }
        Map<String, Object> f = MiniJson.parseObject(new String(body, StandardCharsets.UTF_8));
        String invalid = validate(f, allowedKeys, vehicle);
        if (invalid != null) {
            respond(ex, 400, error(invalid));
            return;
        }
        String client = ex.getRemoteAddress().getAddress().getHostAddress();
        synchronized (store) {
            Long last = store.lastPublishByClient.get(client);
            if (last != null && now - last < PUBLISH_INTERVAL_MILLIS) {
                respond(ex, 429, error("rate limited"));
                return;
            }
            String id = (String) f.get("alertId");
            if (store.byId.containsKey(id)) {
                respond(ex, 409, error("duplicate"));
                return;
            }
            if (store.byId.size() >= MAX_STORED_PER_KIND) {
                respond(ex, 503, error("store full"));
                return;
            }
            double lat = ((Number) f.get("latitude")).doubleValue();
            double lon = ((Number) f.get("longitude")).doubleValue();
            store.byId.put(id, new StoredAlert(f, cellOf(lat, lon), now, now + TTL_MILLIS));
            store.lastPublishByClient.put(client, now);
        }
        respond(ex, 201, "{\"accepted\":true,\"ttlMillis\":" + TTL_MILLIS + "}");
    }

    private void query(HttpExchange ex, AlertStore store, long now) throws IOException {
        String cellsParam = queryParam(ex.getRequestURI().getRawQuery(), "cells");
        if (cellsParam == null || cellsParam.isEmpty()) {
            respond(ex, 400, error("cells required"));
            return;
        }
        Set<String> cells = new HashSet<>();
        for (String c : cellsParam.split(",")) {
            if (!CELL.matcher(c).matches()) {
                respond(ex, 400, error("invalid cell"));
                return;
            }
            cells.add(c);
        }
        if (cells.size() > MAX_QUERY_CELLS) {
            respond(ex, 400, error("too many cells"));
            return;
        }
        StringBuilder sb = new StringBuilder("{\"alerts\":[");
        boolean first = true;
        for (Iterator<StoredAlert> it = store.byId.values().iterator(); it.hasNext(); ) {
            StoredAlert a = it.next();
            if (!cells.contains(a.cell()) || a.expiresAt() <= now) continue;
            Map<String, Object> out = new LinkedHashMap<>(a.fields());
            out.put("ageMillis", now - a.receivedAt());
            out.put("remainingMillis", a.expiresAt() - now);
            if (!first) sb.append(',');
            sb.append(MiniJson.write(out));
            first = false;
        }
        sb.append("]}");
        respond(ex, 200, sb.toString());
    }

    static String validate(Map<String, Object> f, Set<String> allowedKeys, boolean vehicle) {
        for (String key : f.keySet()) {
            if (!allowedKeys.contains(key)) return "field not allowed: " + key;
        }
        if (!(f.get("alertId") instanceof String id) || !ALERT_ID.matcher(id).matches()) return "invalid alertId";
        if (!(f.get("latitude") instanceof Number lat) || !inRange(lat.doubleValue(), -90, 90)) return "invalid latitude";
        if (!(f.get("longitude") instanceof Number lon) || !inRange(lon.doubleValue(), -180, 180)) return "invalid longitude";
        if (!(f.get("isDemo") instanceof Boolean)) return "invalid isDemo";
        if (vehicle) {
            if (!"STRONG".equals(f.get("level"))) return "level must be STRONG";
            Object heading = f.get("headingDegrees");
            if (heading != null && (!(heading instanceof Number h) || !inRange(h.doubleValue(), 0, 360))) {
                return "invalid headingDegrees";
            }
        }
        return null;
    }

    static boolean inRange(double v, double min, double max) {
        return Double.isFinite(v) && v >= min && v <= max;
    }

    /** 0.01도 격자(약 1.1km). 앱의 RelayCells 와 같은 규칙이어야 한다. */
    static String cellOf(double lat, double lon) {
        return (long) Math.floor(lat * 100.0) + ":" + (long) Math.floor(lon * 100.0);
    }

    static String queryParam(String rawQuery, String name) {
        if (rawQuery == null) return null;
        for (String part : rawQuery.split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0 && part.substring(0, eq).equals(name)) {
                return java.net.URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    static byte[] readLimited(InputStream in) throws IOException {
        byte[] data = in.readNBytes(MAX_BODY_BYTES + 1);
        return data.length > MAX_BODY_BYTES ? null : data;
    }

    static String error(String message) {
        return "{\"error\":" + MiniJson.quote(message) + "}";
    }

    static void respond(HttpExchange ex, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    /** 평면 JSON 객체(문자열·숫자·불리언·null 값)만 지원하는 최소 파서/작성기. */
    static final class MiniJson {
        private final String s;
        private int i;

        private MiniJson(String s) {
            this.s = s;
        }

        static Map<String, Object> parseObject(String text) {
            MiniJson p = new MiniJson(text);
            p.ws();
            Map<String, Object> map = new LinkedHashMap<>();
            p.expect('{');
            p.ws();
            if (p.peek() == '}') {
                p.i++;
            } else {
                while (true) {
                    p.ws();
                    String key = p.string();
                    p.ws();
                    p.expect(':');
                    p.ws();
                    if (map.put(key, p.value()) != null) throw new IllegalArgumentException("duplicate key");
                    p.ws();
                    char c = p.next();
                    if (c == '}') break;
                    if (c != ',') throw new IllegalArgumentException("expected ,");
                }
            }
            p.ws();
            if (p.i != p.s.length()) throw new IllegalArgumentException("trailing data");
            return map;
        }

        private Object value() {
            char c = peek();
            if (c == '"') return string();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            if (start == i) throw new IllegalArgumentException("unsupported value");
            return Double.parseDouble(s.substring(start, i));
        }

        private String string() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    char e = next();
                    switch (e) {
                        case '"', '\\', '/' -> sb.append(e);
                        case 'n' -> sb.append('\n');
                        case 't' -> sb.append('\t');
                        case 'r' -> sb.append('\r');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'u' -> {
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            i += 4;
                        }
                        default -> throw new IllegalArgumentException("bad escape");
                    }
                } else {
                    sb.append(c);
                }
            }
        }

        private void ws() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        private char peek() {
            if (i >= s.length()) throw new IllegalArgumentException("eof");
            return s.charAt(i);
        }

        private char next() {
            char c = peek();
            i++;
            return c;
        }

        private void expect(char c) {
            if (next() != c) throw new IllegalArgumentException("expected " + c);
        }

        static String quote(String v) {
            StringBuilder sb = new StringBuilder("\"");
            for (char c : v.toCharArray()) {
                switch (c) {
                    case '"' -> sb.append("\\\"");
                    case '\\' -> sb.append("\\\\");
                    case '\n' -> sb.append("\\n");
                    case '\r' -> sb.append("\\r");
                    case '\t' -> sb.append("\\t");
                    default -> {
                        if (c < 0x20) sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                        else sb.append(c);
                    }
                }
            }
            return sb.append('"').toString();
        }

        static String write(Map<String, Object> map) {
            List<String> parts = new ArrayList<>();
            for (Map.Entry<String, Object> e : map.entrySet()) {
                Object v = e.getValue();
                String json;
                if (v == null) json = "null";
                else if (v instanceof String str) json = quote(str);
                else if (v instanceof Boolean b) json = b.toString();
                else if (v instanceof Long l) json = l.toString();
                else json = Double.toString(((Number) v).doubleValue());
                parts.add(quote(e.getKey()) + ":" + json);
            }
            return "{" + String.join(",", parts) + "}";
        }
    }
}
