package com.example.attemptmapp;

import android.Manifest;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.CurrentLocationRequest;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.android.PolyUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final float DEFAULT_ZOOM = 15f;

    private GoogleMap mMap;
    private FusedLocationProviderClient locationClient;

    private EditText etSearch;
    private Button btnSearch, btnMyLocation, btnZoomIn, btnZoomOut, btnMapType;
    private Marker activeMarker;
    
    private Polyline currentPolyline;
    private final OkHttpClient httpClient = new OkHttpClient();
    private String travelMode = "driving";

    // Data for Bilkent Bus Stops
    private final Map<String, String[]> stopBusesMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        locationClient = LocationServices.getFusedLocationProviderClient(this);

        initBusData();
        bindViews();
        setupButtons();

        SupportMapFragment mapFragment = (SupportMapFragment)
                getSupportFragmentManager().findFragmentById(R.id.mapFragment);

        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        askForLocationPermission();
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
    }

    private void bindViews() {
        etSearch       = findViewById(R.id.etSearch);
        btnSearch      = findViewById(R.id.btnSearch);
        btnMyLocation  = findViewById(R.id.btnMyLocation);
        btnZoomIn      = findViewById(R.id.btnZoomIn);
        btnZoomOut     = findViewById(R.id.btnZoomOut);
        btnMapType     = findViewById(R.id.btnMapType);
    }

    private void setupButtons() {
        btnSearch.setOnClickListener(v -> searchForLocation(etSearch.getText().toString().trim()));

        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                searchForLocation(etSearch.getText().toString().trim());
                return true;
            }
            return false;
        });

        btnMyLocation.setOnClickListener(v -> goToMyLocation());

        btnZoomIn.setOnClickListener(v -> {
            if (mMap != null) mMap.animateCamera(CameraUpdateFactory.zoomIn());
        });

        btnZoomOut.setOnClickListener(v -> {
            if (mMap != null) mMap.animateCamera(CameraUpdateFactory.zoomOut());
        });

        btnMapType.setOnClickListener(this::showMapTypePopup);
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(false);
        mMap.getUiSettings().setMyLocationButtonEnabled(false);

        enableLocationOnMap();

        mMap.setOnMapClickListener(latLng -> dropPin(latLng, "Dropped Pin", getAddressFromLatLng(latLng)));
        
        // Add Bilkent Bus Stops
        addBilkentBusStops();
        
        goToMyLocation();
    }

    private void addBilkentBusStops() {
        LatLng[] stops = {
            new LatLng(39.86657, 32.74831), // Main Entrance
            new LatLng(39.87095, 32.75014), // Rectorate/Library
            new LatLng(39.87216, 32.74913), // EA Building
            new LatLng(39.86975, 32.74895), // FADA
            new LatLng(39.86812, 32.74783), // Music Building
            new LatLng(39.86484, 32.74750), // Dorm 76
            new LatLng(39.86315, 32.74198), // Dorm 90
            new LatLng(39.87648, 32.76480)  // East Campus
        };

        String[] names = {
            "Main Entrance Stop", "Library Stop", "EA Building Stop",
            "FADA Stop", "Music Building Stop", "Dorm 76 Stop",
            "Dorm 90 Stop", "East Campus Stop"
        };

        for (int i = 0; i < stops.length; i++) {
            mMap.addMarker(new MarkerOptions()
                .position(stops[i])
                .title(names[i])
                .snippet("Bilkent University Bus Stop")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)));
        }

        mMap.setOnMarkerClickListener(marker -> {
            String title = marker.getTitle();
            if (title != null && title.contains("Stop")) {
                showBusListDialog(title);
                return true;
            }
            
            // Default behavior for other markers
            marker.showInfoWindow();
            if (marker.equals(activeMarker)) {
                calculateDirections(marker.getPosition());
            }
            return false;
        });
    }

    private void showBusListDialog(String stopName) {
        String[] buses = stopBusesMap.get(stopName);
        if (buses == null) {
            Toast.makeText(this, "No bus data for " + stopName, Toast.LENGTH_SHORT).show();
            return;
        }

        // Generate simulated arrival times for each bus
        String[] displayItems = new String[buses.length];
        Random random = new Random();
        for (int i = 0; i < buses.length; i++) {
            int arrivalInMinutes = random.nextInt(20) + 1; // 1 to 20 minutes
            displayItems[i] = buses[i] + " - Arriving in " + arrivalInMinutes + " min";
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Buses at " + stopName);
        builder.setItems(displayItems, (dialog, which) -> {
            String selectedBus = buses[which];
            Toast.makeText(MainActivity.this, "Tracking " + selectedBus + "...", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Close", null);
        builder.show();
    }

    private void searchForLocation(String query) {
        if (query.isEmpty()) return;
        hideKeyboard();

        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> results = geocoder.getFromLocationName(query, 1);
            if (results != null && !results.isEmpty()) {
                Address found = results.get(0);
                LatLng pos = new LatLng(found.getLatitude(), found.getLongitude());
                dropPin(pos, query, found.getAddressLine(0));
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, DEFAULT_ZOOM));
            }
        } catch (IOException e) {
            Toast.makeText(this, "Search failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void goToMyLocation() {
        if (mMap == null) return;
        if (!locationPermissionGranted()) {
            askForLocationPermission();
            return;
        }

        try {
            locationClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) {
                    LatLng myPos = new LatLng(location.getLatitude(), location.getLongitude());
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(myPos, DEFAULT_ZOOM));
                } else {
                    requestCurrentLocation(null);
                }
            });
        } catch (SecurityException e) { e.printStackTrace(); }
    }

    private void requestCurrentLocation(LatLng destinationToRoute) {
        if (!locationPermissionGranted()) return;

        try {
            CurrentLocationRequest request = new CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                    .build();

            locationClient.getCurrentLocation(request, null).addOnSuccessListener(location -> {
                if (location != null) {
                    LatLng myPos = new LatLng(location.getLatitude(), location.getLongitude());
                    if (destinationToRoute == null) {
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(myPos, DEFAULT_ZOOM));
                    } else {
                        fetchRoute(myPos, destinationToRoute);
                    }
                } else {
                    Toast.makeText(this, "Location fix failed. Ensure GPS is on in emulator controls.", Toast.LENGTH_LONG).show();
                }
            });
        } catch (SecurityException e) { e.printStackTrace(); }
    }

    private void dropPin(LatLng position, String title, String snippet) {
        if (activeMarker != null) activeMarker.remove();
        activeMarker = mMap.addMarker(new MarkerOptions()
                .position(position)
                .title(title)
                .snippet(snippet)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
        if (activeMarker != null) activeMarker.showInfoWindow();
        
        calculateDirections(position);
    }

    private void calculateDirections(LatLng destination) {
        if (!locationPermissionGranted()) return;

        try {
            locationClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) {
                    LatLng origin = new LatLng(location.getLatitude(), location.getLongitude());
                    fetchRoute(origin, destination);
                } else {
                    requestCurrentLocation(destination);
                }
            });
        } catch (SecurityException e) { e.printStackTrace(); }
    }

    private String getApiKey() {
        try {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
            Bundle bundle = ai.metaData;
            return bundle.getString("com.google.android.geo.API_KEY");
        } catch (Exception e) {
            return null;
        }
    }

    private void fetchRoute(LatLng origin, LatLng dest) {
        String apiKey = getApiKey();
        if (apiKey == null) {
            Toast.makeText(this, "API Key not found in Manifest", Toast.LENGTH_SHORT).show();
            return;
        }

        String url = "https://maps.googleapis.com/maps/api/directions/json?" +
                "origin=" + origin.latitude + "," + origin.longitude +
                "&destination=" + dest.latitude + "," + dest.longitude +
                "&mode=" + travelMode +
                "&key=" + apiKey;

        Request request = new Request.Builder().url(url).build();
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Network error", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String jsonData = response.body().string();
                        JSONObject jsonObject = new JSONObject(jsonData);
                        
                        String status = jsonObject.getString("status");
                        if (!status.equals("OK")) {
                            String errorMsg = "API Error: " + status;
                            if (jsonObject.has("error_message")) {
                                errorMsg += "\n\nDetails: " + jsonObject.getString("error_message");
                            }
                            final String finalMsg = errorMsg;
                            runOnUiThread(() -> {
                                new AlertDialog.Builder(MainActivity.this)
                                        .setTitle("Google Maps Error")
                                        .setMessage(finalMsg)
                                        .setPositiveButton("How to fix?", (dialog, which) -> {
                                            new AlertDialog.Builder(MainActivity.this)
                                                .setMessage("1. Go to Google Cloud Console.\n2. Disable 'Application Restrictions' (SHA-1) for this key.\n3. Enable 'Directions API'.")
                                                .show();
                                        })
                                        .setNegativeButton("Close", null)
                                        .show();
                            });
                            return;
                        }

                        JSONArray routes = jsonObject.getJSONArray("routes");
                        if (routes.length() > 0) {
                            JSONObject route = routes.getJSONObject(0);
                            JSONObject legs = route.getJSONArray("legs").getJSONObject(0);
                            String duration = legs.getJSONObject("duration").getString("text");
                            String distance = legs.getJSONObject("distance").getString("text");
                            
                            String points = route.getJSONObject("overview_polyline").getString("points");
                            List<LatLng> path = PolyUtil.decode(points);

                            runOnUiThread(() -> {
                                if (currentPolyline != null) currentPolyline.remove();
                                currentPolyline = mMap.addPolyline(new PolylineOptions()
                                        .addAll(path)
                                        .color(Color.BLUE)
                                        .width(12));
                                
                                Toast.makeText(MainActivity.this, 
                                    "Mode: " + travelMode + " | Time: " + duration + " (" + distance + ")", 
                                    Toast.LENGTH_LONG).show();
                            });
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void showMapTypePopup(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, "Normal Map");
        popup.getMenu().add(0, 2, 0, "Satellite Map");
        popup.getMenu().add(0, 3, 0, "Terrain Map");
        popup.getMenu().add(0, 4, 0, "--- Travel Mode ---").setEnabled(false);
        popup.getMenu().add(0, 5, 0, "🚗 Driving");
        popup.getMenu().add(0, 6, 0, "🚶 Walking");

        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL); break;
                case 2: mMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE); break;
                case 3: mMap.setMapType(GoogleMap.MAP_TYPE_TERRAIN); break;
                case 5: travelMode = "driving"; refreshRouteIfActive(); break;
                case 6: travelMode = "walking"; refreshRouteIfActive(); break;
            }
            return true;
        });
        popup.show();
    }

    private void refreshRouteIfActive() {
        if (activeMarker != null) {
            calculateDirections(activeMarker.getPosition());
        }
    }

    private String getAddressFromLatLng(LatLng latLng) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);
            if (addresses != null && !addresses.isEmpty()) return addresses.get(0).getAddressLine(0);
        } catch (IOException ignored) {}
        return "Unknown Location";
    }

    private boolean locationPermissionGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void askForLocationPermission() {
        if (!locationPermissionGranted()) {
            ActivityCompat.requestPermissions(this, 
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 
                PERMISSION_REQUEST_CODE);
        }
    }

    private void enableLocationOnMap() {
        if (mMap != null && locationPermissionGranted()) {
            try { mMap.setMyLocationEnabled(true); } catch (SecurityException ignored) {}
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            enableLocationOnMap();
            goToMyLocation();
        }
    }

    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
}
