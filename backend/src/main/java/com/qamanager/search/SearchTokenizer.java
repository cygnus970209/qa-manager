package com.qamanager.search;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 검색 토큰화 — 인덱스와 질의가 같은 규칙을 써야 한다.
 *
 * MariaDB InnoDB FULLTEXT 는 공백/구두점으로 나눈 "단어" 단위라 한국어처럼 붙여 쓰는 글에서 중간 글자("카드결제" 의 "결제")를
 * 못 찾는다. 그래서 단어마다 연속 두 글자(바이그램)를 토큰으로 만들어 넣고, 질의도 같은 방식으로 만들어
 * BOOLEAN MODE 에서 모든 토큰이 있어야(+) 하게 한다 = 단어 안 부분 일치와 같은 효과.
 *
 * 토큰 뒤에 '0' 을 붙여 3글자로 만드는 이유:
 *  - InnoDB 기본 최소 토큰 길이(innodb_ft_min_token_size=3) 미만은 색인되지 않는다 → DB 옵션을 바꾸지 않아도 되게
 *  - "on", "to" 같은 영문 불용어(기본 stopword 목록)와 겹치지 않게
 * 한 글자 단어는 글자 + "00" 로 만든다.
 *
 * 띄어쓰기: 질의 "결제 오류" 는 두 단어의 토큰이 모두 있어야 한다(AND). 색인은 띄어쓰기 경계의 글자 쌍
 * ("카드 결제" 의 "드결")도 토큰으로 넣어, 붙여 쓴 질의("카드결제")로 띄어 쓴 문서를 찾고 그 반대도 되게 한다.
 */
public final class SearchTokenizer {

    private SearchTokenizer() {}

    /** 소문자 + 공백 정리 (LIKE·발췌용 텍스트) */
    public static String normalize(String text) {
        if (text == null) return "";
        return text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    /** 색인용 토큰 (중복 유지 — 빈도가 관련도에 반영) */
    public static List<String> tokens(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        String lower = text.toLowerCase(Locale.ROOT);
        for (String word : lower.split("[^\\p{L}\\p{N}]+")) {
            if (word.isEmpty()) continue;
            int[] cps = word.codePoints().toArray();
            if (cps.length == 1) {
                out.add(new String(cps, 0, 1) + "00");
                continue;
            }
            for (int i = 0; i + 1 < cps.length; i++) {
                out.add(new String(cps, i, 2) + "0");
            }
        }
        return out;
    }

    /** 색인용 토큰 문자열 = 단어 토큰 + 공백(띄어쓰기)으로만 나뉜 인접 단어 사이의 글자 쌍 */
    public static String indexText(String text) {
        List<String> out = new ArrayList<>(tokens(text));
        if (text != null) {
            String lower = text.toLowerCase(Locale.ROOT);
            String[] chunks = lower.trim().split("\\s+");
            for (int i = 0; i + 1 < chunks.length; i++) {
                int[] a = chunks[i].codePoints().toArray();
                int[] b = chunks[i + 1].codePoints().toArray();
                if (a.length == 0 || b.length == 0) continue;
                int last = a[a.length - 1];
                int first = b[0];
                if (Character.isLetterOrDigit(last) && Character.isLetterOrDigit(first)) {
                    out.add(new String(new int[]{last, first}, 0, 2) + "0");
                }
            }
        }
        return String.join(" ", out);
    }

    /**
     * BOOLEAN MODE 질의문. 모든 토큰이 있어야 한다(+). 토큰은 글자·숫자·'0' 뿐이라 연산자 충돌이 없다.
     * 한 글자 단어는 색인 토큰("xy0")과 만날 수 없으므로 뺀다. 남는 토큰이 없으면 null (호출자가 LIKE 로 폴백).
     */
    public static String booleanQuery(String query) {
        Set<String> uniq = new LinkedHashSet<>();
        for (String t : tokens(query)) {
            if (t.endsWith("00")) continue; // 한 글자 단어
            uniq.add(t);
        }
        if (uniq.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (String t : uniq) {
            if (sb.length() > 0) sb.append(' ');
            sb.append('+').append(t);
        }
        return sb.toString();
    }

    /** 질의를 단어로 (발췌·강조용) */
    public static List<String> words(String query) {
        List<String> out = new ArrayList<>();
        if (query == null) return out;
        for (String w : query.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (!w.isEmpty()) out.add(w);
        }
        return out;
    }

    public static String clip(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
