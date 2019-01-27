package com.starcon.master.locator;

import android.Manifest;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.text.format.Time;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.security.GeneralSecurityException;
import java.util.Timer;
import java.util.TimerTask;

public class LocationService extends Service implements LocationListener {

    boolean isGPSEnable = false;
    boolean isNetworkEnable = false;
    LocationManager locationManager;
    Location m_location;
    private Handler mHandler = new Handler();
    private Timer mTimer = null;
    public static String str_receiver = "location.service.receiver";
    Intent mIntent;

    private String mServerName;
    private RequestQueue mRequestQueue;
    private String mBfPassword;
    private String mMyId;
    private StringBuilder mOtherResult;


    public LocationService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mRequestQueue = Volley.newRequestQueue(this);
        mTimer = new Timer();
        mIntent = new Intent(str_receiver);
        mOtherResult = new StringBuilder();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onLocationChanged(Location location) {
        m_location = location;
        Log.e("latitude", m_location.getLatitude() + "");
        Log.e("longitude", m_location.getLongitude() + "");
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        SharedPreferences mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        int mUpdateInterval = Integer.valueOf(mSharedPreferences.getString("pref_sync_time", "10")) * 1000;
        mServerName = mSharedPreferences.getString("pref_server", "http://derandr.000webhostapp.com");
        mBfPassword = mSharedPreferences.getString("pref_pass", "SomeTempPassword");
        mMyId = mSharedPreferences.getString("pref_name", "User");
        mTimer.schedule(new TimerTaskToGetLocation(), 5, mUpdateInterval);

        locationManager = (LocationManager) getApplicationContext().getSystemService(LOCATION_SERVICE);
        if (locationManager != null) {
            isGPSEnable = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            isNetworkEnable = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } else {
            Log.e("location", "Location manager isn't available");
        }

        if (isNetworkEnable) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.e("location", "No permissions granted");
            }
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10000, 10, this);
        } else if (isGPSEnable) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000, 10, this);
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mTimer.cancel();
    }

    private void serviceGetLocation() {
        if (m_location != null) serviceDataUpdate();
    }

    private class TimerTaskToGetLocation extends TimerTask {
        @Override
        public void run() {

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    serviceGetLocation();
                }
            });

        }
    }

    private void serviceDataUpdate() {
        Time time = new Time();
        time.setToNow();
        time.switchTimezone("UTC");
        String string_time = time.format2445();
        String my_location = String.valueOf(m_location.getLatitude()) + ";" + String.valueOf(m_location.getLongitude());
        mIntent.putExtra("me", my_location);
        getLocation();
        sendLocation(my_location + ";" + string_time);
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
                mOtherResult.setLength(0);
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
                            mOtherResult.append(decodedID).append(";").append(decodedLocation).append(":");
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                String other_result = mOtherResult.toString();
                if (!other_result.isEmpty()) mIntent.putExtra("other", other_result);
                sendBroadcast(mIntent);
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