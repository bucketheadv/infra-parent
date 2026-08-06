package io.infra.structure.sso.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant

class AudienceValidatorTest {

    private val validator = AudienceValidator("order-service")

    @Test
    fun acceptsTokenForConfiguredAudience() {
        assertThat(validator.validate(jwt(listOf("order-service"))).hasErrors()).isFalse()
    }

    @Test
    fun rejectsTokenForAnotherAudience() {
        assertThat(validator.validate(jwt(listOf("billing-service"))).hasErrors()).isTrue()
    }

    private fun jwt(audience: List<String>): Jwt {
        val issuedAt = Instant.now()
        return Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plusSeconds(300))
            .subject("user-1")
            .audience(audience)
            .build()
    }
}
