package com.example.attemptmapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class SignUpActivity extends AppCompatActivity {
    // Instance variables
    private EditText nameField, emailField, passwordField, confirmPasswordField;
    private Button signUpButton;
    private TextView loginText;
    private FirebaseAuth auth;
    private DatabaseReference database;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        // Initialize firebase
        auth = FirebaseAuth.getInstance();
        database = FirebaseDatabase.getInstance("https://bilkent-bus-tracker-default-rtdb.europe-west1.firebasedatabase.app/").getReference();

        // Initialize the UI components
        nameField = findViewById(R.id.nameField);
        emailField = findViewById(R.id.emailField);
        passwordField = findViewById(R.id.passwordField);
        confirmPasswordField = findViewById(R.id.confirmPasswordField);
        signUpButton = findViewById(R.id.signUpButton);
        loginText = findViewById(R.id.loginText);

        // Listener for the sign up button
        signUpButton.setOnClickListener(v -> {
            signUpUser();
        });

        // Listener for login button
        loginText.setOnClickListener(v -> {
            startActivity(new Intent(SignUpActivity.this, LoginActivity.class));
            finish();
        });
    }

    // Handles sign up functionality
    private void signUpUser() {
        String name = nameField.getText().toString().trim();
        String email = emailField.getText().toString().trim();
        String password = passwordField.getText().toString().trim();
        String confirmPassword = confirmPasswordField.getText().toString().trim();

        // Check if all the fields are filled
        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check if the password and confirm password match
        if (!password.equals(confirmPassword)) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
            return;
        }

        // Password must be at least 6 characters long
        if (password.length() < 6) {
            Toast.makeText(this, "Password should be at least 6 characters", Toast.LENGTH_SHORT).show();
            return;
        }

        signUpButton.setEnabled(false);

        // Try to create the new account
        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = auth.getCurrentUser();
                        if (user != null) {
                            sendEmailVerification(user, name);
                        }
                    } else {
                        // Display the error message to user
                        signUpButton.setEnabled(true);
                        Toast.makeText(SignUpActivity.this, "Authentication failed: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // Sends verification email to the user
    private void sendEmailVerification(FirebaseUser user, String name) {
        user.sendEmailVerification()
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // If the email was sent successfully, save the user to the database
                        saveUserToDatabase(user.getUid(), name, user.getEmail());
                        Toast.makeText(SignUpActivity.this,
                                "Verification email sent to " + user.getEmail() + ". Please verify and then login.",
                                Toast.LENGTH_LONG).show();
                        auth.signOut();
                        startActivity(new Intent(SignUpActivity.this, LoginActivity.class));
                        finish();
                    } else {
                        // Display error
                        signUpButton.setEnabled(true);
                        Toast.makeText(SignUpActivity.this,
                                "Failed to send verification email.",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // Saves the user to the database
    private void saveUserToDatabase(String userId, String name, String email) {
        String role = "student";

        Map<String, Object> userMap = new HashMap<>();
        userMap.put("name", name);
        userMap.put("email", email);
        userMap.put("role", role);

        database.child("users").child(userId).setValue(userMap);
    }
}
