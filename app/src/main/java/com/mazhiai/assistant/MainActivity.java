package com.mazhiai.assistant;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.Color;
import android.util.Base64;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int SPEECH_REQUEST = 41;
    private static final int MAX_HISTORY_MESSAGES = 100;
    private static final int MAX_RESPONSE_CHARS = 65536;
    private LinearLayout messages;
    private EditText input;
    private ScrollView scroll;
    private SharedPreferences prefs;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private Button sendButton;
    private Button micButton;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("mazhi_ai_private", MODE_PRIVATE);
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true;
                String savedLanguage = prefs.getString("language", "mr-IN");
                Locale locale = "hi-IN".equals(savedLanguage) ? new Locale("hi", "IN") : ("en-US".equals(savedLanguage) ? Locale.US : new Locale("mr", "IN"));
                int result = tts.setLanguage(locale);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.setLanguage(Locale.getDefault());
                }
        }});
        buildUi();
        loadHistory();
        if (messages.getChildCount() == 0) {
            addMessage("Mazhi AI", "नमस्कार! मी Mazhi AI. 😊 सेटिंग्जमध्ये सुरक्षित AI सर्व्हर जोडल्यावर ऑनलाइन उत्तरं मिळू शकतील.");
            saveHistory();
        }
    }

    private void buildUi() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.rgb(246,248,252));
        page.setPadding(dp(14),dp(8),dp(14),dp(10));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("Mazhi AI"); title.setTextSize(25);
        title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        title.setTextColor(Color.rgb(30,65,120));
        top.addView(title,new LinearLayout.LayoutParams(0,dp(44),1));
        Button memory = new Button(this); memory.setText("🧠");
        memory.setContentDescription("फोनमधील वैयक्तिक आठवणी"); memory.setOnClickListener(v -> showLocalMemory());
        top.addView(memory,new LinearLayout.LayoutParams(dp(54),dp(44)));
        Button settings = new Button(this); settings.setText("⚙");
        settings.setContentDescription("सेटिंग्ज"); settings.setOnClickListener(v -> showSettings());
        top.addView(settings,new LinearLayout.LayoutParams(dp(54),dp(44)));
        page.addView(top);

        TextView subtitle = new TextView(this);
        subtitle.setText("तुझा वैयक्तिक AI साथी • आठवणी या फोनमध्ये");
        subtitle.setTextSize(13); subtitle.setTextColor(Color.DKGRAY); page.addView(subtitle);

        scroll = new ScrollView(this);
        messages = new LinearLayout(this); messages.setOrientation(LinearLayout.VERTICAL);
        messages.setPadding(0,dp(12),0,dp(12)); scroll.addView(messages);
        page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout controls = new LinearLayout(this); controls.setOrientation(LinearLayout.HORIZONTAL);
        input = new EditText(this); input.setMinLines(1); input.setMaxLines(4); input.setHint("इथे लिही...");
        controls.addView(input,new LinearLayout.LayoutParams(0,dp(52),1));
        micButton = new Button(this); micButton.setText("🎙"); micButton.setContentDescription("आवाजाने बोला");
        micButton.setOnClickListener(v -> startSpeech()); controls.addView(micButton,new LinearLayout.LayoutParams(dp(54),dp(52)));
        sendButton = new Button(this); sendButton.setText("पाठव"); sendButton.setOnClickListener(v -> sendMessage());
        controls.addView(sendButton,new LinearLayout.LayoutParams(dp(76),dp(52)));
        page.addView(controls);
        setContentView(page);
    }

    private void sendMessage() {
        String text = input.getText().toString().trim();
        if (text.isEmpty()) return;
        input.setText("");
        addMessage("तू",text); saveHistory();
        ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);
        String endpoint = prefs.getString("endpoint", "").trim();
        if (endpoint.isEmpty()) {
            deliverReply(offlineReply(text));
            return;
        }
        setBusy(true);
        final TextView placeholder = addMessage("Mazhi AI", "उत्तर शोधतेय…");
        new Thread(() -> {
            String answer = null;
            String error = null;
            HttpURLConnection conn = null;
            try {
                URL url = new URL(endpoint);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(12000); conn.setReadTimeout(20000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setRequestProperty("Accept", "application/json");
                JSONObject payload = new JSONObject(); payload.put("message", text);
                byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream out = conn.getOutputStream()) { out.write(body); }
                int code = conn.getResponseCode();
                InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
                StringBuilder response = new StringBuilder();
                if (stream != null) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (response.length() + line.length() > MAX_RESPONSE_CHARS) {
                                throw new IllegalStateException("AI response too large");
                            }
                            response.append(line);
                        }
                    }
                }
                if (code >= 200 && code < 300) {
                    JSONObject obj = new JSONObject(response.toString());
                    answer = obj.optString("reply", "").trim();
                    if (answer.isEmpty()) error = "सर्व्हरच्या उत्तरात 'reply' मजकूर मिळाला नाही.";
                } else error = "AI सर्व्हरने HTTP " + code + " उत्तर दिलं.";
            } catch (Exception e) {
                error = "AI सर्व्हरशी जोडता आलं नाही. इंटरनेट आणि सर्व्हर URL तपास.";
            } finally { if (conn != null) conn.disconnect(); }
            final String finalAnswer = answer;
            final String finalError = error;
            runOnUiThread(() -> {
                messages.removeView(placeholder);
                setBusy(false);
                if (finalAnswer != null && !finalAnswer.isEmpty()) deliverReply(finalAnswer);
                else deliverReply((finalError == null ? "ऑनलाइन उत्तर मिळालं नाही." : finalError) + "\n\nऑफलाइन मदत: " + offlineReply(text));
            });
        }).start();
    }

    private void deliverReply(String answer) {
        addMessage("Mazhi AI", answer); saveHistory();
        if (ttsReady) tts.speak(answer,TextToSpeech.QUEUE_FLUSH,null,"mazhi_ai_reply");
    }

    private void showSettings() {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(20),dp(8),dp(20),0);
        Button language = new Button(this);
        String currentLanguage = prefs.getString("language", "mr-IN");
        language.setText("भाषा / Language: " + languageName(currentLanguage));
        language.setOnClickListener(v -> showLanguagePicker()); box.addView(language);
        TextView privacy = new TextView(this);
        privacy.setText("वैयक्तिक आठवणी 🧠 फक्त या फोनमध्ये जतन होतात. त्या ऑनलाइन AI कडे पाठवल्या जात नाहीत. ऑनलाइन चॅटमधील संदेश मात्र उत्तर मिळवण्यासाठी तुझ्या निवडलेल्या सर्व्हरकडे जातो.");
        privacy.setTextSize(14); box.addView(privacy);
        TextView help = new TextView(this);
        help.setText("तुझ्या सुरक्षित backend चा HTTPS URL दे. हा endpoint POST {\"message\":\"...\"} स्वीकारून {\"reply\":\"...\"} परत करायला हवा. API key APK मध्ये टाकू नकोस.");
        box.addView(help);
        EditText endpoint = new EditText(this); endpoint.setSingleLine(true); endpoint.setHint("https://your-server.example/chat");
        endpoint.setInputType( android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI );
        endpoint.setText(prefs.getString("endpoint", "")); box.addView(endpoint);
        new AlertDialog.Builder(this).setTitle("Mazhi AI सेटिंग्ज").setView(box)
            .setPositiveButton("जतन करा", (d,w) -> {
                String value = endpoint.getText().toString().trim();
                if (!value.isEmpty()) {
                    try {
                        URL parsed = new URL(value);
                        if (!"https".equalsIgnoreCase(parsed.getProtocol()) || parsed.getHost() == null || parsed.getHost().trim().isEmpty() || parsed.getUserInfo() != null) {
                            throw new IllegalArgumentException("HTTPS URL required");
                        }
                    } catch (Exception invalidUrl) {
                        Toast.makeText(this,"योग्य HTTPS सर्व्हर URL दे. URL मध्ये username/password ठेवू नकोस.",Toast.LENGTH_LONG).show(); return;
                    }
                }
                prefs.edit().putString("endpoint",value).apply();
                Toast.makeText(this,value.isEmpty()?"ऑफलाइन मोड सुरू":"AI सर्व्हर URL जतन केला",Toast.LENGTH_SHORT).show();
            })
            .setNeutralButton("चॅट इतिहास हटवा", (d,w) -> new AlertDialog.Builder(this)
                .setTitle("इतिहास हटवायचा?").setMessage("फोनमध्ये जतन केलेला चॅट इतिहास कायमचा हटवला जाईल.")
                .setNegativeButton("रद्द",null).setPositiveButton("हटवा",(dd,ww)->{
                    prefs.edit().remove("history").apply(); messages.removeAllViews();
                    addMessage("Mazhi AI","इतिहास हटवला. पुन्हा सुरुवात करूया. ❤️"); saveHistory();
                }).show())
            .setNegativeButton("बंद",null).show();
    }

    private String languageName(String code) {
        if ("hi-IN".equals(code)) return "हिन्दी";
        if ("en-US".equals(code)) return "English";
        return "मराठी";
    }

    private void showLanguagePicker() {
        final String[] names = {"मराठी", "हिन्दी", "English"};
        final String[] codes = {"mr-IN", "hi-IN", "en-US"};
        String current = prefs.getString("language", "mr-IN");
        int selected = "hi-IN".equals(current) ? 1 : ("en-US".equals(current) ? 2 : 0);
        new AlertDialog.Builder(this).setTitle("भाषा निवडा / Choose language")
            .setSingleChoiceItems(names, selected, (dialog, which) -> {
                prefs.edit().putString("language", codes[which]).apply();
                if (ttsReady) {
                    Locale locale = which == 1 ? new Locale("hi", "IN") : (which == 2 ? Locale.US : new Locale("mr", "IN"));
                    tts.setLanguage(locale);
                }
                dialog.dismiss(); showSettings();
            }).setNegativeButton("रद्द", null).show();
    }

    private void showLocalMemory() {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(8), dp(18), 0);
        TextView explanation = new TextView(this);
        explanation.setText("इथे स्वतःसाठी नोंदी ठेव. या नोंदी फक्त या फोनमध्ये जतन होतील; ऑनलाइन AI ला पाठवल्या जाणार नाहीत.");
        box.addView(explanation);
        EditText notes = new EditText(this); notes.setMinLines(5); notes.setGravity(Gravity.TOP);
        notes.setHint("उदा. मला मराठीत उत्तरं आवडतात…");
        notes.setText(prefs.getString("local_memory", "")); box.addView(notes);
        new AlertDialog.Builder(this).setTitle("माझ्या वैयक्तिक आठवणी")
            .setView(box).setPositiveButton("फोनमध्ये जतन करा", (d,w) -> {
                prefs.edit().putString("local_memory", notes.getText().toString()).apply();
                Toast.makeText(this, "आठवणी या फोनमध्ये जतन झाल्या", Toast.LENGTH_SHORT).show();
            })
            .setNeutralButton("आठवणी पुसा", (d,w) -> new AlertDialog.Builder(this)
                .setTitle("आठवणी पुसायच्या?").setMessage("या फोनमधील वैयक्तिक नोंदी हटतील.")
                .setNegativeButton("रद्द", null).setPositiveButton("पुसा", (dd,ww) -> {
                    prefs.edit().remove("local_memory").apply();
                    Toast.makeText(this, "वैयक्तिक आठवणी हटवल्या", Toast.LENGTH_SHORT).show();
                }).show())
            .setNegativeButton("रद्द", null).show();
    }

    private String offlineReply(String text) {
        String q = text.toLowerCase(Locale.ROOT);
        if (q.contains("नमस्कार") || q.contains("hello") || q.equals("hi") || q.contains("भाऊ")) return "अरे भावा, बोल ना! 😊 सध्या ऑफलाइन प्राथमिक मोड आहे.";
        if (q.contains("आई") || q.contains("बाबा")) return "आई-बाबांची काळजी घेणं छान आहे. तुला कोणत्या गोष्टीत मदत हवी आहे ते सांग.";
        if (q.contains("नाव")) return "माझं नाव Mazhi AI ❤️";
        if (q.contains("कसं आहेस") || q.contains("कशी आहेस")) return "मी तयार आहे, भावा! 😊 तू कसा आहेस?";
        return "तुझा संदेश मिळाला. सध्या ऑनलाइन AI सर्व्हर सेट केलेला नसेल तर माझी उत्तरं मर्यादित असतील. सेटिंग्जमध्ये सुरक्षित HTTPS backend जोड.";
    }

    private void startSpeech() {
        try {
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            String lang = prefs.getString("language", "mr-IN");
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
            i.putExtra(RecognizerIntent.EXTRA_PROMPT, "en-US".equals(lang) ? "Speak" : ("hi-IN".equals(lang) ? "हिन्दी में बोलें" : "मराठीत बोला"));
            startActivityForResult(i,SPEECH_REQUEST);
        } catch(Exception e) { addMessage("Mazhi AI","या फोनवर आवाज ओळखण्याची सुविधा उपलब्ध नाही किंवा बंद आहे."); }
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==SPEECH_REQUEST && resultCode==RESULT_OK && data!=null) {
            ArrayList<String> r=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if(r!=null&&!r.isEmpty()){input.setText(r.get(0));input.setSelection(input.length());}
        }
    }
    private TextView addMessage(String who,String text) {
        TextView bubble = new TextView(this); bubble.setText(who+"\n"+text); bubble.setTextSize(16);
        bubble.setTextColor(Color.rgb(30,35,45)); bubble.setPadding(dp(12),dp(10),dp(12),dp(10));
        bubble.setBackgroundColor(who.equals("तू")?Color.rgb(220,235,255):Color.WHITE);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.bottomMargin=dp(8); messages.addView(bubble,lp);
        scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));
        return bubble;
    }
    private void setBusy(boolean busy) {
        sendButton.setEnabled(!busy); sendButton.setText(busy ? "..." : "पाठव");
        input.setEnabled(!busy); micButton.setEnabled(!busy);
    }

    // Base64 line records preserve newlines and Marathi characters in messages.
    private void saveHistory() {
        StringBuilder b=new StringBuilder();
        int first = Math.max(0, messages.getChildCount() - MAX_HISTORY_MESSAGES);
        for(int i=first;i<messages.getChildCount();i++) if(messages.getChildAt(i) instanceof TextView) {
            String s=((TextView)messages.getChildAt(i)).getText().toString();
            String encoded=Base64.encodeToString(s.getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP);
            b.append(encoded).append('\n');
        }
        prefs.edit().putString("history",b.toString()).apply();
    }
    private void loadHistory() {
        String h=prefs.getString("history",""); if(h.isEmpty())return;
        String[] lines = h.split("\n");
        int first = Math.max(0, lines.length - MAX_HISTORY_MESSAGES);
        for(int index=first; index<lines.length; index++) {
            String line = lines[index];
            if(line.trim().isEmpty())continue;
            try {
                String s=new String(Base64.decode(line,Base64.DEFAULT),StandardCharsets.UTF_8);
                int n=s.indexOf('\n'); if(n>0)addMessage(s.substring(0,n),s.substring(n+1));
            } catch(Exception ignored) { /* ignore old/incompatible history rows safely */ }
        }
    }
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
    @Override protected void onDestroy(){if(tts!=null){tts.stop();tts.shutdown();}super.onDestroy();}
}
