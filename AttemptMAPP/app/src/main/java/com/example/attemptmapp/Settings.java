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
    // Instance variables
    private TextView email;
    private Button logoutButton;
    private SwitchCompat notificationSwitch;
    private RadioGroup appThemeRadio;
    private Spinner languageSpinner;
    private TextView changeEmailText, changePasswordText, privacyPolicyText, aboutAppText;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply the saved theme and language BEFORE running super.onCreate
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        
        String langCode = prefs.getString("language", "en");
        updateLocale(langCode);
        
        boolean isDarkMode = prefs.getBoolean("dark_mode", false);
        AppCompatDelegate.setDefaultNightMode(isDarkMode ? 
                AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Initialize firebase
        mAuth = FirebaseAuth.getInstance();
        FirebaseUser user = mAuth.getCurrentUser();

        // Initialize the Toolbar with the back button
        Toolbar toolbar = findViewById(R.id.settingsToolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        
        // Handles navigation back to the main map screen
        toolbar.setNavigationOnClickListener(v -> navigateBack());

        // Initialize Views
        email = findViewById(R.id.SettingsEmail);
        logoutButton = findViewById(R.id.logoutButton);
        notificationSwitch = findViewById(R.id.notificationSwitch);
        appThemeRadio = findViewById(R.id.themeRadio);
        languageSpinner = findViewById(R.id.languageSpinner);
        changeEmailText = findViewById(R.id.changeEmailText);
        changePasswordText = findViewById(R.id.changePasswordText);
        privacyPolicyText = findViewById(R.id.privacyPolicyText);
        aboutAppText = findViewById(R.id.aboutAppText);

        // Load the states
        if (user != null) {
            email.setText(user.getEmail());
        } else {
            email.setText(prefs.getString("userEmail", "Not logged in"));
        }

        // Set the notifications and theme state based on previously saved preferences
        notificationSwitch.setChecked(prefs.getBoolean("notifications_enabled", true));
        appThemeRadio.check(isDarkMode ? R.id.rbDark : R.id.rbLight);

        // Setup the language spinner
        String[] languages = {"English", "Turkish", "Russian"};
        String[] langCodes = {"en", "tr", "ru"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, languages);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        languageSpinner.setAdapter(adapter);

        // set the initial value of the spinner to the language saved in the preferences
        int selectedIndex = 0;
        for (int i = 0; i < langCodes.length; i++) {
            if (langCodes[i].equals(langCode)) {
                selectedIndex = i;
                break;
            }
        }
        languageSpinner.setSelection(selectedIndex);

        // *Listeners*
        logoutButton.setOnClickListener(v -> {
            // Handles logout functionality
            mAuth.signOut();
            prefs.edit().clear().apply();
            startActivity(new Intent(this, LoginActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish();
        });

        // Handles the notification state change (changes boolean state)
        notificationSwitch.setOnCheckedChangeListener((b, isChecked) ->
                prefs.edit().putBoolean("notifications_enabled", isChecked).apply());

        // Handles theme change and applies the new theme
        appThemeRadio.setOnCheckedChangeListener((group, checkedId) -> {
            boolean dark = (checkedId == R.id.rbDark);
            if (dark != prefs.getBoolean("dark_mode", false)) {
                prefs.edit().putBoolean("dark_mode", dark).apply();
                AppCompatDelegate.setDefaultNightMode(dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
                // Restart to apply the theme
                recreate();
            }
        });

        // Handles language change and then applies the newly selected language
        languageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = langCodes[position];
                if (!selected.equals(prefs.getString("language", "en"))) {
                    prefs.edit().putString("language", selected).apply();
                    updateLocale(selected);
                    // restart the activity to show changes
                    recreate();
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Possible future ideas: allow the user to change their email and password
        changeEmailText.setOnClickListener(v -> Toast.makeText(this, "Soon...", Toast.LENGTH_SHORT).show());
        changePasswordText.setOnClickListener(v -> Toast.makeText(this, "Soon...", Toast.LENGTH_SHORT).show());
    }

    // Changes the locale based on the selected language to show the UI in that language
    private void updateLocale(String langCode) {
        Locale locale = new Locale(langCode);
        Locale.setDefault(locale);
        Resources res = getResources();
        Configuration conf = res.getConfiguration();
        conf.setLocale(locale);
        res.updateConfiguration(conf, res.getDisplayMetrics());
    }

    // Handles exiting the settings screen and navigation back to the main map screen
    private void navigateBack() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        // Handles what happens when you press the mobile's back button
        navigateBack();
    }

    @Override
    public boolean onSupportNavigateUp() {
        // Handles what happens when the back button is pressed (top left)
        navigateBack();
        return true;
    }
}
