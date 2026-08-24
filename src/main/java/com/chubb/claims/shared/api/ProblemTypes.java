package com.chubb.claims.shared.api;

public final class ProblemTypes {

    public static final String PREFIX = "urn:chubb:claims:problem:";

    public static final String VALIDATION = PREFIX + "validation";
    public static final String INTERNAL = PREFIX + "internal";
    public static final String CONFLICT = PREFIX + "conflict";

    private ProblemTypes() {
    }

    public static String urn(String suffix) {
        return PREFIX + suffix;
    }
}
