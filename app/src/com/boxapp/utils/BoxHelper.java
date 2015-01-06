package com.boxapp.utils;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;

import com.boxapp.LoginActivity;
import com.boxapp.MainActivity;
import com.boxapp.R;
import com.boxapp.entity.LoginDetails;

/**
 * Created by insearching on 18.06.2014.
 */
public class BoxHelper {

    public static void authorize(Context context) {
        SharedPreferences userDetails = context.getSharedPreferences(KeyHelper.USER_DETAILS, Context.MODE_PRIVATE);
        SharedPreferences.Editor edit = userDetails.edit();
        edit.clear();
        edit.commit();

        String responseType = KeyHelper.CODE;
        Intent intent = new Intent(context, LoginActivity.class);
        intent.putExtra(KeyHelper.REQUEST_URL, Credentials.AUTH_URL + "authorize?response_type=" + responseType + "&client_id=" + Credentials.CLIENT_ID);
        context.startActivity(intent);
        ((MainActivity) context).finish();
    }

    public static void saveUserDetails(Context context, LoginDetails details) {
        SharedPreferences userDetails = context.getSharedPreferences(KeyHelper.USER_DETAILS, Context.MODE_PRIVATE);
        SharedPreferences.Editor edit = userDetails.edit();
        edit.clear();
        edit.putString(KeyHelper.Login.ACCESS_TOKEN, details.getAccessToken());
        edit.putString(KeyHelper.Login.REFRESH_TOKEN, details.getRefreshToken());
        edit.putInt(KeyHelper.Login.EXPIRES_IN, details.getExpiresIn());
        edit.commit();
    }

    public static LoginDetails getUserDetails(Context context){
        SharedPreferences userDetails = context.getSharedPreferences(KeyHelper.USER_DETAILS, Context.MODE_PRIVATE);
        String accessToken = userDetails.getString(KeyHelper.Login.ACCESS_TOKEN, null);
        String refreshToken = userDetails.getString(KeyHelper.Login.REFRESH_TOKEN, null);
        int expiresIn = userDetails.getInt(KeyHelper.Login.EXPIRES_IN, 0);
        return new LoginDetails(accessToken, refreshToken, expiresIn);
    }

    public static void updateDownloadNotification(Context context, String fileName, String action, int progress, int smallIcon, boolean isInDetermined) {
        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);

        String contentText = !isInDetermined ? action + " " + progress + "%" : action;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
        builder.setAutoCancel(true)
                .setContentTitle(fileName)
                .setContentText(contentText)
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_launcher))
                .setSmallIcon(smallIcon)
                .setProgress(100, progress, isInDetermined)
                .setTicker(action + " " + fileName);
        manager.notify(0, builder.build());
    }

    /**
     * Show a notification when file is downloaded
     */
    public static void showNotification(Context context, String title, String text, int smallIcon) {
        Intent notificationIntent = new Intent(android.content.Intent.ACTION_VIEW);
        java.io.File file = new java.io.File(KeyHelper.EXT_STORAGE_PATH + "/" + title);
        String extension = android.webkit.MimeTypeMap.getFileExtensionFromUrl(Uri.fromFile(file).toString().toLowerCase());
        String mimetype = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        notificationIntent.setDataAndType(Uri.fromFile(file), mimetype);

        PendingIntent contentIntent = PendingIntent.getActivity(context,
                1, notificationIntent,
                PendingIntent.FLAG_CANCEL_CURRENT);

        NotificationManager manager = (NotificationManager)
                context
                        .getSystemService(Context.NOTIFICATION_SERVICE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
        builder.setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setContentTitle(title)
                .setContentText(text)
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_launcher))
                .setLights(R.color.blue, 1000, 5000)
                .setSmallIcon(smallIcon);
        manager.notify(0, builder.build());
    }
}
