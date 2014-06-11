/*
 * Copyright (c) 2011 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.boxapp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.boxapp.utils.AsyncLib;
import com.boxapp.utils.BoxWidgetProvider;
import com.boxapp.utils.FileInfo;
import com.boxapp.utils.FileListAdapter;
import com.boxapp.utils.KeyMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Map;

public final class MainActivity extends Activity implements DownloadListener {
    public static String jsonQuery = null;
    public static final String FILE_NAME = "filename";
    public static ArrayList<FileInfo> fileList = new ArrayList<FileInfo>();

    private String mAccessToken = null;
    private String mRefreshToken = null;
    private String mCurDirId = "0";
    private String mCurDirName = "All files";
    private String mCopyId = null;
    private String mCopyName = null;
    private String mCopyType = null;
    private ListView mFileListView;

    private final String EXT_STORAGE_PATH = Environment.getExternalStorageDirectory().getPath() + "/BoxApp";
    private static final String TAG = "BoxAppLog";
    //Request URLs
    private static final String ROOT_URL = "https://api.box.com/2.0/";
    private static final String UPLOAD_URL = "https://upload.box.com/api/2.0/files/content";
    //Context menu items
    private static final int OPEN = Menu.FIRST;
    private static final int COPY = Menu.FIRST + 1;
    private static final int PASTE = Menu.FIRST + 2;
    private static final int DELETE = Menu.FIRST + 3;
    private static final int RENAME = Menu.FIRST + 4;
    private static final int DOWNLOAD = Menu.FIRST + 5;
    //Options menu items
    private static final int UPLOAD = R.id.menu_upload;
    private static final int CREATE_FOLDER = R.id.menu_create_folder;
    private static final int PASTE_OPTION = R.id.menu_paste;
    private static final int LOGOUT = R.id.menu_logout;

    private ArrayList<TextView> folderList;
    private final Context context = this;
    private AsyncLib task;
    private TextView homeButton;
    private boolean isFolderChanged = false;
    private Menu menu;

    @SuppressLint("NewApi")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mFileListView = (ListView) findViewById(R.id.fileListView);

        registerForContextMenu(mFileListView);
        getActionBar().setHomeButtonEnabled(true);

        SharedPreferences userDetails = getSharedPreferences(KeyMap.USER_DETAILS, MODE_PRIVATE);
        mAccessToken = userDetails.getString(KeyMap.ACCESS_TOKEN, "");
        mRefreshToken = userDetails.getString(KeyMap.REFRESH_TOKEN, "");
        task = new AsyncLib(context, mAccessToken, mRefreshToken);

        File folder = new File(EXT_STORAGE_PATH);
        if (folder.exists() && folder.isDirectory()) {
            folder.mkdirs();
        }
        folderList = new ArrayList<TextView>();
        homeButton = addPathButton(mCurDirName, mCurDirId);
        if (savedInstanceState != null && savedInstanceState.containsKey("adapter")) {
            findViewById(R.id.loadFilesProgress).setVisibility(View.INVISIBLE);
            mFileListView.setAdapter((FileListAdapter) savedInstanceState.getSerializable("adapter"));
        } else if (isNetworkConnected()) {
            if (mRefreshToken == null || mRefreshToken.equals("")
                    || mRefreshToken.length() < 64) {
                task.authorize();
            } else {
                task.getData(ROOT_URL + "folders/", mCurDirId, folderList);
            }
        } else {
            Toast.makeText(MainActivity.this, "No internet connection." +
                    "Please, check your connection, and try again!", Toast.LENGTH_LONG).show();
        }

        mFileListView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> a, View v, int position, long id) {
                FileInfo fi = findObjectByPos(jsonQuery, (int) id);
                String name = fi.getName();
                String type = fi.getType();
                String ident = fi.getId();
                if (type.equals("folder")) {
                    isFolderChanged = true;
                    openFolder(ident, name);
                } else if (type.equals("file")) {
                    try {
                        if (isFileOnDevice(name, ident)) {
                            openFile(name);
                        } else {
                            isFolderChanged = false;
                            v.findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
                            v.findViewById(R.id.download_status).setVisibility(View.INVISIBLE);

                            task.downloadFile(ROOT_URL + "files/" + ident + "/content",
                                    ident, name, String.valueOf(position));
                        }
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, getString(R.string.no_app_found),
                                Toast.LENGTH_LONG).show();
                        Log.e(TAG, e.getMessage());
                    }
                }
            }
        });
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
        int position = info.position;

        FileInfo fi = findObjectByPos(jsonQuery, position);
        String name = fi.getName();
        String ident = fi.getId();
        String type = fi.getType();
        menu.setHeaderTitle(getString(R.string.select_option));

        if (type.equals(KeyMap.FILE)) {
            if (isFileOnDevice(name, ident))
                menu.add(0, OPEN, Menu.NONE, R.string.open);
            else
                menu.add(0, DOWNLOAD, Menu.NONE, R.string.download);
        } else if (type.equals(KeyMap.FOLDER)) {
            menu.add(0, OPEN, Menu.NONE, R.string.open);
        }
        menu.add(0, COPY, Menu.NONE, R.string.copy);
        if (mCopyId != null) {
            menu.add(0, PASTE, Menu.NONE, R.string.paste);
        }
        menu.add(0, DELETE, Menu.NONE, R.string.delete);
        menu.add(0, RENAME, Menu.NONE, R.string.rename);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        super.onContextItemSelected(item);
        AdapterView.AdapterContextMenuInfo menuInfo;
        menuInfo = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        int index = menuInfo.position;
        int optionSelected = item.getItemId();

        FileInfo fi = findObjectByPos(jsonQuery, index);
        String ident = fi.getId();
        String name = fi.getName();
        String type = fi.getType();
        String etag = fi.getEtag();
        MenuItem itemPaste = menu.findItem(PASTE_OPTION);

        switch (optionSelected) {
            case (OPEN):
                if (type.equals(KeyMap.FILE)) {
                    if (isFileOnDevice(name, ident)) {
                        openFile(name);
                    }
                } else if (type.equals(KeyMap.FOLDER)) {
                    isFolderChanged = true;
                    openFolder(ident, name);
                }
                return true;

            case (COPY):
                mCopyId = ident;
                mCopyType = type;
                mCopyName = name;
                itemPaste.setVisible(true);
                return true;

            case (PASTE):
                isFolderChanged = true;
                String url = ROOT_URL + mCopyType + "s/" + mCopyId + "/copy";
                String data = "{\"parent\": {\"id\" : " + ident + "}}";
                mCopyId = null;
                itemPaste.setVisible(false);
                task.createItem(url, data);
                return true;

            case (DELETE):
                if (type.equals("file")) {
                    isFolderChanged = true;
                    task.deleteData(ROOT_URL + "files/" + ident, etag);
                } else if (type.equals("folder")) {
                    isFolderChanged = true;
                    task.deleteData(ROOT_URL + "folders/" + ident + "?recursive=true");
                }
                return true;

            case (DOWNLOAD):
                if (type.equals("file")) {
                    isFolderChanged = true;
                    task.downloadFile(ROOT_URL + "files/" + ident + "/content",
                            ident, name, String.valueOf(index));
                }
                return true;

            case (RENAME):
                rename(ident, name, type);
                return true;
        }
        return false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        this.menu = menu;
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);

        switch (item.getItemId()) {
            case android.R.id.home:
                homeButton.performClick();
                break;

            case (UPLOAD):
                Intent intent = new Intent(getBaseContext(), ExplorerActivity.class);
                startActivityForResult(intent, 1);
                break;

            case (CREATE_FOLDER):
                createNewFolder();
                break;

            case (PASTE_OPTION):
                String url = ROOT_URL + mCopyType + "s/" + mCopyId + "/copy";
                String data = null;
                if (containsSameName(mCopyName)) {
                    String[] arr = mCopyName.split("\\.");
                    String name = null;
                    String type = null;
                    if (arr.length > 1) {
                        name = "";
                        for (int i = 0; i < arr.length - 1; i++)
                            name += arr[i];
                        type = arr[arr.length - 1];
                    }
                    if (name != null && type != null) {
                        data = "{\"parent\": {\"id\" : " + mCurDirId + "}, \"name\" : \"" + name + " Copy." + type + "\"}";
                    }
                } else
                    data = "{\"parent\": {\"id\" : " + mCurDirId + "}}";
                mCopyId = null;
                MenuItem itemPaste = menu.findItem(PASTE_OPTION);
                itemPaste.setVisible(false);
                if (data != null)
                    task.createItem(url, data);
                break;

            case (LOGOUT):
                mAccessToken = "";
                mRefreshToken = "";
                SharedPreferences userDetails = getSharedPreferences("userdetails", MODE_PRIVATE);
                Editor edit = userDetails.edit();
                edit.clear();
                edit.commit();
                task.authorize();
        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (data != null) {
            String path = data.getStringExtra("path");
            task.uploadFile(UPLOAD_URL, mCurDirId, path);
        }
    }

    @Override
    public void onBackPressed() {
        TextView prevFolderView;
        if (folderList.size() <= 1)
            finish();
        else {
            prevFolderView = folderList.get(folderList.size() - 2);
            prevFolderView.performClick();
        }
    }

    private boolean containsSameName(String curName) {
        if (fileList.size() != 0) {
            for (FileInfo info : fileList) {
                if (info.getName().equals(curName))
                    return true;
            }
            return false;
        }
        return false;
    }

    private String getFileType(String name) {
        String type = null;
        if (name.contains("."))
            type = name.substring(name.indexOf("."));
        return type;
    }

    /**
     * Gets info about file or folder
     *
     * @param json,     JSON data got from response
     * @param position, number of file or folder in JSON
     */
    private FileInfo findObjectByPos(String json, int position) {
        FileInfo fi = null;
        JSONObject data;
        String name = null;
        String type = null;
        String id = null;
        String etag = null;
        try {
            data = new JSONObject(json).getJSONObject("item_collection");
            JSONArray files = data.getJSONArray("entries");
            JSONObject text = files.getJSONObject(position);
            name = text.getString("name");
            type = text.getString("type");
            id = text.getString("id");
            etag = text.getString("etag");
            fi = new FileInfo(name, type, id, etag);
        } catch (JSONException e) {
            Log.e(TAG, e.getMessage());
        }
        return fi;
    }

    /**
     * Creates new folder in a current directory
     * First shows dialog to type name, after creates it
     */
    private void createNewFolder() {
        LayoutInflater inflater = getLayoutInflater();
        View dialoglayout = inflater.inflate(R.layout.new_folder, null);
        final EditText folderNameEdit = (EditText) dialoglayout.findViewById(R.id.folder_name_box);
        AlertDialog.Builder createFolderDialogBuilder = new AlertDialog.Builder(context);
        createFolderDialogBuilder.setTitle(R.string.new_folder);
        createFolderDialogBuilder
                .setCancelable(true)
                .setView(dialoglayout)
                .setPositiveButton(R.string.create, new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int id) {
                        String folderName = folderNameEdit.getText().toString();
                        task.createItem(ROOT_URL + "folders/", "{\"name\":\"" + folderName + "\", \"parent\": " +
                                "{\"id\": \"" + mCurDirId + "\"}}");
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
        AlertDialog alertDialog = createFolderDialogBuilder.create();
        alertDialog.show();
    }

    /**
     * Renames file or folder
     *
     * @param ident, folder or file id
     * @param name,  folder or file name
     * @param type,  type of data: file or folder.
     */
    private void rename(final String ident, final String name, final String type) {
        LayoutInflater inflater = getLayoutInflater();
        View dialoglayout = inflater.inflate(R.layout.rename_dialog, null);
        final EditText folderNameEdit = (EditText) dialoglayout.findViewById(R.id.folder_name_box);
        folderNameEdit.setText(name);
        AlertDialog.Builder renameDialogBuilder = new AlertDialog.Builder(context);
        renameDialogBuilder.setTitle(R.string.rename);
        renameDialogBuilder
                .setCancelable(true)
                .setView(dialoglayout)
                .setPositiveButton(R.string.rename, new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int id) {
                        String newName = folderNameEdit.getText().toString();
                        task.renameItem(ROOT_URL + type + "s/" + ident, "{\"name\":\"" + newName + "\"}",
                                name, newName, ident);
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
        AlertDialog alertDialog = renameDialogBuilder.create();
        alertDialog.show();
    }

    private TextView addPathButton(String label, final String ident) {
        TextView pathView = new TextView(this);
        SpannableString content = new SpannableString(label);
        content.setSpan(new UnderlineSpan(), 0, label.length(), 0);
        content.setSpan(new StyleSpan(Typeface.BOLD), 0, label.length(), 0);
        pathView.setText(content);
        pathView.setTextSize(getResources().getDimension(R.dimen.font_size));
        pathView.setTextColor(getResources().getColor(R.color.path_label));
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        llp.setMargins(0, 0, 10, 0);
        pathView.setLayoutParams(llp);
        pathView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                TextView pathClicked = (TextView) v;
                updatePathList(pathClicked);
                mCurDirId = ident;
                task.getData(ROOT_URL + "folders/", mCurDirId, folderList);
            }
        });
        folderList.add(pathView);
        return pathView;
    }

    private void updatePathList(TextView tv) {
        ArrayList<TextView> tempList = new ArrayList<TextView>();
        for (int i = 0; i < folderList.size(); i++)
            if (tv.equals(folderList.get(i)))
                for (int k = 0; k <= i; k++)
                    tempList.add(folderList.get(k));
        folderList = tempList;
    }

    private void openFolder(String folderId, String folderName) {
        mCurDirId = folderId;
        mCurDirName = folderName;
        addPathButton(mCurDirName, mCurDirId);
        task.getData(ROOT_URL + "folders/", mCurDirId, folderList);
    }

    private void openFile(String name) {
        Intent openIntent = new Intent(android.content.Intent.ACTION_VIEW);
        File file = new File(EXT_STORAGE_PATH + "/" + name);
        String extension = android.webkit.MimeTypeMap.getFileExtensionFromUrl(Uri.fromFile(file).toString().toLowerCase());
        String mimetype = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        openIntent.setDataAndType(Uri.fromFile(file), mimetype);
        startActivity(openIntent);
    }

    private boolean isFileOnDevice(String name, String ident) {
        File data = new File(EXT_STORAGE_PATH + "/" + name);
        if(!data.exists())
            return false;
        SharedPreferences userDetails = getSharedPreferences("downloaded_files", MODE_PRIVATE);
        Map<String, ?> idList = userDetails.getAll();
        String curName = (String) idList.get(ident);
        return (curName != null && curName.equals(name)) ? true : false;
    }

    private boolean isNetworkConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        if (ni == null)
            return false;
        else
            return true;
    }

    @Override
    public void onDownloadStarted(String name) {
        Intent intent = new Intent(BoxWidgetProvider.ACTION_STATUS_CHANGED);
        intent.putExtra("status", getString(R.string.downloading) + " " + name);
        getApplicationContext().sendBroadcast(intent);
    }

    @Override
    public void onDownloadCompleted(int position, String name) {
        if (!isFolderChanged) {
            FileListAdapter adapter = (FileListAdapter) mFileListView.getAdapter();
            adapter.setDownloaded(position, true);
            adapter.notifyDataSetChanged();
        }
        Intent intent = new Intent(BoxWidgetProvider.ACTION_STATUS_CHANGED);
        intent.putExtra("status", name + " " + getString(R.string.downloaded));
        getApplicationContext().sendBroadcast(intent);
    }

    @Override
    public void onProgressChanged(Integer progress, String name) {
        Intent intent = new Intent(BoxWidgetProvider.ACTION_STATUS_CHANGED);
        intent.putExtra("status", getString(R.string.downloading) + " " + name);
        intent.putExtra("progress", progress);
        getApplicationContext().sendBroadcast(intent);
    }

}