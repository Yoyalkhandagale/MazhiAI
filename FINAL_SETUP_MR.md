# Mazhi AI — उरलेली सेटअप पावले (मराठी)

हा प्रोजेक्ट तयार केलेला source code आहे. GitHub/Render खात्यांमध्ये या workspace ला लॉगिन किंवा deploy परवानगी नाही, त्यामुळे खालील cloud पावले मालकानेच पूर्ण करायची आहेत. **API key चॅटमध्ये किंवा Android code मध्ये पाठवू नका.**

## 1. GitHub वर source ठेवणे

1. GitHub मध्ये नवीन repository तयार करा, नाव `MazhiAI-Android` ठेवू शकता.
2. शक्य असल्यास repository Private ठेवा.
3. ZIP फोनवर डाउनलोड करून extract करा.
4. Extract केलेल्या `MazhiAI_Android_Project` फोल्डरमधील फाइल्स repository च्या root मध्ये upload करा. ZIP फाइल स्वतः repository मध्ये टाकणे पुरेसे नाही; `.github`, `app`, `backend-template` आणि Gradle फाइल्स repository root मध्ये दिसल्या पाहिजेत.
5. Commit/push झाल्यावर repository च्या **Actions** tab मध्ये `Mazhi AI Android debug build` आणि `Mazhi AI backend checks` workflow दिसतील.

## 2. APK तयार करणे

1. GitHub → repository → **Actions** उघडा.
2. `Mazhi AI Android debug build` workflow निवडा.
3. **Run workflow** दाबा.
4. Workflow हिरव्या success स्थितीत पूर्ण झाल्यावर run उघडा.
5. **Artifacts** मधून `MazhiAI-debug-apk` डाउनलोड करा.
6. ZIP-ஐ extract करून `app-debug.apk` Android फोनवर install करा. Android warning வந்தால், APK GitHub workflow-இல் வெற்றிகரமாக உருவானதா எனச் சரிபார்த்து மட்டும் தொடரவும்.

இது debug APK; இது signed store-release APK அல்ல.

## 3. Backend deploy (Render उदाहरणம்)

1. GitHub repository-ஐ Render-இல் இணைக்கவும்.
2. Blueprint/Web Service உருவாக்கும்போது repository root-இல் உள்ள `render.yaml`-ஐ பயன்படுத்தவும்; அல்லது Web Service-இல் Root Directory `backend-template` என அமைக்கவும்.
3. `AI_PROVIDER_API_KEY`-ஐ Render Dashboard → Environment-இல் secret ஆகச் சேர்க்கவும். API key-ஐ GitHub files-இல் சேர்க்க வேண்டாம்.
4. `AI_API_URL` மற்றும் `AI_MODEL` உங்கள் provider account-இல் கிடைக்கும் endpoint/model-க்கு பொருந்துகிறதா சரிபார்க்கவும். தற்போதைய default எல்லா கணக்குகளிலும் கிடைக்கும் என உத்தரவாதமில்லை.
5. Deploy முடிந்ததும் `https://YOUR-SERVICE.onrender.com/health` திறக்கவும்; `{"ok":true,...}` கிடைக்க வேண்டும்.
6. Mazhi AI app → Settings → backend URL-இல் `https://YOUR-SERVICE.onrender.com/chat` சேமிக்கவும்.
7. முதலில் தனிப்பட்ட தகவல் இல்லாத சாதாரண செய்தியால் சோதிக்கவும்.

## 4. பாதுகாப்பு

- API key-ஐ Android APK, GitHub repository, screenshot அல்லது chat-இல் வைக்க வேண்டாம்.
- Backend provider-க்கு ஆன்லைன் செய்தி அனுப்பப்படும்; தனிப்பட்ட குறிப்புகள் app-இல் local ஆகவே இருக்கும்.
- Provider pricing/spending limit-ஐ சரிபார்க்கவும். Free hosting தூங்கலாம் அல்லது பயன்பாட்டு வரம்புகள் இருக்கலாம்.
- `health` பதில் மட்டும் கிடைப்பது provider key/model வேலை செய்கிறது என்பதை உறுதிப்படுத்தாது; உண்மையான non-sensitive chat மூலம் தனியாகச் சோதிக்கவும்.

## 5. தற்போதைய சரிபார்ப்பு

- Backend smoke tests: `cd backend-template && npm test`.
- இந்த workspace-இல் backend tests வெற்றிகரமாக ஓடியது.
- Android APK இங்கு build செய்யப்படவில்லை; Android SDK/Gradle toolchain மற்றும் cloud run தேவை.
- GitHub/Render deploy செய்யப்படவில்லை; அதற்கு உரிய account authorization மற்றும் provider secret தேவை.
