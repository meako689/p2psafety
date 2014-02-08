package ua.p2psafety;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.MenuItem;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import com.facebook.Session;
import com.facebook.SessionState;
import com.facebook.UiLifecycleHelper;

import ua.p2psafety.Network.NetworkManager;
import ua.p2psafety.data.PhonesDatasourse;
import ua.p2psafety.data.Prefs;
import ua.p2psafety.sms.GmailOAuth2Sender;
import ua.p2psafety.util.Utils;
import ua.p2psafety.data.Prefs;
import ua.p2psafety.setphones.SetPhoneFragment;

/**
 * Created by ihorpysmennyi on 12/14/13.
 */
public class SosActivity extends ActionBarActivity {
    public static final String FRAGMENT_KEY = "fragmentKey";

    private UiLifecycleHelper mUiHelper;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.act_sosmain);
        setSupportActionBar();

        mUiHelper = new UiLifecycleHelper(this, null);
        mUiHelper.onCreate(savedInstanceState);

        NetworkManager.init(this);

        // SOS launcher with power button press
        startService(new Intent(this, PowerButtonService.class));

        Fragment fragment;

        String fragmentClass = getIntent().getStringExtra(FRAGMENT_KEY);
        if (fragmentClass != null)
            // activity started by widget
            fragment = Fragment.instantiate(this, fragmentClass);
        else {
            // normal start
            fragment = new SendMessageFragment();
        }
        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction().replace(R.id.content_frame, fragment).commit();

        if (Utils.getEmail(this) != null && Utils.isNetworkConnected(this) && Prefs.getGmailToken(this) == null)
        {
            GmailOAuth2Sender sender = new GmailOAuth2Sender(this);
            sender.initToken();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mUiHelper.onResume();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mUiHelper.onActivityResult(requestCode, resultCode, data);
        Session.getActiveSession().onActivityResult(this, requestCode, resultCode, data);
    }

    @Override
    public void onPause() {
        super.onPause();
        mUiHelper.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mUiHelper.onDestroy();
    }

    @Override
    public void onBackPressed() {
        Session currentSession = Session.getActiveSession();
        if (currentSession == null || currentSession.getState() != SessionState.OPENING)
            super.onBackPressed();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mUiHelper.onSaveInstanceState(outState);
        Session session = Session.getActiveSession();
        Session.saveSession(session, outState);
    }

    public void loginToFacebook(Activity activity, Session.StatusCallback callback) {
        if (!Utils.isNetworkConnected(activity)) {
            //errorDialog(activity, DIALOG_NO_CONNECTION);
            return;
        }
        Session session = Session.getActiveSession();
        if (session == null)
        {
            Session.openActiveSession(activity, true, callback);
        }
        else if (!session.getState().isOpened() && !session.getState().isClosed()) {
            session.openForRead(new Session.OpenRequest(activity)
                    //.setPermissions(Const.FB_PERMISSIONS_READ)
                    .setCallback(callback));
        } else {
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
}