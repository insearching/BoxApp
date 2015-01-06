package com.boxapp.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

import retrofit.client.Response;

/**
 * Created by insearching on 18.06.2014.
 */
public class FileHelper {

    private Context context;
    private ProgressUpdateListener callback;

    public FileHelper(Context context) {
        this.context = context;
        callback = (ProgressUpdateListener) context;
    }

    /**
     * Saves file id and file name when file is downloaded
     * of uploaded (exists on device)
     *
     * @param id of file
     */
    public static void saveFileData(Context context, String id) {
        SharedPreferences downloadPrefs = context.getSharedPreferences(KeyHelper.DOWNLOADED_FILES, Context.MODE_MULTI_PROCESS);
        SharedPreferences.Editor edit = downloadPrefs.edit();
        edit.putString(id, id);
        edit.commit();
    }

    /**
     * Deletes file info from shared preferences when
     * file deleting from device
     *
     * @param id of file to delete
     */
    public void deleteFileData(String id) {
        SharedPreferences downloadPrefs = context.getSharedPreferences(KeyHelper.DOWNLOADED_FILES, Context.MODE_MULTI_PROCESS);
        SharedPreferences.Editor edit = downloadPrefs.edit();
        edit.remove(id);
        edit.commit();
    }

    public void recordPreferences(String access_token, String refresh_token) {
        SharedPreferences userPrefs = context.getSharedPreferences(KeyHelper.USER_DETAILS, Context.MODE_PRIVATE);
        SharedPreferences.Editor edit = userPrefs.edit();
        edit.clear();
        edit.putString(KeyHelper.ACCESS_TOKEN, access_token.trim());
        edit.putString(KeyHelper.REFRESH_TOKEN, refresh_token.trim());
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
        SharedPreferences downloadPref = context.getSharedPreferences(KeyHelper.DOWNLOADED_FILES, Context.MODE_MULTI_PROCESS);
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
            SharedPreferences downloadPrefs = context.getSharedPreferences(KeyHelper.DOWNLOADED_FILES, Context.MODE_MULTI_PROCESS);
            String recordedName = downloadPrefs.getString(ident, null);
            if (recordedName != null && recordedName.equals(name)) {
                result = true;
            }
        }
        return result;
    }

    public static byte[] getBytesFromStream(InputStream is) throws IOException {

        int length;
        int size = 1024;
        byte[] buffer;

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        buffer = new byte[size];
        while ((length = is.read(buffer, 0, size)) != -1) {
            bos.write(buffer, 0, length);
        }
        buffer = bos.toByteArray();

        return buffer;
    }

    public static File createFile(String path, String fileName) {
        String sFolder = path + "/";
        File file = new File(sFolder);
        if (!file.exists())
            file.mkdirs();

        try {
            file = new File(sFolder + fileName);

            if (!file.createNewFile()) {
                file.delete();
                if (!file.createNewFile()) {
                    return null;
                }
            }
        } catch (Exception e) {
            return null;
        }
        return file;
    }

    public void saveFile(Response response, int fileId) {
        int progress = 0;
        try {
            InputStream is = response.getBody().in();
            long lengthOfFile = response.getBody().length();

            InputStream input = new BufferedInputStream(is, 8192);
            File file = FileHelper.createFile(KeyHelper.EXT_STORAGE_PATH, String.valueOf(fileId));
            OutputStream output = new FileOutputStream(file);
            byte data[] = new byte[1024];
            long total = 0;
            int count;
            while ((count = input.read(data)) != -1) {
                total += count;
                output.write(data, 0, count);
                int curProgress = (int) ((total * 100) / lengthOfFile);
                if ((curProgress % 5) == 0 && progress != curProgress)
                    callback.onProgressUpdate((int) ((total * 100) / lengthOfFile), String.valueOf(fileId));
            }
            output.flush();
            output.close();
            input.close();
            FileHelper.saveFileData(context, String.valueOf(fileId));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public interface ProgressUpdateListener {
        public void onProgressUpdate(int progress, String fileName);
    }
}
