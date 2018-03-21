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
import android.view.View;
import android.widget.Button;
import android.widget.Checkable;
import android.widget.Toast;
import android.support.v7.widget.Toolbar;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
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

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback {

    private final int REQUEST_CHECK_SETTINGS = 0;

    private GoogleMap mMap;

    private Toolbar toolbar_;

    private FusedLocationProviderClient locationClient_;
    private int updateInterval_;
    private Handler handler_;
    private RequestQueue queue_;
    private HashMap<String, Marker> markers_ = new HashMap<>();

    private String id_;
    private Marker me_;
    private String serverName_;
    private String bfPassword;

    private boolean autoAupdate_ = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);

        toolbar_ = findViewById(R.id.my_toolbar);
        locationClient_ = LocationServices.getFusedLocationProviderClient(this);


        SharedPreferences sharedPref_ = PreferenceManager.getDefaultSharedPreferences(this);
        id_ = sharedPref_.getString("pref_name", "User");
        bfPassword = sharedPref_.getString("pref_pass", "SomeTempPassword");
        updateInterval_ = Integer.valueOf(sharedPref_.getString("pref_sync_time", "10")) * 1000;
        serverName_  = sharedPref_.getString("pref_server", "http://derandr.000webhostapp.com");

        setSupportActionBar(toolbar_);

        createLocationRequest();

        handler_ = new Handler();
        queue_ = Volley.newRequestQueue(this);
        mapFragment.getMapAsync(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences sharedPref_ = PreferenceManager.getDefaultSharedPreferences(this);
        id_ = sharedPref_.getString("pref_name", "User");
        bfPassword = sharedPref_.getString("pref_pass", "SomeTempPassword");
        updateInterval_ = Integer.valueOf(sharedPref_.getString("pref_sync_time", "10")) * 1000;
        serverName_  = sharedPref_.getString("pref_server", "http://derandr.000webhostapp.com");
        if (me_ != null) me_.setTitle(id_);
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
                autoAupdate_ = !autoAupdate_;
                item.setChecked(autoAupdate_);
                item.setIcon(autoAupdate_ ? R.drawable.ic_auto_pressed : R.drawable.ic_auto_update);
                if (autoAupdate_) startRepeatingTask();
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
        me_ = mMap.addMarker(new MarkerOptions().position(new LatLng(0, 0)).title(id_).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
    }

    void updateStatus() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        locationClient_.getLastLocation()
                .addOnSuccessListener(MapsActivity.this, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        if (location != null) {
                            me_.setPosition(new LatLng(location.getLatitude(), location.getLongitude()));
                            sendLocation(String.valueOf(location.getLatitude()) + ";" + String.valueOf(location.getLongitude()));
                            getLocation();
                        }
                    }
                });
    }

    protected void createLocationRequest() {
        LocationRequest locationRequest = new LocationRequest();
        locationRequest.setInterval(updateInterval_);
        locationRequest.setFastestInterval(updateInterval_);
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
                if (autoAupdate_) handler_.postDelayed(StatusChecker, updateInterval_);
            }
        }
    };

    void startRepeatingTask() {
        StatusChecker.run();
    }

    void sendLocation(String location) {
        if (id_.equals("User")) return;
        byte[] encoded_id = new byte[0];
        byte[] encoded_location = new byte[0];
        try {
            encoded_id = LocationSecurity.encrypt(bfPassword, id_);
            encoded_location = LocationSecurity.encrypt(bfPassword, location);
        } catch (GeneralSecurityException e) {
            e.printStackTrace();
        }
        String url = serverName_ + "?v1=" + LocationSecurity.byteArrayToHexString(encoded_id) + "&&v2=" + LocationSecurity.byteArrayToHexString(encoded_location);

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
        queue_.add(request);
    }

    void getLocation() {

        String url = serverName_ + "?v1=query";

        Response.Listener<JSONArray> listener = new Response.Listener<JSONArray>() {
            @Override
            public void onResponse(JSONArray response) {
                for (int i = 0; i < response.length(); i++) {
                    try {
                        JSONObject object = response.getJSONObject(i);

                        String decodedID = "";
                        String decodedLocation = "";
                        try {
                            decodedID = LocationSecurity.decrypt(bfPassword, LocationSecurity.hexStringToByteArray(object.getString("N")));
                            decodedLocation = LocationSecurity.decrypt(bfPassword, LocationSecurity.hexStringToByteArray(object.getString("C")));
                        } catch (GeneralSecurityException e) {
                            e.printStackTrace();
                        }

                        if (!decodedID.equals(id_)) {
                            Log.d("Record: ", "N: " + decodedID + " C: " + decodedLocation);
                            String[] data = decodedLocation.split(";");
                            Marker marker = markers_.get(decodedID);
                            if (marker == null) {
                                markers_.put(decodedID, mMap.addMarker(new MarkerOptions()
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
        queue_.add(request);

    }
}
