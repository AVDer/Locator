package com.starcon.master.locator;

import android.Manifest;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import android.support.v7.widget.Toolbar;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.github.pengrad.mapscaleview.MapScaleView;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.security.GeneralSecurityException;
import java.util.HashMap;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback,
        GoogleMap.OnCameraMoveListener,
GoogleMap.OnCameraIdleListener,
GoogleMap.OnCameraChangeListener,
GoogleMap.OnMarkerClickListener,
GoogleMap.OnMapClickListener{

    private final int REQUEST_CHECK_SETTINGS = 0;

    private GoogleMap mMap;
    private MapScaleView mScaleView;

    private FusedLocationProviderClient mLocationClient;
    private int mUpdateInterval;
    private Handler mRunHandler;
    private RequestQueue mRequestQueue;
    private HashMap<String, Marker> mMarkers = new HashMap<>();

    private String mMyId;
    private Marker mMyMarker;
    private Marker mActiveMarker;
    private String mServerName;
    private String mBfPassword;

    private boolean mAutoUpdate = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);

        Toolbar mToolbar = findViewById(R.id.my_toolbar);
        mLocationClient = LocationServices.getFusedLocationProviderClient(this);


        SharedPreferences sharedPref_ = PreferenceManager.getDefaultSharedPreferences(this);
        mMyId = sharedPref_.getString("pref_name", "User");
        mBfPassword = sharedPref_.getString("pref_pass", "SomeTempPassword");
        mUpdateInterval = Integer.valueOf(sharedPref_.getString("pref_sync_time", "10")) * 1000;
        mServerName = sharedPref_.getString("pref_server", "http://derandr.000webhostapp.com");

        setSupportActionBar(mToolbar);

        createLocationRequest();

        mRunHandler = new Handler();
        mRequestQueue = Volley.newRequestQueue(this);
        mapFragment.getMapAsync(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences sharedPref_ = PreferenceManager.getDefaultSharedPreferences(this);
        mMyId = sharedPref_.getString("pref_name", "User");
        mBfPassword = sharedPref_.getString("pref_pass", "SomeTempPassword");
        mUpdateInterval = Integer.valueOf(sharedPref_.getString("pref_sync_time", "10")) * 1000;
        mServerName = sharedPref_.getString("pref_server", "http://derandr.000webhostapp.com");
        if (mMyMarker != null) mMyMarker.setTitle(mMyId);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar, menu);
        MenuItem menuItem = menu.findItem(R.id.action_auto);
        menuItem.setChecked(false);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_connect:
                updateStatus();
                return true;
            case R.id.action_settings:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                return true;
            case R.id.action_auto:
                mAutoUpdate = !mAutoUpdate;
                item.setChecked(mAutoUpdate);
                item.setIcon(mAutoUpdate ? R.drawable.ic_auto_pressed : R.drawable.ic_auto_update);
                if (mAutoUpdate) startRepeatingTask();
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

    void updateStatus() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mLocationClient.getLastLocation()
                .addOnSuccessListener(MapsActivity.this, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        if (location != null) {
                            mMyMarker.setPosition(new LatLng(location.getLatitude(), location.getLongitude()));
                            sendLocation(String.valueOf(location.getLatitude()) + ";" + String.valueOf(location.getLongitude()));
                            getLocation();
                        }
                    }
                });
        if (mActiveMarker != null) mMap.animateCamera(CameraUpdateFactory.newLatLng(mActiveMarker.getPosition()));
    }

    protected void createLocationRequest() {
        LocationRequest locationRequest = new LocationRequest();
        locationRequest.setInterval(mUpdateInterval);
        locationRequest.setFastestInterval(mUpdateInterval);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest);

        SettingsClient client = LocationServices.getSettingsClient(this);
        Task<LocationSettingsResponse> task = client.checkLocationSettings(builder.build());


        task.addOnSuccessListener(this, new OnSuccessListener<LocationSettingsResponse>() {
            @Override
            public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                Toast.makeText(MapsActivity.this, "Settings OK", Toast.LENGTH_SHORT).show();
            }
        });

        task.addOnFailureListener(this, new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                if (e instanceof ResolvableApiException) {
                    try {
                        ResolvableApiException resolvable = (ResolvableApiException) e;
                        resolvable.startResolutionForResult(MapsActivity.this,
                                REQUEST_CHECK_SETTINGS);
                    } catch (IntentSender.SendIntentException sendEx) {
                        // Ignore the error.
                    }
                }
            }
        });

    }

    Runnable StatusChecker = new Runnable() {
        @Override
        public void run() {
            try {
                updateStatus();
            } finally {
                if (mAutoUpdate) mRunHandler.postDelayed(StatusChecker, mUpdateInterval);
            }
        }
    };

    void startRepeatingTask() {
        StatusChecker.run();
    }

    void sendLocation(String location) {
        if (mMyId.equals("User")) return;
        byte[] encoded_id = new byte[0];
        byte[] encoded_location = new byte[0];
        try {
            encoded_id = LocationSecurity.encrypt(mBfPassword, mMyId);
            encoded_location = LocationSecurity.encrypt(mBfPassword, location);
        } catch (GeneralSecurityException e) {
            e.printStackTrace();
        }
        String url = mServerName + "?v1=" + LocationSecurity.byteArrayToHexString(encoded_id) + "&&v2=" + LocationSecurity.byteArrayToHexString(encoded_location);

        StringRequest request = new StringRequest(com.android.volley.Request.Method.GET, url,
                new com.android.volley.Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        // No action
                    }
                }, new com.android.volley.Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                // No action
            }
        });
        mRequestQueue.add(request);
    }

    void getLocation() {

        String url = mServerName + "?v1=query";

        Response.Listener<JSONArray> listener = new Response.Listener<JSONArray>() {
            @Override
            public void onResponse(JSONArray response) {
                for (int i = 0; i < response.length(); i++) {
                    try {
                        JSONObject object = response.getJSONObject(i);

                        String decodedID = "";
                        String decodedLocation = "";
                        try {
                            decodedID = LocationSecurity.decrypt(mBfPassword, LocationSecurity.hexStringToByteArray(object.getString("N")));
                            decodedLocation = LocationSecurity.decrypt(mBfPassword, LocationSecurity.hexStringToByteArray(object.getString("C")));
                        } catch (GeneralSecurityException e) {
                            e.printStackTrace();
                        }

                        if (!decodedID.equals(mMyId)) {
                            Log.d("Record: ", "N: " + decodedID + " C: " + decodedLocation);
                            String[] data = decodedLocation.split(";");
                            Marker marker = mMarkers.get(decodedID);
                            if (marker == null) {
                                mMarkers.put(decodedID, mMap.addMarker(new MarkerOptions()
                                        .position(new LatLng(Double.valueOf(data[0]), Double.valueOf(data[1])))
                                        .title(decodedID)
                                        .icon(BitmapDescriptorFactory.defaultMarker(MarkerProperties.MarkerColourByIndex(i)))));
                            }
                            else {
                                marker.setPosition(new LatLng(Double.valueOf(data[0]), Double.valueOf(data[1])));
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        };

        Response.ErrorListener error_listener = new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                // TODO: Handle error
            }
        };

        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, url, "", listener, error_listener);
        mRequestQueue.add(request);

    }
}
