package com.example.attemptmapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnLogin;
    private TextView tvSignUp;
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance("https://bilkent-bus-tracker-default-rtdb.europe-west1.firebasedatabase.app/").getReference();

        // RUN SETUP ONCE IF NEEDED (Uncomment, run app once, then re-comment):
        // setupDriverAccounts();

        FirebaseUser currentUser = mAuth.getCurrentUser();
        // Drivers (@bilkent.edu.tr) don't need verification check on resume
        if (currentUser != null && (currentUser.isEmailVerified() || (currentUser.getEmail() != null && currentUser.getEmail().endsWith("@bilkent.edu.tr")))) {
            fetchUserRoleAndStartMain(currentUser.getUid());
            return;
        }

        setContentView(R.layout.activity_login);

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvSignUp = findViewById(R.id.tvSignUp);

        btnLogin.setOnClickListener(v -> {
            loginUser();
        });

        tvSignUp.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, SignUpActivity.class));
        });
    }

    private void setupDriverAccounts() {
        String[][] drivers = {
                {"tunus@bilkent.edu.tr", "tunus123", "driver_tunus", "Tunus Driver"},
                {"sihhiye@bilkent.edu.tr", "sihhiye123", "driver_sihhiye", "Sihhiye Driver"},
                {"ring@bilkent.edu.tr", "ring123", "driver_ring", "Main Ring Driver"}
        };

        for (String[] driver : drivers) {
            String email = driver[0];
            String password = driver[1];
            String role = driver[2];
            String name = driver[3];

            mAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            FirebaseUser user = mAuth.getCurrentUser();
                            if (user != null) {
                                Map<String, Object> userMap = new HashMap<>();
                                userMap.put("name", name);
                                userMap.put("email", email);
                                userMap.put("role", role);
                                mDatabase.child("users").child(user.getUid()).setValue(userMap);
                                Log.d("Setup", "Created: " + email);
                                mAuth.signOut(); 
                            }
                        } else {
                            Log.e("Setup", "Failed for " + email + ": " + task.getException().getMessage());
                        }
                    });
        }
    }

    private void loginUser() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        btnLogin.setEnabled(false);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            // Drivers (@bilkent.edu.tr) don't need verification, others do
                            if (user.isEmailVerified() || (user.getEmail() != null && user.getEmail().endsWith("@bilkent.edu.tr"))) {
                                fetchUserRoleAndStartMain(user.getUid());
                            } else {
                                btnLogin.setEnabled(true);
                                Toast.makeText(this, "Please verify your email first.", Toast.LENGTH_LONG).show();
                                mAuth.signOut();
                            }
                        }
                    } else {
                        btnLogin.setEnabled(true);
                        Toast.makeText(LoginActivity.this, "Authentication failed: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void fetchUserRoleAndStartMain(String userId) {
        mDatabase.child("users").child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String role = snapshot.child("role").getValue(String.class);
                if (role == null) role = "student";

                SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString("userRole", role);
                editor.apply();

                startActivity(new Intent(LoginActivity.this, MainActivity.class));
                finish();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                btnLogin.setEnabled(true);
                Toast.makeText(LoginActivity.this, "Database error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
