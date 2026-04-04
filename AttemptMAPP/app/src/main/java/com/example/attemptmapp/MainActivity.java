package com.example.attemptmapp;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.maps.android.PolyUtil;

import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final float DEFAULT_ZOOM = 15f;
    private static final String TAG = "BusTracker";
    private static final String DB_URL = "https://bilkent-bus-tracker-default-rtdb.europe-west1.firebasedatabase.app/";

    private GoogleMap mMap;
    private FusedLocationProviderClient locationClient;
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);

    private EditText etSearch;
    private ImageButton btnSettings, btnHome, btnFavorites;
    private Marker activeMarker;
    private BottomSheetDialog currentBottomSheet;
    
    private Polyline currentPolyline;
    private Polyline currentBusPolyline;
    private final OkHttpClient httpClient = new OkHttpClient();
    private String travelMode = "driving";
    
    // Firebase Tracking
    private FirebaseDatabase database;
    private DatabaseReference dbRef;
    private String userRole = "";
    private final Map<String, Marker> busMarkers = new HashMap<>();
    private final Set<String> persistentVisibleBuses = new HashSet<>();
    private final Handler trackerHandler = new Handler(Looper.getMainLooper());
    private Runnable trackerRunnable;
    private boolean isMapReady = false;

    // Bilkent University Coordinates (Main Campus)
    private final LatLng BILKENT_UNIVERSITY = new LatLng(39.8682, 32.7487);

    // Stop Coordinates
    private final LatLng STOP_DORM91 = new LatLng(39.86869237025446, 32.763616574588);
    private final LatLng STOP_DORM92 = new LatLng(39.8694869378631, 32.76261873828766);
    private final LatLng STOP_MESCIT = new LatLng(39.86770849854348, 32.7511017991731);
    private final LatLng STOP_BILKA_HILL = new LatLng(39.86526670742294, 32.74824902002951);
    private final LatLng STOP_KUTUPHANE = new LatLng(39.88105736313171, 32.754873699180145);
    private final LatLng STOP_NIZAMIYE = new LatLng(39.86657, 32.74831);
    
    private final LatLng DEST_TUNUS = new LatLng(39.9117, 32.8544);
    private final LatLng DEST_SIHHIYE = new LatLng(39.9298, 32.8530);
    private final LatLng WAY_ASTI = new LatLng(39.9168, 32.8122);
    private final LatLng WAY_BAHCELIEVLER = new LatLng(39.9213, 32.8228);
    private final LatLng WAY_MALTEPE = new LatLng(39.9250, 32.8430);

    private final Map<String, List<BusArrival>> stopSchedules = new HashMap<>();
    private final Map<String, Integer> busColors = new HashMap<>();

    static class BusArrival implements Comparable<BusArrival> {
        String busName;
        int hour;
        int minute;

        BusArrival(String busName, int hour, int minute) {
            this.busName = busName;
            this.hour = hour;
            this.minute = minute;
        }

        int getAbsoluteMinutes() { return hour * 60 + minute; }
        String getTimeString() { return String.format(Locale.getDefault(), "%02d:%02d", hour, minute); }

        @Override
        public int compareTo(BusArrival other) {
            return Integer.compare(this.getAbsoluteMinutes(), other.getAbsoluteMinutes());
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        userRole = prefs.getString("userRole", "");
        
        if (userRole.isEmpty()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);
        
        try {
            database = FirebaseDatabase.getInstance(DB_URL);
            dbRef = database.getReference("live_buses");
        } catch (Exception e) {
            Log.e(TAG, "Firebase setup failed: " + e.getMessage());
        }
        
        locationClient = LocationServices.getFusedLocationProviderClient(this);
        initBusData();
        bindViews();
        setupButtons();
        
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapFragment);
        if (mapFragment != null) mapFragment.getMapAsync(this);
        askForLocationPermission();

        if (userRole.startsWith("driver_")) {
            startDriverTracking();
        }
    }

    private void startDriverTracking() {
        if (trackerRunnable != null) trackerHandler.removeCallbacks(trackerRunnable);
        trackerRunnable = new Runnable() {
            @Override
            public void run() {
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    locationClient.getLastLocation().addOnSuccessListener(location -> {
                        if (location != null && dbRef != null) {
                            Map<String, Object> map = new HashMap<>();
                            map.put("lat", location.getLatitude());
                            map.put("lng", location.getLongitude());
                            map.put("timestamp", System.currentTimeMillis());
                            dbRef.child(userRole).setValue(map);
                        }
                    });
                }
                trackerHandler.postDelayed(this, 3000);
            }
        };
        trackerHandler.post(trackerRunnable);
    }

    private void startStudentListening() {
        if (dbRef == null) return;
        dbRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isMapReady) return;
                
                for (DataSnapshot bus : snapshot.getChildren()) {
                    String id = bus.getKey();
                    if (id == null) continue;
                    Double lat = bus.child("lat").getValue(Double.class);
                    Double lng = bus.child("lng").getValue(Double.class);
                    Long time = bus.child("timestamp").getValue(Long.class);
                    if (lat != null && lng != null && time != null) {
                        if (System.currentTimeMillis() - time < 120000) {
                            updateLiveMarker(id, new LatLng(lat, lng));
                        } else {
                            removeLiveMarker(id);
                        }
                    }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void updateLiveMarker(String id, LatLng pos) {
        runOnUiThread(() -> {
            if (mMap == null) return;
            if (busMarkers.containsKey(id)) {
                Marker marker = busMarkers.get(id);
                if (marker != null) {
                    marker.setPosition(pos);
                    // Visibility is determined by persistentVisibleBuses
                    marker.setVisible(persistentVisibleBuses.contains(id));
                }
            } else {
                String label = id.replace("driver_", "").toUpperCase() + " (LIVE)";
                boolean isPersistent = persistentVisibleBuses.contains(id);
                Marker m = mMap.addMarker(new MarkerOptions()
                        .position(pos)
                        .title(label)
                        .anchor(0.5f, 0.5f)
                        .zIndex(999)
                        .icon(createRedCircleIcon())
                        .visible(isPersistent)); 
                busMarkers.put(id, m);
            }
        });
    }

    private BitmapDescriptor createRedCircleIcon() {
        int size = 100;
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setColor(Color.WHITE);
        c.drawCircle(size/2f, size/2f, size/2f, p);
        p.setColor(Color.RED);
        c.drawCircle(size/2f, size/2f, (size/2f)-10, p);
        return BitmapDescriptorFactory.fromBitmap(b);
    }

    private void removeLiveMarker(String id) {
        runOnUiThread(() -> {
            if (busMarkers.containsKey(id)) {
                Marker marker = busMarkers.get(id);
                if (marker != null) marker.remove();
                busMarkers.remove(id);
            }
        });
    }

    private void initBusData() {
        busColors.put("Ring", Color.RED); 
        busColors.put("Tunus", Color.MAGENTA);
        busColors.put("Sihhiye", Color.BLUE);

        // Dorm 91
        List<BusArrival> d91 = new ArrayList<>();
        int[] ringHours = {8, 9, 10, 11, 13};
        for (int h : ringHours) d91.add(new BusArrival("Ring", h, 0));
        for (int h = 8; h <= 23; h++) {
            d91.add(new BusArrival("Tunus", h, 30));
            d91.add(new BusArrival("Sihhiye", h, 30));
        }
        stopSchedules.put("Dorm 91", d91);

        // Dorm 92
        List<BusArrival> d92 = new ArrayList<>();
        for (int h : ringHours) d92.add(new BusArrival("Ring", h, 3));
        for (int h = 8; h <= 23; h++) {
            d92.add(new BusArrival("Tunus", h, 33));
            d92.add(new BusArrival("Sihhiye", h, 33));
        }
        stopSchedules.put("Dorm 92", d92);

        // Mescit
        List<BusArrival> mescit = new ArrayList<>();
        for (int h = 8; h <= 23; h++) mescit.add(new BusArrival("Ring", h, 0));
        for (int h = 8; h <= 17; h++) {
            mescit.add(new BusArrival("Tunus", h, 0));
            mescit.add(new BusArrival("Tunus", h, 30));
        }
        for (int h = 18; h <= 23; h++) mescit.add(new BusArrival("Tunus", h, 0));
        stopSchedules.put("Mescit bus stop", mescit);

        // Bilka hill
        List<BusArrival> bilka = new ArrayList<>();
        for (int h = 8; h <= 23; h++) {
            bilka.add(new BusArrival("Ring", h, 2));
            bilka.add(new BusArrival("Tunus", h, 35));
        }
        stopSchedules.put("Bilka hill bus stop", bilka);

        // Kütüphane
        List<BusArrival> lib = new ArrayList<>();
        int[] libRing = {8, 9, 10, 12, 13};
        for (int h : libRing) lib.add(new BusArrival("Ring", h, 10));
        for (int h = 8; h <= 23; h++) {
            lib.add(new BusArrival("Tunus", h, 50));
            lib.add(new BusArrival("Sihhiye", h, 50));
        }
        stopSchedules.put("Kütüphane", lib);

        // Nizamiye
        List<BusArrival> niz = new ArrayList<>();
        for (int h = 8; h <= 23; h++) {
            niz.add(new BusArrival("Ring", h, 15));
            niz.add(new BusArrival("Tunus", h, 55));
            niz.add(new BusArrival("Sihhiye", h, 55));
        }
        stopSchedules.put("Nizamiye", niz);

        // Sort all
        for (List<BusArrival> list : stopSchedules.values()) Collections.sort(list);
    }

    private void bindViews() {
        etSearch = findViewById(R.id.etSearch);
        btnHome = findViewById(R.id.btnHome);
        btnFavorites = findViewById(R.id.btnFavorites);
        btnSettings = findViewById(R.id.btnSettings);
    }

    private void setupButtons() {
        btnHome.setOnClickListener(v -> { if (mMap != null) mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(BILKENT_UNIVERSITY, DEFAULT_ZOOM)); });
        btnFavorites.setOnClickListener(v -> Toast.makeText(this, "Favorites coming soon!", Toast.LENGTH_SHORT).show());
        btnSettings.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, Settings.class)));
        etSearch.setOnEditorActionListener((v, actionId, event) -> { searchForLocation(etSearch.getText().toString().trim()); return true; });
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap; isMapReady = true;
        mMap.setPadding(0, 220, 0, 160);
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);
        mMap.getUiSettings().setMapToolbarEnabled(false);
        enableLocationOnMap();

        mMap.setOnMapClickListener(latLng -> {
            if (currentBottomSheet != null && currentBottomSheet.isShowing()) {
                currentBottomSheet.dismiss();
            }
            executorService.execute(() -> {
                String address = getAddressFromLatLng(latLng);
                runOnUiThread(() -> dropPin(latLng, "Dropped Pin", address));
            });
        });

        addBilkentBusStops();
        startStudentListening();
    }

    private void addBilkentBusStops() {
        LatLng[] coords = {STOP_DORM91, STOP_DORM92, STOP_MESCIT, STOP_BILKA_HILL, STOP_KUTUPHANE, STOP_NIZAMIYE};
        String[] names = {"Dorm 91", "Dorm 92", "Mescit bus stop", "Bilka hill bus stop", "Kütüphane", "Nizamiye"};
        for (int i = 0; i < coords.length; i++) {
            mMap.addMarker(new MarkerOptions().position(coords[i]).title(names[i]).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)));
        }
        mMap.setOnMarkerClickListener(marker -> {
            String title = marker.getTitle();
            if (title != null && stopSchedules.containsKey(title)) { showBusStopBottomSheet(title); return true; }
            if (activeMarker != null && marker.equals(activeMarker)) { activeMarker.remove(); activeMarker = null; if (currentPolyline != null) currentBusPolyline.remove(); return true; }
            marker.showInfoWindow(); return false;
        });
    }

    private void showBusStopBottomSheet(String stopName) {
        if (userRole.startsWith("driver_")) return;
        
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_bus_stop, null);
        TextView tvRemaining = view.findViewById(R.id.tvRemainingTime);
        TextView tvNextArrival = view.findViewById(R.id.tvNextArrivalTime);
        LinearLayout container = view.findViewById(R.id.llScheduleContainer);

        List<BusArrival> schedule = stopSchedules.get(stopName);
        if (schedule == null) return;

        Calendar now = Calendar.getInstance();
        int currentMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

        List<BusArrival> upcoming = new ArrayList<>();
        for (BusArrival arrival : schedule) {
            if (arrival.getAbsoluteMinutes() >= currentMin) upcoming.add(arrival);
        }

        if (upcoming.isEmpty()) {
            tvRemaining.setText("No more buses today");
            tvNextArrival.setText("End of service");
        } else {
            BusArrival next = upcoming.get(0);
            int diff = next.getAbsoluteMinutes() - currentMin;
            tvRemaining.setText(diff + " min remaining");
            tvNextArrival.setText("Next: " + next.getTimeString());

            for (BusArrival b : upcoming) {
                View row = LayoutInflater.from(this).inflate(R.layout.item_schedule_row, container, false);
                ((TextView) row.findViewById(R.id.tvTime)).setText(b.getTimeString());
                ((TextView) row.findViewById(R.id.tvBusName)).setText(b.busName);
                
                String driverId = "driver_" + b.busName.toLowerCase();
                View btnLocation = row.findViewById(R.id.ivLocation);
                
                btnLocation.setOnClickListener(v -> {
                    if (busMarkers.containsKey(driverId)) {
                        Marker m = busMarkers.get(driverId);
                        if (m != null) {
                            if (persistentVisibleBuses.contains(driverId)) {
                                // Already visible, hide it
                                persistentVisibleBuses.remove(driverId);
                                m.setVisible(false);
                                Toast.makeText(this, "Live location hidden", Toast.LENGTH_SHORT).show();
                            } else {
                                // Not visible, show it
                                persistentVisibleBuses.add(driverId);
                                m.setVisible(true);
                                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(m.getPosition(), 16f));
                                Toast.makeText(this, "Live location shown", Toast.LENGTH_SHORT).show();
                            }
                            if (currentBottomSheet != null) currentBottomSheet.dismiss();
                        }
                    } else {
                        Toast.makeText(this, "Driver for " + b.busName + " is not live", Toast.LENGTH_SHORT).show();
                    }
                });

                row.findViewById(R.id.ivTrack).setOnClickListener(v -> { 
                    fetchBusTrajectory(b.busName); 
                    if(currentBottomSheet != null) currentBottomSheet.dismiss(); 
                });
                
                container.addView(row);
            }
        }

        if (currentBottomSheet != null && currentBottomSheet.isShowing()) currentBottomSheet.dismiss();
        
        currentBottomSheet = new BottomSheetDialog(this);
        currentBottomSheet.setContentView(view);
        
        View bottomSheetInternal = currentBottomSheet.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheetInternal != null) {
            bottomSheetInternal.setBackgroundColor(Color.TRANSPARENT);
            ViewGroup.LayoutParams params = bottomSheetInternal.getLayoutParams();
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            bottomSheetInternal.setLayoutParams(params);
        }
        
        currentBottomSheet.show();
    }

    private void fetchBusTrajectory(String busName) {
        List<LatLng> waypoints = new ArrayList<>();
        LatLng origin = STOP_DORM91, dest = STOP_NIZAMIYE;
        waypoints.add(STOP_DORM92); waypoints.add(STOP_MESCIT); waypoints.add(STOP_BILKA_HILL); waypoints.add(STOP_KUTUPHANE);
        if (busName.contains("Tunus")) { waypoints.add(WAY_ASTI); waypoints.add(WAY_BAHCELIEVLER); dest = DEST_TUNUS; }
        else if (busName.contains("Sihhiye")) { waypoints.add(WAY_ASTI); waypoints.add(WAY_MALTEPE); dest = DEST_SIHHIYE; }
        
        Integer color = busColors.get(busName.replace(" Bus", ""));
        requestRoute(origin, dest, waypoints, color != null ? color : Color.GRAY);
    }

    private void requestRoute(LatLng origin, LatLng dest, List<LatLng> waypoints, int color) {
        String apiKey = getApiKey();
        StringBuilder url = new StringBuilder("https://maps.googleapis.com/maps/api/directions/json?origin=").append(origin.latitude).append(",").append(origin.longitude).append("&destination=").append(dest.latitude).append(",").append(dest.longitude).append("&mode=driving&key=").append(apiKey);
        if (!waypoints.isEmpty()) { url.append("&waypoints=optimize:false"); for (LatLng wp : waypoints) url.append("|").append(wp.latitude).append(",").append(wp.longitude); }
        httpClient.newCall(new Request.Builder().url(url.toString()).build()).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {}
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        JSONObject json = new JSONObject(response.body().string());
                        if (json.getString("status").equals("OK")) {
                            List<LatLng> pathPoints = PolyUtil.decode(json.getJSONArray("routes").getJSONObject(0).getJSONObject("overview_polyline").getString("points"));
                            runOnUiThread(() -> {
                                if (currentBusPolyline != null) currentBusPolyline.remove();
                                currentBusPolyline = mMap.addPolyline(new PolylineOptions().addAll(pathPoints).color(color).width(15).jointType(2).startCap(new com.google.android.gms.maps.model.RoundCap()).endCap(new com.google.android.gms.maps.model.RoundCap()));
                                if (!pathPoints.isEmpty()) mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pathPoints.get(pathPoints.size()/2), 14f));
                            });
                        }
                    } catch (Exception ignored) {}
                }
            }
        });
    }

    private String getApiKey() { try { return getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA).metaData.getString("com.google.android.geo.API_KEY"); } catch (Exception e) { return null; } }
    private void searchForLocation(String q) { 
        if (q.isEmpty()) return; 
        hideKeyboard(); 
        executorService.execute(() -> {
            try { 
                List<Address> r = new Geocoder(this, Locale.getDefault()).getFromLocationName(q, 1); 
                if (r != null && !r.isEmpty()) { 
                    LatLng p = new LatLng(r.get(0).getLatitude(), r.get(0).getLongitude()); 
                    String address = r.get(0).getAddressLine(0);
                    runOnUiThread(() -> { dropPin(p, q, address); mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(p, DEFAULT_ZOOM)); });
                } 
            } catch (IOException ignored) {} 
        });
    }
    private void dropPin(LatLng p, String t, String s) { 
        if (activeMarker != null) activeMarker.remove(); 
        activeMarker = mMap.addMarker(new MarkerOptions().position(p).title(t).snippet(s).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))); 
        calculateDirections(p); 
    }
    private void calculateDirections(LatLng d) { 
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        locationClient.getLastLocation().addOnSuccessListener(l -> { 
            if (l != null) { 
                String u = "https://maps.googleapis.com/maps/api/directions/json?origin=" + l.getLatitude() + "," + l.getLongitude() + "&destination=" + d.latitude + "," + d.longitude + "&mode=" + travelMode + "&key=" + getApiKey(); 
                httpClient.newCall(new Request.Builder().url(u).build()).enqueue(new Callback() { 
                    @Override public void onFailure(@NonNull Call c, @NonNull IOException e) {} 
                    @Override public void onResponse(@NonNull Call c, @NonNull Response r) throws IOException { 
                        if (r.isSuccessful() && r.body() != null) { 
                            try { 
                                List<LatLng> p = PolyUtil.decode(new JSONObject(r.body().string()).getJSONArray("routes").getJSONObject(0).getJSONObject("overview_polyline").getString("points")); 
                                runOnUiThread(() -> { if (currentPolyline != null) currentPolyline.remove(); currentPolyline = mMap.addPolyline(new PolylineOptions().addAll(p).color(Color.BLUE).width(12)); }); 
                            } catch (Exception ignored) {} 
                        } 
                    } 
                }); 
            } 
        }); 
    }
    private String getAddressFromLatLng(LatLng l) { 
        try { List<Address> adds = new Geocoder(this, Locale.getDefault()).getFromLocation(l.latitude, l.longitude, 1); if (adds != null && !adds.isEmpty()) return adds.get(0).getAddressLine(0); } catch (Exception e) { return "Unknown"; } return "Unknown"; 
    }
    private boolean locationPermissionGranted() { return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED; }
    private void askForLocationPermission() { ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_CODE); }
    private void enableLocationOnMap() { if (mMap != null && locationPermissionGranted()) { try { mMap.setMyLocationEnabled(true); } catch (SecurityException ignored) {} } }
    private void hideKeyboard() { InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE); View view = getCurrentFocus(); if (view != null && imm != null) { imm.hideSoftInputFromWindow(view.getWindowToken(), 0); view.clearFocus(); } }
    @Override public void onRequestPermissionsResult(int r, @NonNull String[] p, @NonNull int[] g) { super.onRequestPermissionsResult(r, p, g); if (r == PERMISSION_REQUEST_CODE && g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) enableLocationOnMap(); }
    @Override protected void onDestroy() { super.onDestroy(); if (trackerHandler != null && trackerRunnable != null) trackerHandler.removeCallbacks(trackerRunnable); executorService.shutdown(); }
}
