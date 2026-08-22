package com.hyper.market.api;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class XiaomiApiSigner {
    private static final String SIGN_ALPHABET =
            "leDTKhmg4MafVFp73x6djvLiHn2G9XPruARBwS0q1OzNJt8WobZsQcYyEICk5U-_";
    private static final String SIGN_KEY_PREFIX = "good luck!";

    private XiaomiApiSigner() { }

    public static String signedGet(String baseUrl, Map<String, String> parameters) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>(parameters);
        String nonce = System.currentTimeMillis() + "_" + ThreadLocalRandom.current().nextInt(1000);
        String unsignedUrl = appendQuery(baseUrl, values);
        values.put("_n", nonce);
        values.put("_s", makeSignature(unsignedUrl, nonce));
        values.put("_v", "1");
        return appendQuery(baseUrl, values);
    }

    public static String signedPost(String baseUrl, Map<String, String> parameters, String responseSign) {
        if (responseSign == null || responseSign.isEmpty()) {
            return baseUrl;
        }
        String canonical = postCanonical(baseUrl, parameters, responseSign);
        String digest = sha1(canonical);
        String separator = querySeparator(baseUrl);
        return baseUrl + separator + "signature=" + urlEncode(digest);
    }

    public static SignedPostRequest signedPost(String baseUrl, Map<String, String> parameters) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>(parameters);
        String nonce = System.currentTimeMillis() + "_" + ThreadLocalRandom.current().nextInt(1000);
        String unsignedUrl = appendQuery(baseUrl, values);
        values.put("_n", nonce);
        values.put("_s", makeSignature(unsignedUrl, nonce));
        values.put("_v", "1");
        return new SignedPostRequest(baseUrl, values);
    }

    public static String formBody(Map<String, String> parameters) {
        List<String> pairs = new ArrayList<>();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            pairs.add(urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()));
        }
        return String.join("&", pairs);
    }

    public static final class SignedPostRequest {
        private final String url;
        private final Map<String, String> parameters;

        private SignedPostRequest(String url, Map<String, String> parameters) {
            this.url = url;
            this.parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
        }

        public String getUrl() { return url; }
        public Map<String, String> getParameters() { return parameters; }
    }

    private static String makeSignature(String url, String nonce) {
        String canonical = canonicalText(url, nonce);
        long timestamp = parseTimestamp(nonce);
        String algorithm = algorithmFor(timestamp);
        byte[] digest = hmac(algorithm, SIGN_KEY_PREFIX + nonce, canonical);
        return encodeCustom(digest);
    }

    private static String canonicalText(String url, String nonce) {
        String[] parts = url.split("\\?", 2);
        List<String> pairs = queryPairs(parts.length == 2 ? parts[1] : "");
        pairs.add("_n=" + nonce);
        pairs.add("_p=" + signedKeys(pairs));
        return pathForSignature(parts[0]) + "\n" + joinSignedPairs(pairs);
    }

    private static String pathForSignature(String urlWithoutQuery) {
        int schemeEnd = urlWithoutQuery.indexOf("://");
        int pathStart = schemeEnd < 0 ? urlWithoutQuery.indexOf('/')
                : urlWithoutQuery.indexOf('/', schemeEnd + 3);
        return pathStart < 0 ? "/" : urlWithoutQuery.substring(pathStart);
    }

    private static List<String> queryPairs(String query) {
        List<String> result = new ArrayList<>();
        if (query.isEmpty()) {
            return result;
        }
        for (String pair : query.split("&")) {
            if (!pair.isEmpty() && !pair.startsWith("_n=")
                    && !pair.startsWith("_s=") && !pair.startsWith("_v=")) {
                result.add(urlDecode(pair));
            }
        }
        return result;
    }

    private static String urlDecode(String value) {
        try {
            return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException exception) {
            throw new IllegalStateException("UTF-8 is unavailable", exception);
        }
    }

    private static String signedKeys(List<String> pairs) {
        List<String> names = new ArrayList<>();
        for (String pair : pairs) {
            int equals = pair.indexOf('=');
            if (equals > 0 && SIGNED_KEYS.contains(pair.substring(0, equals))) {
                names.add(pair.substring(0, equals));
            }
        }
        return String.join(";", names);
    }

    private static String joinSignedPairs(List<String> pairs) {
        pairs.sort(String.CASE_INSENSITIVE_ORDER);
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < pairs.size(); index++) {
            String pair = pairs.get(index);
            int equals = pair.indexOf('=');
            if (equals > 0 && SIGNED_KEYS.contains(pair.substring(0, equals))) {
                result.append(pair, 0, equals).append('&');
                int insertAt = result.length();
                for (int valueIndex = equals + 1; valueIndex < pair.length(); valueIndex++) {
                    result.insert(insertAt, pair.charAt(valueIndex));
                }
                if (index < pairs.size() - 1) {
                    result.append('=');
                }
            } else if (index == pairs.size() - 1 && result.toString().endsWith("=")) {
                result.deleteCharAt(result.length() - 1);
            }
        }
        return result.toString();
    }

    private static String postCanonical(String url, Map<String, String> parameters, String responseSign) {
        String path = url.substring(url.indexOf("://") + 3);
        int slash = path.indexOf('/');
        path = slash >= 0 ? path.substring(slash) : "/";
        StringBuilder result = new StringBuilder("POST&").append(path);
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                result.append('&').append(entry.getKey()).append('=').append(entry.getValue());
            }
        }
        return result.append('&').append(responseSign).toString();
    }

    private static long parseTimestamp(String nonce) {
        int separator = nonce.indexOf('_');
        return Long.parseLong(nonce.substring(0, separator));
    }

    private static String algorithmFor(long timestamp) {
        return switch ((int) (timestamp % 4)) {
            case 0 -> "HmacMD5";
            case 1 -> "HmacSHA256";
            case 2 -> "HmacSHA1";
            default -> "HmacSHA384";
        };
    }

    private static byte[] hmac(String algorithm, String key, String value) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), algorithm));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("HMAC is unavailable: " + algorithm, exception);
        }
    }

    private static String encodeCustom(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index + 2 < bytes.length; index += 3) {
            appendBlock(result, bytes[index], bytes[index + 1], bytes[index + 2]);
        }
        appendTail(result, bytes);
        return result.toString();
    }

    private static void appendBlock(StringBuilder result, byte first, byte second, byte third) {
        int a = first & 255;
        int b = second & 255;
        int c = third & 255;
        result.append(SIGN_ALPHABET.charAt((a >> 2) & 63));
        result.append(SIGN_ALPHABET.charAt(((a << 4) | (b >> 4)) & 63));
        result.append(SIGN_ALPHABET.charAt(((b << 2) | (c >> 6)) & 63));
        result.append(SIGN_ALPHABET.charAt(c & 63));
    }

    private static void appendTail(StringBuilder result, byte[] bytes) {
        int index = bytes.length - bytes.length % 3;
        int left = bytes.length - index;
        if (left == 1) {
            int value = bytes[index] & 255;
            result.append(SIGN_ALPHABET.charAt(value >> 2));
            result.append(SIGN_ALPHABET.charAt((value << 4) & 63));
        } else if (left == 2) {
            int first = bytes[index] & 255;
            int second = bytes[index + 1] & 255;
            result.append(SIGN_ALPHABET.charAt(first >> 2));
            result.append(SIGN_ALPHABET.charAt(((first << 4) | (second >> 4)) & 63));
            result.append(SIGN_ALPHABET.charAt((second << 2) & 63));
        }
    }

    private static String sha1(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-1")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-1 is unavailable", exception);
        }
    }

    private static String appendQuery(String baseUrl, Map<String, String> parameters) {
        if (parameters.isEmpty()) {
            return baseUrl;
        }
        String separator = querySeparator(baseUrl);
        List<String> pairs = new ArrayList<>();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            pairs.add(urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()));
        }
        return baseUrl + separator + String.join("&", pairs);
    }

    private static String querySeparator(String baseUrl) {
        if (baseUrl.endsWith("?")) {
            return "";
        }
        return baseUrl.contains("?") ? "&" : "?";
    }

    private static String urlEncode(String value) {
        StringBuilder encoded = new StringBuilder(value.length());
        for (byte item : value.getBytes(StandardCharsets.UTF_8)) {
            int unsigned = item & 0xff;
            if (isUnreserved(unsigned)) {
                encoded.append((char) unsigned);
            } else {
                encoded.append('%');
                encoded.append(String.format(java.util.Locale.ROOT, "%02X", unsigned));
            }
        }
        return encoded.toString();
    }

    private static boolean isUnreserved(int value) {
        return value >= 'a' && value <= 'z'
                || value >= 'A' && value <= 'Z'
                || value >= '0' && value <= '9'
                || value == '-' || value == '.' || value == '_' || value == '~';
    }

    private static final java.util.Set<String> SIGNED_KEYS = SetFactory.create();

    private static final class SetFactory {
        private static java.util.Set<String> create() {
            java.util.Set<String> keys = new java.util.HashSet<>();
            Collections.addAll(keys, "activedTimeInterval", "ad", "adExchangeFlag", "adFlag", "apkChannel",
                    "appId", "bottomTab", "carrier", "clientId", "co", "count", "cpuArchitecture",
                    "device", "deviceType", "digestParams", "downloadingAppInfo", "excludedAppIds",
                    "ext_apkChannel", "ext_marketType", "flag", "folderName", "get", "gpId", "h5", "id",
                    "imei", "installDay", "installError", "instance_id", "international", "keyword", "la",
                    "launchDay", "lo", "marketVersion", "miuiBigVersionCode", "miuiBigVersionName", "model",
                    "_n", "needLruCache", "network", "newUser", "oldApkHash", "oldVersionCode", "os",
                    "packageName", "packageNameList", "page", "pageConfigVersion", "pageRef", "pageSize",
                    "pageTag", "params", "pos", "posChain", "previousAppIds", "proxyTimeout", "query", "reason",
                    "recentInstallCompleteAppInfo", "ref", "refPosition", "refresh", "refs", "resolution", "ro",
                    "sco", "sdk", "searchScope", "shouldNativeInterceptRequest", "sid", "sla", "sourcePackage",
                    "stamp", "targetVersionCode", "type", "update", "versionCode", "webResVersion", "zoneSuffix",
                    "aiQuery");
            return Collections.unmodifiableSet(keys);
        }
    }
}
