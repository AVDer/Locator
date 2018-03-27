package com.starcon.master.locator;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.support.v7.widget.Toolbar;
import android.text.format.Time;

import com.github.pengrad.mapscaleview.MapScaleView;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.HashMap;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback,
        GoogleMap.OnCameraMoveListener,
        GoogleMap.OnCameraIdleListener,
        GoogleMap.OnCameraChangeListener,
        GoogleMap.OnMarkerClickListener,
        GoogleMap.OnMapClickListener {

    private final int REQUEST_CHECK_SETTINGS = 0;

    private GoogleMap mMap;
    private MapScaleView mScaleView;

    private HashMap<String, Marker> mMarkers = new HashMap<>();

    private String mMyId;
    private Marker mMyMarker;
    private Marker mActiveMarker;
    private Intent mLocationIntent;
    private Time mTime = new Time();

    private boolean mServiceRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);

        Toolbar mToolbar = findViewById(R.id.my_toolbar);

        SharedPreferences sharedPref_ = PreferenceManager.getDefaultSharedPreferences(this);
        mMyId = sharedPref_.getString("pref_name", "User");

        mServiceRunning = isServiceRunning(LocationService.class);

        mLocationIntent = new Intent(getApplicationContext(), LocationService.class);

        setSupportActionBar(mToolbar);

        mapFragment.getMapAsync(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar, menu);
        MenuItem menuItem = menu.findItem(R.id.action_auto);
        menuItem.setChecked(mServiceRunning);
        menuItem.setIcon(mServiceRunning ? R.drawable.ic_auto_pressed : R.drawable.ic_auto_update);
        return true;
    }


    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            /*
            case R.id.action_connect:
                return true;
                */
            case R.id.action_settings:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                return true;
            case R.id.action_auto:
                mServiceRunning = !mServiceRunning;
                item.setChecked(mServiceRunning);
                item.setIcon(mServiceRunning ? R.drawable.ic_auto_pressed : R.drawable.ic_auto_update);
                if (mServiceRunning && !isServiceRunning(LocationService.class)) {
                        startService(mLocationIntent);
                } else {
                    if (isServiceRunning(LocationService.class)) {
                        stopService(mLocationIntent);
                    }
                }
                return true;
            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);

        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMyMarker = mMap.addMarker(new MarkerOptions().position(new LatLng(0, 0)).title(mMyId).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
        mActiveMarker = mMyMarker;
        mScaleView = (MapScaleView) findViewById(R.id.scaleView);
        mScaleView.metersOnly();
        CameraPosition cameraPosition = mMap.getCameraPosition();
        mScaleView.update(cameraPosition.zoom, cameraPosition.target.latitude);

        googleMap.setOnCameraMoveListener(this);
        googleMap.setOnCameraIdleListener(this);
        googleMap.setOnCameraChangeListener(this);
        googleMap.setOnMarkerClickListener(this);
        googleMap.setOnMapClickListener(this);
    }

    @Override
    public void onCameraMove() {
        CameraPosition cameraPosition = mMap.getCameraPosition();
        mScaleView.update(cameraPosition.zoom, cameraPosition.target.latitude);
    }

    @Override
    public void onCameraIdle() {
        CameraPosition cameraPosition = mMap.getCameraPosition();
        mScaleView.update(cameraPosition.zoom, cameraPosition.target.latitude);
    }

    @Override
    public void onCameraChange(CameraPosition cameraPosition) {
        mScaleView.update(cameraPosition.zoom, cameraPosition.target.latitude);
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        mActiveMarker = marker;
        return false;
    }

    @Override
    public void onMapClick(LatLng latLng) {
        mActiveMarker = null;
    }

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.hasExtra("me")) {
                String my_location = intent.getStringExtra("me");
                String[] data = my_location.split(";");
                mMyMarker.setPosition(new LatLng(Double.valueOf(data[0]), Double.valueOf(data[1])));
            }
            if (intent.hasExtra("other")) {
                String other_location = intent.getStringExtra("other");
                String[] records = other_location.split(":");
                for (int i = 0; i < records.length; ++i) {
                    String[] record = records[i].split(";");
                    if (record.length < 4) continue;
                    Marker marker = mMarkers.get(record[0]);
                    mTime.switchTimezone("UTC");
                    mTime.parse(record[3]);
                    mTime.switchTimezone(Time.getCurrentTimezone());
                    String time_string = mTime.format("%H:%M:%S");
                    if (marker == null) {
                        mMarkers.put(record[0], mMap.addMarker(new MarkerOptions()
                                .position(new LatLng(Double.valueOf(record[1]), Double.valueOf(record[2])))
                                .title(record[0] + "/" + time_string)
                                .icon(BitmapDescriptorFactory.defaultMarker(MarkerProperties.MarkerColourByIndex(i)))));
                    } else {
                        marker.setPosition(new LatLng(Double.valueOf(record[1]), Double.valueOf(record[2])));
                        marker.setTitle(record[0] + "/" + time_string);
                    }
                }
            }
            if (mActiveMarker != null)
                mMap.animateCamera(CameraUpdateFactory.newLatLng(mActiveMarker.getPosition()));
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences sharedPref_ = PreferenceManager.getDefaultSharedPreferences(this);
        mMyId = sharedPref_.getString("pref_name", "User");
        if (mMyMarker != null) mMyMarker.setTitle(mMyId);
        registerReceiver(broadcastReceiver, new IntentFilter(LocationService.str_receiver));
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(broadcastReceiver);
    }
}
