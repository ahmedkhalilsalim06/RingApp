package com.example.attemptmapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FavoritesActivity extends AppCompatActivity {
    // Instance variables
    private RecyclerView favoritesUI;
    private FavoritesAdapter adapter;
    private TextView emptyText;
    private ImageButton backButton, homeButton, favoritesButton, settingsButton;
    private Set<String> favorites;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorites);

        // Initialize Views
        favoritesUI = findViewById(R.id.favoritesUI);
        emptyText = findViewById(R.id.emptyText);
        backButton = findViewById(R.id.backButton);
        homeButton = findViewById(R.id.homeButton);
        favoritesButton = findViewById(R.id.favoritesButton);
        settingsButton = findViewById(R.id.settingsButton);

        // Highlight the favorites icon in the bottom navigation by changing the alpha values
        favoritesButton.setAlpha(1.0f);
        homeButton.setAlpha(0.6f);
        settingsButton.setAlpha(0.6f);

        loadFavorites();

        favoritesUI.setLayoutManager(new LinearLayoutManager(this));
        updateList();

        // Listeners for navigation buttons
        backButton.setOnClickListener(v -> finish()); // back button functionality
        homeButton.setOnClickListener(v -> {
            // Open main map screen
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });
        settingsButton.setOnClickListener(v -> {
            // Open settings
            startActivity(new Intent(this, Settings.class));
            finish();
        });

        favoritesButton.setOnClickListener(v -> {
            // Scrolls back up to the top since we are already in favorites
            favoritesUI.smoothScrollToPosition(0);
        });
    }

    // Load favorite bus stops from stored preferences
    private void loadFavorites() {
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        favorites = prefs.getStringSet("favorites", new HashSet<>());
    }

    // Handles what to show based on if favorites exist
    private void updateList() {
        if (favorites.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            favoritesUI.setVisibility(View.GONE);
        } else {
            emptyText.setVisibility(View.GONE);
            favoritesUI.setVisibility(View.VISIBLE);
            adapter = new FavoritesAdapter(new ArrayList<>(favorites));
            favoritesUI.setAdapter(adapter);
        }
    }

    // Adapter class to make and update view holders for the favorites
    class FavoritesAdapter extends RecyclerView.Adapter<FavoritesAdapter.ViewHolder> {
        // Instance variables
        private List<String> items;

        // Constructor
        FavoritesAdapter(List<String> items) {
            this.items = items;
        }

        // Creates a new view holder
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_favorite, parent, false);
            return new ViewHolder(view);
        }

        // Updates what is displayed on the view holder
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String stopName = items.get(position);
            holder.stopName.setText(stopName);

            // Shows the bus stop on map screen when the show button is clicked
            holder.showButton.setOnClickListener(v -> {
                Intent intent = new Intent(FavoritesActivity.this, MainActivity.class);
                intent.putExtra("show_stop", stopName);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
            });
        }

        // Getter
        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            // Instance variables
            TextView stopName;
            ImageButton showButton;

            // Constructor
            ViewHolder(View itemView) {
                super(itemView);
                stopName = itemView.findViewById(R.id.stopName);
                showButton = itemView.findViewById(R.id.showOnMapButton);
            }
        }
    }
}
