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

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.ActionBarActivity;
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

import com.boxapp.entity.FileInfo;
import com.boxapp.entity.Folder;
import com.boxapp.service.DownloadService;
import com.boxapp.service.UploadService;
import com.boxapp.utils.APIHelper;
import com.boxapp.utils.BoxHelper;
import com.boxapp.utils.BoxWidgetProvider;
import com.boxapp.utils.Credentials;
import com.boxapp.utils.FileHelper;
import com.boxapp.utils.FileListAdapter;
import com.boxapp.utils.KeyMap;
import com.handmark.pulltorefresh.library.PullToRefreshBase;
import com.handmark.pulltorefresh.library.PullToRefreshListView;

import java.io.File;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public final class MainActivity extends ActionBarActivity implements DownloadListener, UploadListener, APIHelper.TaskListener {

    private static final String TAG = "BoxApp";
    private String mJson = null;
    private String mAccessToken = null;
    private String mRefreshToken = null;
    private String mCurDirId = "0";
    private String mCurDirName = "All files";
    private String mCopyId = null;
    private String mCopyType = null;
    private PullToRefreshListView mFileListView;
    private FileListAdapter mAdapter;
    private FileHelper mFileHelper;
    private ArrayList<Folder> mPathList;

    private DownloadService mDownloadService;
    private UploadService mUploadService;

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

    private APIHelper helper;
    private TextView homeButton;
    private boolean isFolderChanged = false;
    private Menu menu;


    private ServiceConnection mDownloadConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            mDownloadService = ((DownloadService.FileDownloadBinder) binder).getService();
            mDownloadService.attachListener(MainActivity.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mDownloadService = null;
        }
    };

    private ServiceConnection mUploadConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            mUploadService = ((UploadService.FileUploadBinder) binder).getService();
            mUploadService.attachListener(MainActivity.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mUploadService = null;
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mFileListView = (PullToRefreshListView) findViewById(R.id.fileListView);
        mFileHelper = new FileHelper(this);
        mPathList = new ArrayList<Folder>();

        registerForContextMenu(mFileListView);
        getSupportActionBar().setHomeButtonEnabled(true);

        SharedPreferences userDetails = getSharedPreferences(KeyMap.USER_DETAILS, MODE_PRIVATE);
        mAccessToken = userDetails.getString(KeyMap.ACCESS_TOKEN, "");
        mRefreshToken = userDetails.getString(KeyMap.REFRESH_TOKEN, "");
        helper = new APIHelper(this, mAccessToken, mRefreshToken);

        File folder = new File(KeyMap.EXT_STORAGE_PATH);
        if (folder.exists() && folder.isDirectory()) {
            folder.mkdirs();
        }

//        homeButton = addPathButton(mCurDirName, mCurDirId);
        if (savedInstanceState != null && savedInstanceState.containsKey(KeyMap.JSON)) {
            mJson = savedInstanceState.getString(KeyMap.JSON);
            displayFileStructure(BoxHelper.getFolderItems(mJson));
        } else if (isNetworkConnected()) {
            if (mRefreshToken == null || mRefreshToken.equals("")
                    || mRefreshToken.length() < 64) {
                helper.authorize();
            } else {
                helper.getData(Credentials.ROOT_URL + "folders/", mCurDirId);
            }
        } else {
            Toast.makeText(MainActivity.this, getString(R.string.no_internet_connection), Toast.LENGTH_LONG).show();
        }

        mFileListView.setOnRefreshListener(new PullToRefreshBase.OnRefreshListener<ListView>() {
            @Override
            public void onRefresh(PullToRefreshBase<ListView> refreshView) {
                openFolder(mCurDirId);
            }
        });

        mFileListView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> a, View v, int position, long id) {
                FileInfo info = BoxHelper.findObjectByPos(mJson, (int) id);
                String name = info.getName();
                String type = info.getType();
                String ident = info.getId();
                if (type.equals(KeyMap.FOLDER)) {
                    isFolderChanged = true;
                    openFolder(ident, name);
                } else if (type.equals(KeyMap.FILE)) {
                    try {
                        if (isFileOnDevice(name, ident)) {
                            openFile(name);
                        } else {
                            isFolderChanged = false;
                            v.findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
                            v.findViewById(R.id.download_status).setVisibility(View.INVISIBLE);

                            mDownloadService.downloadFile(Credentials.ROOT_URL + "files/" + ident + "/content", mAccessToken,
                                    ident, name, String.valueOf(position), KeyMap.EXT_STORAGE_PATH);
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
    protected void onResume() {
        super.onResume();
        if (mJson != null)
            displayFileStructure(BoxHelper.getFolderItems(mJson));
        setListVisibility(true);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(KeyMap.JSON, mJson);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
        int position = info.position;

        FileInfo fi = BoxHelper.findObjectByPos(mJson, position);
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

        FileInfo info = BoxHelper.findObjectByPos(mJson, index);
        String ident = info.getId();
        String name = info.getName();
        String type = info.getType();
        String etag = info.getEtag();
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
                itemPaste.setVisible(true);
                return true;

            case (PASTE):
                isFolderChanged = true;
                String url = Credentials.ROOT_URL + mCopyType + "s/" + mCopyId + "/copy";
                String data = "{\"parent\": {\"id\" : " + ident + "}}";
                mCopyId = null;
                itemPaste.setVisible(false);
                helper.createItem(url, data);
                return true;

            case (DELETE):
                if (type.equals("file")) {
                    isFolderChanged = true;
                    helper.deleteData(Credentials.ROOT_URL + "files/" + ident, etag);
                } else if (type.equals("folder")) {
                    isFolderChanged = true;
                    helper.deleteData(Credentials.ROOT_URL + "folders/" + ident + "?recursive=true");
                }
                return true;

            case (DOWNLOAD):
                if (type.equals("file")) {
                    isFolderChanged = true;
                    mDownloadService.downloadFile(Credentials.ROOT_URL + "files/" + ident + "/content", mAccessToken,
                            ident, name, String.valueOf(index), KeyMap.EXT_STORAGE_PATH);
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
//            case android.R.id.home:
//                //homeButton.performClick();
//                break;

            case (UPLOAD):
                Intent intent = new Intent(getBaseContext(), ExplorerActivity.class);
                startActivityForResult(intent, 1);
                break;

            case (CREATE_FOLDER):
                createNewFolder();
                break;

            case (PASTE_OPTION):
                String url = Credentials.ROOT_URL + mCopyType + "s/" + mCopyId + "/copy";
                String data = "{\"parent\": {\"id\" : " + mCurDirId + "}}";
                mCopyId = null;
                MenuItem itemPaste = menu.findItem(PASTE_OPTION);
                itemPaste.setVisible(false);
                if (data != null)
                    helper.createItem(url, data);
                break;

            case (LOGOUT):
                mAccessToken = null;
                mRefreshToken = null;
                SharedPreferences userDetails = getSharedPreferences(KeyMap.USER_DETAILS, MODE_PRIVATE);
                Editor edit = userDetails.edit();
                edit.clear();
                edit.commit();
                helper.authorize();
        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (data != null) {
            String path = data.getStringExtra(KeyMap.PATH);
            mUploadService.uploadFile(Credentials.UPLOAD_URL, mAccessToken, mCurDirId, path);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindService(new Intent(this, DownloadService.class), mDownloadConnection, Context.BIND_AUTO_CREATE);
        bindService(new Intent(this, UploadService.class), mUploadConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindService(mDownloadConnection);
        unbindService(mUploadConnection);
    }

//    @Override
//    public void onBackPressed() {
//        TextView prevFolderView;
//        if (mFolderList.size() <= 1)
//            finish();
//        else {
//            prevFolderView = mFolderList.get(mFolderList.size() - 2);
//            prevFolderView.performClick();
//        }
//    }

    /**
     * Shows file structure, including file and folders
     * Also shows download status.
     *
     * @param fileList, ArrayList of files and folders info
     *                  which have to be represented
     */
    private void displayFileStructure(ArrayList<FileInfo> fileList) {
        final String ATTRIBUTE_NAME_TITLE = "title";
        final String ATTRIBUTE_NAME_DOWNLOADED = "status";
        final String ATTRIBUTE_NAME_IMAGE = "image";

        Map<String, Integer> drawableList = new HashMap<String, Integer>();
        drawableList.put(".jpg", R.drawable.jpg);
        drawableList.put(".jpeg", R.drawable.jpeg);
        drawableList.put(".png", R.drawable.png);
        drawableList.put(".gif", R.drawable.gif);

        drawableList.put(".doc", R.drawable.doc);
        drawableList.put(".docx", R.drawable.docx);
        drawableList.put(".ppt", R.drawable.ppt);
        drawableList.put(".pptx", R.drawable.pptx);

        drawableList.put(".pdf", R.drawable.pdf);
        drawableList.put(".txt", R.drawable.txt);
        drawableList.put(".exe", R.drawable.exe);
        drawableList.put(".mp3", R.drawable.mp3);
        drawableList.put(".mp4", R.drawable.mp4);
        drawableList.put(".psd", R.drawable.psd);
        drawableList.put(".rar", R.drawable.rar);
        drawableList.put(".zip", R.drawable.zip);

        ArrayList<Map<String, Object>> data = new ArrayList<Map<String, Object>>(fileList.size());
        Map<String, Object> itemMap;
        for (FileInfo info : fileList) {
            itemMap = new HashMap<String, Object>();
            String name = info.getName();
            String type = info.getType();
            itemMap.put(ATTRIBUTE_NAME_TITLE, name);

            if (type.equals(KeyMap.FOLDER)) {
                itemMap.put(ATTRIBUTE_NAME_IMAGE, R.drawable.folder);
            } else if (type.equals(KeyMap.FILE)) {
                Integer format = null;
                if (name.contains("")) {
                    String fileType = name.toLowerCase().substring(name.lastIndexOf(""), name.length());
                    format = drawableList.get(fileType);
                }
                if (format == null)
                    format = R.drawable.blank;
                itemMap.put(ATTRIBUTE_NAME_IMAGE, format);
            }

            if (type.equals(KeyMap.FOLDER)) {
                itemMap.put(ATTRIBUTE_NAME_DOWNLOADED, null);
            } else if (type.equals(KeyMap.FILE)) {
                if (mFileHelper.isFileOnDevice(name, info.getId(), KeyMap.EXT_STORAGE_PATH)) {
                    itemMap.put(ATTRIBUTE_NAME_DOWNLOADED, R.drawable.file_downloaded);
                } else {
                    itemMap.put(ATTRIBUTE_NAME_DOWNLOADED, R.drawable.non_downloaded);
                }
            }
            data.add(itemMap);
        }

        mAdapter = new FileListAdapter(this, data);
        mFileListView.setAdapter(mAdapter);

//        mPathListLayout.removeAllViewsInLayout();
//        for (TextView tv : mFolderList) {
//            mPathListLayout.addView(tv);
//        }
    }

    /**
     * Creates new folder in a current directory
     * First shows dialog to type name, after creates it
     */
    private void createNewFolder() {
        LayoutInflater inflater = getLayoutInflater();
        View layout = inflater.inflate(R.layout.new_folder, null);
        final EditText folderNameEdit = (EditText) layout.findViewById(R.id.folder_name_box);
        AlertDialog.Builder createFolderDialogBuilder = new AlertDialog.Builder(this);
        createFolderDialogBuilder.setTitle(R.string.new_folder);
        createFolderDialogBuilder
                .setCancelable(true)
                .setView(layout)
                .setPositiveButton(R.string.create, new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int id) {
                        String folderName = folderNameEdit.getText().toString();
                        helper.createItem(Credentials.ROOT_URL + "folders/", "{\"name\":\"" + folderName + "\", \"parent\": " +
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
        AlertDialog.Builder renameDialogBuilder = new AlertDialog.Builder(this);
        renameDialogBuilder.setTitle(R.string.rename);
        renameDialogBuilder
                .setCancelable(true)
                .setView(dialoglayout)
                .setPositiveButton(R.string.rename, new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int id) {
                        String newName = folderNameEdit.getText().toString();
                        helper.renameItem(Credentials.ROOT_URL + type + "s/" + ident, "{\"name\":\"" + newName + "\"}",
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

    private void updateFolderList(){
        for(Folder folder : mPathList){
            String label = folder.getLabel();
            final String id = folder.getId();

            TextView pathTv = new TextView(this);
            SpannableString content = new SpannableString(label);
            content.setSpan(new UnderlineSpan(), 0, label.length(), 0);
            content.setSpan(new StyleSpan(Typeface.BOLD), 0, label.length(), 0);
            pathTv.setText(content);
            pathTv.setTextSize(getResources().getDimension(R.dimen.font_size));
            pathTv.setTextColor(getResources().getColor(R.color.path_label));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, 10, 0);
            pathTv.setLayoutParams(params);
            pathTv.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    TextView pathClicked = (TextView) v;
//                    updatePathList(pathClicked);
                    setListVisibility(false);
                    openFolder(id);
                }
            });
        }
    }

//    private TextView addPathButton(String label, final String ident) {
//        mPathList.add(new Folder(ident, label));
//
//        TextView pathTv = new TextView(this);
//        SpannableString content = new SpannableString(label);
//        content.setSpan(new UnderlineSpan(), 0, label.length(), 0);
//        content.setSpan(new StyleSpan(Typeface.BOLD), 0, label.length(), 0);
//        pathTv.setText(content);
//        pathTv.setTextSize(getResources().getDimension(R.dimen.font_size));
//        pathTv.setTextColor(getResources().getColor(R.color.path_label));
//
//        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
//        params.setMargins(0, 0, 10, 0);
//        pathTv.setLayoutParams(params);
//        pathTv.setOnClickListener(new OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                TextView pathClicked = (TextView) v;
//                updatePathList(pathClicked);
//                setListVisibility(false);
//                openFolder(ident);
//            }
//        });
//        mFolderList.add(pathTv);
//        return pathTv;
//    }

    private void setListVisibility(boolean isReady) {
        findViewById(R.id.loadLayout).setVisibility(isReady ? View.INVISIBLE : View.VISIBLE);
        mFileListView.setVisibility(isReady ? View.VISIBLE : View.INVISIBLE);
    }

//    private void updatePathList(TextView tv) {
//        ArrayList<TextView> tempList = new ArrayList<TextView>();
//        for (int i = 0; i < mFolderList.size(); i++)
//            if (tv.equals(mFolderList.get(i)))
//                for (int k = 0; k <= i; k++)
//                    tempList.add(mFolderList.get(k));
//        mFolderList = tempList;
//    }

    private void openFolder(String folderId) {
        mCurDirId = folderId;
        helper.getData(Credentials.ROOT_URL + "folders/", mCurDirId);
    }

    private void openFolder(String folderId, String folderName) {
        setListVisibility(false);
        mCurDirId = folderId;
        mCurDirName = folderName;
        mPathList.add(new Folder(mCurDirId, mCurDirName));
        helper.getData(Credentials.ROOT_URL + "folders/", mCurDirId);
    }

    private void openFile(String name) {
        Intent openIntent = new Intent(android.content.Intent.ACTION_VIEW);
        File file = new File(KeyMap.EXT_STORAGE_PATH + "/" + name);
        String extension = android.webkit.MimeTypeMap.getFileExtensionFromUrl(Uri.fromFile(file).toString().toLowerCase());
        String mimetype = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        openIntent.setDataAndType(Uri.fromFile(file), mimetype);
        startActivity(openIntent);
    }

    private boolean isFileOnDevice(String name, String ident) {
        File data = new File(KeyMap.EXT_STORAGE_PATH + "/" + name);
        if (!data.exists())
            return false;
        SharedPreferences userDetails = getSharedPreferences(KeyMap.DOWNLOADED_FILES, MODE_PRIVATE);
        Map<String, ?> idList = userDetails.getAll();
        String curName = (String) idList.get(ident);
        return curName != null && curName.equals(name);
    }

    private boolean isNetworkConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm.getActiveNetworkInfo() != null;
    }

    @Override
    public void onDownloadStarted(String name) {
        Intent intent = new Intent(BoxWidgetProvider.ACTION_STATUS_CHANGED);
        intent.putExtra(KeyMap.STATUS, getString(R.string.downloading) + " " + name);
        sendBroadcast(intent);
    }

    @Override
    public void onDownloadCompleted(int position, String name, Integer result) {
        boolean isDownloaded = result == HttpURLConnection.HTTP_OK;
        String status = isDownloaded ? name + " " + getString(R.string.downloaded) : getString(R.string.download_failed) + " " + name;
        if (!isFolderChanged) {
            mAdapter.setDownloaded(position - 1, isDownloaded);
            mAdapter.notifyDataSetChanged();
        }
        Intent intent = new Intent(BoxWidgetProvider.ACTION_STATUS_CHANGED);
        intent.putExtra(KeyMap.STATUS, status);
        sendBroadcast(intent);
    }

    @Override
    public void onUploadStarted(String name) {
        Intent intent = new Intent(BoxWidgetProvider.ACTION_STATUS_CHANGED);
        intent.putExtra(KeyMap.STATUS, getString(R.string.uploading) + " " + name);
        sendBroadcast(intent);
    }

    @Override
    public void onProgressChanged(Integer progress, String name, String action) {
        Intent intent = new Intent(BoxWidgetProvider.ACTION_STATUS_CHANGED);
        intent.putExtra(KeyMap.STATUS, action + " " + name);
        intent.putExtra(KeyMap.PROGRESS, progress);
        sendBroadcast(intent);
    }

    @Override
    public void onUploadCompleted(String name) {
        Intent intent = new Intent(BoxWidgetProvider.ACTION_STATUS_CHANGED);
        intent.putExtra(KeyMap.STATUS, getString(R.string.upload_completed) + " " + name);
        sendBroadcast(intent);
    }

    @Override
    public void onUploadFailed(int code) {
        if (code == HttpURLConnection.HTTP_CONFLICT) {
            Toast.makeText(this, getString(R.string.file_exists), Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, getString(R.string.unknown_error), Toast.LENGTH_LONG).show();
        }

    }

    @Override
    public void onDataReceived(String json) {
        setListVisibility(true);
        displayFileStructure(BoxHelper.getFolderItems(json));
        mFileListView.onRefreshComplete();
        mJson = json;
    }
}