package com.elipair.spacestudyship.study.exploration.constant;

public enum NodeType {
    PLANET,
    REGION;

    /** DB 컬럼/JSON 직렬화용 소문자 표현 ("planet" / "region"). */
    public String value() {
        return name().toLowerCase();
    }

    public static NodeType from(String value) {
        return NodeType.valueOf(value.toUpperCase());
    }
}
