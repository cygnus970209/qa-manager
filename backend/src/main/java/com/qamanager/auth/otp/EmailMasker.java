package com.qamanager.auth.otp;

/** 이메일 주소 마스킹(로그/클라이언트 표시용). hong@intocns.com -> ho***@i***.com */
public final class EmailMasker {

    private EmailMasker() {}

    public static String mask(String email) {
        if (email == null || email.isBlank()) return "***";
        int at = email.indexOf('@');
        if (at <= 0) return "***";
        String local = email.substring(0, at);
        String domain = email.substring(at + 1);

        String maskedLocal = local.length() <= 2
            ? local.charAt(0) + "***"
            : local.substring(0, 2) + "***";

        int dot = domain.lastIndexOf('.');
        String tld = dot >= 0 ? domain.substring(dot) : "";
        String maskedDomain = (domain.isEmpty() ? "*" : String.valueOf(domain.charAt(0))) + "***" + tld;

        return maskedLocal + "@" + maskedDomain;
    }
}
