package com.hp;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class RegexTest {
    public static final Pattern LANGUAGE_PATTERN = Pattern.compile("learn\\s(java|php|go)");

    public static final Pattern CHINA_PHONE_NUMBER_PATTERN = Pattern.compile("(1[3-9]\\d{9})|(0\\d{2,3}-\\d{7,8})");

    private final Pattern REGION_PHONE_NUMBER_PATTERN = Pattern.compile("^0\\d{2,3}-\\d{7,8}$");

    @ParameterizedTest
    @ValueSource(strings = {
            "010-12345678",
            "020-9999999",
            "0755-7654321"
    })
    void test_phone_number(String number) {
        assertTrue(REGION_PHONE_NUMBER_PATTERN.matcher(number).matches());
    }

    @Test
    void test_brackets() {
        assertTrue(LANGUAGE_PATTERN.matcher("learn java").matches());
        // 全字符串匹配
        assertFalse(LANGUAGE_PATTERN.matcher("learn javaScript").matches());
        String longLanguage = """
                learn java;
                learn javaScript;
                learn go;
                learn golang;
                learn python;
                """;
        log.debug("longLanguage is {}", longLanguage);
        Matcher matcher = LANGUAGE_PATTERN.matcher(longLanguage);
        // 查找子串匹配
        assertTrue(matcher.find());
        assertEquals(0, matcher.start());
        log.debug("groupCount = {}", matcher.groupCount());
        assertEquals(0, matcher.start());
    }

    @Test
    void test_find_substring() {
        String longText = "我的电话是13812345678，你的电话是13987654321，公司电话是010-87654321，紧急联系人是15811112222";
        Matcher matcher = CHINA_PHONE_NUMBER_PATTERN.matcher(longText);
        ArrayList<String> allMatches = new ArrayList<>();
        while (matcher.find()) {
            allMatches.add(matcher.group());
        }
        log.debug("allMatches are {}", allMatches);
        assertEquals(4, allMatches.size());
        assertLinesMatch(List.of("13812345678", "13987654321", "010-87654321", "15811112222"), allMatches);
    }
}
