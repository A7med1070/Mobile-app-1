const https = require('https');

const token = "14c2c9477eac1f7714540e81fff97192de143c74cf2aaa9c0538a2e61751";

const privacyContent = [
    { tag: "h3", children: ["سياسة الخصوصية وأمان البيانات - تطبيق نظّامي (Nithami)"] },
    { tag: "p", children: ["تاريخ السريان: أكتوبر 2026"] },
    { tag: "p", children: ["اسم التطبيق: نظّامي (Nithami)"] },
    { tag: "p", children: ["البريد الإلكتروني للمطور: asdahmed1886@gmail.com"] },
    { tag: "hr" },
    { tag: "h4", children: ["مقدمة والتزام الخصوصية"] },
    { tag: "p", children: ["يلتزم تطبيق «نظّامي» بحماية خصوصية المستخدمين وبياناتهم الشخصية بأعلى معايير الأمان والشفافية، وفقاً لسياسات Google Play وقوانين حماية البيانات الرقمية."] },
    { tag: "h4", children: ["1. البيانات التي يتم جمعها"] },
    { tag: "p", children: ["• بيانات المصادقة والحساب: الاسم، البريد الإلكتروني، ومعرف الحساب الفريد عبر خدمة Google Sign-In لتأمين وصولك إلى مهامك وجدولك."] },
    { tag: "p", children: ["• بيانات المهام والروتين اليومي: عناوين المهام، التذكيرات، وقوائم المراجعة التي تقوم بإنشائها لتنظيم يومك."] },
    { tag: "p", children: ["• البيانات الغذائية والصحية: الوزن، الطول، العمر، السعرات المستهدفة، وسجلات الوجبات اليومية لتتبع الأهداف الصحية."] },
    { tag: "p", children: ["• صور الأطباق (اختياري): عند استخدام ميزة فحص الوجبات، يتم إرسال الصورة بصورة مشفرة ومؤقتة لتحليل المكونات الغذائية دون تخزين دائم لملف الصورة على خوادمنا."] },
    { tag: "h4", children: ["2. الغرض من معالجة البيانات"] },
    { tag: "p", children: ["تُعالج البيانات فقط لمزامنة المهام والروتين مع السحاب المشفر، وجدولة التنبيهات المحلية في الوقت المحدد، وتوليد التوصيات وحساب السعرات."] },
    { tag: "h4", children: ["3. إخلاء مسؤولية طبي وتغذوي"] },
    { tag: "p", children: ["جميع المعلومات والخطط الغذائية وحسابات السعرات المولدة داخل التطبيق هي معلومات استرشادية تعليمية فقط، ولا تُعتبر بديلاً عن الاستشارة الطبية المتخصصة أو توصيات أخصائي التغذية العلاجية."] },
    { tag: "h4", children: ["4. عدم مشاركة أو بيع البيانات"] },
    { tag: "p", children: ["نحن لا نبيع بياناتك الشخصية مطلقاً ولا نشاركها مع أي أطراف إعلانية أو تسويقية. نستخدم فقط البنية التحتية السحابية المعتمدة لشركة Google (Firebase و Google Gemini API)."] },
    { tag: "h4", children: ["5. أذونات الجهاز"] },
    { tag: "p", children: ["• إشعارات التنبيه (POST_NOTIFICATIONS): تُطلب بشكل سياقي فقط عند تفعيل التذكير لأي مهمة."] },
    { tag: "p", children: ["• منبه التذكير الدقيق (SCHEDULE_EXACT_ALARM): لضمان إطلاق التنبيهات في وقتها."] },
    { tag: "p", children: ["• الصور: يستخدم التطبيق منتقي صور أندرويد الرسمي (Photo Picker) وهو آمن ولا يتطلب أي أذونات تخزين واسعة."] },
    { tag: "h4", children: ["6. حذف الحساب والبيانات"] },
    { tag: "p", children: ["يمتلك المستخدم الحق الكامل في حذف حسابه وكافة البيانات المخزنة نهائياً في أي وقت مباشرة من داخل التطبيق عبر: (الملف الشخصي > حذف الحساب وكافة البيانات نهائياً)، أو عبر مراسلة المطور على: asdahmed1886@gmail.com."] },
    { tag: "hr" },
    { tag: "h4", children: ["Privacy Policy (English Summary)"] },
    { tag: "p", children: ["Nithami App respects your privacy. We collect minimal account and task data strictly to deliver task management and nutritional tracking services through Google Firebase. No personal data is sold or shared with third-party advertisers. Users can permanently delete their account and all data at any time in-app or by contacting asdahmed1886@gmail.com."] }
];

const deletionContent = [
    { tag: "h3", children: ["Account and Data Deletion Request - Nithami App"] },
    { tag: "p", children: ["طلب حذف الحساب وجميع البيانات المرتبطة - تطبيق نظّامي (Nithami)"] },
    { tag: "p", children: ["وفقاً لسياسات متجر Google Play، يحق لكل مستخدم لتطبيق «نظّامي» طلب حذف حسابه وجميع بياناته المخزنة نهائياً."] },
    { tag: "h4", children: ["ماذا يحدث عند حذف حسابك؟"] },
    { tag: "p", children: ["• المهام والروتين: يتم حذف جميع المهام والمهام الفرعية وسجلات الإنجاز فورياً."] },
    { tag: "p", children: ["• البيانات الغذائية: يتم حذف خطط الوجبات ومحتوى اليوميات والسعرات المسجلة."] },
    { tag: "p", children: ["• المصادقة والتنبيهات: يُلغى الحساب من خوادم Firebase Auth نهائياً وتُلغى كافة التنبيهات المجدولة."] },
    { tag: "h4", children: ["طرق حذف الحساب:"] },
    { tag: "p", children: ["1. من داخل التطبيق مباشرة (فوري): افتح تطبيق نظّامي > الملف الشخصي > اضغط على زر «حذف الحساب وكافة البيانات نهائياً»."] },
    { tag: "p", children: ["2. عبر البريد الإلكتروني للمطور: أرسل بريداً إلكترونياً إلى asdahmed1886@gmail.com من نفس البريد المسجل في التطبيق بعنوان «طلب حذف حساب نظّامي»، وسيتم مسح الحساب وبياناته نهائياً خلال 48 ساعة."] }
];

function createPage(title, content, callback) {
    const data = JSON.stringify({
        access_token: token,
        title: title,
        author_name: "Nithami App",
        content: content,
        return_content: false
    });

    const options = {
        hostname: 'api.telegra.ph',
        port: 443,
        path: '/createPage',
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Content-Length': Buffer.byteLength(data)
        }
    };

    const req = https.request(options, (res) => {
        let body = '';
        res.on('data', chunk => body += chunk);
        res.on('end', () => {
            const resp = JSON.parse(body);
            callback(resp);
        });
    });

    req.on('error', (e) => console.error(e));
    req.write(data);
    req.end();
}

createPage("Privacy Policy - Nithami App", privacyContent, (res1) => {
    console.log("EN_PRIVACY_URL: " + (res1.result ? res1.result.url : JSON.stringify(res1)));
    createPage("Delete Account - Nithami App", deletionContent, (res2) => {
        console.log("EN_DELETION_URL: " + (res2.result ? res2.result.url : JSON.stringify(res2)));
    });
});
