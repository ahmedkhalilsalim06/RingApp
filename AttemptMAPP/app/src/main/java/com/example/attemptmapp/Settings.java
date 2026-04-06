package com.example.attemptmapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Locale;

public class Settings extends AppCompatActivity {

    private TextView tvEmail;
    private Button btnLogout;
    private SwitchCompat switchNotifications;
    private RadioGroup rgTheme;
    private Spinner spinnerLanguage;
    private TextView btnChangeEmail, btnChangePassword, btnPrivacyPolicy, btnAbout;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply saved theme and language BEFORE super.onCreate
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        
        String langCode = prefs.getString("language", "en");
        updateLocale(langCode);
        
        boolean isDarkMode = prefs.getBoolean("dark_mode", false);
        AppCompatDelegate.setDefaultNightMode(isDarkMode ? 
                AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser user = mAuth.getCurrentUser();

        // Initialize Toolbar as Back Button
        Toolbar toolbar = findViewById(R.id.settingsToolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        // Initialize Views
        tvEmail = findViewById(R.id.tvSettingsEmail);
        btnLogout = findViewById(R.id.btnSettingsLogout);
        switchNotifications = findViewById(R.id.switchNotifications);
        rgTheme = findViewById(R.id.rgTheme);
        spinnerLanguage = findViewById(R.id.spinnerLanguage);
        btnChangeEmail = findViewById(R.id.btnChangeEmail);
        btnChangePassword = findViewById(R.id.btnChangePassword);
        btnPrivacyPolicy = findViewById(R.id.btnPrivacyPolicy);
        btnAbout = findViewById(R.id.btnAbout);

        // Load states
        if (user != null) {
            tvEmail.setText(user.getEmail());
        } else {
            tvEmail.setText(prefs.getString("userEmail", "Not logged in"));
        }
        
        switchNotifications.setChecked(prefs.getBoolean("notifications_enabled", true));
        rgTheme.check(isDarkMode ? R.id.rbDark : R.id.rbLight);

        // Setup Spinner
        String[] languages = {"English", "Turkish", "German", "French"};
        String[] langCodes = {"en", "tr", "de", "fr"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, languages);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerLanguage.setAdapter(adapter);

        for (int i = 0; i < langCodes.length; i++) {
            if (langCodes[i].equals(langCode)) {
                spinnerLanguage.setSelection(i);
                break;
            }
        }

        // Listeners
        btnLogout.setOnClickListener(v -> {
            mAuth.signOut();
            prefs.edit().clear().apply();
            startActivity(new Intent(this, LoginActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish();
        });

        switchNotifications.setOnCheckedChangeListener((b, isChecked) -> 
                prefs.edit().putBoolean("notifications_enabled", isChecked).apply());

        rgTheme.setOnCheckedChangeListener((group, checkedId) -> {
            boolean dark = (checkedId == R.id.rbDark);
            if (dark != prefs.getBoolean("dark_mode", false)) {
                prefs.edit().putBoolean("dark_mode", dark).apply();
                AppCompatDelegate.setDefaultNightMode(dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
            }
        });

        spinnerLanguage.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = langCodes[position];
                if (!selected.equals(prefs.getString("language", "en"))) {
                    prefs.edit().putString("language", selected).apply();
                    // Force activity restart for language
                    finish();
                    startActivity(getIntent());
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnChangeEmail.setOnClickListener(v -> Toast.makeText(this, "Soon...", Toast.LENGTH_SHORT).show());
        btnChangePassword.setOnClickListener(v -> Toast.makeText(this, "Soon...", Toast.LENGTH_SHORT).show());
    }

    private void updateLocale(String langCode) {
        Locale locale = new Locale(langCode);
        Locale.setDefault(locale);
        Resources res = getResources();
        Configuration conf = res.getConfiguration();
        conf.setLocale(locale);
        res.updateConfiguration(conf, res.getDisplayMetrics());
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
