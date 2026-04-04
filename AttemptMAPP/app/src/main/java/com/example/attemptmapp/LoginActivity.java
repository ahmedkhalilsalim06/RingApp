package com.example.attemptmapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {

    private EditText etLoginCode;
    private Button btnLogin;
    private TextView tvSignUp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        if (prefs.contains("userRole")) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_login);

        etLoginCode = findViewById(R.id.etLoginCode);
        btnLogin = findViewById(R.id.btnLogin);
        tvSignUp = findViewById(R.id.tvSignUp);

        btnLogin.setOnClickListener(v -> {
            String input = etLoginCode.getText().toString().trim();
            String code = input.toLowerCase();
            String userRole = "";

            if (code.equals("student")) {
                userRole = "student";
            } else if (code.equals("tunus") || code.equals("ring") || code.equals("sihhiye")) {
                userRole = "driver_" + code;
            }

            if (!userRole.isEmpty()) {
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString("userRole", userRole);
                editor.apply();

                startActivity(new Intent(LoginActivity.this, MainActivity.class));
                finish();
            } else {
                Toast.makeText(this, "Invalid code. Use 'student', 'Tunus', 'Ring', or 'Sihhiye'", Toast.LENGTH_LONG).show();
            }
        });

        tvSignUp.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, SignUpActivity.class));
        });
    }
}
