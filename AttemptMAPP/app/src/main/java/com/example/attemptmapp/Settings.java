package com.example.attemptmapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class Settings extends AppCompatActivity {

    private TextView tvName, tvEmail, tvRole;
    private Button btnLogout, btnBackToMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        tvName = findViewById(R.id.tvSettingsName);
        tvEmail = findViewById(R.id.tvSettingsEmail);
        tvRole = findViewById(R.id.tvSettingsRole);
        btnLogout = findViewById(R.id.btnSettingsLogout);
        btnBackToMap = findViewById(R.id.btnBackToMap);

        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        String name = prefs.getString("userName", "N/A");
        String email = prefs.getString("userEmail", "N/A");
        String role = prefs.getString("userRole", "N/A");

        tvName.setText("Name: " + name);
        tvEmail.setText("Email: " + email);
        tvRole.setText("Role: " + role);

        btnBackToMap.setOnClickListener(v -> {
            finish(); // Since MainActivity is usually in the back stack
        });

        btnLogout.setOnClickListener(v -> {
            prefs.edit().clear().apply();
            Intent intent = new Intent(Settings.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }
}
