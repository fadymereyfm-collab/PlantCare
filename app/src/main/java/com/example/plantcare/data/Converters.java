package com.example.plantcare.data;

import androidx.room.TypeConverter;

import java.util.Date;

/**
 * Shared type converters (Date <-> Long).
 */
public class Converters {

    @TypeConverter
    public static Long dateToLong(Date date) {
        return (date == null) ? null : date.getTime();
    }

    @TypeConverter
    public static Date longToDate(Long value) {
        return (value == null) ? null : new Date(value);
    }
}