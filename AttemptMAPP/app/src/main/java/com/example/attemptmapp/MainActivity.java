package com.example.attemptmapp;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
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
    // Constants and static variables
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final float DEFAULT_ZOOM = 15f;
    private static final String TAG = "BusTracker";
    private static final String DB_URL = "https://bilkent-bus-tracker-default-rtdb.europe-west1.firebasedatabase.app/";

    // Instance variables
    private GoogleMap map;
    private FusedLocationProviderClient locationClient;
    private LocationCallback driverLocationCallback;
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);

    // Ui elements
    private EditText searchBar;
    private RecyclerView searchResultsUI;
    private SearchSystem searchSystem;
    private ImageButton settingsButton, homeButton, favoritesButton;
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

    // Bus arrival class, each instance shows up as a row in the bus stop schedule
    public static class BusArrival implements Comparable<BusArrival> {
        // Instance variables
        String busName;
        int hour;
        int minute;

        // Constructor
        BusArrival(String busName, int hour, int minute) {
            this.busName = busName;
            this.hour = hour;
            this.minute = minute;
        }

        // Getters
        int getMinutes() { return hour * 60 + minute; }
        String getTimeString() { return String.format(Locale.getDefault(), "%02d:%02d", hour, minute); }

        // Comparison method to sort the rows according to their arrival time
        @Override
        public int compareTo(BusArrival other) {
            return Integer.compare(this.getMinutes(), other.getMinutes());
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check user role
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        userRole = prefs.getString("userRole", "");

        // If no role is saved, then the user isn't logged in, thus redirect to login page
        if (userRole.isEmpty()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        // Initialize Firebase
        try {
            dbRef = FirebaseDatabase.getInstance(DB_URL).getReference("live_buses");
        } catch (Exception e) {
            Log.e(TAG, "Firebase setup failed: " + e.getMessage());
        }
        
        locationClient = LocationServices.getFusedLocationProviderClient(this);
        initializeBusData(); // Initializes the bus data
        bindViews(); // Binds UI elements to variables
        setupButtons(); // Attaches button listeners

        // Set up the map
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapFragment);
        if (mapFragment != null) mapFragment.getMapAsync(this);
        askForLocationPermission(); // Ask for location permission if it is not granted

        // Broadcast the location if it is a driver
        if (userRole.startsWith("driver_")) {
            startDriverTracking();
        }
    }


    private void startDriverTracking() {
        // Check for location permission
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        // Get location updates every 3 seconds
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
                .setMinUpdateIntervalMillis(2000)
                .build();

        // Store the location in the database
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

    // Adds a listener for retrieving and updating live bus locations
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
                            // Create and update the marker if the bus was updated in the last 5 minutes
                            updateLiveMarker(id, new LatLng(lat, lng));
                        } else {
                            // Remove the bus marker if it has not been updated for more than 5 minutes
                            removeLiveMarker(id);
                        }
                    }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    // Updates the bus marker
    private void updateLiveMarker(String id, LatLng pos) {
        runOnUiThread(() -> {
            if (map == null) return;
            if (busMarkers.containsKey(id)) {
                // Update the existing marker's location
                Marker marker = busMarkers.get(id);
                if (marker != null) {
                    marker.setPosition(pos);
                    marker.setVisible(persistentVisibleBuses.contains(id));
                }
            } else {
                // Create a new marker
                String label = id.replace("driver_", "").toUpperCase() + " (LIVE)";
                boolean isPersistent = persistentVisibleBuses.contains(id);
                Marker m = map.addMarker(new MarkerOptions()
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

    // Creates a custom red marker for the live buses
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

    // Removes the given live bus marker
    private void removeLiveMarker(String id) {
        runOnUiThread(() -> {
            if (busMarkers.containsKey(id)) {
                Marker marker = busMarkers.get(id);
                if (marker != null) marker.remove();
                busMarkers.remove(id);
            }
        });
    }

    private void initializeBusData() {
        // Set color for each bus type's route line
        busColors.put("Ring", Color.RED); 
        busColors.put("Tunus", Color.MAGENTA);
        busColors.put("Sihhiye", Color.BLUE);

        // Creates bus arrival lists for each bus stop with proper schedule
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

    // Initialize UI elements
    private void bindViews() {
        searchBar = findViewById(R.id.searchBar);
        searchResultsUI = findViewById(R.id.searchResultsUI);
        homeButton = findViewById(R.id.homeButton);
        favoritesButton = findViewById(R.id.favoritesButton);
        settingsButton = findViewById(R.id.settingsButton);
    }

    // Listener setup
    private void setupButtons() {
        homeButton.setOnClickListener(v -> {
            // Zoom to bilkent university (default location)
            if (searchSystem != null) searchSystem.clearAndHide();
            if (map != null) map.animateCamera(CameraUpdateFactory.newLatLngZoom(BILKENT_UNIVERSITY, DEFAULT_ZOOM));
        });
        favoritesButton.setOnClickListener(v -> {
            // Send to favorites screen
            if (searchSystem != null) searchSystem.clearAndHide();
            startActivity(new Intent(MainActivity.this, FavoritesActivity.class));
        });
        settingsButton.setOnClickListener(v -> {
            // Send to settings screen
            if (searchSystem != null) searchSystem.clearAndHide();
            startActivity(new Intent(MainActivity.this, Settings.class));
        });
    }

    // Handles what happen when a new intent is received by the main screen
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        checkShowStopIntent(intent);
    }

    // Handles what happens when the main screen is resumed
    @Override
    protected void onResume() {
        super.onResume();
        checkShowStopIntent(getIntent());
    }

    // Checks if the given intent is an intent for showing a bus stop, if it is, then it shows that bus stop
    private void checkShowStopIntent(Intent intent) {
        if (intent != null && intent.hasExtra("show_stop")) {
            String stopName = intent.getStringExtra("show_stop");
            LatLng pos = getLatLngForStop(stopName);
            if (pos != null && map != null) {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 17f));
                showBusStopBottomSheet(stopName);
            }
            intent.removeExtra("show_stop");
        }
    }

    // Returns the coordinates for the given bus stop
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
        map = googleMap; isMapReady = true;

        // Apply dark mode style if enabled
        int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
            try {
                map.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style_dark));
            } catch (Exception e) {
                Log.e(TAG, "Can't find style. Error: ", e);
            }
        }

        // Create the search system
        searchSystem = new SearchSystem(this, searchBar, searchResultsUI, stopSchedules, map);

        // Set up the map (extra settings)
        map.setPadding(0, 220, 0, 160);
        map.getUiSettings().setZoomControlsEnabled(true);
        map.getUiSettings().setMyLocationButtonEnabled(true);
        map.getUiSettings().setMapToolbarEnabled(false);
        enableLocationOnMap();

        // Removes the bus schedule sheet (if shown) when the map is clicked
        map.setOnMapClickListener(latLng -> {
            if (currentBottomSheet != null && currentBottomSheet.isShowing()) {
                currentBottomSheet.dismiss();
            }
        });

        addBilkentBusStops(); // Places the bus stop markers on the map
        startStudentListening(); // Starts listening for live bus locations
        checkShowStopIntent(getIntent()); // Main map could've been loaded by other screens, so check to display bus stops
    }

    // Places the bus stop markers on the map
    private void addBilkentBusStops() {
        LatLng[] coords = {STOP_DORM91, STOP_DORM92, STOP_MESCIT, STOP_BILKA_HILL, STOP_KUTUPHANE, STOP_NIZAMIYE};
        String[] names = {"Dorm 91", "Dorm 92", "Mescit bus stop", "Bilka hill bus stop", "Kütüphane", "Nizamiye"};
        for (int i = 0; i < coords.length; i++) {
            map.addMarker(new MarkerOptions().position(coords[i]).title(names[i]).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)));
        }

        // Listener for when markers are clicked on the map
        map.setOnMarkerClickListener(marker -> {
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

    // Displays the bus schedule of a bus stop and handles all the related functionalities
    public void showBusStopBottomSheet(String stopName) {
        if (userRole.startsWith("driver_")) return;

        // Initialize views of the bus schedule sheet
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_bus_stop, null);
        TextView remainingTime = view.findViewById(R.id.remainingTime);
        TextView nextArrivalTime = view.findViewById(R.id.nextArrivalTime);
        LinearLayout container = view.findViewById(R.id.scheduleContainer);
        View addToFavoritesButton = view.findViewById(R.id.addToFavoritesButton);
        View notifyButton = view.findViewById(R.id.notifyButton);

        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        Set<String> favorites = new HashSet<>(prefs.getStringSet("favorites", new HashSet<>()));

        // Check if the bus stop is already in favorites or not, adjust button accordingly
        boolean isFavorite = favorites.contains(stopName);
        if (addToFavoritesButton != null) {
            addToFavoritesButton.setAlpha(isFavorite ? 1.0f : 0.4f);

            // Listener for when the favorite button is clicked
            addToFavoritesButton.setOnClickListener(v -> {
                if (favorites.contains(stopName)) {
                    favorites.remove(stopName);
                    addToFavoritesButton.setAlpha(0.4f);
                    Toast.makeText(this, "Removed from favorites", Toast.LENGTH_SHORT).show();
                } else {
                    favorites.add(stopName);
                    addToFavoritesButton.setAlpha(1.0f);
                    Toast.makeText(this, "Added to favorites", Toast.LENGTH_SHORT).show();
                }
                prefs.edit().putStringSet("favorites", favorites).apply();
            });
        }

        // Show only the bus schedules that are coming up (in the future)
        List<BusArrival> schedule = stopSchedules.get(stopName);
        if (schedule == null) return;

        Calendar now = Calendar.getInstance();
        int currentMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

        List<BusArrival> upcoming = new ArrayList<>();
        for (BusArrival arrival : schedule) {
            if (arrival.getMinutes() >= currentMin) upcoming.add(arrival);
        }

        if (upcoming.isEmpty()) {
            remainingTime.setText("No more buses today");
            nextArrivalTime.setText("End of service");
            if (notifyButton != null) notifyButton.setVisibility(View.GONE);
        } else {
            BusArrival next = upcoming.get(0);
            int diff = next.getMinutes() - currentMin;
            remainingTime.setText(diff + " min remaining");
            nextArrivalTime.setText("Next: " + next.getTimeString());

            // Listener for when notify button is clicked, schedules a notification if notifications are enabled
            if (notifyButton != null) {
                notifyButton.setOnClickListener(v -> {
                    boolean enabled = prefs.getBoolean("notifications_enabled", true);
                    if (!enabled) {
                        Toast.makeText(this, "Notifications are disabled in settings", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Schedule notification for 5 minutes before
                    scheduleNotification(stopName, next);
                });
            }

            // Create a row for each bus arrival instance
            for (BusArrival b : upcoming) {
                View row = LayoutInflater.from(this).inflate(R.layout.item_schedule_row, container, false);
                ((TextView) row.findViewById(R.id.timeText)).setText(b.getTimeString());
                ((TextView) row.findViewById(R.id.busName)).setText(b.busName);
                
                String driverId = "driver_" + b.busName.toLowerCase();
                View liveLocationButton = row.findViewById(R.id.liveLocationButton);

                // Show live location button listener, displays live location if available
                if (liveLocationButton != null) {
                    liveLocationButton.setOnClickListener(v -> {
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
                                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(m.getPosition(), 16f));
                                    Toast.makeText(this, "Live location shown", Toast.LENGTH_SHORT).show();
                                }
                                if (currentBottomSheet != null) currentBottomSheet.dismiss();
                            }
                        } else {
                            Toast.makeText(this, "Driver for " + b.busName + " is not live", Toast.LENGTH_SHORT).show();
                        }
                    });
                }

                // Show route button listener, shows the route of the bus
                View routeButton = row.findViewById(R.id.showRouteButton);
                if (routeButton != null) {
                    routeButton.setOnClickListener(v -> { 
                        fetchBusTrajectory(b.busName); 
                        if(currentBottomSheet != null) currentBottomSheet.dismiss(); 
                    });
                }
                
                container.addView(row);
            }
        }

        if (currentBottomSheet != null && currentBottomSheet.isShowing()) currentBottomSheet.dismiss();

        // Creates a new bottom sheet, updates it accordingly and then display it
        currentBottomSheet = new BottomSheetDialog(this);
        currentBottomSheet.setContentView(view);
        
        Window window = currentBottomSheet.getWindow();
        if (window != null) {
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

    // Schedules a notification for the given bus
    private void scheduleNotification(String stopName, BusArrival bus) {
        // Create a notification intent
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, NotificationReceiver.class);
        intent.putExtra("stop_name", stopName);
        intent.putExtra("bus_name", bus.busName);
        intent.putExtra("arrival_time", bus.getTimeString());

        // Turns the previous intent into a pending intent so that it can be fired later
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this, 
                stopName.hashCode(), 
                intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Calculate the notification time
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, bus.hour);
        calendar.set(Calendar.MINUTE, bus.minute);
        calendar.set(Calendar.SECOND, 0);
        
        long notifyTime = calendar.getTimeInMillis() - (5 * 60 * 1000); // 5 minutes before

        // Check if the arrival time is less than 5 minutes
        if (notifyTime <= System.currentTimeMillis()) {
            Toast.makeText(this, "Bus is arriving too soon for a 5-min reminder", Toast.LENGTH_SHORT).show();
            return;
        }

        if (alarmManager != null) {
            // Check if notifications for the app are enabled on the mobile
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!alarmManager.canScheduleExactAlarms()) {
                    // If they aren't, open settings screen and ask for permission
                    Intent setIntent = new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                    startActivity(setIntent);
                    return;
                }
            }
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, notifyTime, pendingIntent);
            Toast.makeText(this, "Reminder set for 5 mins before " + bus.getTimeString(), Toast.LENGTH_SHORT).show();
        }
    }

    // Builds the route based on the bus type
    private void fetchBusTrajectory(String busName) {
        List<LatLng> waypoints = new ArrayList<>();
        LatLng origin = STOP_DORM91, dest = STOP_NIZAMIYE;
        waypoints.add(STOP_DORM92); waypoints.add(STOP_MESCIT); waypoints.add(STOP_BILKA_HILL); waypoints.add(STOP_KUTUPHANE);
        if (busName.contains("Tunus")) { waypoints.add(WAY_ASTI); waypoints.add(WAY_BAHCELIEVLER); dest = DEST_TUNUS; }
        else if (busName.contains("Sihhiye")) { waypoints.add(WAY_ASTI); waypoints.add(WAY_MALTEPE); dest = DEST_SIHHIYE; }
        
        Integer color = busColors.get(busName.replace(" Bus", ""));
        requestRoute(origin, dest, waypoints, color != null ? color : Color.GRAY);
    }

    // Fetches and draws the bus route on the map
    private void requestRoute(LatLng origin, LatLng dest, List<LatLng> waypoints, int color) {
        String apiKey = getApiKey();

        // Build the request url
        StringBuilder url = new StringBuilder("https://maps.googleapis.com/maps/api/directions/json?origin=").append(origin.latitude).append(",").append(origin.longitude).append("&destination=").append(dest.latitude).append(",").append(dest.longitude).append("&mode=driving&key=").append(apiKey);
        if (!waypoints.isEmpty()) { url.append("&waypoints=optimize:false"); for (LatLng wp : waypoints) url.append("|").append(wp.latitude).append(",").append(wp.longitude); }

        // Send the request
        httpClient.newCall(new Request.Builder().url(url.toString()).build()).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {}

            // Converts the response into a list of coordinates (LatLng)
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        JSONObject json = new JSONObject(response.body().string());
                        if (json.getString("status").equals("OK")) {
                            List<LatLng> pathPoints = PolyUtil.decode(json.getJSONArray("routes").getJSONObject(0).getJSONObject("overview_polyline").getString("points"));
                            // Draw the route on the map
                            runOnUiThread(() -> {
                                if (currentBusPolyline != null) currentBusPolyline.remove();
                                currentBusPolyline = map.addPolyline(new PolylineOptions().addAll(pathPoints).color(color).width(15).jointType(2).startCap(new com.google.android.gms.maps.model.RoundCap()).endCap(new com.google.android.gms.maps.model.RoundCap()));
                                if (!pathPoints.isEmpty()) map.animateCamera(CameraUpdateFactory.newLatLngZoom(pathPoints.get(pathPoints.size()/2), 14f));
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
    private void enableLocationOnMap() { if (map != null && locationPermissionGranted()) { try { map.setMyLocationEnabled(true); } catch (SecurityException ignored) {} } }
    public void hideKeyboard() { InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE); View view = getCurrentFocus(); if (view != null && imm != null) { imm.hideSoftInputFromWindow(view.getWindowToken(), 0); view.clearFocus(); } }

    // Handles the result of asking for user's location
    @Override public void onRequestPermissionsResult(int r, @NonNull String[] p, @NonNull int[] g) { 
        super.onRequestPermissionsResult(r, p, g); 
        if (r == PERMISSION_REQUEST_CODE && g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) {
            enableLocationOnMap();
            if (userRole.startsWith("driver_")) {
                startDriverTracking();
            }
        } 
    }

    // Removes the location listener
    @Override protected void onDestroy() { 
        super.onDestroy(); 
        if (locationClient != null && driverLocationCallback != null) {
            locationClient.removeLocationUpdates(driverLocationCallback);
        }
        executorService.shutdown();
    }
}
