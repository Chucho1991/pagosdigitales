package com.femsa.gpf.pagosdigitales;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.femsa.gpf.pagosdigitales.domain.service.SignatureService;

class SignatureServiceTest {

    @Test
    void sha256HexIsDeterministic() {
        SignatureService service = new SignatureService();
        String hash1 = service.sha256Hex("abc123");
        String hash2 = service.sha256Hex("abc123");
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void isValidIgnoresCase() {
        SignatureService service = new SignatureService();
        assertThat(service.isValid("ABC", "abc")).isTrue();
    }
}
