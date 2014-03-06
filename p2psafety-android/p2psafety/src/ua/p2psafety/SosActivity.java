package ua.p2psafety;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.MenuItem;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import com.facebook.Session;
import com.facebook.SessionState;
import com.facebook.UiLifecycleHelper;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.location.LocationClient;

import ua.p2psafety.Network.NetworkManager;
import ua.p2psafety.data.PhonesDatasourse;
import ua.p2psafety.data.Prefs;
import ua.p2psafety.sms.GmailOAuth2Sender;
import ua.p2psafety.util.Logs;
import ua.p2psafety.util.Utils;

/**
 * Created by ihorpysmennyi on 12/14/13.
 */
public class SosActivity extends ActionBarActivity implements
        GooglePlayServicesClient.ConnectionCallbacks,
        GooglePlayServicesClient.OnConnectionFailedListener {
    public static final String FRAGMENT_KEY = "fragmentKey";
    // milliseconds per second
    public static final int MILLISECONDS_PER_SECOND = 1000;
    public static final int LOCATION_FIX_TIMEOUT = MILLISECONDS_PER_SECOND * 10;

    private UiLifecycleHelper mUiHelper;
    public static Logs mLogs;
    public static AWLocationListener locationListener;
    private static LocationClient mLocationClient;
    private static LocationManager mLocationManager;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.act_sosmain);
        setSupportActionBar();

        mLogs = new Logs(this);
        mLogs.info("\n\n\n==========================\n==============================");
        mLogs.info("SosActiviy. onCreate()");
        mUiHelper = new UiLifecycleHelper(this, null);
        mUiHelper.onCreate(savedInstanceState);

        mLogs.info("SosActiviy. onCreate. Initiating NetworkManager");
        NetworkManager.init(this);
        mLogs.info("SosActiviy. onCreate. Starting PowerButtonService");
        startService(new Intent(this, PowerButtonService.class));
        if (!Utils.isServiceRunning(this, XmppService.class) &&
            Utils.isServerAuthenticated(this) &&
            !EventManager.getInstance(this).isSosStarted())
        {
            startService(new Intent(this, XmppService.class));
        }

        mLocationClient = new LocationClient(this, this, this);
        locationListener = new AWLocationListener();
        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    }

    @Override
    public void onResume() {
        super.onResume();
        mUiHelper.onResume();

        Fragment fragment;

        String fragmentClass = getIntent().getStringExtra(FRAGMENT_KEY);
        if (fragmentClass != null) {
            // activity started from outside
            // and requested to show specific fragment
            mLogs.info("SosActiviy. onCreate. Activity requested to open " + fragmentClass);
            fragment = Fragment.instantiate(this, fragmentClass);
            fragment.setArguments(getIntent().getExtras());
        } else {
            // normal start
            mLogs.info("SosActiviy. onCreate. Normal start. Opening SendMessageFragment");
            fragment = new SendMessageFragment();
        }

        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction().addToBackStack(null)
                .replace(R.id.content_frame, fragment).commit();

        if (Utils.getEmail(this) != null && Utils.isNetworkConnected(this, mLogs) && Prefs.getGmailToken(this) == null)
        {
            mLogs.info("SosActiviy. onCreate. Getting new GmailOAuth token");
            GmailOAuth2Sender sender = new GmailOAuth2Sender(this);
            sender.initToken();
        }
        mLogs.info("SosActiviy. onCreate. Checking for location services");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.i("onNewIntent", "NEW INTENT!");
        setIntent(intent);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mLogs.info("SosActiviy.onActivityResult()");
        mUiHelper.onActivityResult(requestCode, resultCode, data);
        Session.getActiveSession().onActivityResult(this, requestCode, resultCode, data);
    }

    @Override
    public void onPause() {
        super.onPause();
        mLogs.info("SosActiviy.onPause");
        mUiHelper.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mLogs.info("SosActiviy.onDestroy()");
        mLogs.info("\n\n\n==========================\n==============================");
        mUiHelper.onDestroy();
        mLogs.close();
    }

    @Override
    public void onBackPressed() {
        mLogs.info("SosActivity.onBackPressed()");
        Session currentSession = Session.getActiveSession();
        if (currentSession == null || currentSession.getState() != SessionState.OPENING) {
            super.onBackPressed();
        } else {
            mLogs.info("SosActivity. onBackPressed. Ignoring");
        }

        FragmentManager fm = getSupportFragmentManager();
        if (fm.getBackStackEntryCount() == 0) {
            finish();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mLogs.info("SosActivity.onSaveInstanceState()");
        mUiHelper.onSaveInstanceState(outState);
        mLogs.info("SosActivity. onSaveInstanceState. Saving session");
        Session session = Session.getActiveSession();
        Session.saveSession(session, outState);
    }

    public void loginToFacebook(Activity activity, Session.StatusCallback callback) {
        mLogs.info("SosActivity. loginToFacebook()");
        if (!Utils.isNetworkConnected(activity, mLogs)) {
            mLogs.info("SosActivity. loginToFacebook. No network");
            Utils.errorDialog(activity, Utils.DIALOG_NO_CONNECTION);
            return;
        }
        Session session = Session.getActiveSession();
        if (session == null) {
            mLogs.info("SosActivity. No FB session. Opening a new one");
            Session.openActiveSession(activity, true, callback);
        }
        else if (!session.getState().isOpened() && !session.getState().isClosed()) {
            mLogs.info("SosActivity. loginToFacebook. FB session not opened AND not closed. Opening for read");
            session.openForRead(new Session.OpenRequest(activity)
                    //.setPermissions(Const.FB_PERMISSIONS_READ)
                    .setCallback(callback));
        } else {
            mLogs.info("SosActivity. loginToFacebook. FB session opened or closed. Opening a new one");
            Session.openActiveSession(activity, true, callback);
        }
    }

    private void setSupportActionBar() {
        ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle("");
        ImageView icon;
        if (android.os.Build.VERSION.SDK_INT < 11)
            icon = (ImageView) findViewById(R.id.home);
        else
            icon = (ImageView) findViewById(android.R.id.home);
        FrameLayout.LayoutParams iconLp = (FrameLayout.LayoutParams) icon.getLayoutParams();
        iconLp.topMargin = iconLp.bottomMargin = 0;
        icon.setLayoutParams(iconLp);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case android.R.id.home:
                if (new PhonesDatasourse(SosActivity.this).getAllPhones().size() == 0) {
                    Toast.makeText(SosActivity.this, R.string.enter_phones, Toast.LENGTH_LONG).show();
                    break;
                }
                Fragment fragment = new SendMessageFragment();
                FragmentManager fragmentManager = getSupportFragmentManager();
                for (int i = 0; i < fragmentManager.getBackStackEntryCount(); ++i) {
                    fragmentManager.popBackStack();
                }
                FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
                //fragmentTransaction.setCustomAnimations(R.anim.slide_right_in, R.anim.slide_right_out, R.anim.slide_left_in, R.anim.slide_left_out);
                fragmentTransaction.replace(R.id.content_frame, fragment).commit();
                break;

        }
        return (super.onOptionsItemSelected(menuItem));
    }

    @Override
    public void onConnected(Bundle bundle) {
        for (String provider : mLocationManager.getProviders(true)) {
            mLocationManager.requestLocationUpdates(provider, 1000, 0, locationListener);
        }
    }

    @Override
    public void onDisconnected() {
        // do nothing
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        // ignore
    }

    public class AWLocationListener implements android.location.LocationListener {

        private Location mLocation;                                     // last known location
        private OnLocationChangedListener mOnLocationChangedListener;

        @Override
        public void onLocationChanged(Location location) {
            mLocation = location;

            if (location.getProvider().equals("gps")) {
                // GPS location is most accurate, so stop updating
                mLocationManager.removeUpdates(this);
            }

            if (mOnLocationChangedListener != null) {
                mOnLocationChangedListener.onLocationChanged(mLocation);
            }
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) { }

        @Override
        public void onProviderEnabled(String provider) { }

        @Override
        public void onProviderDisabled(String provider) { }

        /**
         * Return last known location
         * @param isNeededLocationServices whether we need to Location Services should be active or not,
         *                                 if true, then we ask user for activate Services,
         *                                 if false, then we just ignore it, and return location,
         *                                 which we have
         * @return last known location
         */
        public Location getLastLocation(boolean isNeededLocationServices) {
            // if last known location is not null and not too old - return it
            if (mLocation != null
                    && (System.currentTimeMillis() - mLocation.getTime() <= LOCATION_FIX_TIMEOUT))
                return mLocation;

            // start listening GPS if it is enabled
            if (mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0,
                        locationListener);
            }

            // if some of providers are not active, then ask user to do it
            if (isNeededLocationServices
                    && (!mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    !mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))) {
                // Build the alert dialog
                AlertDialog.Builder builder = new AlertDialog.Builder(SosActivity.this);
                builder.setTitle(getString(R.string.location_services_not_active));
                builder.setMessage(getString(R.string.please_enable_location_services));
                builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialogInterface, int i) {
                        // Show location settings when the user acknowledges the alert dialog
                        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        startActivity(intent);
                    }
                });
                Dialog alertDialog = builder.create();
                alertDialog.setCanceledOnTouchOutside(false);
                alertDialog.show();
            } else {
                // return some location from PASSIVE provider
                mLocation = mLocationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
            }
            return mLocation;
        }

        public void setOnLocationChangedListener(OnLocationChangedListener listener) {
            mOnLocationChangedListener = listener;
        }
    }

    public interface OnLocationChangedListener {
        public void onLocationChanged(Location loc);
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (mLocationClient.isConnected()) {
            /*
             * Remove location updates for a listener.
             */
            mLocationManager.removeUpdates(locationListener);
        }
        /*
         * After disconnect() is called, the client is
         * considered "dead".
         */
        mLocationClient.disconnect();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mLocationClient.connect();
    }
}