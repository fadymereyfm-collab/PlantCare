package com.example.plantcare;

import at.favre.lib.crypto.bcrypt.BCrypt;

/**
 * Password hashing utility using BCrypt.
 *
 * BCrypt automatically handles salt generation internally —
 * each call to hash() produces a unique hash even for the same password.
 * This means passwords CANNOT be compared via SQL equality;
 * use verify() instead.
 *
 * Cost factor 12 is a good balance between security and performance
 * on mobile devices (~200-400ms on modern phones).
 */
public class PasswordUtils {

    private static final int BCRYPT_COST = 12;

    /**
     * Hashes a plaintext password using BCrypt with automatic salt.
     * Returns a BCrypt hash string (60 chars, starts with "$2a$").
     *
     * @param password the plaintext password
     * @return BCrypt hash string
     */
    public static String hash(String password) {
        return BCrypt.withDefaults().hashToString(BCRYPT_COST, password.toCharArray());
    }

    /**
     * Verifies a plaintext password against a stored BCrypt hash.
     *
     * @param password   the plaintext password to check
     * @param storedHash the BCrypt hash from the database
     * @return true if the password matches
     */
    public static boolean verify(String password, String storedHash) {
        if (password == null || storedHash == null) return false;

        // Handle legacy SHA-256 hashes (64-char hex strings without "$" prefix).
        // If the stored hash is a legacy format, compare using SHA-256 for
        // backward compatibility, then the caller should re-hash with BCrypt.
        if (!storedHash.startsWith("$") && storedHash.length() == 64) {
            return legacySha256Hash(password).equals(storedHash);
        }

        BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(), storedHash);
        return result.verified;
    }

    /**
     * Checks if a stored hash is in the old SHA-256 format and needs
     * to be upgraded to BCrypt.
     */
    public static boolean needsUpgrade(String storedHash) {
        if (storedHash == null) return false;
        // Legacy hashes are 64-char hex without "$" prefix
        return !storedHash.startsWith("$") && storedHash.length() == 64;
    }

    /**
     * Legacy SHA-256 hash for backward compatibility only.
     * DO NOT use for new passwords.
     */
    private static String legacySha256Hash(String password) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
