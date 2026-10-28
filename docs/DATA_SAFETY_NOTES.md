# ملاحظات Data safety وسياسة الخصوصية (للمراجعة قبل النشر)

> هذه ملاحظات تحضيرية وليست إقرارًا بالتوافق مع Google Play. لا تُقدَّم نموذج Data safety قبل التحقق من النقاط أدناه.

## 1) ما يفعله كود التطبيق نفسه (معروف)
- يقرأ إحصاءات الاستخدام محليًا عبر `UsageStatsManager` لترتيب التطبيقات، ولا يرسلها لأي خادم.
- لا حساب مستخدم، ولا خادم، ولا Firebase/Supabase، ولا تحليلات.
- يخزّن محليًا عدّاد الإعلانات اليومي فقط (SharedPreferences).
- الأذونات المعلنة بيد المطوّر: `SYSTEM_ALERT_WINDOW`، `PACKAGE_USAGE_STATS`، `FOREGROUND_SERVICE`، `FOREGROUND_SERVICE_SPECIAL_USE`.
- لا موقع ولا جهات اتصال ولا بلوتوث.

## 2) ما لا يمكن افتراضه (يجب التحقق)
- **بيانات مكتبة Start.io:** لا تفترض أنها لا تجمع بيانات. استخرج بعد البناء ملف الـ merged manifest (يرفعه GitHub Actions كملف `merged-manifest` ضمن الـ Artifacts) وراجع الأذونات التي أضافتها المكتبة فعليًا.
- طابق إجابات Data safety مع توثيق Start.io الرسمي (support.start.io) وسياسة خصوصيتها لإصدار SDK المستخدم، واسأل دعمها (support@start.io) عن قائمة البيانات التي تجمعها وتشاركها.
- **معرّف الإعلانات (Advertising ID):** تحقق مما إذا كان إذن `AD_ID` موجودًا في الـ merged manifest، وصرّح باستخدامه في Play Console بما يطابقه.
- **الموافقة (GDPR/CCPA):** توثيق Start.io يذكر نافذة إفصاح/موافقة مدمجة في المكتبة مفعّلة افتراضيًا، ويوصي بتمرير علم الموافقة `pas` لمستخدمي الاتحاد الأوروبي. لم أبنِ شاشة موافقة خاصة (ليست ضمن المواصفات)؛ راجع مع Start.io/مستشارك القانوني ما إذا كانت كافية لجمهورك.
- **الخدمة الأمامية `specialUse`:** تحتاج إلى تصريح/تبرير في نموذج Google Play (Foreground service). اكتب أن الخدمة تُبقي نافذة عائمة يشغّلها المستخدم.
- **إذن إحصاءات الاستخدام والظهور فوق التطبيقات:** صنّفهما بدقة في Play Console (Sensitive permissions) واشرح الغرض الأساسي للتطبيق.
- **استهداف API:** راجع متطلبات Google Play الحالية لـ target API قبل النشر (القيمة الحالية 35).

## 3) رابط سياسة الخصوصية
- الملف: `docs/privacy-policy.html` (استبدل `{{اسم المطوّر}}` و`{{البريد الإلكتروني}}`).
- للنشر على رابط عام عبر GitHub: Settings ← Pages ← Source: Deploy from a branch ← Branch: main، Folder: `/docs`.
  الرابط يكون عادة `https://USERNAME.github.io/REPO/privacy-policy.html` (تحقق من الرابط الفعلي الذي تعرضه GitHub).

## 4) المراجع الرسمية
- https://support.google.com/googleplay/android-developer/answer/10144311
- https://support.google.com/googleplay/android-developer/answer/9859455
- https://developer.android.com/reference/android/app/usage/UsageStatsManager
- https://developer.android.com/reference/android/view/WindowManager.LayoutParams
- https://www.start.io/
