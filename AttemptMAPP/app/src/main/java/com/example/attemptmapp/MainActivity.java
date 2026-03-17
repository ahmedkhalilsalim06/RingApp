package com.example.attemptmapp;

import android.Manifest;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
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
import java.util.ArrayList;
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
    private Polyline currentBusPolyline;
    private final OkHttpClient httpClient = new OkHttpClient();
    private String travelMode = "driving";

    // Stop Coordinates
    private final LatLng STOP_MAIN = new LatLng(39.86657, 32.74831);
    private final LatLng STOP_LIBRARY = new LatLng(39.87095, 32.75014);
    private final LatLng STOP_EA = new LatLng(39.87216, 32.74913);
    private final LatLng STOP_FADA = new LatLng(39.86975, 32.74895);
    private final LatLng STOP_MUSIC = new LatLng(39.86812, 32.74783);
    private final LatLng STOP_DORM76 = new LatLng(39.86484, 32.74750);
    private final LatLng STOP_DORM90 = new LatLng(39.86315, 32.74198);
    private final LatLng STOP_EAST = new LatLng(39.87648, 32.76480);
    
    // City Destinations
    private final LatLng DEST_TUNUS = new LatLng(39.9117, 32.8544);
    private final LatLng DEST_SIHHIYE = new LatLng(39.9298, 32.8530);
    
    // Intermediate Landmarks for Shuttles
    private final LatLng WAY_ASTI = new LatLng(39.9168, 32.8122);
    private final LatLng WAY_BAHCELIEVLER = new LatLng(39.9213, 32.8228);
    private final LatLng WAY_MALTEPE = new LatLng(39.9250, 32.8430);

    private final Map<String, String[]> stopBusesMap = new HashMap<>();
    private final Map<String, Integer> busColors = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        locationClient = LocationServices.getFusedLocationProviderClient(this);
        initBusData();
        bindViews();
        setupButtons();
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapFragment);
        if (mapFragment != null) mapFragment.getMapAsync(this);
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

        busColors.put("Ring 1", Color.RED);
        busColors.put("Ring 2", Color.GREEN);
        busColors.put("Tunus Shuttle", Color.MAGENTA);
        busColors.put("Sihhiye Shuttle", Color.BLUE);
        busColors.put("East Campus Shuttle", Color.rgb(255, 165, 0));
        busColors.put("Tunus (East)", Color.CYAN);
        busColors.put("Sihhiye (East)", Color.DKGRAY);
    }

    private void bindViews() {
        etSearch = findViewById(R.id.etSearch);
        btnSearch = findViewById(R.id.btnSearch);
        btnMyLocation = findViewById(R.id.btnMyLocation);
        btnZoomIn = findViewById(R.id.btnZoomIn);
        btnZoomOut = findViewById(R.id.btnZoomOut);
        btnMapType = findViewById(R.id.btnMapType);
    }

    private void setupButtons() {
        btnSearch.setOnClickListener(v -> searchForLocation(etSearch.getText().toString().trim()));
        btnMyLocation.setOnClickListener(v -> goToMyLocation());
        btnZoomIn.setOnClickListener(v -> { if (mMap != null) mMap.animateCamera(CameraUpdateFactory.zoomIn()); });
        btnZoomOut.setOnClickListener(v -> { if (mMap != null) mMap.animateCamera(CameraUpdateFactory.zoomOut()); });
        btnMapType.setOnClickListener(this::showMapTypePopup);
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(false);
        enableLocationOnMap();
        mMap.setOnMapClickListener(latLng -> dropPin(latLng, "Dropped Pin", getAddressFromLatLng(latLng)));
        addBilkentBusStops();
        goToMyLocation();
    }

    private void addBilkentBusStops() {
        LatLng[] coords = {STOP_MAIN, STOP_LIBRARY, STOP_EA, STOP_FADA, STOP_MUSIC, STOP_DORM76, STOP_DORM90, STOP_EAST};
        String[] names = {"Main Entrance Stop", "Library Stop", "EA Building Stop", "FADA Stop", "Music Building Stop", "Dorm 76 Stop", "Dorm 90 Stop", "East Campus Stop"};
        for (int i = 0; i < coords.length; i++) {
            mMap.addMarker(new MarkerOptions().position(coords[i]).title(names[i]).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)));
        }
        mMap.setOnMarkerClickListener(marker -> {
            if (marker.getTitle() != null && marker.getTitle().contains("Stop")) {
                showBusListDialog(marker.getTitle());
                return true;
            }
            marker.showInfoWindow();
            return false;
        });
    }

    private void showBusListDialog(String stopName) {
        String[] buses = stopBusesMap.get(stopName);
        if (buses == null) return;
        String[] displayItems = new String[buses.length];
        Random random = new Random();
        for (int i = 0; i < buses.length; i++) {
            displayItems[i] = buses[i] + " - Arriving in " + (random.nextInt(20) + 1) + " min";
        }
        new AlertDialog.Builder(this).setTitle("Buses at " + stopName).setItems(displayItems, (dialog, which) -> fetchBusTrajectory(buses[which])).show();
    }

    private void fetchBusTrajectory(String busName) {
        List<LatLng> waypoints = new ArrayList<>();
        LatLng origin = STOP_MAIN;
        LatLng destination = STOP_MAIN;

        if (busName.equals("Ring 1")) {
            waypoints.add(STOP_MUSIC); waypoints.add(STOP_FADA); waypoints.add(STOP_LIBRARY); waypoints.add(STOP_EA);
        } else if (busName.equals("Ring 2")) {
            waypoints.add(STOP_DORM76); waypoints.add(STOP_DORM90); waypoints.add(STOP_LIBRARY); waypoints.add(STOP_EA); waypoints.add(STOP_FADA);
        } else if (busName.contains("Tunus")) {
            origin = busName.contains("East") ? STOP_EAST : STOP_MAIN;
            waypoints.add(STOP_LIBRARY); waypoints.add(WAY_ASTI); waypoints.add(WAY_BAHCELIEVLER);
            destination = DEST_TUNUS;
        } else if (busName.contains("Sihhiye")) {
            origin = busName.contains("East") ? STOP_EAST : STOP_MAIN;
            waypoints.add(STOP_LIBRARY); waypoints.add(WAY_ASTI); waypoints.add(WAY_MALTEPE);
            destination = DEST_SIHHIYE;
        } else if (busName.equals("East Campus Shuttle")) {
            origin = STOP_LIBRARY; destination = STOP_EAST;
        }

        requestRoute(origin, destination, waypoints, busColors.get(busName));
    }

    private void requestRoute(LatLng origin, LatLng dest, List<LatLng> waypoints, int color) {
        StringBuilder url = new StringBuilder("https://maps.googleapis.com/maps/api/directions/json?origin=")
                .append(origin.latitude).append(",").append(origin.longitude)
                .append("&destination=").append(dest.latitude).append(",").append(dest.longitude)
                .append("&mode=driving&key=").append(getApiKey());

        if (!waypoints.isEmpty()) {
            url.append("&waypoints=optimize:false");
            for (LatLng wp : waypoints) {
                url.append("|").append(wp.latitude).append(",").append(wp.longitude);
            }
        }

        httpClient.newCall(new Request.Builder().url(url.toString()).build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {}
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        JSONObject json = new JSONObject(response.body().string());
                        if (json.getString("status").equals("OK")) {
                            List<LatLng> path = PolyUtil.decode(json.getJSONArray("routes").getJSONObject(0).getJSONObject("overview_polyline").getString("points"));
                            runOnUiThread(() -> {
                                if (currentBusPolyline != null) currentBusPolyline.remove();
                                currentBusPolyline = mMap.addPolyline(new PolylineOptions().addAll(path).color(color).width(15).jointType(2).startCap(new com.google.android.gms.maps.model.RoundCap()).endCap(new com.google.android.gms.maps.model.RoundCap()));
                                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(path.get(path.size()/2), 14f));
                            });
                        }
                    } catch (Exception ignored) {}
                }
            }
        });
    }

    private String getApiKey() {
        try {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
            return ai.metaData.getString("com.google.android.geo.API_KEY");
        } catch (Exception e) { return null; }
    }

    // Boilerplate Location/Search Methods
    private void searchForLocation(String query) {
        if (query.isEmpty()) return;
        try {
            List<Address> results = new Geocoder(this, Locale.getDefault()).getFromLocationName(query, 1);
            if (results != null && !results.isEmpty()) {
                LatLng pos = new LatLng(results.get(0).getLatitude(), results.get(0).getLongitude());
                dropPin(pos, query, results.get(0).getAddressLine(0));
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, DEFAULT_ZOOM));
            }
        } catch (IOException ignored) {}
    }

    private void goToMyLocation() {
        if (!locationPermissionGranted()) { askForLocationPermission(); return; }
        try {
            locationClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(), location.getLongitude()), DEFAULT_ZOOM));
            });
        } catch (SecurityException ignored) {}
    }

    private void dropPin(LatLng position, String title, String snippet) {
        if (activeMarker != null) activeMarker.remove();
        activeMarker = mMap.addMarker(new MarkerOptions().position(position).title(title).snippet(snippet).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
        calculateDirections(position);
    }

    private void calculateDirections(LatLng destination) {
        locationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                String url = "https://maps.googleapis.com/maps/api/directions/json?origin=" + location.getLatitude() + "," + location.getLongitude() + "&destination=" + destination.latitude + "," + destination.longitude + "&mode=" + travelMode + "&key=" + getApiKey();
                httpClient.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
                    @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {}
                    @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        if (response.isSuccessful()) {
                            try {
                                JSONObject json = new JSONObject(response.body().string());
                                List<LatLng> path = PolyUtil.decode(json.getJSONArray("routes").getJSONObject(0).getJSONObject("overview_polyline").getString("points"));
                                runOnUiThread(() -> {
                                    if (currentPolyline != null) currentPolyline.remove();
                                    currentPolyline = mMap.addPolyline(new PolylineOptions().addAll(path).color(Color.BLUE).width(12));
                                });
                            } catch (Exception ignored) {}
                        }
                    }
                });
            }
        });
    }

    private void showMapTypePopup(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, "Normal"); popup.getMenu().add(0, 2, 0, "Satellite"); popup.getMenu().add(0, 3, 0, "Terrain");
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) mMap.setMapType(1); else if (item.getItemId() == 2) mMap.setMapType(2); else if (item.getItemId() == 3) mMap.setMapType(3);
            return true;
        });
        popup.show();
    }

    private String getAddressFromLatLng(LatLng latLng) { try { return new Geocoder(this, Locale.getDefault()).getFromLocation(latLng.latitude, latLng.longitude, 1).get(0).getAddressLine(0); } catch (Exception e) { return "Unknown"; } }
    private boolean locationPermissionGranted() { return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED; }
    private void askForLocationPermission() { ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_CODE); }
    private void enableLocationOnMap() { if (mMap != null && locationPermissionGranted()) { try { mMap.setMyLocationEnabled(true); } catch (SecurityException ignored) {} } }
}
