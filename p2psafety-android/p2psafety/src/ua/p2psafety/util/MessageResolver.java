package ua.p2psafety.util;

import android.content.Context;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Looper;
import android.text.format.DateFormat;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import ua.p2psafety.LocationService;
import ua.p2psafety.data.EmailsDatasourse;
import ua.p2psafety.data.PhonesDatasourse;
import ua.p2psafety.data.Prefs;

/**
 * Created by ihorpysmennyi on 12/7/13.
 */
public class MessageResolver {
    private Context context;
    private String message;
    private List<String> phones;
    private List<String> emails;
    public static Logs LOGS;

    public MessageResolver(Context context) {
        this.context = context;
        PhonesDatasourse phonesDatasourse = new PhonesDatasourse(context);
        EmailsDatasourse emailsDatasourse = new EmailsDatasourse(context);
        message = Prefs.getMessage(context);
        phones = new ArrayList<String>();
        phones = phonesDatasourse.getAllPhones();
        emails = emailsDatasourse.getAllEmails();

        LOGS = new Logs(context);
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();

        if (LOGS != null)
            LOGS.close();
    }

    /**
     * @return time and day as "hh:mm, ddd" or "hh:mm:ss, ddd"
     */
    public static String formatTimeAndDay(final long timestamp, final boolean includeSeconds) {
        return (DateFormat.format("kk:mm" + (includeSeconds ? ".ss" : "") + ",E", timestamp).toString());
    }

    private void sendMessage(String message) {
        LOGS.info("MessageResolver. Ready to send message: " + message);
        for (String phone : phones)
            SMSSender.send(phone, message, context);
        sendEmails(message);
    }

    public void sendEmails(String message, File file) {
        if (file == null)
            return;

        String account = Utils.getEmail(context);
        if (account != null && emails.size() > 0) {
            String csv = emails.toString().replace("[", "").replace("]", "").replace(", ", ",");
            GmailOAuth2Sender gmailOAuth2Sender = new GmailOAuth2Sender(context);
            gmailOAuth2Sender.sendMail("SOS!!!", message, account, csv, file);
        }
    }

    private void sendEmails(String message) {
        LOGS.info("MessageResolver. Sending emails");
        String account = Utils.getEmail(context);
        LOGS.info("MessageResolver. User email: " + account);
        if (account!=null && emails.size() > 0) {
            String csv = emails.toString().replace("[", "").replace("]", "").replace(", ", ",");
            GmailOAuth2Sender gmailOAuth2Sender = new GmailOAuth2Sender(context);
            gmailOAuth2Sender.sendMail("SOS!!!", message, account, csv);
        } else {
            LOGS.info("MessageResolver. User has no GMail account or no recipients provided");
        }
    }

    // TODO: refactor this code or better whole MessageResolver
    // (split it into SMSSender & EmailSender?)
    public void sendMessages() {
        LOGS.info("MessageResolver. Building message");
        AsyncTask ast = new AsyncTask() {
            @Override
            protected Object doInBackground(Object[] params) {
                try {
                    if (Prefs.getIsLoc(context)) {
                        LOGS.info("MessageResolver. Location option is turned ON");
                        Looper.prepare();
                        Location location = LocationService.locationListener.getLastLocation(true);
                        if (location != null) {
                            LOGS.info("MessageResolver. Got location");
                            String lat = location.getLatitude() + "";
                            if (lat.length() > 10)
                                lat = lat.substring(0, 9);
                            String lon = location.getLongitude() + "";
                            if (lon.length() > 10)
                                lon = lon.substring(0, 9);

                            message = new StringBuilder().append(message)
                                    .append("\n")
                                    .append(formatTimeAndDay(location.getTime(), false))
                                    .append(" https://maps.google.com/maps?q=")
                                    .append(lat).append(",").append(lon).toString();
                        } else {
                            LOGS.info("MessageResolver. Location is NULL");
                        }

                        sendMessage(message);
                        Log.d("Message", "Message sent" + message);
                        Looper.loop();
                    } else {
                        LOGS.info("MessageResolver. Location option is turned OFF");
                        sendMessage(message);
                        Log.d("Message", "Message sent" + message);
                    }
                } catch (Exception e) {
                    LOGS.error("Can't send messages", e);
                    e.printStackTrace();
                    Log.e("Error", "while sending messages ", e);
                }
                return null;
            }
        };

        AsyncTaskExecutionHelper.executeParallel(ast);
    }
}
