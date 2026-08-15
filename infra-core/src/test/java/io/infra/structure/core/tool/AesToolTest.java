package io.infra.structure.core.tool;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesToolTest {

    @Test
    void generateKey_shouldReturnBase64Key() {
        String key = AesTool.generateKey();
        assertThat(key).isNotBlank();
        assertThat(AesTool.decrypt(AesTool.encrypt("data", key), key)).isEqualTo("data");
    }

    @Test
    void generateKey_withExplicitBits_shouldWorkFor128And256() {
        assertThat(AesTool.generateKey(128)).isNotBlank();
        assertThat(AesTool.generateKey(256)).isNotBlank();
    }

    @Test
    void encryptThenDecrypt_shouldRestorePlainText() {
        String key = AesTool.generateKey();
        String cipher = AesTool.encrypt("hello世界, userId=42", key);
        assertThat(cipher).isNotEqualTo("hello世界, userId=42");
        assertThat(AesTool.decrypt(cipher, key)).isEqualTo("hello世界, userId=42");
    }

    @Test
    void encrypt_shouldBeNonDeterministic_sinceIvIsRandom() {
        String key = AesTool.generateKey();
        String plain = "same text";
        assertThat(AesTool.encrypt(plain, key)).isNotEqualTo(AesTool.encrypt(plain, key));
    }

    @Test
    void decrypt_withWrongKey_shouldThrow() {
        String cipher = AesTool.encrypt("secret", AesTool.generateKey());
        assertThatThrownBy(() -> AesTool.decrypt(cipher, AesTool.generateKey()))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void decrypt_withTamperedCipher_shouldThrow() {
        String key = AesTool.generateKey();
        String cipher = AesTool.encrypt("secret", key);
        String tampered = (cipher.charAt(0) == 'A' ? "B" : "A") + cipher.substring(1);
        assertThatThrownBy(() -> AesTool.decrypt(tampered, key))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void decrypt_withInvalidBase64Key_shouldThrow() {
        String cipher = AesTool.encrypt("secret", AesTool.generateKey());
        assertThatThrownBy(() -> AesTool.decrypt(cipher, "not-a-valid-base64!!!"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decrypt_withTooShortCipher_shouldThrow() {
        assertThatThrownBy(() -> AesTool.decrypt("AQIDBA==", AesTool.generateKey()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void roundTrip_shouldSupportEmptyAndLongStrings() {
        String key = AesTool.generateKey();
        String longText = "敏感字段".repeat(1000);
        assertThat(AesTool.decrypt(AesTool.encrypt("", key), key)).isEmpty();
        assertThat(AesTool.decrypt(AesTool.encrypt(longText, key), key)).isEqualTo(longText);
    }
}