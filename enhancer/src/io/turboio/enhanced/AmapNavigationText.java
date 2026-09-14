package io.turboio.enhanced;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** Pure-Java selection and sanitization for text exposed by AMap's Android notification. */
final class AmapNavigationText {
    static final int MAX_CONTENT_CODE_POINTS = 480;

    static final class Candidate {
        final String content;
        Candidate(String content) { this.content = content; }
    }

    private AmapNavigationText() {}

    static Candidate select(String title, String text, String bigText, List<String> textLines,
                            String subText, String summaryText, String category, boolean ongoing) {
        List<String> values = new ArrayList<>();
        add(values, bigText);
        add(values, text);
        if (textLines != null) for (String line : textLines) add(values, line);
        add(values, subText);
        add(values, summaryText);
        add(values, title);

        LinkedHashSet<String> unique = new LinkedHashSet<>(values);
        String selected = "";
        for (String value : unique) {
            if (looksLikeGuidance(value)) { selected = value; break; }
        }
        boolean navigationContext = "navigation".equalsIgnoreCase(normalize(category));
        if (!navigationContext && ongoing) {
            for (String value : unique) if (normalize(value).contains("导航")) { navigationContext = true; break; }
        }
        if (selected.isEmpty() && navigationContext) {
            for (String value : unique) {
                if (!isGeneric(value)) { selected = value; break; }
            }
        }
        if (selected.isEmpty()) return null;
        return new Candidate(shorten(selected, MAX_CONTENT_CODE_POINTS));
    }

    static String normalize(String value) {
        if (value == null) return "";
        return value.replace('\u00a0', ' ').replaceAll("[\\s\\p{Z}]+", " ").trim();
    }

    static boolean looksLikeGuidance(String value) {
        String text = normalize(value).toLowerCase(Locale.ROOT);
        if (text.isEmpty()) return false;
        String[] directions = {"左转", "右转", "直行", "掉头", "调头", "靠左", "靠右", "驶入", "驶出", "行驶", "进入", "离开", "出口", "入口", "匝道", "环岛", "到达", "目的地", "导航", "红绿灯", "路口"};
        for (String direction : directions) if (text.contains(direction)) return true;
        return text.matches(".*\\d+(?:\\.\\d+)?\\s*(?:米|公里|m|km)(?:后|处|到|，|,| ).*");
    }

    private static void add(List<String> values, String value) {
        String normalized = normalize(value);
        if (!normalized.isEmpty()) values.add(normalized);
    }

    private static boolean isGeneric(String value) {
        String text = normalize(value);
        return text.isEmpty() || "高德地图".equals(text) || "正在使用定位服务".equals(text);
    }

    private static String shorten(String value, int max) {
        if (value.codePointCount(0, value.length()) <= max) return value;
        return value.substring(0, value.offsetByCodePoints(0, max - 1)) + "…";
    }
}
