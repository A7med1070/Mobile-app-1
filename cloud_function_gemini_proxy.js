/**
 * Cloud Function: Gemini AI Proxy with Zero-Trust Authentication and Rate Limiting
 * 
 * Target: Google Cloud Functions / Firebase Functions (Node.js 18+)
 * Purpose: Securely isolates the Gemini API Key on the server side so it is never
 *          exposed or extractable from the client Android APK.
 * 
 * Deployment command:
 *   firebase deploy --only functions:geminiNutritionProxy
 */

const functions = require("firebase-functions");
const admin = require("firebase-admin");
const { GoogleGenerativeAI } = require("@google/generative-ai");

admin.initializeApp();
const db = admin.firestore();

// Retrieve secret key from environment or Google Cloud Secret Manager
const GEMINI_API_KEY = process.env.GEMINI_API_KEY || functions.config().gemini?.key;
const genAI = new GoogleGenerativeAI(GEMINI_API_KEY);

/**
 * Enforces per-user daily rate limits stored in Firestore under /users/{uid}/rate_limits/gemini
 * Limits: Max 20 requests per day per user
 */
async function checkAndIncrementRateLimit(uid) {
    const today = new Date().toISOString().split("T")[0]; // YYYY-MM-DD
    const limitRef = db.collection("users").document(uid).collection("rate_limits").document("gemini_" + today);

    return db.runTransaction(async (transaction) => {
        const doc = await transaction.get(limitRef);
        const currentCount = doc.exists ? (doc.data().count || 0) : 0;
        const MAX_PER_DAY = 20;

        if (currentCount >= MAX_PER_DAY) {
            throw new functions.https.HttpsError(
                "resource-exhausted",
                `تم تجاوز الحد اليومي المسموح به لاستخدام الذكاء الاصطناعي (${MAX_PER_DAY} طلبات/يوم). يرجى المحاولة غداً.`
            );
        }

        transaction.set(limitRef, {
            count: currentCount + 1,
            lastRequest: admin.firestore.FieldValue.serverTimestamp(),
            date: today
        }, { merge: true });

        return currentCount + 1;
    });
}

exports.geminiNutritionProxy = functions.https.onCall(async (data, context) => {
    // 1. Zero-Trust Authentication: Verify that caller is logged in
    if (!context.auth || !context.auth.uid) {
        throw new functions.https.HttpsError(
            "unauthenticated",
            "يجب تسجيل الدخول أولاً للوصول إلى خادم الذكاء الاصطناعي."
        );
    }

    const uid = context.auth.uid;
    const { action, payload } = data; // action: 'generate_plan' | 'scan_food'

    // 2. Rate Limiting to prevent billing spikes
    await checkAndIncrementRateLimit(uid);

    try {
        const model = genAI.getGenerativeModel({
            model: "gemini-2.5-flash",
            generationConfig: {
                responseMimeType: "application/json"
            }
        });

        if (action === "generate_plan") {
            const prompt = `أنت خبير تغذية وذكاء اصطناعي سريري. قم بإنشاء خطة نظام غذائي متكاملة بصيغة JSON بناءً على المعطيات:
العمر: ${payload.age}
الوزن: ${payload.weightKg} كجم
الطول: ${payload.heightCm} سم
الجنس: ${payload.gender}
الهدف: ${payload.goal}
مستوى النشاط: ${payload.activityLevel}

يجب أن يكون الناتج كائن JSON حصراً مطابقاً للنموذج التالي:
{
  "dailyTargetCalories": 2000,
  "proteinGrams": 150,
  "carbsGrams": 200,
  "fatGrams": 65,
  "title": "خطة غذائية متوازنة",
  "summary": "ملخص الخطة",
  "meals": [
    {
      "name": "إفطار صحي",
      "timeOfDay": "08:00 ص",
      "calories": 450,
      "proteinGrams": 25,
      "carbsGrams": 45,
      "fatGrams": 15,
      "items": ["بيضتان مسلوقتان", "شريحة خبز شوفان", "خيار وطماطم"],
      "advice": "شرب كوب ماء قبل الوجبة"
    }
  ]
}`;
            const result = await model.generateContent(prompt);
            return JSON.parse(result.response.text());
        } else if (action === "scan_food") {
            // Food image analysis
            const { imageBase64, mimeType } = payload;
            const prompt = `حلل صورة الوجبة المرفقة بدقة وقدّر البيانات الغذائية بصيغة JSON مطابقة:
{
  "productName": "اسم الوجبة",
  "standardPortion": "حجم الحصة",
  "baseCalories": 350,
  "proteinGrams": 20,
  "carbsGrams": 30,
  "fatGrams": 10,
  "notes": "ملاحظات غذائية استرشادية"
}`;
            const imagePart = {
                inlineData: {
                    data: imageBase64,
                    mimeType: mimeType || "image/jpeg"
                }
            };
            const result = await model.generateContent([prompt, imagePart]);
            return JSON.parse(result.response.text());
        } else {
            throw new functions.https.HttpsError("invalid-argument", "إجراء غير معروف");
        }
    } catch (err) {
        console.error("Gemini Proxy Error:", err);
        throw new functions.https.HttpsError("internal", err.message || "فشل معالجة الطلب في خادم الذكاء الاصطناعي");
    }
});
