package com.example.plantcare.model;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class RoomCategories {
    private static final List<String> DEFAULT_ROOMS = Arrays.asList(
            "Wohnzimmer", "Schlafzimmer", "Flur", "Küche", "Bad", "Toilette"
    );
    private static final String PREFS_NAME = "room_categories";
    private static final String KEY_USER_ROOMS = "user_rooms";

    public static List<String> getAllRooms(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        HashSet<String> userRooms = new HashSet<>(prefs.getStringSet(KEY_USER_ROOMS, new HashSet<String>()));
        List<String> allRooms = new ArrayList<>(DEFAULT_ROOMS);
        allRooms.addAll(userRooms);
        return allRooms;
    }

    public static void addUserRoom(Context context, String room) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        HashSet<String> userRooms = new HashSet<>(prefs.getStringSet(KEY_USER_ROOMS, new HashSet<String>()));
        userRooms.add(room);
        prefs.edit().putStringSet(KEY_USER_ROOMS, userRooms).apply();
    }
}