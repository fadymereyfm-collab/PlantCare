---
description: تحقّق من اكتمال مرحلة كاملة قبل الانتقال للتي بعدها
argument-hint: <A|B|C|D|E>
---

# /phase-gate $ARGUMENTS

تحقّق صارم من إكمال **كل** معايير قبول المرحلة `$ARGUMENTS` في `PlantCare_Pre_Release_Audit.md`.

## الخطوات

1. **اقرأ** قسم المرحلة `$ARGUMENTS` في Audit (Phase A → القسم 8.A، Phase B → 8.B، إلخ).

2. **لكل مهمة في المرحلة** (A1, A2, ..., A5 لـPhase A):
   - شغّل أوامر grep المذكورة في معايير القبول.
   - سجّل ✅ أو ❌ مع الناتج الفعلي.

3. **بناء التحقّق:**
   ```bash
   ./gradlew clean assembleProdRelease
   ```
   - يجب أن ينجح.
   - عدد warnings يجب ألّا يزيد عن آخر snapshot.

4. **حصيلة:**
   - **كل المعايير ✅** ⇒ المرحلة مكتملة. اكتب entry في PROGRESS.md:
     ```markdown
     ## Phase $ARGUMENTS — COMPLETED at <date>
     ### All acceptance criteria verified:
     - [✅] A1: <evidence>
     - [✅] A2: <evidence>
     - ...
     ### Build: ✅ assembleProdRelease passed
     ### Next phase: <next>
     ```
   - **معيار واحد على الأقل ❌** ⇒ المرحلة غير مكتملة. **ارفض** الانتقال للمرحلة التالية. حدّد المهام الناقصة بدقة.

## قواعد صارمة

- لا تقبل "تقريباً مكتمل".
- لا تقبل "سأنفّذها لاحقاً".
- إذا كانت مهمة معلَّقة بقرار من Fady (مثل A4 — TFLite vs hide feature)، اعتبرها ❌ واطلب القرار.

## مثال

```
$ /phase-gate A

Phase A — Pre-release blockers
══════════════════════════════

A1 (SecurePrefs unify):  ✅
   grep -rn '"current_user_email"' app/src/main/java
   ⇒ 4 results, all in SecurePrefsHelper.kt + EmailContext.kt ✅

A2 (Billing wired):  ❌
   grep -rn "BillingManager.getInstance" app/src/main/java | grep -v "billing/"
   ⇒ 0 results — BillingManager.connect() ما زال غير مُستدعى من App.onCreate
   مطلوب: أضِف الاتصال + ربط Paywall

A3 (Weather key):  ✅
   grep -rn "YOUR_API_KEY_HERE" app/src/main/java
   ⇒ 0 results ✅

A4 (TFLite):  ⏸️
   معلَّقة بقرار Fady بين تنزيل النموذج أو إخفاء الميزة

A5 (AdMob prod IDs):  ❌
   لا يزال ca-app-pub-3940256099942544 في strings.xml:281

النتيجة: Phase A غير مكتمل. مطلوب: A2, A4, A5
لا انتقال إلى Phase B حتى تُنجز.
```
