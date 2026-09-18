package dev.sellout.platform.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * OIDC settings every service needs. Missing values fail boot with the property name in the
 * message.
 *
 * @param issuer the token issuer, e.g. {@code http://localhost:8180/realms/sellout}
 * @param audience the audience claim every access token must carry, e.g. {@code sellout-api}
 */
@Validated
@ConfigurationProperties("sellout.oidc")
public record SelloutOidcProperties(
    @NotBlank(message = "sellout.oidc.issuer must be set (SELLOUT_OIDC_ISSUER)") String issuer,
    @NotBlank(message = "sellout.oidc.audience must be set (SELLOUT_OIDC_AUDIENCE)")
        String audience) {}
