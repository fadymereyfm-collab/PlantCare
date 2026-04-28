package com.example.plantcare;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.plantcare.feature.streak.StreakBridge;
import com.example.plantcare.weekbar.DefaultPlantIcon;
import com.example.plantcare.weekbar.PlantImageLoader;

import java.util.*;

public class DailyWateringAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_ROOM_HEADER = 0;
    private static final int TYPE_REMINDER = 1;

    private final List<Object> items = new ArrayList<>(); // RoomHeader or WateringReminder
    private final Context context;
    private final String userEmail;

    /**
     * يمثّل عنوان الغرفة في القائمة
     */
    public static class RoomHeader {
        public final int roomId;
        public final String roomName;
        public final List<WateringReminder> reminders; // التذكيرات الخاصة بهذه الغرفة

        public RoomHeader(int roomId, String roomName, List<WateringReminder> reminders) {
            this.roomId = roomId;
            this.roomName = roomName;
            this.reminders = reminders;
        }
    }

    public DailyWateringAdapter(List<WateringReminder> reminders, Context context, String userEmail) {
        this.context = context;
        this.userEmail = userEmail;
        // Backwards compatibility: flat list
        setItemsFlat(reminders);
    }

    /**
     * Set items grouped by rooms. Each RoomHeader is followed by its reminders.
     */
    public void setGroupedItems(List<RoomHeader> roomGroups) {
        items.clear();
        for (RoomHeader group : roomGroups) {
            items.add(group);
            // ترتيب: غير المنجز أولاً، ثم المتأخر أكثر
            List<WateringReminder> sorted = new ArrayList<>(group.reminders);
            Collections.sort(sorted, (a, b) -> {
                if (a.done != b.done) return a.done ? 1 : -1;
                int overdueA = a.getDaysOverdue();
                int overdueB = b.getDaysOverdue();
                if (overdueA != overdueB) return Integer.compare(overdueB, overdueA);
                return a.date.compareTo(b.date);
            });
            items.addAll(sorted);
        }
        notifyDataSetChanged();
    }

    /**
     * Flat list (backwards compatibility, no room headers)
     */
    public void setItems(List<WateringReminder> reminderList) {
        setItemsFlat(reminderList);
    }

    private void setItemsFlat(List<WateringReminder> reminderList) {
        items.clear();
        if (reminderList != null) {
            List<WateringReminder> sorted = new ArrayList<>(reminderList);
            Collections.sort(sorted, (a, b) -> {
                if (a.done != b.done) return a.done ? 1 : -1;
                int overdueA = a.getDaysOverdue();
                int overdueB = b.getDaysOverdue();
                if (overdueA != overdueB) return Integer.compare(overdueB, overdueA);
                return a.date.compareTo(b.date);
            });
            items.addAll(sorted);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return (items.get(position) instanceof RoomHeader) ? TYPE_ROOM_HEADER : TYPE_REMINDER;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_ROOM_HEADER) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_today_room_header, parent, false);
            return new RoomHeaderViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_daily_watering, parent, false);
            return new ReminderViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object item = items.get(position);
        if (holder instanceof RoomHeaderViewHolder && item instanceof RoomHeader) {
            ((RoomHeaderViewHolder) holder).bind((RoomHeader) item);
        } else if (holder instanceof ReminderViewHolder && item instanceof WateringReminder) {
            ((ReminderViewHolder) holder).bind((WateringReminder) item, position);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // ========== ViewHolders ==========

    class RoomHeaderViewHolder extends RecyclerView.ViewHolder {
        TextView textRoomHeader;

        RoomHeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            textRoomHeader = itemView.findViewById(R.id.textRoomHeader);
        }

        void bind(RoomHeader header) {
            textRoomHeader.setText(header.roomName);

            // نقرة طويلة: سقاية جميع نباتات الغرفة
            itemView.setOnLongClickListener(v -> {
                showBulkWaterDialog(header);
                return true;
            });
        }
    }

    private void showBulkWaterDialog(RoomHeader header) {
        new AlertDialog.Builder(context)
                .setTitle(R.string.dialog_water_all_title)
                .setMessage(context.getString(R.string.dialog_water_all_message, header.roomName))
                .setPositiveButton(R.string.action_yes, (dialog, which) -> {
                    com.example.plantcare.util.BgExecutor.io(() -> {
                        ReminderDao reminderDao = DatabaseClient.getInstance(context)
                                .getAppDatabase().reminderDao();
                        boolean anyMarkedDone = false;
                        for (WateringReminder r : header.reminders) {
                            if (!r.done) {
                                r.done = true;
                                r.completedDate = new Date();
                                r.wateredBy = userEmail;
                                reminderDao.update(r);
                                anyMarkedDone = true;
                            }
                        }
                        // Streak/Challenge-Update: idempotent pro Tag — ein Aufruf
                        // genügt auch bei Bulk-Water.
                        if (anyMarkedDone) {
                            StreakBridge.onReminderMarkedDone(context, userEmail);
                        }
                        ((MainActivity) context).runOnUiThread(() -> {
                            notifyDataSetChanged();
                            DataChangeNotifier.notifyChange();
                        });
                    });
                })
                .setNegativeButton("Nein", null)
                .show();
    }

    class ReminderViewHolder extends RecyclerView.ViewHolder {
        View sideStripe;
        ImageView imageThumb;
        TextView plantName;
        TextView textOverdue;
        ImageView typeIcon;

        ReminderViewHolder(@NonNull View itemView) {
            super(itemView);
            sideStripe = itemView.findViewById(R.id.sideStripe);
            imageThumb = itemView.findViewById(R.id.imagePlantThumb);
            plantName = itemView.findViewById(R.id.textPlantName);
            textOverdue = itemView.findViewById(R.id.textOverdue);
            typeIcon = itemView.findViewById(R.id.imageTypeIcon);
        }

        void bind(WateringReminder reminder, int position) {
            // اسم النبتة
            plantName.setText(reminder.plantName);

            // شريط جانبي: أخضر عند غير منجز، رمادي باهت عند المنجز
            int stripeColor = reminder.done ? 0xFFDDDDDD
                    : context.getResources().getColor(R.color.pc_success);
            sideStripe.setBackgroundColor(stripeColor);

            // شفافية خفيفة عند الاكتمال
            float alpha = reminder.done ? 0.5f : 1.0f;
            itemView.setAlpha(alpha);
            plantName.setAlpha(alpha);

            // شارة التأخير +N
            int overdueDays = reminder.getDaysOverdue();
            if (!reminder.done && overdueDays > 0) {
                textOverdue.setText("+" + overdueDays);
                textOverdue.setVisibility(View.VISIBLE);
            } else {
                textOverdue.setVisibility(View.GONE);
            }

            // أيقونة السقاية: تظهر فقط للتذكيرات التلقائية
            boolean isManual = (reminder.description != null && !reminder.description.trim().isEmpty())
                    || (reminder.repeat == null || reminder.repeat.equals("0") || reminder.repeat.isEmpty());
            typeIcon.setVisibility(isManual ? View.GONE : View.VISIBLE);

            // تحميل صورة النبتة عبر PlantImageLoader الموحّد
            loadPlantThumbAsync(reminder, imageThumb);

            // نقرة على الصورة = فتح تفاصيل النبتة (مختصر النقرة الطويلة على الكارت).
            // تمّ الفصل عن نقرة الكارت الأساسية حتى لا نكسر سلوك «اسقِ/تراجَع» الحالي.
            imageThumb.setOnClickListener(v -> openPlantDetails(reminder));

            // نقرة واحدة: إنهاء/إلغاء أو إعادة جدولة لو متأخر
            itemView.setOnClickListener(v -> {
                if (reminder.done) {
                    reminder.done = false;
                    reminder.completedDate = null;
                    updateReminderAndNotify(reminder);
                } else {
                    int od = reminder.getDaysOverdue();
                    if (od > 0) {
                        showRescheduleDialogCustom(reminder, od);
                    } else {
                        reminder.done = true;
                        reminder.completedDate = new Date();
                        reminder.wateredBy = userEmail;
                        updateReminderAndNotify(reminder);
                        StreakBridge.onReminderMarkedDone(context, userEmail);
                        Analytics.INSTANCE.logReminderCompleted(context);
                    }
                }
            });

            // نقرة طويلة: إدارة التذكير
            itemView.setOnLongClickListener(v -> {
                if (isManual) {
                    new AlertDialog.Builder(context)
                            .setTitle("Erinnerung verwalten")
                            .setItems(new CharSequence[]{"Bearbeiten", "Löschen"}, (dialog, which) -> {
                                if (which == 0) {
                                    EditManualReminderDialogFragment dialogFragment =
                                            EditManualReminderDialogFragment.newInstance(reminder);
                                    dialogFragment.show(((MainActivity) context).getSupportFragmentManager(),
                                            "edit_manual_reminder");
                                } else if (which == 1) {
                                    com.example.plantcare.util.BgExecutor.io(() -> {
                                        DatabaseClient.getInstance(context).getAppDatabase().reminderDao().delete(reminder);
                                        ((MainActivity) context).runOnUiThread(() -> {
                                            items.remove(position);
                                            notifyDataSetChanged();
                                            DataChangeNotifier.notifyChange();
                                        });
                                    });
                                }
                            })
                            .setNegativeButton("Abbrechen", null)
                            .show();
                } else {
                    // التذكيرات التلقائية: افتح تفاصيل النبات (نفس سلوك نقرة الصورة).
                    openPlantDetails(reminder);
                }
                return true;
            });
        }
    }

    /**
     * يفتح تفاصيل النبتة المرتبطة بهذا التذكير. يعمل في الخلفية لاستعلام DAO
     * ثم يعود للخيط الرئيسي لعرض الحوار.
     *
     * السبب: التقرير الجوهري (٢٣ أبريل) رصد أن نقرة على بند «اليوم» كانت نقرة ميّتة؛
     * أُضيفت هذه الطريقة حتى يربط الإجراء «اضغط → افتح النبتة» للصورة وللنقرة الطويلة.
     */
    private void openPlantDetails(WateringReminder reminder) {
        if (reminder == null || reminder.plantName == null) return;
        com.example.plantcare.util.BgExecutor.io(() -> {
            PlantDao plantDao = DatabaseClient.getInstance(context).plantDao();

            Plant foundPlant = null;
            List<Plant> allUserCopies =
                    plantDao.getAllUserPlantsWithNameAndUser(reminder.plantName, userEmail);
            if (!allUserCopies.isEmpty()) {
                foundPlant = plantDao.findById(allUserCopies.get(0).id);
            } else {
                foundPlant = plantDao.findByNickname(reminder.plantName);
                if (foundPlant == null) foundPlant = plantDao.findByName(reminder.plantName);
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

    private void loadPlantThumbAsync(WateringReminder reminder, ImageView target) {
        com.example.plantcare.util.BgExecutor.io(() -> {
            PlantDao plantDao = DatabaseClient.getInstance(context).plantDao();
            Plant plant = null;

            List<Plant> copiesByName = plantDao.getAllUserPlantsWithNameAndUser(reminder.plantName, userEmail);
            if (!copiesByName.isEmpty()) {
                plant = plantDao.findById(copiesByName.get(0).id);
            }
            if (plant == null) {
                plant = plantDao.findByNickname(reminder.plantName);
            }
            if (plant == null) {
                plant = plantDao.findByName(reminder.plantName);
            }

            final Plant p = plant;
            ((MainActivity) context).runOnUiThread(() -> {
                if (p != null) {
                    PlantImageLoader.loadInto(context, target, (long) p.id, p.name, userEmail);
                } else {
                    // Variante anhand des Pflanzen­namens wählen — gleicher Geist wie PlantImageLoader.
                    target.setImageResource(
                            DefaultPlantIcon.INSTANCE.forPlant(reminder.plantName, 0L)
                    );
                }
            });
        });
    }

    private void updateReminderAndNotify(WateringReminder reminder) {
        com.example.plantcare.util.BgExecutor.io(() -> {
            DatabaseClient.getInstance(context).getAppDatabase().reminderDao().update(reminder);
            ((MainActivity) context).runOnUiThread(() -> {
                notifyDataSetChanged();
                DataChangeNotifier.notifyChange();
            });
        });
    }

    private void showRescheduleDialogCustom(WateringReminder reminder, int overdue) {
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
            reminder.wateredBy = userEmail;
            updateReminderAndNotify(reminder);
            StreakBridge.onReminderMarkedDone(context, userEmail);
            Analytics.INSTANCE.logReminderCompleted(context);
            dialog.dismiss();
        });
        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
        // Make dialog window transparent so only the rounded card is visible
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
    }
}
