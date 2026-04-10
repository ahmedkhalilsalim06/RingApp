package com.example.attemptmapp;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SearchSystem {
    // Instance variables
    private final MainActivity activity;
    private final EditText searchBar;
    private final RecyclerView searchResultsUI;
    private final Map<String, List<MainActivity.BusArrival>> stopSchedules;
    private final GoogleMap map;
    private final SearchAdapter adapter;
    private final List<String> searchResults = new ArrayList<>();

    // Constructor
    public SearchSystem(MainActivity activity, EditText searchBar, RecyclerView searchResultsUI,
                        Map<String, List<MainActivity.BusArrival>> stopSchedules, GoogleMap map) {
        this.activity = activity;
        this.searchBar = searchBar;
        this.searchResultsUI = searchResultsUI;
        this.stopSchedules = stopSchedules;
        this.map = map;

        this.searchResultsUI.setLayoutManager(new LinearLayoutManager(activity.getApplicationContext()));
        this.adapter = new SearchAdapter();
        this.searchResultsUI.setAdapter(adapter);

        setupListeners();
    }

    // *Listeners*
    private void setupListeners() {
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            // Perform a search every time the text changes
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                performSearch(s.toString().trim());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // Performs a search when user clicks the search bar and the search bar already had some text
        searchBar.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && !searchBar.getText().toString().isEmpty()) {
                performSearch(searchBar.getText().toString().trim());
            }
        });

        // Hide the keyboard when the user presses enter
        searchBar.setOnEditorActionListener((v, actionId, event) -> {
            activity.hideKeyboard();
            return true;
        });
    }

    // Searches for bus stops based on the given query
    public void performSearch(String query) {
        searchResults.clear();
        if (query.isEmpty()) {
            searchResultsUI.setVisibility(View.GONE);
            return;
        }

        // Loop through every bus stop and check if query is a part of its name
        String queryLowercase = query.toLowerCase();
        for (String stopName : stopSchedules.keySet()) {
            if (stopName.toLowerCase().contains(queryLowercase)) {
                searchResults.add(stopName);
            }
        }

        // Update the search results
        if (searchResults.isEmpty()) {
            searchResultsUI.setVisibility(View.GONE);
        } else {
            searchResultsUI.setVisibility(View.VISIBLE);
            adapter.notifyDataSetChanged();
        }
    }

    // Clears the search text and hides the search results and mobile keyboard
    public void clearAndHide() {
        searchBar.setText("");
        searchResultsUI.setVisibility(View.GONE);
        activity.hideKeyboard();
    }

    // Adapter class to make and update ViewHolders for the search results
    private class SearchAdapter extends RecyclerView.Adapter<SearchAdapter.ViewHolder> {
        // Creates a new ViewHolder
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_search_result, parent, false));
        }

        // Updates the contents of the ViewHolder
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            // Set the bus stop name and type
            String stopName = searchResults.get(position);
            holder.name.setText(stopName);
            holder.type.setText("Bilkent University Stop");

            // Shows the bus stop on the map when the entire row is clicked
            holder.itemView.setOnClickListener(v -> {
                LatLng pos = activity.getLatLngForStop(stopName);
                if (pos != null && map != null) {
                    clearAndHide();
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 17f));
                    activity.showBusStopBottomSheet(stopName);
                }
            });

            // Shows the bus stop on the map when the "Show on Map" button is clicked
            holder.showButton.setOnClickListener(v -> holder.itemView.performClick());
        }

        // Getter
        @Override public int getItemCount() { return searchResults.size(); }

        // Container for showing the bus stop results
        class ViewHolder extends RecyclerView.ViewHolder {
            // Instance variables
            TextView name, type;
            View showButton;

            // Constructor
            ViewHolder(View v) {
                super(v);
                name = v.findViewById(R.id.stopName);
                type = v.findViewById(R.id.stopType);
                showButton = v.findViewById(R.id.showOnMapButton);
            }
        }
    }
}
