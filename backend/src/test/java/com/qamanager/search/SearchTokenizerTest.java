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
    void 색인은_띄어쓰기_경계의_글자_쌍도_넣어_붙여쓴_질의와_맞는다() {
        String indexed = SearchTokenizer.indexText("카드 결제 화면");
        assertThat(indexed.split(" ")).contains("드결0", "제화0");
        // 붙여 쓴 질의 → 띄어 쓴 문서, 띄어 쓴 질의 → 붙여 쓴 문서 모두 토큰이 포함된다
        assertThat(List.of(indexed.split(" "))).containsAll(List.of(SearchTokenizer.booleanQuery("카드결제").replace("+", "").split(" ")));
        assertThat(List.of(SearchTokenizer.indexText("카드결제").split(" "))).containsAll(List.of(SearchTokenizer.booleanQuery("카드 결제").replace("+", "").split(" ")));
        // 구두점으로 나뉜 곳은 이어 붙이지 않는다
        assertThat(SearchTokenizer.indexText("결제(토스)").split(" ")).doesNotContain("제토0");
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
