package org.example.project2.domain.auth.service;

import org.example.project2.domain.auth.service.token.OpaqueTokenGenerator;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class OpaqueTokenGeneratorTest {

    @Test
    void generatedTokenContainsThirtyTwoRandomBytes() {
        String token = new OpaqueTokenGenerator().generate();

        assertThat(Base64.getUrlDecoder().decode(token)).hasSize(32);
    }

    @Test
    void generatedTokensAreDifferent() {
        OpaqueTokenGenerator generator = new OpaqueTokenGenerator();

        assertThat(generator.generate()).isNotEqualTo(generator.generate());
    }
}
