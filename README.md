# PlantCare - تطبيق العناية بالنباتات

## كيف يعمل هذا التطبيق؟ / How Does This Application Work?

### العربية

**PlantCare** هو تطبيق Android مخصص لمساعدة المستخدمين في العناية بنباتاتهم بطريقة علمية ومنظمة.

#### الميزات الأساسية:

1. **مراقبة النباتات**
   - تسجيل النباتات المختلفة في حديقتك أو منزلك
   - تتبع حالة كل نبتة (صحية، تحتاج رعاية، مريضة)
   - إضافة صور لمراقبة نمو النباتات

2. **جدولة العناية**
   - تذكيرات ذكية للري
   - تذكيرات للتسميد
   - تذكيرات لتقليم النباتات
   - تذكيرات لإعادة الزراعة

3. **دليل النباتات**
   - معلومات شاملة عن أنواع النباتات المختلفة
   - متطلبات العناية لكل نوع
   - نصائح للعناية الموسمية

4. **التشخيص الذكي**
   - تحليل صور النباتات لاكتشاف الأمراض
   - اقتراح علاجات طبيعية
   - نصائح الوقاية

5. **مجتمع البستانيين**
   - مشاركة الخبرات مع المستخدمين الآخرين
   - طرح الأسئلة والحصول على إجابات
   - مشاركة صور النباتات والإنجازات

#### كيف يعمل النظام:

```
المستخدم يفتح التطبيق
↓
يسجل نباتاته الجديدة أو يعرض الموجودة
↓
يضبط جدول العناية (ري، تسميد، تقليم)
↓
يتلقى تذكيرات في الأوقات المناسبة
↓
يسجل أنشطة العناية المكتملة
↓
يتتبع تقدم نمو النباتات
↓
يحصل على نصائح وتوجيهات مخصصة
```

---

### English

**PlantCare** is an Android application designed to help users take care of their plants in a scientific and organized manner.

#### Core Features:

1. **Plant Monitoring**
   - Register different plants in your garden or home
   - Track the status of each plant (healthy, needs care, sick)
   - Add photos to monitor plant growth

2. **Care Scheduling**
   - Smart watering reminders
   - Fertilizing reminders
   - Plant pruning reminders
   - Repotting reminders

3. **Plant Guide**
   - Comprehensive information about different plant types
   - Care requirements for each type
   - Seasonal care tips

4. **Smart Diagnosis**
   - Analyze plant photos to detect diseases
   - Suggest natural treatments
   - Prevention tips

5. **Gardener Community**
   - Share experiences with other users
   - Ask questions and get answers
   - Share plant photos and achievements

#### How the System Works:

```
User opens the app
↓
Registers new plants or views existing ones
↓
Sets up care schedule (watering, fertilizing, pruning)
↓
Receives reminders at appropriate times
↓
Records completed care activities
↓
Tracks plant growth progress
↓
Gets personalized tips and guidance
```

## Technical Architecture / البنية التقنية

### Application Structure

```
PlantCare/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/plantcare/
│   │   │   │   ├── activities/
│   │   │   │   │   ├── MainActivity.java
│   │   │   │   │   ├── PlantDetailActivity.java
│   │   │   │   │   └── AddPlantActivity.java
│   │   │   │   ├── fragments/
│   │   │   │   │   ├── PlantListFragment.java
│   │   │   │   │   ├── CareScheduleFragment.java
│   │   │   │   │   └── PlantGuideFragment.java
│   │   │   │   ├── models/
│   │   │   │   │   ├── Plant.java
│   │   │   │   │   ├── CareTask.java
│   │   │   │   │   └── PlantType.java
│   │   │   │   ├── database/
│   │   │   │   │   ├── PlantDatabase.java
│   │   │   │   │   └── PlantDao.java
│   │   │   │   ├── services/
│   │   │   │   │   ├── NotificationService.java
│   │   │   │   │   └── PlantAnalysisService.java
│   │   │   │   └── utils/
│   │   │   │       ├── ImageUtils.java
│   │   │   │       └── DateUtils.java
│   │   │   ├── res/
│   │   │   │   ├── layout/
│   │   │   │   ├── drawable/
│   │   │   │   ├── values/
│   │   │   │   └── menu/
│   │   │   └── AndroidManifest.xml
│   │   └── androidTest/
│   └── build.gradle
├── gradle/
├── build.gradle
└── settings.gradle
```

