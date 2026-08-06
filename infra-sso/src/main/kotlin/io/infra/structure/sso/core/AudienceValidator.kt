package io.infra.structure.sso.core

import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt

/** 校验 access token 的 audience 是否包含当前资源服务标识。 */
class AudienceValidator(private val audience: String) : OAuth2TokenValidator<Jwt> {

    /**
     * 校验令牌 audience 声明中是否包含当前资源服务器标识。
     *
     * audience 不匹配时返回标准 invalid_token 错误，令牌不会被转换为已认证用户。
     */
    override fun validate(token: Jwt): OAuth2TokenValidatorResult {
        return if (token.audience.contains(audience)) {
            OAuth2TokenValidatorResult.success()
        } else {
            OAuth2TokenValidatorResult.failure(INVALID_AUDIENCE)
        }
    }

    private companion object {
        val INVALID_AUDIENCE = OAuth2Error(
            "invalid_token",
            "The token does not contain the required audience",
            null
        )
    }
}
