package com.boxapp.utils;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.boxapp.R;
import com.boxapp.entity.FileInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;

/**
 * Created by insearching on 18.06.2014.
 */
public class BoxHelper {

    private static final String TAG = "JSON";

    /**
     * Gets info about file or folder, without array
     * in JSON query.
     *
     * @param json   JSON data got from response
     * @param number number of file or folder in JSON
     * @return FileInfo object with info about file
     */
    public static FileInfo findObject(String json, int number) {
        try {
            JSONArray files = new JSONObject(json).getJSONArray(KeyMap.ENTRIES);
            JSONObject text = files.getJSONObject(number);
            String name = text.getString(KeyMap.NAME);
            String type = text.getString(KeyMap.TYPE);
            String id = text.getString(KeyMap.ID);
            String etag = text.getString(KeyMap.ETAG);
            return new FileInfo(name, type, id, etag);
        } catch (JSONException e) {
            Log.e(TAG, e.getMessage());
            return null;
        }
    }

    /**
     * Gets info about file or folder
     *
     * @param json     JSON data got from response
     * @param position number of file or folder in JSON
     */
    public static FileInfo findObjectByPos(String json, int position) {
        FileInfo info = null;
        try {
            JSONObject data = new JSONObject(json).getJSONObject(KeyMap.ITEM_COLLECTION);
            JSONArray entries = data.getJSONArray(KeyMap.ENTRIES);
            JSONObject object = entries.getJSONObject(position);
            String name = object.getString(KeyMap.NAME);
            String type = object.getString(KeyMap.TYPE);
            String id = object.getString(KeyMap.ID);
            String etag = object.getString(KeyMap.ETAG);
            info = new FileInfo(name, type, id, etag);
        } catch (JSONException e) {
            Log.e(TAG, e.getMessage());
        }
        return info;
    }

    public static ArrayList<FileInfo> getFolderItems(String json) {
        ArrayList<FileInfo> fileList = new ArrayList<FileInfo>();
        try {
            JSONObject data = new JSONObject(json).getJSONObject(KeyMap.ITEM_COLLECTION);
            JSONArray files = data.getJSONArray(KeyMap.ENTRIES);
            int fileCount = files.length();
            for (int i = 0; i < fileCount; i++) {
                JSONObject text = files.getJSONObject(i);
                String name = text.getString(KeyMap.NAME);
                String type = text.getString(KeyMap.TYPE);
                String id = text.getString(KeyMap.ID);
                FileInfo fi = new FileInfo(name, type, id);
                fileList.add(fi);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return fileList;
    }

    public static void updateDownloadNotification(Context context, String fileName, String action, int progress, int smallIcon, boolean isInDeterminated) {
        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);

        String contentText = !isInDeterminated ? action + " " + progress + "%" : action;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
        builder.setAutoCancel(true)
                .setContentTitle(fileName)
                .setContentText(contentText)
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_launcher))
                .setSmallIcon(smallIcon)
                .setProgress(100, progress, isInDeterminated)
                .setTicker(action + " " + fileName);
        manager.notify(0, builder.build());
    }

    /**
     * Show a notification when file is downloaded
     */
    public static void showNotification(Context context, String title, String text, String path, int smallIcon) {
        Intent notificationIntent = new Intent(android.content.Intent.ACTION_VIEW);
        File file = new File(path + "/" + title);
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
