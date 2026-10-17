package com.example.plantcare;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface UserDao {

    @Insert
    void insert(User user);

    @Query("SELECT * FROM User WHERE email = :email LIMIT 1")
    User getUserByEmail(@NonNull String email);

    @Query("SELECT * FROM User WHERE email = :email AND passwordHash = :passwordHash LIMIT 1")
    User login(@NonNull String email, @Nullable String passwordHash);

    @Query("SELECT * FROM User")
    List<User> getAllUsers();

    @Query("UPDATE User SET name = :newName WHERE email = :email")
    void updateUserName(@NonNull String email, @Nullable String newName);

    @Query("UPDATE User SET passwordHash = :newHash WHERE email = :email")
    void updateUserPassword(@NonNull String email, @Nullable String newHash);

    @Query("DELETE FROM User WHERE email = :email")
    void deleteUserByEmail(@NonNull String email);
}