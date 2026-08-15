package io.infra.structure.core.tool;

import lombok.experimental.UtilityClass;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM 对称加解密工具。
 *
 * <p>密文采用 Base64(IV + ciphertext) 拼接格式，密钥为 Base64 编码的字节串。
 * 使用 GCM 认证加密模式，可同时保证机密性与完整性。
 *
 * @author sven
 * Created on 2026/8/15
 */
@UtilityClass
public class AesTool {

    /** 算法名称。 */
    private static final String ALGORITHM = "AES";

    /** 加密变换：AES-GCM，无填充。 */
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    /** GCM 模式 IV 长度（字节）。 */
    private static final int IV_LENGTH = 12;

    /** GCM 认证标签长度（位）。 */
    private static final int TAG_BITS = 128;

    /** 默认密钥长度（位）。 */
    private static final int DEFAULT_KEY_BITS = 256;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * 生成 AES 密钥，返回 Base64 编码的密钥字符串。
     * @return Base64 编码的 256 位密钥
     */
    public String generateKey() {
        return generateKey(DEFAULT_KEY_BITS);
    }

    /**
     * 生成指定长度的 AES 密钥，返回 Base64 编码的密钥字符串。
     * @param keyBits 密钥长度（位），支持 128/192/256
     * @return Base64 编码的密钥
     */
    public String generateKey(int keyBits) {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
            keyGenerator.init(keyBits, SECURE_RANDOM);
            SecretKey secretKey = keyGenerator.generateKey();
            return Base64.getEncoder().encodeToString(secretKey.getEncoded());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES 密钥生成失败", e);
        }
    }

    /**
     * AES-GCM 加密。
     * @param plainText 明文
     * @param base64Key Base64 编码的 AES 密钥
     * @return Base64(IV + 密文)
     */
    public String encrypt(String plainText, String base64Key) {
        try {
            byte[] key = decodeKey(base64Key);
            byte[] iv = new byte[IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, ALGORITHM), new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(cipherText, 0, payload, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES 加密失败", e);
        }
    }

    /**
     * AES-GCM 解密。
     * @param cipherText 由 {@link #encrypt} 生成的 Base64(IV + 密文)
     * @param base64Key Base64 编码的 AES 密钥
     * @return 明文
     */
    public String decrypt(String cipherText, String base64Key) {
        try {
            byte[] key = decodeKey(base64Key);
            byte[] payload = Base64.getDecoder().decode(cipherText);
            if (payload.length <= IV_LENGTH) {
                throw new IllegalArgumentException("密文格式非法：缺少 IV");
            }
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(payload, 0, iv, 0, IV_LENGTH);
            byte[] encrypted = new byte[payload.length - IV_LENGTH];
            System.arraycopy(payload, IV_LENGTH, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, ALGORITHM), new GCMParameterSpec(TAG_BITS, iv));
            byte[] plainBytes = cipher.doFinal(encrypted);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES 解密失败", e);
        }
    }

    private byte[] decodeKey(String base64Key) {
        try {
            return Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("密钥不是合法的 Base64 编码", e);
        }
    }
}