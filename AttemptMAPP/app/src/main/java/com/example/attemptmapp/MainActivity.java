package com.example.attemptmapp;

import android.Manifest;
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
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
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
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.maps.android.PolyUtil;

import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    
    private Polyline currentPolyline;
    private Polyline currentBusPolyline;
    private final OkHttpClient httpClient = new OkHttpClient();
    private String travelMode = "driving";
    
    // Firebase Tracking
    private FirebaseDatabase database;
    private DatabaseReference dbRef;
    private String userRole = "";
    private final Map<String, Marker> busMarkers = new HashMap<>();
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
    private final LatLng STOP_KUTUPHANE = new LatLng(39.87095, 32.75014); // Library
    private final LatLng STOP_NIZAMIYE = new LatLng(39.86657, 32.74831); // Main Entrance
    
    private final LatLng DEST_TUNUS = new LatLng(39.9117, 32.8544);
    private final LatLng DEST_SIHHIYE = new LatLng(39.9298, 32.8530);
    private final LatLng WAY_ASTI = new LatLng(39.9168, 32.8122);
    private final LatLng WAY_BAHCELIEVLER = new LatLng(39.9213, 32.8228);
    private final LatLng WAY_MALTEPE = new LatLng(39.9250, 32.8430);

    private final Map<String, String[]> stopBusesMap = new HashMap<>();
    private final Map<String, String> busSchedules = new HashMap<>();
    private final Map<String, Integer> busColors = new HashMap<>();

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
            
            database.getReference(".info/connected").addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    Boolean connected = snapshot.getValue(Boolean.class);
                    if (connected != null && connected) Log.d(TAG, "STATUS: CONNECTED");
                    else Log.d(TAG, "STATUS: OFFLINE");
                }
                @Override public void onCancelled(@NonNull DatabaseError error) {}
            });
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
        } else {
            startStudentListening();
        }
    }

    private void startDriverTracking() {
        if (trackerRunnable != null) trackerHandler.removeCallbacks(trackerRunnable);
        
        trackerRunnable = new Runnable() {
            @Override
            public void run() {
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    locationClient.getLastLocation().addOnSuccessListener(location -> {
                        if (location != null) {
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
                }
            } else {
                String label = id.replace("driver_", "").toUpperCase() + " (LIVE)";
                Marker m = mMap.addMarker(new MarkerOptions()
                        .position(pos)
                        .title(label)
                        .anchor(0.5f, 0.5f)
                        .zIndex(999)
                        .icon(createRedCircleIcon()));
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
                if (marker != null) {
                    marker.remove();
                }
                busMarkers.remove(id);
            }
        });
    }

    private void initBusData() {
        stopBusesMap.put("Dorm 91", new String[]{"Ring Bus", "Tunus Bus", "Sihhiye Bus"});
        busSchedules.put("Dorm 91_Ring Bus", "8:00, 9:00, 10:00, 11:00, 13:00");
        busSchedules.put("Dorm 91_Tunus Bus", "Every 30 (8:30-23:30)");
        busSchedules.put("Dorm 91_Sihhiye Bus", "Every 30 (8:30-23:30)");

        stopBusesMap.put("Dorm 92", new String[]{"Ring Bus", "Tunus Bus", "Sihhiye Bus"});
        busSchedules.put("Dorm 92_Ring Bus", "8:03, 9:03, 10:03, 11:03, 13:03");
        busSchedules.put("Dorm 92_Tunus Bus", "Every 32-33 (8:33-23:33)");
        busSchedules.put("Dorm 92_Sihhiye Bus", "Every 32-33 (8:33-23:33)");

        stopBusesMap.put("Mescit bus stop", new String[]{"Tunus Bus", "Ring Bus"});
        busSchedules.put("Mescit bus stop_Tunus Bus", "Every 30 (8:30-17:30), every hr (18:00-23:00)");
        busSchedules.put("Mescit bus stop_Ring Bus", "Every hour");

        stopBusesMap.put("Bilka hill bus stop", new String[]{"Ring Bus", "Tunus Bus"});
        
        stopBusesMap.put("Kütüphane", new String[]{"Ring Bus", "Tunus Bus", "Sihhiye Bus"});
        stopBusesMap.put("Nizamiye", new String[]{"Ring Bus", "Tunus Bus", "Sihhiye Bus"});

        busColors.put("Ring Bus", Color.RED); 
        busColors.put("Tunus Bus", Color.MAGENTA);
        busColors.put("Sihhiye Bus", Color.BLUE);
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
        
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            searchForLocation(etSearch.getText().toString().trim());
            return true;
        });
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
            executorService.execute(() -> {
                String address = getAddressFromLatLng(latLng);
                runOnUiThread(() -> dropPin(latLng, "Dropped Pin", address));
            });
        });
        
        addBilkentBusStops();
        if (userRole.equals("student")) startStudentListening();
    }

    private void addBilkentBusStops() {
        LatLng[] coords = {STOP_DORM91, STOP_DORM92, STOP_MESCIT, STOP_BILKA_HILL, STOP_KUTUPHANE, STOP_NIZAMIYE};
        String[] names = {"Dorm 91", "Dorm 92", "Mescit bus stop", "Bilka hill bus stop", "Kütüphane", "Nizamiye"};
        for (int i = 0; i < coords.length; i++) {
            mMap.addMarker(new MarkerOptions().position(coords[i]).title(names[i]).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)));
        }
        mMap.setOnMarkerClickListener(marker -> {
            String title = marker.getTitle();
            if (title != null && (title.contains("stop") || title.contains("Dorm") || title.equals("Kütüphane") || title.equals("Nizamiye"))) { 
                showBusListDialog(title); return true; 
            }
            if (activeMarker != null && marker.equals(activeMarker)) { activeMarker.remove(); activeMarker = null; if (currentPolyline != null) currentPolyline.remove(); return true; }
            marker.showInfoWindow(); return false;
        });
    }

    private void showBusListDialog(String stopName) {
        if (userRole.startsWith("driver_")) return;
        String[] buses = stopBusesMap.get(stopName); 
        if (buses == null || buses.length == 0) {
            Toast.makeText(this, "No bus information for this stop", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] items = new String[buses.length];
        for (int i = 0; i < buses.length; i++) {
            String busName = buses[i];
            String id = "driver_" + busName.toLowerCase().replace(" ", "");
            String schedule = busSchedules.get(stopName + "_" + busName);
            items[i] = busName + (busMarkers.containsKey(id) ? " - [LIVE]" : " - Sched: " + (schedule != null ? schedule : "See schedule"));
        }
        new AlertDialog.Builder(this).setTitle("Buses at " + stopName).setItems(items, (d, w) -> fetchBusTrajectory(buses[w])).show();
    }

    private void fetchBusTrajectory(String busName) {
        List<LatLng> waypoints = new ArrayList<>();
        LatLng origin = STOP_DORM91;
        
        // common route sequence: Dorm 91 -> Dorm 92 -> Mescit -> Bilka hill -> Kütüphane -> Nizamiye
        waypoints.add(STOP_DORM92);
        waypoints.add(STOP_MESCIT);
        waypoints.add(STOP_BILKA_HILL);
        waypoints.add(STOP_KUTUPHANE);
        LatLng dest = STOP_NIZAMIYE;

        if (busName.contains("Tunus")) {
            waypoints.add(WAY_ASTI);
            waypoints.add(WAY_BAHCELIEVLER);
            dest = DEST_TUNUS;
        } else if (busName.contains("Sihhiye")) {
            waypoints.add(WAY_ASTI);
            waypoints.add(WAY_MALTEPE);
            dest = DEST_SIHHIYE;
        }
        
        Integer color = busColors.get(busName);
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
                    runOnUiThread(() -> {
                        dropPin(p, q, address); 
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(p, DEFAULT_ZOOM)); 
                    });
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
        try { 
            List<Address> adds = new Geocoder(this, Locale.getDefault()).getFromLocation(l.latitude, l.longitude, 1); 
            if (adds != null && !adds.isEmpty()) return adds.get(0).getAddressLine(0); 
        } catch (Exception e) { return "Unknown"; }
        return "Unknown"; 
    }

    private boolean locationPermissionGranted() { return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED; }
    private void askForLocationPermission() { ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_CODE); }
    private void enableLocationOnMap() { if (mMap != null && locationPermissionGranted()) { try { mMap.setMyLocationEnabled(true); } catch (SecurityException ignored) {} } }
    
    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        View view = getCurrentFocus();
        if (view != null && imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            view.clearFocus();
        }
    }

    @Override public void onRequestPermissionsResult(int r, @NonNull String[] p, @NonNull int[] g) { 
        super.onRequestPermissionsResult(r, p, g);
        if (r == PERMISSION_REQUEST_CODE && g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) { enableLocationOnMap(); } 
    }
    
    @Override protected void onDestroy() { 
        super.onDestroy(); 
        if (trackerHandler != null && trackerRunnable != null) trackerHandler.removeCallbacks(trackerRunnable); 
        executorService.shutdown();
    }
}
