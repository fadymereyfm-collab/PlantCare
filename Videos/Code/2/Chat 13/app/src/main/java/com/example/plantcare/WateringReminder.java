package com.example.plantcare;

import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.io.Serializable;

@Entity(
    foreignKeys = @ForeignKey(
        entity = Plant.class,
        parentColumns = "id",
        childColumns = "plantId",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {
        @Index("plantId"),
        @Index("userEmail"),
        @Index(value = {"userEmail", "done"})
    }
)
public class WateringReminder implements Serializable {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public int plantId;

    @Nullable
    public String plantName;

    @Nullable
    public String date; // بصيغة yyyy-MM-dd

    public boolean done;

    @Nullable
    public Date completedDate; // تاريخ الإنجاز

    @Nullable
    public String repeat; // عدد الأيام كنص، مثلاً "5" أو "7"

    @Nullable
    public String description;

    // 🟢 البريد الإلكتروني لصاحب التذكير
    @Nullable
    public String userEmail;

    /**
     * E-Mail des Familien­mitglieds, das diese Erinnerung zuletzt abgehakt hat.
     * Null = noch nicht ausgeführt ODER vor dem v7→v8-Upgrade ausgeführt.
     * Wird gesetzt, sobald {@link #done} = true gemarkt wird.
     */
    @Nullable
    public String wateredBy;

    /**
     * Markiert die Erinnerung als Teil eines Behandlungs­plans (nicht als
     * normaler Gieß-Rhythmus). 0 = normal, 1 = Krankheits­behandlung.
     * Wird von TreatmentPlanBuilder beim Erzeugen gesetzt, damit der
     * Behandlungs­verlauf separat ausgewertet werden kann.
     */
    public int isTreatment;

    /**
     * Plant Journal (v11): freitext-Notiz, die der Nutzer beim Abhaken einer
     * Erinnerung hinterlassen kann (z. B. „bedürftig gewirkt"). Optional, null bei
     * allen vor v11 abgehakten Erinnerungen.
     */
    @Nullable
    public String notes;

    /**
     * لحساب عدد الأيام المتأخرة
     */
    public int getDaysOverdue() {
        if (done || date == null) return 0;

        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date reminderDate = sdf.parse(date);
            Date today = sdf.parse(sdf.format(new Date())); // لضمان تجاهل الوقت

            if (reminderDate != null && today != null && today.after(reminderDate)) {
                long diff = today.getTime() - reminderDate.getTime();
                return (int) (diff / (1000 * 60 * 60 * 24));
            }
        } catch (ParseException e) {
            com.example.plantcare.CrashReporter.INSTANCE.log(e);
        }
        return 0;
    }

    /**
     * 🆕 توليد تواريخ متكررة بناءً على عدد الأيام
     * @param limitDays عدد الأيام المستقبلية التي نريد إنشاء تذكيرات خلالها
     * @return قائمة تواريخ متكررة بصيغة yyyy-MM-dd
     */
    public List<String> generateRecurringDates(int limitDays) {
        List<String> dates = new ArrayList<>();
        if (date == null || repeat == null) return dates;

        try {
            int intervalDays = Integer.parseInt(repeat);
            if (intervalDays <= 0) return dates;

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(sdf.parse(date));

            Date today = new Date();
            for (int i = 0; i < limitDays; i += intervalDays) {
                if (calendar.getTime().after(today)) {
                    dates.add(sdf.format(calendar.getTime()));
                }
                calendar.add(Calendar.DAY_OF_YEAR, intervalDays);
            }

        } catch (Exception e) {
            com.example.plantcare.CrashReporter.INSTANCE.log(e);
        }

        return dates;
    }
}