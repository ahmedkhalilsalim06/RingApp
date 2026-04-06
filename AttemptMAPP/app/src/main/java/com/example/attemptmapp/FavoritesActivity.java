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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FavoritesActivity extends AppCompatActivity {

    private RecyclerView rvFavorites;
    private FavoritesAdapter adapter;
    private TextView tvEmpty;
    private ImageButton btnBack, btnHome, btnFavorites, btnSettings;
    private Set<String> favorites;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorites);

        rvFavorites = findViewById(R.id.rvFavorites);
        tvEmpty = findViewById(R.id.tvEmpty);
        btnBack = findViewById(R.id.btnBack);
        btnHome = findViewById(R.id.btnHome);
        btnFavorites = findViewById(R.id.btnFavorites);
        btnSettings = findViewById(R.id.btnSettings);

        // Highlight the current screen in bottom nav
        btnFavorites.setAlpha(1.0f);
        btnHome.setAlpha(0.6f);
        btnSettings.setAlpha(0.6f);

        loadFavorites();

        rvFavorites.setLayoutManager(new LinearLayoutManager(this));
        updateList();

        btnBack.setOnClickListener(v -> finish());
        btnHome.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });
        btnSettings.setOnClickListener(v -> {
            startActivity(new Intent(this, Settings.class));
            finish();
        });
        
        // Settings for Favorites button (already here)
        btnFavorites.setOnClickListener(v -> {
            // Already here, maybe scroll to top
            rvFavorites.smoothScrollToPosition(0);
        });
    }

    private void loadFavorites() {
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        favorites = prefs.getStringSet("favorites", new HashSet<>());
        
        // For demonstration, if empty, add some defaults if you want, 
        // but typically it should be empty.
        // if (favorites.isEmpty()) {
        //    favorites.add("Mescit bus stop");
        //    favorites.add("Dorm 91");
        // }
    }

    private void updateList() {
        if (favorites.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            rvFavorites.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            rvFavorites.setVisibility(View.VISIBLE);
            adapter = new FavoritesAdapter(new ArrayList<>(favorites));
            rvFavorites.setAdapter(adapter);
        }
    }

    class FavoritesAdapter extends RecyclerView.Adapter<FavoritesAdapter.ViewHolder> {
        private List<String> items;

        FavoritesAdapter(List<String> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_favorite, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String stopName = items.get(position);
            holder.tvStopName.setText(stopName);
            holder.btnShowOnMap.setOnClickListener(v -> {
                Intent intent = new Intent(FavoritesActivity.this, MainActivity.class);
                intent.putExtra("show_stop", stopName);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvStopName;
            ImageButton btnShowOnMap;

            ViewHolder(View itemView) {
                super(itemView);
                tvStopName = itemView.findViewById(R.id.tvStopName);
                btnShowOnMap = itemView.findViewById(R.id.btnShowOnMap);
            }
        }
    }
}