### Key Components / المكونات الأساسية

#### 1. Plant Model / نموذج النبتة
```java
public class Plant {
    private long id;
    private String name;
    private String type;
    private Date plantingDate;
    private String location;
    private int wateringFrequency; // days
    private int fertilizingFrequency; // days
    private Date lastWatered;
    private Date lastFertilized;
    private PlantStatus status;
    private List<String> photoUrls;
}
```

#### 2. Care Task Model / نموذج مهام العناية
```java
public class CareTask {
    private long id;
    private long plantId;
    private TaskType type; // WATERING, FERTILIZING, PRUNING
    private Date scheduledDate;
    private boolean completed;
    private String notes;
}
```

#### 3. Notification Service / خدمة التذكيرات
```java
public class NotificationService {
    // Send daily reminders for plant care
    // Schedule future notifications
    // Handle user interactions with notifications
}
```

## Getting Started / البدء

### Prerequisites / المتطلبات الأساسية

- Android Studio 4.0+
- Android SDK 21+
- Java 8+

### Installation / التثبيت

1. **Clone the repository / استنساخ المستودع**
   ```bash
   git clone https://github.com/fadymereyfm-collab/PlantCare.git
   cd PlantCare
   ```

2. **Open in Android Studio / افتح في Android Studio**
   - Import the project into Android Studio
   - Wait for Gradle sync to complete

3. **Build and Run / البناء والتشغيل**
   - Connect an Android device or start an emulator
   - Click "Run" or press Ctrl+R

## Usage Guide / دليل الاستخدام

### Adding a New Plant / إضافة نبتة جديدة

1. Open the app / افتح التطبيق
2. Tap the "+" button / اضغط على زر "+"
3. Fill in plant details:
   - Name / الاسم
   - Type / النوع
   - Location / المكان
   - Care frequency / تكرار العناية
4. Take a photo (optional) / التقط صورة (اختياري)
5. Save / احفظ

### Setting Care Reminders / ضبط تذكيرات العناية

1. Select a plant from the list / اختر نبتة من القائمة
2. Go to "Care Schedule" / انتقل إلى "جدول العناية"
3. Set watering frequency / اضبط تكرار الري
4. Set fertilizing schedule / اضبط جدول التسميد
5. Enable notifications / فعل التذكيرات

### Tracking Plant Health / تتبع صحة النباتات

1. Open plant details / افتح تفاصيل النبتة
2. Add regular photos / أضف صوراً منتظمة
3. Update plant status / حدث حالة النبتة
4. Record care activities / سجل أنشطة العناية
5. Monitor growth progress / راقب تقدم النمو

## Plant Care Tips / نصائح العناية بالنباتات

### General Guidelines / الإرشادات العامة

#### Watering / الري
- Check soil moisture before watering
- Water early morning or evening
- Use room temperature water
- Ensure proper drainage

#### Light Requirements / متطلبات الضوء
- Indoor plants: bright, indirect light
- Outdoor plants: follow species requirements
- Rotate plants regularly for even growth

#### Fertilizing / التسميد
- Use balanced fertilizer during growing season
- Reduce fertilizing in winter
- Follow package instructions for dilution

#### Common Problems / المشاكل الشائعة
- **Overwatering**: Yellow leaves, root rot
- **Underwatering**: Wilting, dry soil
- **Insufficient light**: Leggy growth, pale leaves
- **Pests**: Regular inspection and treatment

## Contributing / المساهمة

We welcome contributions to improve PlantCare! Please:

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## License / الترخيص

This project is licensed under the MIT License - see the LICENSE file for details.

## Support / الدعم

For questions or support, please:
- Open an issue on GitHub
- Contact: fadymerey.fm@gmail.com

## Roadmap / خارطة الطريق

### Upcoming Features / الميزات القادمة

- [ ] AI-powered plant disease detection
- [ ] Weather integration for watering suggestions
- [ ] Plant identification using camera
- [ ] Social features and plant sharing
- [ ] Offline mode support
- [ ] Widget for quick care actions
- [ ] Integration with IoT sensors
- [ ] Multi-language support expansion

---

**PlantCare** - Making plant care simple and effective for everyone!

**PlantCare** - جعل العناية بالنباتات بسيطة وفعالة للجميع!