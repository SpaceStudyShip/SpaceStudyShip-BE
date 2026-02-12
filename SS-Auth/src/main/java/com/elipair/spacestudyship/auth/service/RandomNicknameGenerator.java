package com.elipair.spacestudyship.auth.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
public class RandomNicknameGenerator {

    private static final String[] ADJECTIVES = {
            "열정적인", "집중하는", "똑똑한", "부지런한", "꾸준한",
            "노력하는", "성실한", "호기심많은", "창의적인", "도전하는",
            "침착한", "끈기있는", "영리한", "탐구하는", "빛나는"
    };
    private static final String[] NOUNS = {
            "우주인", "탐험가", "연구원", "조종사", "항해사",
            "과학자", "발명가", "천문학자", "개척자", "모험가",
            "학자", "수학자", "탐사대원", "공학자", "선장"
    };
    private static final int NICKNAME_SUFFIX_BOUND = 10_000;

    public String generate() {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        String adjective = ADJECTIVES[random.nextInt(ADJECTIVES.length)];
        String noun = NOUNS[random.nextInt(NOUNS.length)];
        int suffixNumber = random.nextInt(NICKNAME_SUFFIX_BOUND);

        return adjective + noun + suffixNumber;
    }
}
