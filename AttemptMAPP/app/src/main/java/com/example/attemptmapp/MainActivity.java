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
import java.util.Random;
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
    private final LatLng STOP_MAIN = new LatLng(39.86657, 32.74831);
    private final LatLng STOP_LIBRARY = new LatLng(39.87095, 32.75014);
    private final LatLng STOP_EA = new LatLng(39.87216, 32.74913);
    private final LatLng STOP_FADA = new LatLng(39.86975, 32.74895);
    private final LatLng STOP_MUSIC = new LatLng(39.86812, 32.74783);
    private final LatLng STOP_DORM76 = new LatLng(39.86484, 32.74750);
    private final LatLng STOP_DORM90 = new LatLng(39.86315, 32.74198);
    private final LatLng STOP_EAST = new LatLng(39.87648, 32.76480);
    
    private final LatLng DEST_TUNUS = new LatLng(39.9117, 32.8544);
    private final LatLng DEST_SIHHIYE = new LatLng(39.9298, 32.8530);
    private final LatLng WAY_ASTI = new LatLng(39.9168, 32.8122);
    private final LatLng WAY_BAHCELIEVLER = new LatLng(39.9213, 32.8228);
    private final LatLng WAY_MALTEPE = new LatLng(39.9250, 32.8430);

    private final Map<String, String[]> stopBusesMap = new HashMap<>();
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
        stopBusesMap.put("Main Entrance Stop", new String[]{"Tunus Shuttle", "Sihhiye Shuttle", "Ring 1", "Ring 2"});
        stopBusesMap.put("Library Stop", new String[]{"Ring 1", "Ring 2", "East Campus Shuttle"});
        stopBusesMap.put("EA Building Stop", new String[]{"Ring 1", "Ring 2"});
        stopBusesMap.put("FADA Stop", new String[]{"Ring 1", "Ring 2"});
        stopBusesMap.put("Music Building Stop", new String[]{"Ring 1"});
        stopBusesMap.put("Dorm 76 Stop", new String[]{"Ring 1", "Ring 2"});
        stopBusesMap.put("Dorm 90 Stop", new String[]{"Ring 1", "Ring 2"});
        stopBusesMap.put("East Campus Stop", new String[]{"East Campus Shuttle", "Tunus (East)", "Sihhiye (East)"});
        busColors.put("Ring 1", Color.RED); busColors.put("Ring 2", Color.GREEN); busColors.put("Tunus Shuttle", Color.MAGENTA);
        busColors.put("Sihhiye Shuttle", Color.BLUE); busColors.put("East Campus Shuttle", Color.rgb(255, 165, 0));
        busColors.put("Tunus (East)", Color.CYAN); busColors.put("Sihhiye (East)", Color.DKGRAY);
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
        
        // Fix for "Touch mechanics": Apply padding so native buttons don't overlap with our UI bars
        // Padding: Left, Top (Height of Search bar), Right, Bottom (Height of Nav bar)
        mMap.setPadding(0, 220, 0, 160);
        
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);
        mMap.getUiSettings().setMapToolbarEnabled(false); // Disable the floating 'Directions' toolbar which can bug out
        
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
        LatLng[] coords = {STOP_MAIN, STOP_LIBRARY, STOP_EA, STOP_FADA, STOP_MUSIC, STOP_DORM76, STOP_DORM90, STOP_EAST};
        String[] names = {"Main Entrance Stop", "Library Stop", "EA Building Stop", "FADA Stop", "Music Building Stop", "Dorm 76 Stop", "Dorm 90 Stop", "East Campus Stop"};
        for (int i = 0; i < coords.length; i++) {
            mMap.addMarker(new MarkerOptions().position(coords[i]).title(names[i]).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)));
        }
        mMap.setOnMarkerClickListener(marker -> {
            if (marker.getTitle() != null && marker.getTitle().contains("Stop")) { showBusListDialog(marker.getTitle()); return true; }
            if (activeMarker != null && marker.equals(activeMarker)) { activeMarker.remove(); activeMarker = null; if (currentPolyline != null) currentPolyline.remove(); return true; }
            marker.showInfoWindow(); return false;
        });
    }

    private void showBusListDialog(String stopName) {
        if (userRole.startsWith("driver_")) return;
        String[] buses = stopBusesMap.get(stopBusesMap.containsKey(stopName) ? stopName : ""); if (buses == null) return;
        String[] items = new String[buses.length]; Random r = new Random();
        for (int i = 0; i < buses.length; i++) {
            String id = "driver_" + buses[i].toLowerCase().replace(" ", "");
            items[i] = buses[i] + (busMarkers.containsKey(id) ? " - [LIVE]" : " - Sched: " + (r.nextInt(20)+1) + " min");
        }
        new AlertDialog.Builder(this).setTitle("Buses at " + stopName).setItems(items, (d, w) -> fetchBusTrajectory(buses[w])).show();
    }

    private void fetchBusTrajectory(String busName) {
        List<LatLng> waypoints = new ArrayList<>(); LatLng origin = STOP_MAIN, dest = STOP_MAIN;
        if (busName.equals("Ring 1")) { waypoints.add(STOP_MUSIC); waypoints.add(STOP_FADA); waypoints.add(STOP_LIBRARY); waypoints.add(STOP_EA); }
        else if (busName.equals("Ring 2")) { waypoints.add(STOP_DORM76); waypoints.add(STOP_DORM90); waypoints.add(STOP_LIBRARY); waypoints.add(STOP_EA); waypoints.add(STOP_FADA); }
        else if (busName.contains("Tunus")) { origin = busName.contains("East") ? STOP_EAST : STOP_MAIN; waypoints.add(STOP_LIBRARY); waypoints.add(WAY_ASTI); waypoints.add(WAY_BAHCELIEVLER); dest = DEST_TUNUS; }
        else if (busName.contains("Sihhiye")) { origin = busName.contains("East") ? STOP_EAST : STOP_MAIN; waypoints.add(STOP_LIBRARY); waypoints.add(WAY_ASTI); waypoints.add(WAY_MALTEPE); dest = DEST_SIHHIYE; }
        else if (busName.equals("East Campus Shuttle")) { origin = STOP_LIBRARY; dest = STOP_EAST; }
        
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
