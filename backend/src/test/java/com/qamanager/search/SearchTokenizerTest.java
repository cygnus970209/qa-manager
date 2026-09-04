package com.qamanager.search;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SearchTokenizerTest {

    @Test
    void 바이그램_토큰은_세글자로_만들고_한글자_단어는_00을_붙인다() {
        assertThat(SearchTokenizer.tokens("카드결제 앱"))
            .containsExactly("카드0", "드결0", "결제0", "앱00");
    }

    @Test
    void 영문은_소문자로_바꾸고_구두점은_경계로_본다() {
        assertThat(SearchTokenizer.tokens("Payment-Error"))
            .containsExactly("pa0", "ay0", "ym0", "me0", "en0", "nt0", "er0", "rr0", "ro0", "or0");
    }

    @Test
    void 질의는_모든_토큰을_필수로_만들고_한글자_단어는_뺀다() {
        assertThat(SearchTokenizer.booleanQuery("결제 앱")).isEqualTo("+결제0");
        assertThat(SearchTokenizer.booleanQuery("앱")).isNull();
        assertThat(SearchTokenizer.booleanQuery("카드결제 오류")).isEqualTo("+카드0 +드결0 +결제0 +오류0");
    }

    @Test
    void 단어_안의_부분_문자열도_같은_토큰을_만들어_매칭된다() {
        List<String> indexed = SearchTokenizer.tokens("카드결제 화면");
        List<String> query = SearchTokenizer.tokens("결제");
        assertThat(indexed).containsAll(query);
    }

    @Test
    void 발췌는_질의어_주변을_잘라낸다() {
        String body = "앞부분 ".repeat(20) + "여기에 결제 오류가 납니다 " + "뒷부분 ".repeat(60);
        String s = SearchService.snippet(body, List.of("결제"));
        assertThat(s).contains("결제 오류").startsWith("…").endsWith("…");
        assertThat(SearchService.snippet("짧은 본문", List.of("없음"))).isEqualTo("짧은 본문");
    }
}
