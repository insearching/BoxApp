package com.boxapp.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

/**
 * Created by insearching on 18.06.2014.
 */
public class FileHelper {

    private Context context;

    public FileHelper(Context context) {
        this.context = context;
    }

    /**
     * Saves file id and file name when file is downloaded
     * of uploaded (exists on device)
     *
     * @param ident of file
     * @param name  of file
     */
    public void saveFileData(String ident, String name) {
        SharedPreferences downloadPrefs = context.getSharedPreferences(KeyMap.DOWNLOADED_FILES, Context.MODE_MULTI_PROCESS);
        SharedPreferences.Editor edit = downloadPrefs.edit();
        edit.putString(ident, name);
        edit.commit();
    }

    public void recordPreferences(String access_token, String refresh_token) {
        SharedPreferences userPrefs = context.getSharedPreferences(KeyMap.USER_DETAILS, Context.MODE_PRIVATE);
        SharedPreferences.Editor edit = userPrefs.edit();
        edit.clear();
        edit.putString(KeyMap.ACCESS_TOKEN, access_token.trim());
        edit.putString(KeyMap.REFRESH_TOKEN, refresh_token.trim());
        edit.commit();
    }

    /**
     * Deletes file info from shared preferences when
     * file deleting from device
     *
     * @param ident of file to delete
     */
    public void deleteFileData(String ident) {
        SharedPreferences downloadPrefs = context.getSharedPreferences(KeyMap.DOWNLOADED_FILES, Context.MODE_MULTI_PROCESS);
        SharedPreferences.Editor edit = downloadPrefs.edit();
        edit.remove(ident);
        edit.commit();
    }

    public void copyFileOnDevice(String srcPath, String destPath) {
        try {
            InputStream in = new FileInputStream(new File(srcPath));
            OutputStream out = new FileOutputStream(new File(destPath));
            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            in.close();
            out.close();
        } catch (FileNotFoundException ex) {
            Log.d("File copy", ex.getMessage());
        } catch (IOException e) {
            Log.e("File copy", "IO error");
        }
    }

    public boolean renameFileOnDevice(String ident, String oldName, String newName, String extStoragePath) {
        SharedPreferences downloadPref = context.getSharedPreferences(KeyMap.DOWNLOADED_FILES, Context.MODE_MULTI_PROCESS);
        SharedPreferences.Editor edit = downloadPref.edit();
        Map<String, ?> idList = downloadPref.getAll();
        if (idList.get(ident) != null) {
            edit.putString(ident, newName);
            edit.commit();
        }

        File dir = new File(extStoragePath);
        if (dir.exists()) {
            File from = new File(dir, oldName);
            File to = new File(dir, newName);
            if (from.exists()) {
                from.renameTo(to);
                return true;
            }
        }
        return false;
    }

    public boolean isFileOnDevice(String name, String ident, String extStoragePath) {
        boolean result = false;
        File data = new File(extStoragePath + "/" + name);
        if (data.exists()) {
            SharedPreferences downloadPrefs = context.getSharedPreferences(KeyMap.DOWNLOADED_FILES, Context.MODE_MULTI_PROCESS);
            String recordedName = downloadPrefs.getString(ident, null);
            if (recordedName != null && recordedName.equals(name)) {
                result = true;
            }
        }
        return result;
    }
}
