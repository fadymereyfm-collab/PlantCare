package com.example.plantcare;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.plantcare.feature.streak.StreakBridge;
import com.example.plantcare.weekbar.PlantImageLoader;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class TodayAdapter extends RecyclerView.Adapter<TodayAdapter.ViewHolder> {

    private final List<WateringReminder> reminders = new ArrayList<>();

    public void setItems(List<WateringReminder> items) {
        reminders.clear();
        if (items != null) reminders.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_daily_watering, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        WateringReminder r = reminders.get(position);
        Context context = holder.itemView.getContext();

        // اسم النبتة
        holder.plantName.setText(r.plantName != null ? r.plantName : "Pflanze");

        // الشريط الجانبي: أخضر عند غير منجز، رمادي باهت عند المنجز
        int stripeColor = r.done ? 0xFFDDDDDD
                : ContextCompat.getColor(context, R.color.pc_success);
        holder.sideStripe.setBackgroundColor(stripeColor);

        // شفافية عامة عند الاكتمال
        float alpha = r.done ? 0.5f : 1.0f;
        holder.itemView.setAlpha(alpha);
        holder.plantName.setAlpha(alpha);

        // شارة التأخير: +N بالأحمر أسفل الاسم (تظهر فقط إذا متأخر وغير منجز)
        int overdueDays = r.getDaysOverdue();
        if (!r.done && overdueDays > 0) {
            holder.textOverdue.setText("+" + overdueDays);
            holder.textOverdue.setVisibility(View.VISIBLE);
        } else {
            holder.textOverdue.setVisibility(View.GONE);
        }

        // أيقونة النوع: تظهر فقط للتذكيرات التلقائية (سقاية مجدولة) repeat > 0
        boolean isManual = (r.description != null && !r.description.trim().isEmpty())
                || (r.repeat == null || r.repeat.equals("0") || r.repeat.isEmpty());

        if (isManual) {
            holder.typeIcon.setVisibility(View.GONE);
        } else {
            holder.typeIcon.setVisibility(View.VISIBLE);
            holder.typeIcon.setImageResource(R.drawable.ic_watering_can); // PNG الصحيح
            holder.typeIcon.clearColorFilter(); // لا tint
        }

        // تحميل صورة مصغّرة للنبتة
        loadPlantThumbAsync(context, r, holder.imageThumb);

        // نقرة واحدة: إنهاء/إلغاء أو إعادة جدولة لو متأخر
        holder.itemView.setOnClickListener(v -> {
            if (r.done) {
                r.done = false;
                r.completedDate = null;
                updateReminder(context, r);
            } else {
                int od = r.getDaysOverdue();
                if (od > 0) {
                    showRescheduleDialogCustom(context, r, od);
                } else {
                    r.done = true;
                    r.completedDate = new Date();
                    String email = StreakBridge.getCurrentEmail(context);
                    r.wateredBy = email;
                    updateReminder(context, r);
                    StreakBridge.onReminderMarkedDone(context, email);
                }
            }
        });

        // نقرة طويلة: إدارة التذكير اليدوي (تحرير/حذف) أو عرض تفاصيل النبات للتلقائي
        holder.itemView.setOnLongClickListener(v -> {
            if (isManual) {
                new AlertDialog.Builder(context)
                        .setTitle("Erinnerung verwalten")
                        .setItems(new CharSequence[]{"Bearbeiten", "Löschen"}, (dialog, which) -> {
                            if (which == 0 && context instanceof MainActivity) {
                                EditManualReminderDialogFragment dialogFragment = EditManualReminderDialogFragment.newInstance(r);
                                dialogFragment.show(((MainActivity) context).getSupportFragmentManager(), "edit_manual_reminder");
                            } else if (which == 1) {
                                com.example.plantcare.util.BgExecutor.io(() -> {
                                    DatabaseClient.getInstance(context).getAppDatabase().reminderDao().delete(r);
                                    ((MainActivity) context).runOnUiThread(() -> {
                                        reminders.remove(position);
                                        notifyDataSetChanged();
                                        DataChangeNotifier.notifyChange();
                                    });
                                });
                            }
                        })
                        .setNegativeButton("Abbrechen", null)
                        .show();
            } else {
                com.example.plantcare.util.BgExecutor.io(() -> {
                    PlantDao plantDao = DatabaseClient.getInstance(context).plantDao();
                    Plant foundPlant = null;

                    String userEmail = EmailContext.current(context);
                    List<Plant> allUserCopies = plantDao.getAllUserPlantsWithNameAndUser(r.plantName, userEmail);
                    if (!allUserCopies.isEmpty()) {
                        foundPlant = plantDao.findById(allUserCopies.get(0).id);
                    }
                    if (foundPlant == null) {
                        foundPlant = plantDao.findByNickname(r.plantName);
                        if (foundPlant == null) foundPlant = plantDao.findByName(r.plantName);
                    }

                    final Plant finalPlant = foundPlant;
                    if (finalPlant != null && context instanceof MainActivity) {
                        ((MainActivity) context).runOnUiThread(() -> {
                            PlantDetailDialogFragment dialog =
                                    PlantDetailDialogFragment.newInstance(finalPlant, true);
                            dialog.setReadOnlyMode(true);
                            dialog.show(((MainActivity) context).getSupportFragmentManager(),
                                    "plant_detail_popup");
                        });
                    }
                });
            }
            return true;
        });
    }

    private void updateReminder(Context context, WateringReminder reminder) {
        com.example.plantcare.util.BgExecutor.io(() -> {
            DatabaseClient.getInstance(context).getAppDatabase().reminderDao().update(reminder);
            ((MainActivity) context).runOnUiThread(() -> {
                notifyDataSetChanged();
                DataChangeNotifier.notifyChange();
            });
        });
    }

    private void showRescheduleDialogCustom(Context context, WateringReminder reminder, int overdue) {
        View content = LayoutInflater.from(context).inflate(R.layout.dialog_reschedule, null, false);
        TextView title = content.findViewById(R.id.title);
        TextView message = content.findViewById(R.id.message);
        Button btnYes = content.findViewById(R.id.btnYes);
        Button btnNo = content.findViewById(R.id.btnNo);
        Button btnClose = content.findViewById(R.id.btnClose);

        title.setText(context.getString(R.string.dialog_reschedule_title));
        message.setText(context.getString(R.string.dialog_reschedule_message, overdue));

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(content)
                .create();

        btnYes.setOnClickListener(v -> {
            com.example.plantcare.util.BgExecutor.io(() -> {
                ReminderUtils.rescheduleFromToday(reminder, context);
                ((MainActivity) context).runOnUiThread(() -> {
                    dialog.dismiss();
                    DataChangeNotifier.notifyChange();
                });
            });
        });
        btnNo.setOnClickListener(v -> {
            reminder.done = true;
            reminder.completedDate = new Date();
            String email = StreakBridge.getCurrentEmail(context);
            reminder.wateredBy = email;
            updateReminder(context, reminder);
            StreakBridge.onReminderMarkedDone(context, email);
            dialog.dismiss();
        });
        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
        // Make dialog window transparent so only the rounded card is visible
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
    }

    private void loadPlantThumbAsync(Context context, WateringReminder reminder, ImageView target) {
        com.example.plantcare.util.BgExecutor.io(() -> {
            PlantDao plantDao = DatabaseClient.getInstance(context).plantDao();
            Plant plant = null;

            String userEmail = EmailContext.current(context);

            List<Plant> copiesByNameAndUser = plantDao.getAllUserPlantsWithNameAndUser(reminder.plantName, userEmail);
            if (!copiesByNameAndUser.isEmpty()) {
                plant = plantDao.findById(copiesByNameAndUser.get(0).id);
            }
            if (plant == null) {
                List<Plant> byNick = plantDao.getAllUserPlantsWithNicknameAndUser(reminder.plantName, userEmail);
                if (!byNick.isEmpty()) plant = plantDao.findById(byNick.get(0).id);
            }
            if (plant == null) plant = plantDao.findByNickname(reminder.plantName);
            if (plant == null) plant = plantDao.findByName(reminder.plantName);

            final Plant p = plant;
            ((MainActivity) context).runOnUiThread(() -> {
                if (p != null) {
                    PlantImageLoader.loadInto(context, target, (long) p.id, p.name, userEmail);
                } else {
                    target.setImageResource(R.drawable.ic_default_plant);
                }
            });
        });
    }

    @Override
    public int getItemCount() {
        return reminders.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        View sideStripe;
        ImageView imageThumb;
        TextView plantName;
        TextView textOverdue;
        ImageView typeIcon;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            sideStripe = itemView.findViewById(R.id.sideStripe);
            imageThumb = itemView.findViewById(R.id.imagePlantThumb);
            plantName = itemView.findViewById(R.id.textPlantName);
            textOverdue = itemView.findViewById(R.id.textOverdue);
            typeIcon = itemView.findViewById(R.id.imageTypeIcon);
        }
    }
}