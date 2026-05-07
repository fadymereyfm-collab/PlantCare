package com.example.plantcare;

import androidx.lifecycle.LiveData;
import androidx.room.*;
import java.util.List;

@Dao
public interface RoomCategoryDao {
    @Query("SELECT * FROM RoomCategory WHERE userEmail = :userEmail "
            + "ORDER BY position ASC, name COLLATE NOCASE ASC")
    List<RoomCategory> getAllRoomsForUser(String userEmail);

    /**
     * Reactive variant — Room re-emits whenever the row set changes, so
     * MyPlantsFragment can drop its DataChangeNotifier-tickle pattern in
     * favour of a plain Observer. Same ordering as the snapshot variant
     * so the UI stays consistent regardless of which method the caller
     * picks.
     */
    @Query("SELECT * FROM RoomCategory WHERE userEmail = :userEmail "
            + "ORDER BY position ASC, name COLLATE NOCASE ASC")
    LiveData<List<RoomCategory>> observeAllRoomsForUser(String userEmail);

    /** Bulk position update used by drag-to-reorder. */
    @Query("UPDATE RoomCategory SET position = :position WHERE id = :id")
    void updatePosition(int id, int position);

    @Insert
    long insert(RoomCategory room);

    @Update
    void update(RoomCategory room);

    @Delete
    void delete(RoomCategory room);

    @Query("SELECT * FROM RoomCategory WHERE name = :name AND userEmail = :userEmail LIMIT 1")
    RoomCategory findByName(String name, String userEmail);

    @Query("SELECT * FROM RoomCategory WHERE id = :id")
    RoomCategory findById(int id);
}