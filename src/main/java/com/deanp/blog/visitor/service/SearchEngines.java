package com.deanp.blog.visitor.service;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Referer 호스트가 검색 결과 화면이면 검색 엔진 이름을 알려 준다.
 * 같은 회사라도 포털 첫 화면·블로그·메일처럼 검색이 아닌 서비스(www.naver.com, blog.naver.com, mail.google.com)는 검색 유입으로 보지 않는다.
 */
final class SearchEngines {

    /** google.com, www.google.co.kr, images.google.de 같은 Google 검색 도메인과 안드로이드 Google 앱 */
    private static final Pattern GOOGLE = Pattern.compile(
            "(www\\.|images\\.)?google\\.(com|[a-z]{2}|co\\.[a-z]{2}|com\\.[a-z]{2})|com\\.google\\.android\\.googlequicksearchbox");
    private static final Map<String, String> HOSTS = Map.ofEntries(
            Map.entry("search.naver.com", "Naver"),
            Map.entry("m.search.naver.com", "Naver"),
            Map.entry("search.daum.net", "Daum"),
            Map.entry("m.search.daum.net", "Daum"),
            Map.entry("www.bing.com", "Bing"),
            Map.entry("bing.com", "Bing"),
            Map.entry("cn.bing.com", "Bing"),
            Map.entry("search.yahoo.com", "Yahoo"),
            Map.entry("search.yahoo.co.jp", "Yahoo"),
            Map.entry("duckduckgo.com", "DuckDuckGo"),
            Map.entry("www.baidu.com", "Baidu"),
            Map.entry("m.baidu.com", "Baidu"),
            Map.entry("yandex.ru", "Yandex"),
            Map.entry("yandex.com", "Yandex"),
            Map.entry("ya.ru", "Yandex"),
            Map.entry("www.ecosia.org", "Ecosia"),
            Map.entry("search.zum.com", "Zum"),
            Map.entry("m.search.zum.com", "Zum"),
            Map.entry("search.brave.com", "Brave"));

    private SearchEngines() {
    }

    /**
     * @param host Referer 호스트 또는 null
     * @return 검색 엔진 이름, 검색 유입이 아니면 null
     */
    static String fromHost(String host) {
        if (host == null) {
            return null;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        if (GOOGLE.matcher(normalized).matches()) {
            return "Google";
        }
        return HOSTS.get(normalized);
    }
}
