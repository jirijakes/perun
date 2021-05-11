package chacha;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;

public class ChaCha20Poly1305 {

    private static final String ENCRYPT_ALGO = "ChaCha20-Poly1305";
    private static final int NONCE_LEN = 12; // 96 bits, 12 bytes

    public byte[] encrypt(SecretKey key, byte[] nonce, byte[] aad, byte[] pText) throws Exception {

        Cipher cipher = Cipher.getInstance(ENCRYPT_ALGO);

        // IV, initialization value with nonce
        IvParameterSpec iv = new IvParameterSpec(nonce);

        cipher.init(Cipher.ENCRYPT_MODE, key, iv);
	cipher.updateAAD(aad);

        byte[] encryptedText = cipher.doFinal(pText);

        // append nonce to the encrypted text
        byte[] output = ByteBuffer.allocate(encryptedText.length) // + NONCE_LEN)
                .put(encryptedText)
                // .put(nonce)
                .array();

        return output;
    }

    public byte[] decrypt(SecretKey key, byte[] nonce, byte[] aad, byte[] cText) throws Exception {

        // ByteBuffer bb = ByteBuffer.wrap(cText);

        // split cText to get the appended nonce
        // byte[] encryptedText = new byte[cText.length - NONCE_LEN];
        // byte[] nonce = new byte[NONCE_LEN];
        // bb.get(encryptedText);
        // bb.get(nonce);

        Cipher cipher = Cipher.getInstance(ENCRYPT_ALGO);

        IvParameterSpec iv = new IvParameterSpec(nonce);

        cipher.init(Cipher.DECRYPT_MODE, key, iv);
	cipher.updateAAD(aad);
	
        // decrypted text
        byte[] output = cipher.doFinal(cText);

        return output;

    }

    // 96-bit nonce (12 bytes)
    private static byte[] getNonce() {
        byte[] newNonce = new byte[12];
        new SecureRandom().nextBytes(newNonce);
        return newNonce;
    }

}
