package com.starcon.master.locator;

import java.security.GeneralSecurityException;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public final class LocationSecurity {

    private final static String ALGORITM = "Blowfish";
    private final static String KEY = "2356a3a42ba5781f80a72dad3f90aeee8ba93c7637aaf218a8b8c18c";

    public static byte[] encrypt(String key, String plainText) throws GeneralSecurityException {

        SecretKey secret_key = new SecretKeySpec(key.getBytes(), ALGORITM);

        Cipher cipher = Cipher.getInstance(ALGORITM);
        cipher.init(Cipher.ENCRYPT_MODE, secret_key);

        return cipher.doFinal(plainText.getBytes());
    }

    public static String decrypt(String key, byte[] encryptedText) throws GeneralSecurityException {

        SecretKey secret_key = new SecretKeySpec(key.getBytes(), ALGORITM);

        Cipher cipher = Cipher.getInstance(ALGORITM);
        cipher.init(Cipher.DECRYPT_MODE, secret_key);

        byte[] decrypted = cipher.doFinal(encryptedText);

        return new String(decrypted);
    }

    public static String byteArrayToHexString(byte[] bytes)
    {
        StringBuilder buffer = new StringBuilder();
        for (byte aByte : bytes) {
            if (((int) aByte & 0xff) < 0x10)
                buffer.append("0");
            buffer.append(Long.toString((int) aByte & 0xff, 16));
        }
        return buffer.toString();
    }

    public static byte[] hexStringToByteArray(String str)
    {
        int i = 0;
        byte[] results = new byte[str.length() / 2];
        for (int k = 0; k < str.length();)
        {
            results[i] = (byte)(Character.digit(str.charAt(k++), 16) << 4);
            results[i] += (byte)(Character.digit(str.charAt(k++), 16));
            i++;
        }
        return results;
    }
}
