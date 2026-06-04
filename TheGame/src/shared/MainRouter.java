package shared;

import java.util.LinkedHashMap;
import java.util.Map;

import base.Params;
import base.SubRouter;

public final class MainRouter {

    private final Map<String, SubRouter> routers = new LinkedHashMap<>();

    public void addRouter(String prefix, SubRouter router) {
        String key = normalizePrefix(prefix);
        if (key.isEmpty()) throw new RuntimeException("Empty prefix not allowed");
        if (routers.containsKey(key)) throw new RuntimeException("Prefix already registered: " + key);
        routers.put(key, router);
    }

    public Object route(String path, Params p) {
        String full = normalizePath(path);
        String prefix = extractPrefix(full);
        String subPath = stripPrefix(full);

        SubRouter r = routers.get(prefix);
        if (r == null) throw new RuntimeException("No router for prefix: " + prefix + " (path: " + path + ")");
        return r.route(subPath, p);
    }

    private String normalizePath(String path) {
        if (path == null) return "/";
        String s = path.trim();
        if (s.isEmpty()) return "/";
        if (!s.startsWith("/")) s = "/" + s;
        if (s.length() > 1 && s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private String normalizePrefix(String prefix) {
        if (prefix == null) return "";
        String s = prefix.trim();
        if (s.startsWith("/")) s = s.substring(1);
        if (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private String extractPrefix(String fullPath) {
        if (fullPath.equals("/")) return "";
        int secondSlash = fullPath.indexOf('/', 1);
        if (secondSlash == -1) return fullPath.substring(1);
        return fullPath.substring(1, secondSlash);
    }

    private String stripPrefix(String fullPath) {
        int secondSlash = fullPath.indexOf('/', 1);
        if (secondSlash == -1) return "/";
        return fullPath.substring(secondSlash);
    }
}