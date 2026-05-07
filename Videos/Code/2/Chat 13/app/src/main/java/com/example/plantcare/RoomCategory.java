package com.example.plantcare;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Ignore;

@Entity(tableName = "RoomCategory")
public class RoomCategory {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @NonNull
    public String name;

    public String userEmail;

    /**
     * User-defined sort order (drag-to-reorder). Lower values come first;
     * ties fall back to the alphabetical ordering in the SQL query. New
     * rows default to 0 so freshly inserted rooms float to the top of the
     * un-reordered tail until the user explicitly drags them.
     */
    public int position;

    // منشئ افتراضي فارغ (مطلوب من Room وبعض الأدابتورات)
    public RoomCategory() {
        this.name = "";
        this.userEmail = "";
        this.position = 0;
    }

    @Ignore
    public RoomCategory(@NonNull String name, String userEmail) {
        this.name = name;
        this.userEmail = userEmail;
        this.position = 0;
    }
}