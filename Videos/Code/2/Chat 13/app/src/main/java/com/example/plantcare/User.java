package com.example.plantcare;

import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity
public class User {
    @PrimaryKey
    @NonNull
    public String email; // البريد الإلكتروني هو المفتاح الأساسي والفريد

    @Nullable
    public String name; // الاسم اختياري

    @Nullable
    public String passwordHash; // كلمة السر (تخزين مشفر)

    public User(@NonNull String email, @Nullable String name, @Nullable String passwordHash) {
        this.email = email;
        this.name = name;
        this.passwordHash = passwordHash;
    }
}