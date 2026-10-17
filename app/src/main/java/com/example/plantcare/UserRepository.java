package com.example.plantcare;

import android.content.Context;
import android.content.SharedPreferences;

public class UserRepository {

    private static UserRepository INSTANCE;
    private final com.example.plantcare.data.repository.AuthRepository authRepo;
    private final SharedPreferences prefs;

    private UserRepository(Context ctx) {
        authRepo = com.example.plantcare.data.repository.AuthRepository.getInstance(ctx);
        prefs = SecurePrefsHelper.INSTANCE.get(ctx);
    }

    public static synchronized UserRepository get(Context ctx) {
        if (INSTANCE == null) INSTANCE = new UserRepository(ctx.getApplicationContext());
        return INSTANCE;
    }

    public long signUp(String email, String name, String password) {
        User existing = authRepo.getUserByEmailBlocking(email);
        if (existing != null) return -1L;

        String hashed = PasswordUtils.hash(password);
        User u = new User(email, name, hashed);
        authRepo.insertUserBlocking(u);
        setCurrentUser(email);
        return 1L;
    }

    /**
     * Sign in using BCrypt verification.
     * Also handles transparent upgrade from legacy SHA-256 hashes:
     * if the stored hash is legacy and the password matches,
     * re-hashes with BCrypt automatically.
     */
    public Long signIn(String email, String password) {
        User u = authRepo.getUserByEmailBlocking(email);
        if (u == null || u.passwordHash == null) return null;

        boolean matches = PasswordUtils.verify(password, u.passwordHash);
        if (!matches) return null;

        // Transparent upgrade: if the hash is legacy SHA-256, re-hash with BCrypt
        if (PasswordUtils.needsUpgrade(u.passwordHash)) {
            String newHash = PasswordUtils.hash(password);
            authRepo.updateUserPasswordBlocking(email, newHash);
        }

        setCurrentUser(email);
        return 1L;
    }

    public void setCurrentUser(String email) {
        prefs.edit().putString(SecurePrefsHelper.KEY_USER_EMAIL, email).apply();
    }

    public String getCurrentUser() {
        return prefs.getString(SecurePrefsHelper.KEY_USER_EMAIL, null);
    }

    public void logout() {
        prefs.edit().remove(SecurePrefsHelper.KEY_USER_EMAIL).apply();
    }

    // جلب اسم المستخدم حسب البريد
    public String getUserNameByEmail(String email) {
        User user = authRepo.getUserByEmailBlocking(email);
        if (user != null) return user.name;
        return "";
    }
}
