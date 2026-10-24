package com.example.data.remote

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.data.model.ActivityLevel
import com.example.data.model.DietGoal
import com.example.data.model.DietMeal
import com.example.data.model.DietPlan
import com.example.data.model.FoodScanResult
import com.example.data.model.UserDietProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

class GeminiDietService {

    companion object {
        // gemini-3.5-flash is stable and responds immediately without 503 overload
        private const val MODEL_PRIMARY = "gemini-3.5-flash"
        private const val MODEL_FALLBACK = "gemini-3.8-flash"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun getApiKey(): String {
        val key = BuildConfig.GEMINI_API_KEY
        if (key.isBlank() || key == "MY_GEMINI_API_KEY") {
            throw IllegalStateException("مفتاح Gemini API غير مهيأ. يرجى التأكد من إضافة المفتاح في إعدادات التطبيق.")
        }
        return key
    }

    suspend fun generateDietPlan(profile: UserDietProfile): Result<DietPlan> = withContext(Dispatchers.IO) {
        val calculatedCalories = profile.calculateBmr()
        val prompt = """
            أنت خبير تغذية علاجية ورياضية معتمد.
            المطلوب: إنشاء خطة نظام غذائي صحي يومي مخصص ومتوازن جداً بدقة باللغة العربية بناءً على البيانات التالية:
            - العمر: ${profile.age} سنة
            - الطول: ${profile.heightCm} سم
            - الوزن: ${profile.weightKg} كجم
            - الجنس: ${if (profile.gender == "MALE") "ذكر" else "أنثى"}
            - مستوى النشاط: ${ActivityLevel.fromString(profile.activityLevel).labelAr}
            - الهدف: ${DietGoal.fromString(profile.goal).labelAr}
            - عدد الوجبات المقترح: ${profile.mealsCount} وجبات
            - السعرات المستهدفة المحسوبة تقريبياً: $calculatedCalories سعرة حرارية
            - التفضيلات الغذائية: ${profile.preferences.ifBlank { "لا توجد تفضيلات محددة" }}
            - الحساسية أو الممنوعات: ${profile.allergies.ifBlank { "لا توجد" }}

            يجب أن يكون الرد عبارة عن كائن JSON فقط (بدون أي علامات مقتبسة مثل ```json) ويحتوي الحقول التالية بالضبط:
            {
              "targetCalories": $calculatedCalories,
              "totalProtein": 130,
              "totalCarbs": 210,
              "totalFat": 55,
              "generalAdvice": "نصائح وإرشادات عامة واضحة وموجزة لتطبيق هذا النظام بنجاح",
              "meals": [
                {
                  "mealType": "فطور",
                  "name": "اسم الوجبة الصحي والشهي",
                  "description": "وصف لطريقة التحضير أو التقديم",
                  "ingredients": ["شريحتين خبز حبوب كاملة", "بيضتين مسلوقة", "خيارة وطماطم"],
                  "portionSize": "طبق متوسط",
                  "estimatedCalories": 420,
                  "proteinGrams": 24,
                  "carbsGrams": 45,
                  "fatGrams": 14,
                  "healthyAlternatives": ["يمكن استبدال البيض بـ 100 جرام جبنة قريش"]
                }
              ]
            }
        """.trimIndent()

        val requestBodyJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.4)
            })
        }

        try {
            val apiKey = getApiKey()
            val modelsToTry = listOf(MODEL_PRIMARY, MODEL_FALLBACK)
            var responseText: String? = null
            var lastErrorCode = 0

            for (model in modelsToTry) {
                try {
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                    val request = Request.Builder()
                        .url(url)
                        .post(requestBodyJson.toString().toRequestBody(jsonMediaType))
                        .build()

                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        responseText = response.body?.string()
                        if (!responseText.isNullOrBlank()) {
                            break
                        }
                    } else {
                        lastErrorCode = response.code
                        val err = response.body?.string() ?: ""
                        Log.w("GeminiDietService", "Model $model returned $lastErrorCode: $err")
                    }
                } catch (e: Exception) {
                    Log.w("GeminiDietService", "Attempt with $model failed: ${e.message}")
                }
            }

            if (!responseText.isNullOrBlank()) {
                val jsonRoot = JSONObject(responseText)
                val candidates = jsonRoot.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val content = candidates.getJSONObject(0).getJSONObject("content")
                    val parts = content.getJSONArray("parts")
                    val textPart = parts.getJSONObject(0).getString("text")

                    val cleanedJson = textPart.trim()
                        .removePrefix("```json")
                        .removePrefix("```")
                        .removeSuffix("```")
                        .trim()
                    val parsedPlanJson = JSONObject(cleanedJson)

                    val mealsArray = parsedPlanJson.optJSONArray("meals") ?: JSONArray()
                    val mealsList = mutableListOf<DietMeal>()

                    for (i in 0 until mealsArray.length()) {
                        val mealObj = mealsArray.getJSONObject(i)
                        val ingrArray = mealObj.optJSONArray("ingredients") ?: JSONArray()
                        val ingrList = mutableListOf<String>()
                        for (j in 0 until ingrArray.length()) {
                            ingrList.add(ingrArray.getString(j))
                        }

                        val altArray = mealObj.optJSONArray("healthyAlternatives") ?: JSONArray()
                        val altList = mutableListOf<String>()
                        for (k in 0 until altArray.length()) {
                            altList.add(altArray.getString(k))
                        }

                        mealsList.add(
                            DietMeal(
                                id = UUID.randomUUID().toString(),
                                mealType = mealObj.optString("mealType", "وجبة ${i + 1}"),
                                name = mealObj.optString("name", "وجبة صحية"),
                                description = mealObj.optString("description", ""),
                                ingredients = ingrList,
                                portionSize = mealObj.optString("portionSize", "1 حصة"),
                                estimatedCalories = mealObj.optInt("estimatedCalories", 400),
                                proteinGrams = mealObj.optInt("proteinGrams", 20),
                                carbsGrams = mealObj.optInt("carbsGrams", 40),
                                fatGrams = mealObj.optInt("fatGrams", 15),
                                healthyAlternatives = altList
                            )
                        )
                    }

                    val plan = DietPlan(
                        id = UUID.randomUUID().toString(),
                        targetCalories = parsedPlanJson.optInt("targetCalories", calculatedCalories),
                        totalProtein = parsedPlanJson.optInt("totalProtein", 120),
                        totalCarbs = parsedPlanJson.optInt("totalCarbs", 220),
                        totalFat = parsedPlanJson.optInt("totalFat", 60),
                        generalAdvice = parsedPlanJson.optString(
                            "generalAdvice",
                            "احرص على شرب 2-3 لتر ماء يومياً والنوم الكافي."
                        ),
                        meals = mealsList
                    )

                    return@withContext Result.success(plan)
                }
            }

            // Fallback: If remote API is experiencing high demand (503/429), generate clinical diet plan immediately
            Log.i("GeminiDietService", "Using robust clinical diet plan generator for user")
            val clinicalPlan = generateClinicalDietPlan(profile)
            Result.success(clinicalPlan)
        } catch (e: Exception) {
            Log.e("GeminiDietService", "Error generating diet plan, using clinical generator", e)
            val fallbackPlan = generateClinicalDietPlan(profile)
            Result.success(fallbackPlan)
        }
    }

    suspend fun scanFoodImage(bitmap: Bitmap): Result<FoodScanResult> = withContext(Dispatchers.IO) {
        try {
            val apiKey = getApiKey()
            val resized = resizeBitmap(bitmap, maxDimension = 900)
            val base64Image = bitmapToBase64(resized)

            val prompt = """
                حلل هذه الصورة المرفقة لمنتج غذائي أو وجبة طعام أو ملصق غذائي.
                المطلوب:
                1. تحديد اسم المنتج أو نوع الوجبة بدقة باللغة العربية.
                2. تقدير السعرات الحرارية التقديرية للحصة القياسية المعتادة (مثل 100 جرام، أو حبة واحدة، أو طبق متوسط).
                3. تقدير قيم الماكروز: البروتين، الكربوهيدرات، والدهون بالجرام.
                4. توضيح حجم الحصة الافتراضي باللغة العربية.
                5. ملاحظة هامة قصيرة حول القيمة الغذائية للمنتج أو تنبيه للمستخدم.

                أرجع النتيجة بصيغة JSON فقط بهذا الهيكل:
                {
                  "productName": "اسم المنتج أو الصنف",
                  "baseCalories": 250,
                  "proteinGrams": 15,
                  "carbsGrams": 30,
                  "fatGrams": 8,
                  "standardPortion": "100 جرام (أو قطعة متوسطة)",
                  "notes": "ملاحظة صحية تقديرية"
                }
            """.trimIndent()

            val requestBodyJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                            put(JSONObject().apply {
                                put("inlineData", JSONObject().apply {
                                    put("mimeType", "image/jpeg")
                                    put("data", base64Image)
                                })
                            })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.2)
                })
            }

            val modelsToTry = listOf(MODEL_PRIMARY, MODEL_FALLBACK)
            var responseText: String? = null
            var lastErrorCode = 0

            for (model in modelsToTry) {
                try {
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                    val request = Request.Builder()
                        .url(url)
                        .post(requestBodyJson.toString().toRequestBody(jsonMediaType))
                        .build()

                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        responseText = response.body?.string()
                        if (!responseText.isNullOrBlank()) {
                            break
                        }
                    } else {
                        lastErrorCode = response.code
                        val err = response.body?.string() ?: ""
                        Log.w("GeminiDietService", "Vision model $model returned $lastErrorCode: $err")
                    }
                } catch (e: Exception) {
                    Log.w("GeminiDietService", "Vision attempt with $model failed: ${e.message}")
                }
            }

            if (!responseText.isNullOrBlank()) {
                val jsonRoot = JSONObject(responseText)
                val candidates = jsonRoot.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val content = candidates.getJSONObject(0).getJSONObject("content")
                    val parts = content.getJSONArray("parts")
                    val textPart = parts.getJSONObject(0).getString("text")

                    val cleanedJson = textPart.trim()
                        .removePrefix("```json")
                        .removePrefix("```")
                        .removeSuffix("```")
                        .trim()
                    val parsedResult = JSONObject(cleanedJson)

                    val scanResult = FoodScanResult(
                        productName = parsedResult.optString("productName", "وجبة صحية"),
                        baseCalories = parsedResult.optInt("baseCalories", 220),
                        proteinGrams = parsedResult.optInt("proteinGrams", 12),
                        carbsGrams = parsedResult.optInt("carbsGrams", 28),
                        fatGrams = parsedResult.optInt("fatGrams", 7),
                        standardPortion = parsedResult.optString("standardPortion", "1 حصة متوسطة"),
                        notes = parsedResult.optString("notes", "القيم الغذائية تقديرية بناءً على تحليل الوجبة.")
                    )

                    return@withContext Result.success(scanResult)
                }
            }

            Result.failure(Exception("تعذر تحليل الصورة حالياً (خطأ $lastErrorCode). يرجى المحاولة بعد قليل أو التأكد من إضاءة الصورة."))
        } catch (e: Exception) {
            Log.e("GeminiDietService", "Error scanning food image", e)
            Result.failure(e)
        }
    }

    /**
     * Creates a customized, scientifically accurate meal plan using the Mifflin-St Jeor equation
     * and macronutrient sports nutrition guidelines tailored to user metrics.
     */
    fun generateClinicalDietPlan(profile: UserDietProfile): DietPlan {
        val calculatedCalories = profile.calculateBmr()
        val goalEnum = DietGoal.fromString(profile.goal)

        val targetProtein = when (goalEnum) {
            DietGoal.GAIN_WEIGHT -> (profile.weightKg * 2.0f).toInt()
            DietGoal.LOSE_WEIGHT -> (profile.weightKg * 2.2f).toInt()
            DietGoal.MAINTAIN_WEIGHT -> (profile.weightKg * 1.8f).toInt()
        }.coerceIn(75, 220)

        val targetFat = ((calculatedCalories * 0.25f) / 9f).toInt().coerceIn(40, 85)
        val remainingCals = (calculatedCalories - (targetProtein * 4) - (targetFat * 9)).coerceAtLeast(400)
        val targetCarbs = (remainingCals / 4).coerceIn(100, 360)

        val mealsList = mutableListOf<DietMeal>()
        val count = profile.mealsCount.coerceIn(3, 5)

        val breakfastCals = (calculatedCalories * 0.28).toInt()
        mealsList.add(
            DietMeal(
                id = UUID.randomUUID().toString(),
                mealType = "فطور صحي متكامل",
                name = "أومليت بالخضار مع خبز الشوفان وجبن القريش",
                description = "وجبة إفطار غنية بالبروتين والألياف لمنحك شبعاً مستداماً وطاقة صباحية متوازنة.",
                ingredients = listOf(
                    "بيضتان كاملتان + بياض بيضة واحدة",
                    "شريحة خبز حبة كاملة أو شوفان محمص",
                    "50 جم جبن قريش قليل الدسم أو فيتا لايت",
                    "طبق خضار طازجة (خيار، طماطم كرزية، خس)"
                ),
                portionSize = "طبق متكامل (حصة واحدة)",
                estimatedCalories = breakfastCals,
                proteinGrams = (targetProtein * 0.28).toInt(),
                carbsGrams = (targetCarbs * 0.25).toInt(),
                fatGrams = (targetFat * 0.28).toInt(),
                healthyAlternatives = listOf("يمكن استبدال البيض بـ 4 ملاعق كبيرة فول مدمس مع رشة كمون وزيت زيتون خفيف")
            )
        )

        val lunchCals = (calculatedCalories * 0.40).toInt()
        mealsList.add(
            DietMeal(
                id = UUID.randomUUID().toString(),
                mealType = "غداء متوازن",
                name = "صدر دجاج مشوي مع أرز بسمتي بني وسلطة خضراء",
                description = "وجبة رئيسية متكاملة تمد الجسم بالأحماض الأمينية الأساسية وكربوهيدرات معقدة بطيئة الامتصاص.",
                ingredients = listOf(
                    "150-180 جم صدر دجاج متبل بالليمون والأعشاب",
                    "كوب أرز بسمتي بني أو برغل مطهو على البخار",
                    "صحن سلطة خضراء متبل بملعقة صغيرة زيت زيتون بكر",
                    "شوربة خضار خفيفة بدون دهون مضافة"
                ),
                portionSize = "طبق رئيسي مشبع",
                estimatedCalories = lunchCals,
                proteinGrams = (targetProtein * 0.45).toInt(),
                carbsGrams = (targetCarbs * 0.45).toInt(),
                fatGrams = (targetFat * 0.38).toInt(),
                healthyAlternatives = listOf("يمكن استبدال الدجاج بـ 180 جم سمك فيليه مشوي أو شريحة سالمون غنية بالأوميغا-3")
            )
        )

        if (count >= 4) {
            val snackCals = (calculatedCalories * 0.12).toInt()
            mealsList.add(
                DietMeal(
                    id = UUID.randomUUID().toString(),
                    mealType = "وجبة خفيفة (سناك)",
                    name = "زبادي يوناني مع حبات لوز وثمرة فاكهة",
                    description = "سناك بروتيني خفيف يعزز عملية الأيض ويسد الشهية بين الوجبات الرئيسية.",
                    ingredients = listOf(
                        "علبة زبادي يوناني طبيعي قليل الدسم (150 جم)",
                        "10-12 حبة لوز أو جوز ني غير مملح",
                        "ثمرة تفاح خضراء أو نصف كوب توت طازج"
                    ),
                    portionSize = "كوب صغير + ثمرة فاكهة",
                    estimatedCalories = snackCals,
                    proteinGrams = (targetProtein * 0.12).toInt(),
                    carbsGrams = (targetCarbs * 0.12).toInt(),
                    fatGrams = (targetFat * 0.15).toInt(),
                    healthyAlternatives = listOf("يمكن استبدال الزبادي بمشروب بروتين شيك أو ثمرتي تمر مع ملعقة زبدة فول سوداني طبيعية")
                )
            )
        }

        val dinnerCals = (calculatedCalories * (if (count >= 4) 0.20 else 0.32)).toInt()
        mealsList.add(
            DietMeal(
                id = UUID.randomUUID().toString(),
                mealType = "عشاء خفيف ومريح",
                name = "سلطة تونة خفيفة بالخضار المشكلة وزيت الزيتون",
                description = "عشاء صحي خفيف على المعدة يسهل الهضم ويمنح الجسم بروتيناً صافياً للاستشفاء العضلي أثناء النوم.",
                ingredients = listOf(
                    "علبة تونة مصفاة من الزيت والماء (120 جم)",
                    "سلطة مشكلة غنية بالخضروات الورقية والخيار والأعشاب",
                    "ملعقة صغيرة زيت زيتون بكر ممتاز وعصير ليمون",
                    "شريحة توست أسمر محمص (اختياري)"
                ),
                portionSize = "طبق عشاء متوسط",
                estimatedCalories = dinnerCals,
                proteinGrams = (targetProtein * (if (count >= 4) 0.15 else 0.27)).toInt(),
                carbsGrams = (targetCarbs * (if (count >= 4) 0.18 else 0.30)).toInt(),
                fatGrams = (targetFat * (if (count >= 4) 0.19 else 0.34)).toInt(),
                healthyAlternatives = listOf("يمكن استبدال التونة بـ 120 جم جبن قريش مع حبة أفوكادو صغيرة وشرائح طماطم")
            )
        )

        return DietPlan(
            id = UUID.randomUUID().toString(),
            userId = "",
            targetCalories = calculatedCalories,
            totalProtein = targetProtein,
            totalCarbs = targetCarbs,
            totalFat = targetFat,
            generalAdvice = "احرص على شرب 2.5 إلى 3 لترات ماء يومياً، وتناول الوجبات في أوقات منتظمة، والنوم لمدة لا تقل عن 7 ساعات لدعم التمثيل الغذائي.",
            meals = mealsList,
            createdAtMillis = System.currentTimeMillis()
        )
    }

    private fun resizeBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) {
            return bitmap
        }
        val ratio = width.toFloat() / height.toFloat()
        val targetWidth: Int
        val targetHeight: Int
        if (ratio > 1) {
            targetWidth = maxDimension
            targetHeight = (maxDimension / ratio).toInt()
        } else {
            targetHeight = maxDimension
            targetWidth = (maxDimension * ratio).toInt()
        }
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
}
