package com.sentinel;

import android.app.Activity;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.os.Build;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    
    private static final String API_URL = "https://silver-space-adventure-97rj6r6g45wp2947-8000.app.github.dev/api/v1/telemetry";
    
    private Button scanButton;
    private ProgressBar progressBar;
    private TextView resultText;
    private TextView trustScoreText;
    private TextView riskLevelText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        scanButton = findViewById(R.id.scanButton);
        progressBar = findViewById(R.id.progressBar);
        resultText = findViewById(R.id.resultText);
        trustScoreText = findViewById(R.id.trustScoreText);
        riskLevelText = findViewById(R.id.riskLevelText);
        
        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scanDevice();
            }
        });
    }
    
    private void scanDevice() {
        scanButton.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        resultText.setText("Collecting device data...");
        trustScoreText.setText("");
        riskLevelText.setText("");
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject telemetry = new JSONObject();
                    telemetry.put("device_id", Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID));
                    telemetry.put("model", Build.MODEL);
                    telemetry.put("android_version", Build.VERSION.RELEASE);
                    telemetry.put("fingerprint", Build.FINGERPRINT);
                    telemetry.put("bootloader_status", Build.BOOTLOADER.contains("unlocked") ? "UNLOCKED" : "LOCKED");
                    telemetry.put("safety_net_result", "UNKNOWN");
                    telemetry.put("installed_apps_count", getPackageManager().getInstalledApplications(0).size());
                    telemetry.put("timestamp", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(new Date()));
                    
                    URL url = new URL(API_URL);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);
                    
                    OutputStream os = conn.getOutputStream();
                    os.write(telemetry.toString().getBytes("UTF-8"));
                    os.close();
                    
                    int responseCode = conn.getResponseCode();
                    if (responseCode == 200) {
                        java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(conn.getInputStream()));
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        reader.close();
                        
                        final JSONObject result = new JSONObject(response.toString());
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                int trustScore = result.getInt("trust_score");
                                String riskLevel = result.getString("risk_level");
                                String recommendation = result.getString("recommendation");
                                
                                trustScoreText.setText(String.valueOf(trustScore));
                                riskLevelText.setText(riskLevel);
                                resultText.setText(recommendation);
                                
                                if (trustScore >= 80) {
                                    trustScoreText.setTextColor(0xFF4CAF50);
                                } else if (trustScore >= 50) {
                                    trustScoreText.setTextColor(0xFFFF9800);
                                } else {
                                    trustScoreText.setTextColor(0xFFF44336);
                                }
                            }
                        });
                    } else {
                        final String error = "Server error: " + responseCode;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                resultText.setText(error);
                            }
                        });
                    }
                    
                } catch (Exception e) {
                    final String error = "Error: " + e.getMessage();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            resultText.setText(error);
                        }
                    });
                } finally {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressBar.setVisibility(View.GONE);
                            scanButton.setEnabled(true);
                        }
                    });
                }
            }
        }).start();
    }
}
