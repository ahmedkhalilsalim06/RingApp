package com.example.attemptmapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
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
    // Instance variables
    private EditText emailField, passwordField;
    private Button loginButton;
    private TextView signUpText;
    private FirebaseAuth auth;
    private DatabaseReference database;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize firebase
        auth = FirebaseAuth.getInstance();
        database = FirebaseDatabase.getInstance("https://bilkent-bus-tracker-default-rtdb.europe-west1.firebasedatabase.app/").getReference();

        // Check if user is already logged in
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null && (currentUser.isEmailVerified() || (currentUser.getEmail() != null && currentUser.getEmail().endsWith("@bilkent.edu.tr")))) {
            fetchUserRoleAndStartMain(currentUser.getUid());
            return;
        }

        setContentView(R.layout.activity_login);

        // Set up the UI elements
        emailField = findViewById(R.id.emailField);
        passwordField = findViewById(R.id.passwordField);
        loginButton = findViewById(R.id.loginButton);
        signUpText = findViewById(R.id.signUpText);

        // Log in button listener
        loginButton.setOnClickListener(v -> {
            loginUser();
        });

        // Sign up button listener
        signUpText.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, SignUpActivity.class));
        });
    }

    // Creates driver accounts and then stores them in the database
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

            // Stores the driver accounts in the database
            auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            FirebaseUser user = auth.getCurrentUser();
                            if (user != null) {
                                Map<String, Object> userMap = new HashMap<>();
                                userMap.put("name", name);
                                userMap.put("email", email);
                                userMap.put("role", role);
                                database.child("users").child(user.getUid()).setValue(userMap);
                                Log.d("Setup", "Created: " + email);
                                auth.signOut();
                            }
                        } else {
                            Log.e("Setup", "Failed for " + email + ": " + task.getException().getMessage());
                        }
                    });
        }
    }

    // Handles log in functionality
    private void loginUser() {
        String email = emailField.getText().toString().trim();
        String password = passwordField.getText().toString().trim();

        // Check if both fields are filled
        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        loginButton.setEnabled(false);

        // Attempt to log in
        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // Check if the user is verified (email verification)
                        FirebaseUser user = auth.getCurrentUser();
                        if (user != null) {
                            if (user.isEmailVerified() || (user.getEmail() != null && user.getEmail().endsWith("@bilkent.edu.tr"))) {
                                // Start the app
                                fetchUserRoleAndStartMain(user.getUid());
                            } else {
                                // Display error message
                                loginButton.setEnabled(true);
                                Toast.makeText(this, "Please verify your email first.", Toast.LENGTH_LONG).show();
                                auth.signOut();
                            }
                        }
                    } else {
                        // Display error message
                        loginButton.setEnabled(true);
                        Toast.makeText(LoginActivity.this, "Authentication failed: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // Gets the user's role from the database, stores it in shared preferences, and then starts main map screen
    private void fetchUserRoleAndStartMain(String userId) {
        database.child("users").child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
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

            // Displays error message if there's an error with fetching the data
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                loginButton.setEnabled(true);
                Toast.makeText(LoginActivity.this, "Database error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
