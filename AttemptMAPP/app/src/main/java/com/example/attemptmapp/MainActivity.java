package com.example.attemptmapp;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final float DEFAULT_ZOOM = 15f;

    private GoogleMap mMap;
    private FusedLocationProviderClient locationClient;

    private EditText etSearch;
    private Button btnSearch, btnMyLocation, btnZoomIn, btnZoomOut, btnMapType;
    private Marker activeMarker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        locationClient = LocationServices.getFusedLocationProviderClient(this);

        bindViews();
        setupButtons();

        SupportMapFragment mapFragment = (SupportMapFragment)
                getSupportFragmentManager().findFragmentById(R.id.mapFragment);

        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        askForLocationPermission();
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

        // Basic settings
        mMap.getUiSettings().setZoomControlsEnabled(false); // We use custom buttons
        mMap.getUiSettings().setMyLocationButtonEnabled(false);

        enableLocationOnMap();

        mMap.setOnMapClickListener(latLng -> dropPin(latLng, "Dropped Pin", getAddressFromLatLng(latLng)));

        // Move to current location once map is ready
        goToMyLocation();
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
    }

    private void showMapTypePopup(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, "Normal");
        popup.getMenu().add(0, 2, 0, "Satellite");
        popup.getMenu().add(0, 3, 0, "Terrain");
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
            else if (item.getItemId() == 2) mMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
            else if (item.getItemId() == 3) mMap.setMapType(GoogleMap.MAP_TYPE_TERRAIN);
            return true;
        });
        popup.show();
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
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_CODE);
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