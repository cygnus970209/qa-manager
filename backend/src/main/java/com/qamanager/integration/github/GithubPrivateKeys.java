package com.qamanager.integration.github;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * GitHub App private key PEM 파서.
 *
 * Manifest conversion 이 돌려주는 PEM 은 PKCS#1 ("BEGIN RSA PRIVATE KEY") 형식인데
 * JDK KeyFactory 는 PKCS#8 만 읽는다. 외부 의존성(BouncyCastle) 없이
 * PKCS#1 DER 을 PKCS#8 구조로 감싸서 변환한다.
 */
final class GithubPrivateKeys {

    private GithubPrivateKeys() {}

    static PrivateKey parse(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new GithubApiException("GitHub App private key 가 비어있음");
        }
        boolean pkcs1 = pem.contains("BEGIN RSA PRIVATE KEY");
        String body = pem
            .replaceAll("-----BEGIN [A-Z ]+-----", "")
            .replaceAll("-----END [A-Z ]+-----", "")
            .replaceAll("\\s", "");
        try {
            byte[] der = Base64.getDecoder().decode(body);
            byte[] pkcs8 = pkcs1 ? wrapPkcs1(der) : der;
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new GithubApiException("GitHub App private key 파싱 실패", e);
        }
    }

    /** PKCS#1 → PKCS#8: SEQUENCE { INTEGER 0, SEQUENCE { OID rsaEncryption, NULL }, OCTET STRING { pkcs1 } } */
    private static byte[] wrapPkcs1(byte[] pkcs1) {
        byte[] version = {0x02, 0x01, 0x00};
        byte[] rsaAlgId = {0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86,
            (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00};
        byte[] keyOctet = der(0x04, pkcs1);
        return der(0x30, concat(version, rsaAlgId, keyOctet));
    }

    private static byte[] der(int tag, byte[] content) {
        byte[] len = derLength(content.length);
        byte[] out = new byte[1 + len.length + content.length];
        out[0] = (byte) tag;
        System.arraycopy(len, 0, out, 1, len.length);
        System.arraycopy(content, 0, out, 1 + len.length, content.length);
        return out;
    }

    private static byte[] derLength(int n) {
        if (n < 0x80) return new byte[]{(byte) n};
        if (n <= 0xFF) return new byte[]{(byte) 0x81, (byte) n};
        if (n <= 0xFFFF) return new byte[]{(byte) 0x82, (byte) (n >> 8), (byte) n};
        return new byte[]{(byte) 0x83, (byte) (n >> 16), (byte) (n >> 8), (byte) n};
    }

    private static byte[] concat(byte[]... parts) {
        int total = 0;
        for (byte[] p : parts) total += p.length;
        byte[] out = new byte[total];
        int pos = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, pos, p.length);
            pos += p.length;
        }
        return out;
    }
}
