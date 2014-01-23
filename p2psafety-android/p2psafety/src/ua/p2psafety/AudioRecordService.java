package ua.p2psafety;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.MediaRecorder;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.IBinder;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

import ua.p2psafety.data.Prefs;

public class AudioRecordService extends Service {
    private static Boolean mTimerOn = false;
    private static long mDuration;
    private static long mTimeLeft = 0;

    private static AudioRecordTimer mTimer;

    private static MediaRecorder mRecorder;
    File mRecordFile = null;

    @Override
    public void onCreate() {
        super.onCreate();
    }

    public void onCreate(Context context) {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!mTimerOn) {
            startRecording();
        }
        return START_STICKY;
    }

    private class AudioRecordTimer extends CountDownTimer {
        public AudioRecordTimer(long millisInFuture, long countDownInterval) {
            super(millisInFuture, countDownInterval);
        }

        @Override
        public void onFinish() {
            stopRecording();
        }

        @Override
        public void onTick(long millisUntilFinished) {
            mTimeLeft = millisUntilFinished;

            // update notification only once in a minute
            if (mTimeLeft / 1000 % 60 == 0)
                Notifications.notifAudioRecording(getApplicationContext(), mTimeLeft, mDuration);
        }
    }

    public void startRecording() {
        try {
            prepareRecorder();
            mRecorder.start();

            mDuration = Prefs.getMediaRecordLength(getApplicationContext());
            mTimeLeft = mDuration;
            mTimer = new AudioRecordTimer(mDuration, 1000);
            mTimer.start();
            mTimerOn = true;

            Notifications.notifAudioRecording(getApplicationContext(), mTimeLeft, mDuration);
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), "Can't start audio recording", Toast.LENGTH_LONG)
                 .show();
        }
    }

    private void prepareRecorder() throws IOException {
        File mediaDir;
        String state = Environment.getExternalStorageState();
        if(state.equals(Environment.MEDIA_MOUNTED))
            mediaDir = Environment.getExternalStorageDirectory();
        else
            mediaDir = getFilesDir();
        mRecordFile = File.createTempFile("sound", ".mp4", mediaDir);

        mRecorder = new MediaRecorder();
        mRecorder.setAudioSource(MediaRecorder.getAudioSourceMax());
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT);
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mRecorder.setAudioEncodingBitRate(128000);
        mRecorder.setAudioSamplingRate(16000);
        mRecorder.setOutputFile(mRecordFile.getAbsolutePath());
        mRecorder.prepare();
    }

    public void stopRecording() {
        mRecorder.stop();
        mRecorder.release();

        mTimer.cancel();
        mTimerOn = false;

        Notifications.removeNotification(getApplicationContext(), Notifications.NOTIF_AUDIO_RECORD_CODE);
        Notifications.notifAudioRecordingFinished(getApplicationContext());
    }

    @Override
    public void onDestroy() {
        if (mTimerOn) {
            stopRecording();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }

    public static void setDuration(Context context, long val) {
        mDuration = val;
        //Prefs.putAudioDuration(context, mDuration);
    }

    public static long getAudioDuration(Context context) {
        if (mDuration == 0) {
            //mDuration = Prefs.getAudioDuration(context);
        }
        return mDuration;
    }
}