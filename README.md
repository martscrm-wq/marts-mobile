# Marts CRM — تطبيق أندرويد مع تتبع خلفي (Capacitor)

غلاف Android يضم `Marts_System_Merged.html` نفسه + تتبع موقع خلفي حقيقي يعمل مع **قفل الشاشة** (خدمة أمامية `LocationTrackingService` + `LocationManager`، بلا اعتماد على Google Play Services).

## لماذا هذا التطبيق؟
المتصفح يعلّق JavaScript عند قفل الشاشة → التتبع المتواصل مستحيل من المتصفح. هذا التطبيق يسجّل نقاط الموقع في الخدمة الخلفية (إشعار دائم "تتبع الموقع نشط") وعند فتح التطبيق تُدمج النقاط تلقائياً في مخزن `gpsTracking` وتظهر في تبويب HR → تتبع الموقع، وتُزامن سحابياً عبر Firebase الموجود.

## المكونات
- `app/index.html` — نسخة التطبيق من `public\Marts_System_Merged.html` (نفس الملف).
- `android/app/src/main/java/com/marts/crm/LocationTrackingService.java` — خدمة أمامية تسجل نقاط GPS في `SharedPreferences` (سقف 1000 نقطة، فاصل زمني من إعدادات GPS: `trackIntervalMin`).
- `android/app/src/main/java/com/marts/crm/MartsGeolocationPlugin.java` — Plugin Capacitor:
  - `startTracking({intervalMs})` → يطلب الأذونات (الموقع في المقدمة/الخلفية + الإشعارات) ويبدأ الخدمة ويطلب إعفاء من توفير البطارية.
  - `stopTracking()` / `isTracking()` / `getPendingPoints()` / `clearPendingPoints()`.
- الجسر في HTML (`gpsNativeAvailable`/`gpsNativeStart`/`gpsNativeStop`/`gpsNativeFlush`) — إذا توفر التطبيق يستخدم الخلفي، وإلا يبقى سلوك المتصفح كما هو.

## بناء الـ APK
### 1) متطلبات
- Node.js + Android Studio (أو Android SDK + JDK 17).
- التوقيع: النسخة التجريبية موقعة بمفتاح debug تلقائياً (`android\app\build\outputs\apk\debug\app-debug.apk`).

### 2) تثبيت (مرة واحدة فقط بعد كل تعديل للـ HTML أو الـ Java)
```powershell
# من مجلد المشروع
npm install
npx cap sync        # ينسخ webDir إلى android/assets + يعيد بناء أصول Capacitor
```

### 3) بناء الـ APK
الطريقة أ (Android Studio):
```powershell
npx cap open android   # ثم Run ▸ Build APK، أو Menu ▸ Build ▸ Build Bundle(s)/APK(s)
```
الطريقة ب (سطر أوامر — سبق بناؤها بنجاح على هذا الجهاز):
```powershell
cd C:\marts-mobile\android
$env:JAVA_HOME="C:\Android\jdk17\jdk-17.0.20+8"; $env:ANDROID_HOME="C:\Android"
.\gradlew.bat assembleDebug          # apk تصحيح = app-debug.apk
# أو نسخة Release موقعة:
.\gradlew.bat assembleRelease        # يتطلب توقيع إنتاج (انظر أدناه)
```
> تم بناء `app-debug.apk` (4.1MB) بنجاح على جهاز المستخدم ونسخه إلى `C:\Users\khelw\Downloads\MartsCRM-debug.apk`. بيئة البناء المحلية: JDK 17 في `C:\Android\jdk17` + SDK في `C:\Android` (بدون Android Studio، عبر أدوات سطر أوامر).

### 4) التوقيع للإنتاج (اختياري — للتوزيع خارج الجهاز)
أنشئ `keystore.jks` وضع في `android\app\build.gradle` كتلة `signingConfigs` بالإشارة إليه، أو استخدم Android Studio: Build ▸ Generate Signed Bundle/APK.

## التثبيت على جهاز الموظف
1. انسخ `app-debug.apk` إلى الهاتف، ثم افتحه واضغط تثبيت.
2. فعّل "السماح بتثبيت من مصادر غير معروفة" إذا طلب النظام ذلك.
3. سجّل الدخول، اربط حسابك بموظفك (صلاحية `orgchart_view` للتفعيل الذاتي).
4. افتح تبويب HR → كشف الحضور → "بدء تتبع الموقع" و**وافق على:
   - "السماح بالوصول إلى الموقع" → **دائماً / Allow all the time** (مهم جداً للتتبع مع قفل الشاشة).
   - الإشعارات (Android 13+) → **السماح**.
   - "استبعاد من توفير البطارية" → **السماح** (يظهر تلقائياً).
5. لوقف التتبع: نفس الزر → "إيقاف التتبع".

## ملاحظات مهمة
- الفاصل الزمني للتتبع يُقرأ من إعدادات GPS في التطبيق (`trackIntervalMin`، الافتراضي 5 دقائق). غيّره من صفحة الإعدادات في المتصفح/التطبيق قبل بدء التتبع.
- عند إغلاق التطبيق من قائمة المهام (سحب لأعلى)، تُقتل الخدمة في بعض الشركات المصنعة (Xiaomi/Huawei). لتفادي ذلك: ثبّت "السماح بالتشغيل في الخلفية" للإعدادات الخاصة بالبطارية، أو أعد فتح التطبيق مرة بعد كل تشغيل للجهاز.
- iOS (جاهز): `ios/App/App/MartsGeolocationPlugin.swift` يستخدم `CLLocationManager` مع `allowsBackgroundLocationUpdates` + `UIBackgroundModes: location` في `Info.plist` (نفس واجهة `window.Capacitor.Plugins.MartsGeolocation`). لا يحتاج اشتراكاً مدفوعاً، لكن التتبع في الخلفية على iOS يتطلب:
  - **ماك + Xcode** للبناء (لا يمكن البناء من Windows)، أو خدمة بناء سحابية (GitHub Actions / Codemagic / Bitrise).
  - **Apple Developer Program (99$/سنة)** للتثبيت على الأجهزة ورفع الـ IPA إلى App Store.
  - المستخدم يختار "Allow Always" عند طلب صلاحية الموقع (أول مرة). إذا اختار "While Using" يتوقف التتبع في الخلفية.
  - مراجعة Apple: إضافة `UIBackgroundModes: location` تتطلب تبريراً واضحاً في مراجعة التطبيق.
- النقاط المخزنة محلياً أثناء إغلاق التطبيق تُدمج في `gpsTracking` عند أول فتح تالٍ (`appStateChange` / `gpsNativeFlush`).

## النشر على المتاجر
- **Play Store (أندرويد)**: ارفع ملف AAB موقّع. من Android Studio: Build ▸ Generate Signed Bundle/APK ▸ Android App Bundle. أو من CLI: `.\gradlew.bat bundleRelease` بعد إعداد التوقيع في `android/app/build.gradle`.
- **App Store (iOS)**: على ماك — `cd ios/App && pod install` ثم افتح `App.xcworkspace` في Xcode، واضبط التوقيع (فريقك) ثم Product ▸ Archive ثم Upload to App Store Connect. أو عبر CI سحابي.

