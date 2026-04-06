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
    private final MainActivity activity;
    private final EditText etSearch;
    private final RecyclerView rvSearchResults;
    private final Map<String, List<MainActivity.BusArrival>> stopSchedules;
    private final GoogleMap mMap;
    private final SearchAdapter adapter;
    private final List<String> searchResults = new ArrayList<>();

    public SearchSystem(MainActivity activity, EditText etSearch, RecyclerView rvSearchResults,
                        Map<String, List<MainActivity.BusArrival>> stopSchedules, GoogleMap mMap) {
        this.activity = activity;
        this.etSearch = etSearch;
        this.rvSearchResults = rvSearchResults;
        this.stopSchedules = stopSchedules;
        this.mMap = mMap;

        this.rvSearchResults.setLayoutManager(new LinearLayoutManager(activity));
        this.adapter = new SearchAdapter();
        this.rvSearchResults.setAdapter(adapter);

        setupListeners();
    }

    private void setupListeners() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                performSearch(s.toString().trim());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        etSearch.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && !etSearch.getText().toString().isEmpty()) {
                performSearch(etSearch.getText().toString().trim());
            }
        });

        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            activity.hideKeyboard();
            return true;
        });
    }

    public void performSearch(String query) {
        searchResults.clear();
        if (query.isEmpty()) {
            rvSearchResults.setVisibility(View.GONE);
            return;
        }

        String queryLower = query.toLowerCase();
        for (String stopName : stopSchedules.keySet()) {
            if (stopName.toLowerCase().contains(queryLower)) {
                searchResults.add(stopName);
            }
        }

        if (searchResults.isEmpty()) {
            rvSearchResults.setVisibility(View.GONE);
        } else {
            rvSearchResults.setVisibility(View.VISIBLE);
            adapter.notifyDataSetChanged();
        }
    }

    public void clearAndHide() {
        etSearch.setText("");
        rvSearchResults.setVisibility(View.GONE);
        activity.hideKeyboard();
    }

    private class SearchAdapter extends RecyclerView.Adapter<SearchAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_search_result, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String stopName = searchResults.get(position);
            holder.tvName.setText(stopName);
            holder.tvType.setText("Bilkent University Stop");

            holder.itemView.setOnClickListener(v -> {
                LatLng pos = activity.getLatLngForStop(stopName);
                if (pos != null && mMap != null) {
                    clearAndHide();
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 17f));
                    activity.showBusStopBottomSheet(stopName);
                }
            });

            holder.btnShow.setOnClickListener(v -> holder.itemView.performClick());
        }

        @Override public int getItemCount() { return searchResults.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvType;
            View btnShow;
            ViewHolder(View v) {
                super(v);
                tvName = v.findViewById(R.id.tvStopName);
                tvType = v.findViewById(R.id.tvStopType);
                btnShow = v.findViewById(R.id.btnShowOnMap);
            }
        }
    }
}
