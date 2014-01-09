package ua.ip.sosmessage;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.concurrent.TimeUnit;

import ua.ip.sosmessage.message.MessageFragment;
import ua.ip.sosmessage.setphones.SetPhoneFragment;
import ua.ip.sosmessage.sms.MessageResolver;

public class DelayedSosFragment extends Fragment {
    TextView mTimerText;
    Button mTimerBtn;
    ImageButton mArrowUpBtn, mArrowDownBtn;

    Activity mActivity;

    public DelayedSosFragment() {
        super();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mActivity = getActivity();
        ((SosActivity) mActivity).getSupportActionBar().setHomeButtonEnabled(false);
        ((SosActivity) mActivity).getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        Typeface font = Typeface.createFromAsset(getActivity().getAssets(), "fonts/RobotoCondensed-Bold.ttf");

        View view = inflater.inflate(R.layout.frag_sos_timer, container, false);

        mTimerText = (TextView) view.findViewById(R.id.timerText);
        mTimerBtn = (Button) view.findViewById(R.id.timerBtn);
        mArrowUpBtn = (ImageButton) view.findViewById(R.id.arrowUpBtn);
        mArrowDownBtn = (ImageButton) view.findViewById(R.id.arrowDownBtn);

        return view;
    }

    @Override
    public void onViewCreated (View view, Bundle savedInstanceState) {
        mTimerBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (DelayedSosService.isTimerOn()) {
                    mActivity.stopService(new Intent(mActivity, DelayedSosService.class));
                    onTimerStop();
                } else {
                    mActivity.startService(new Intent(mActivity, DelayedSosService.class));
                    onTimerStart();
                }
            }
        });

        mArrowUpBtn.setOnTouchListener(new OnTouchContinuousListener() {
            @Override
            public void onTouchRepeat(View view) {
                if (!DelayedSosService.isTimerOn()) {
                    long sosDelay = DelayedSosService.getSosDelay(mActivity);
                    sosDelay += 1*60*1000; // +1 min
                    sosDelay = Math.min(sosDelay, 120 * 60 * 1000); // max 120 min
                    DelayedSosService.setSosDelay(mActivity, sosDelay);
                    showSosDelay(sosDelay);
                }
            }
        });

        mArrowDownBtn.setOnTouchListener(new OnTouchContinuousListener() {
            @Override
            public void onTouchRepeat(View view) {
                if (!DelayedSosService.isTimerOn()) {
                    long sosDelay = DelayedSosService.getSosDelay(mActivity);
                    sosDelay -= 1*60*1000; // -1 min
                    sosDelay = Math.max(sosDelay, 1*60*1000); // min 1 min
                    DelayedSosService.setSosDelay(mActivity, sosDelay);
                    showSosDelay(sosDelay);
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();

        // start listen to delayed sos timer
        IntentFilter filter = new IntentFilter();
        filter.addAction(DelayedSosService.SOS_DELAY_TICK);
        filter.addAction(DelayedSosService.SOS_DELAY_FINISH);
        filter.addAction(DelayedSosService.SOS_DELAY_CANCEL);
        mActivity.registerReceiver(mBroadcastReceiver, filter);

        if (DelayedSosService.isTimerOn())
            onTimerStart();
        else
            onTimerStop();
    }

    @Override
    public void onPause() {
        super.onPause();

        mActivity.unregisterReceiver(mBroadcastReceiver);
    }

    private void onTimerStart() {
        showSosDelay(DelayedSosService.getTimeLeft());
        mArrowUpBtn.setImageDrawable(
                getResources().getDrawable(R.drawable.arrow_up_inactive));
        mArrowDownBtn.setImageDrawable(
                getResources().getDrawable(R.drawable.arrow_down_inactive));
        mTimerBtn.setText(getResources().getString(R.string.stop));
    }

    private void onTimerStop() {
        mArrowUpBtn.setImageDrawable(
                getResources().getDrawable(R.drawable.arrow_up));
        mArrowDownBtn.setImageDrawable(
                getResources().getDrawable(R.drawable.arrow_down));
        showSosDelay(DelayedSosService.getSosDelay(mActivity));
        mTimerBtn.setText(getResources().getString(R.string.start));
    }

    private void showSosDelay(long sosDelay) {
        String timerText = String.format("%02d:%02d",
                TimeUnit.MILLISECONDS.toSeconds(sosDelay) / 60,
                TimeUnit.MILLISECONDS.toSeconds(sosDelay) % 60);
        mTimerText.setText(timerText);
    }

    // Broadcast from DelayedSosService timer
    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equals(DelayedSosService.SOS_DELAY_TICK)) {
                // show time left
                showSosDelay(DelayedSosService.getTimeLeft());
            }
            else if (action.equals(DelayedSosService.SOS_DELAY_FINISH)) {
                onTimerStop();
            }
            else if (action.equals(DelayedSosService.SOS_DELAY_CANCEL)) {
                onTimerStop();
            }
        }
    };
}