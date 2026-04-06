package com.example.attemptmapp;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
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
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
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
    private LocationCallback driverLocationCallback;
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);

    private EditText etSearch;
    private RecyclerView rvSearchResults;
    private SearchSystem searchSystem;
    private ImageButton btnSettings, btnHome, btnFavorites;
    private Marker activeMarker;
    private BottomSheetDialog currentBottomSheet;
    
    private Polyline currentBusPolyline;
    private final OkHttpClient httpClient = new OkHttpClient();
    
    // Firebase Tracking
    private DatabaseReference dbRef;
    private String userRole = "";
    private final Map<String, Marker> busMarkers = new HashMap<>();
    private final Set<String> persistentVisibleBuses = new HashSet<>();
    private boolean isMapReady = false;

    // Bilkent University Coordinates
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

    public final Map<String, List<BusArrival>> stopSchedules = new HashMap<>();
    private final Map<String, Integer> busColors = new HashMap<>();

    public static class BusArrival implements Comparable<BusArrival> {
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
            dbRef = FirebaseDatabase.getInstance(DB_URL).getReference("live_buses");
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
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
                .setMinUpdateIntervalMillis(2000)
                .build();

        driverLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                if (dbRef != null) {
                    android.location.Location location = locationResult.getLastLocation();
                    if (location != null) {
                        Map<String, Object> map = new HashMap<>();
                        map.put("lat", location.getLatitude());
                        map.put("lng", location.getLongitude());
                        map.put("timestamp", System.currentTimeMillis());
                        dbRef.child(userRole).setValue(map);
                    }
                }
            }
        };

        locationClient.requestLocationUpdates(locationRequest, driverLocationCallback, Looper.getMainLooper());
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
                        if (Math.abs(System.currentTimeMillis() - time) < 300000) {
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

        List<BusArrival> d91 = new ArrayList<>();
        int[] ringHours = {8, 9, 10, 11, 13};
        for (int h : ringHours) d91.add(new BusArrival("Ring", h, 0));
        for (int h = 8; h <= 23; h++) {
            d91.add(new BusArrival("Tunus", h, 30));
            d91.add(new BusArrival("Sihhiye", h, 30));
        }
        stopSchedules.put("Dorm 91", d91);

        List<BusArrival> d92 = new ArrayList<>();
        for (int h : ringHours) d92.add(new BusArrival("Ring", h, 3));
        for (int h = 8; h <= 23; h++) {
            d92.add(new BusArrival("Tunus", h, 33));
            d92.add(new BusArrival("Sihhiye", h, 33));
        }
        stopSchedules.put("Dorm 92", d92);

        List<BusArrival> mescit = new ArrayList<>();
        for (int h = 8; h <= 23; h++) mescit.add(new BusArrival("Ring", h, 0));
        for (int h = 8; h <= 17; h++) {
            mescit.add(new BusArrival("Tunus", h, 0));
            mescit.add(new BusArrival("Tunus", h, 30));
        }
        for (int h = 18; h <= 23; h++) mescit.add(new BusArrival("Tunus", h, 0));
        stopSchedules.put("Mescit bus stop", mescit);

        List<BusArrival> bilka = new ArrayList<>();
        for (int h = 8; h <= 23; h++) {
            bilka.add(new BusArrival("Ring", h, 2));
            bilka.add(new BusArrival("Tunus", h, 35));
        }
        stopSchedules.put("Bilka hill bus stop", bilka);

        List<BusArrival> lib = new ArrayList<>();
        int[] libRing = {8, 9, 10, 12, 13};
        for (int h : libRing) lib.add(new BusArrival("Ring", h, 10));
        for (int h = 8; h <= 23; h++) {
            lib.add(new BusArrival("Tunus", h, 50));
            lib.add(new BusArrival("Sihhiye", h, 50));
        }
        stopSchedules.put("Kütüphane", lib);

        List<BusArrival> niz = new ArrayList<>();
        for (int h = 8; h <= 23; h++) {
            niz.add(new BusArrival("Ring", h, 15));
            niz.add(new BusArrival("Tunus", h, 55));
            niz.add(new BusArrival("Sihhiye", h, 55));
        }
        stopSchedules.put("Nizamiye", niz);

        for (List<BusArrival> list : stopSchedules.values()) Collections.sort(list);
    }

    private void bindViews() {
        etSearch = findViewById(R.id.etSearch);
        rvSearchResults = findViewById(R.id.rvSearchResults);
        btnHome = findViewById(R.id.btnHome);
        btnFavorites = findViewById(R.id.btnFavorites);
        btnSettings = findViewById(R.id.btnSettings);
    }

    private void setupButtons() {
        btnHome.setOnClickListener(v -> {
            if (searchSystem != null) searchSystem.clearAndHide();
            if (mMap != null) mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(BILKENT_UNIVERSITY, DEFAULT_ZOOM));
        });
        btnFavorites.setOnClickListener(v -> {
            if (searchSystem != null) searchSystem.clearAndHide();
            startActivity(new Intent(MainActivity.this, FavoritesActivity.class));
        });
        btnSettings.setOnClickListener(v -> {
            if (searchSystem != null) searchSystem.clearAndHide();
            startActivity(new Intent(MainActivity.this, Settings.class));
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        checkShowStopIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkShowStopIntent(getIntent());
    }

    private void checkShowStopIntent(Intent intent) {
        if (intent != null && intent.hasExtra("show_stop")) {
            String stopName = intent.getStringExtra("show_stop");
            LatLng pos = getLatLngForStop(stopName);
            if (pos != null && mMap != null) {
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 17f));
                showBusStopBottomSheet(stopName);
            }
            intent.removeExtra("show_stop");
        }
    }

    public LatLng getLatLngForStop(String stopName) {
        if ("Dorm 91".equals(stopName)) return STOP_DORM91;
        if ("Dorm 92".equals(stopName)) return STOP_DORM92;
        if ("Mescit bus stop".equals(stopName)) return STOP_MESCIT;
        if ("Bilka hill bus stop".equals(stopName)) return STOP_BILKA_HILL;
        if ("Kütüphane".equals(stopName)) return STOP_KUTUPHANE;
        if ("Nizamiye".equals(stopName)) return STOP_NIZAMIYE;
        return null;
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap; isMapReady = true;

        // Apply dark mode style if enabled
        int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
            try {
                mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style_dark));
            } catch (Exception e) {
                Log.e(TAG, "Can't find style. Error: ", e);
            }
        }

        searchSystem = new SearchSystem(this, etSearch, rvSearchResults, stopSchedules, mMap);
        mMap.setPadding(0, 220, 0, 160);
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);
        mMap.getUiSettings().setMapToolbarEnabled(false);
        enableLocationOnMap();

        mMap.setOnMapClickListener(latLng -> {
            if (currentBottomSheet != null && currentBottomSheet.isShowing()) {
                currentBottomSheet.dismiss();
            }
        });

        addBilkentBusStops();
        startStudentListening();
        checkShowStopIntent(getIntent());
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
            if (activeMarker != null && marker.equals(activeMarker)) { 
                activeMarker.remove(); 
                activeMarker = null; 
                if (currentBusPolyline != null) currentBusPolyline.remove(); 
                return true; 
            }
            marker.showInfoWindow(); return false;
        });
    }

    public void showBusStopBottomSheet(String stopName) {
        if (userRole.startsWith("driver_")) return;
        
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_bus_stop, null);
        TextView tvRemaining = view.findViewById(R.id.tvRemainingTime);
        TextView tvNextArrival = view.findViewById(R.id.tvNextArrivalTime);
        LinearLayout container = view.findViewById(R.id.llScheduleContainer);
        View btnFavorite = view.findViewById(R.id.btnFavorite);

        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        Set<String> favorites = new HashSet<>(prefs.getStringSet("favorites", new HashSet<>()));
        
        boolean isFavorite = favorites.contains(stopName);
        if (btnFavorite != null) {
            btnFavorite.setAlpha(isFavorite ? 1.0f : 0.4f);
            btnFavorite.setOnClickListener(v -> {
                if (favorites.contains(stopName)) {
                    favorites.remove(stopName);
                    btnFavorite.setAlpha(0.4f);
                    Toast.makeText(this, "Removed from favorites", Toast.LENGTH_SHORT).show();
                } else {
                    favorites.add(stopName);
                    btnFavorite.setAlpha(1.0f);
                    Toast.makeText(this, "Added to favorites", Toast.LENGTH_SHORT).show();
                }
                prefs.edit().putStringSet("favorites", favorites).apply();
            });
        }

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
                
                if (btnLocation != null) {
                    btnLocation.setOnClickListener(v -> {
                        if (busMarkers.containsKey(driverId)) {
                            Marker m = busMarkers.get(driverId);
                            if (m != null) {
                                if (persistentVisibleBuses.contains(driverId)) {
                                    persistentVisibleBuses.remove(driverId);
                                    m.setVisible(false);
                                    Toast.makeText(this, "Live location hidden", Toast.LENGTH_SHORT).show();
                                } else {
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
                }

                View btnTrack = row.findViewById(R.id.ivTrack);
                if (btnTrack != null) {
                    btnTrack.setOnClickListener(v -> { 
                        fetchBusTrajectory(b.busName); 
                        if(currentBottomSheet != null) currentBottomSheet.dismiss(); 
                    });
                }
                
                container.addView(row);
            }
        }

        if (currentBottomSheet != null && currentBottomSheet.isShowing()) currentBottomSheet.dismiss();
        
        currentBottomSheet = new BottomSheetDialog(this);
        currentBottomSheet.setContentView(view);
        
        Window window = currentBottomSheet.getWindow();
        if (window != null) {
            // Find the ID dynamically to avoid compile-time errors with R.id.design_bottom_sheet
            int bottomSheetId = getResources().getIdentifier("design_bottom_sheet", "id", "com.google.android.material");
            if (bottomSheetId != 0) {
                View bottomSheet = window.findViewById(bottomSheetId);
                if (bottomSheet != null) {
                    bottomSheet.setBackgroundResource(android.R.color.transparent);
                }
            }
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

    private boolean locationPermissionGranted() { return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED; }
    private void askForLocationPermission() { ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_CODE); }
    private void enableLocationOnMap() { if (mMap != null && locationPermissionGranted()) { try { mMap.setMyLocationEnabled(true); } catch (SecurityException ignored) {} } }
    public void hideKeyboard() { InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE); View view = getCurrentFocus(); if (view != null && imm != null) { imm.hideSoftInputFromWindow(view.getWindowToken(), 0); view.clearFocus(); } }
    
    @Override public void onRequestPermissionsResult(int r, @NonNull String[] p, @NonNull int[] g) { 
        super.onRequestPermissionsResult(r, p, g); 
        if (r == PERMISSION_REQUEST_CODE && g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) {
            enableLocationOnMap();
            if (userRole.startsWith("driver_")) {
                startDriverTracking();
            }
        } 
    }
    
    @Override protected void onDestroy() { 
        super.onDestroy(); 
        if (locationClient != null && driverLocationCallback != null) {
            locationClient.removeLocationUpdates(driverLocationCallback);
        }
        executorService.shutdown();
    }
}
