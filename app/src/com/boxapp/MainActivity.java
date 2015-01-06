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
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import com.boxapp.entity.DownloadStatus;
import com.boxapp.entity.Item;
import com.boxapp.entity.LoginDetails;
import com.boxapp.service.DownloadService;
import com.boxapp.service.UploadService;
import com.boxapp.utils.APIHelper;
import com.boxapp.utils.BoxHelper;
import com.boxapp.utils.BoxWidgetProvider;
import com.boxapp.utils.Credentials;
import com.boxapp.utils.FileHelper;
import com.boxapp.utils.ItemAdapter;
import com.boxapp.utils.KeyHelper;
import com.handmark.pulltorefresh.library.PullToRefreshBase;
import com.handmark.pulltorefresh.library.PullToRefreshListView;

import java.io.File;
import java.net.HttpURLConnection;
import java.util.List;

import retrofit.client.Response;

public final class MainActivity extends ActionBarActivity implements UploadListener, OnItemClickListener, APIHelper.RestListener, FileHelper.ProgressUpdateListener {

    private static final String TAG = "BoxApp";
    private long mCurrentFolder = 0;
    private long mParentFolder = -1;
    private String mCurrentFolderName = "All files";
    private long mCopyId = 0;
    private Item.Type mCopyType = null;
    private PullToRefreshListView mFileListView;
    private ItemAdapter mAdapter;
    private FileHelper mFileHelper;
//    private ArrayList<Folder> mPathList;

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

    private APIHelper helper = null;
    private Menu menu;

    private ServiceConnection mDownloadConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            mDownloadService = ((DownloadService.FileDownloadBinder) binder).getService();
            mDownloadService.attachListener(new DownloadListener() {
                @Override
                public void onDownloadStarted(String fileName) {
                    BoxHelper.showNotification(MainActivity.this, fileName, getString(R.string.download_started), android.R.drawable.stat_sys_download);
                    Intent intent = new Intent(BoxWidgetProvider.ACTION_STATUS_CHANGED);
                    intent.putExtra(KeyHelper.STATUS, getString(R.string.downloading) + " " + fileName);
                    sendBroadcast(intent);
                }

                @Override
                public void onDownloadCompleted(String fileId, String fileName) {
                    int position = mAdapter.getPositionById(Long.parseLong(fileId));
                    if (position != -1) {
                        mAdapter.setStatus(mAdapter.getPositionById(Long.parseLong(fileId)), true);
                        mAdapter.notifyDataSetChanged();
                    }

                    BoxHelper.showNotification(MainActivity.this, fileName, getString(R.string.download_completed), android.R.drawable.stat_sys_download_done);
                    Intent intent = new Intent(BoxWidgetProvider.ACTION_STATUS_CHANGED);
                    intent.putExtra(KeyHelper.STATUS, true);
                    sendBroadcast(intent);
                }

                @Override
                public void onProgressChanged(int progress, String fileName, String action) {
                    BoxHelper.updateDownloadNotification(MainActivity.this, fileName, getString(R.string.downloading), progress, android.R.drawable.stat_sys_download, false);
                    Intent intent = new Intent(BoxWidgetProvider.ACTION_STATUS_CHANGED);
                    intent.putExtra(KeyHelper.STATUS, action + " " + fileName);
                    intent.putExtra(KeyHelper.PROGRESS, progress);
                    sendBroadcast(intent);
                }

                @Override
                public void onDownloadFailed(String fileName) {
                    BoxHelper.showNotification(MainActivity.this, fileName, getString(R.string.download_failed), android.R.drawable.stat_notify_error);
                    Intent intent = new Intent(BoxWidgetProvider.ACTION_STATUS_CHANGED);
                    intent.putExtra(KeyHelper.STATUS, false);
                    sendBroadcast(intent);
                }
            });
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
//        mPathList = new ArrayList<Folder>();

        getSupportActionBar().setHomeButtonEnabled(true);

        java.io.File folder = new java.io.File(KeyHelper.EXT_STORAGE_PATH);
        if (folder.exists() && folder.isDirectory()) {
            folder.mkdirs();
        }

        SharedPreferences userDetails = getSharedPreferences(KeyHelper.USER_DETAILS, MODE_PRIVATE);
        String accessToken = userDetails.getString(KeyHelper.Login.ACCESS_TOKEN, null);
        String refreshToken = userDetails.getString(KeyHelper.Login.REFRESH_TOKEN, null);
        int expiresIn = userDetails.getInt(KeyHelper.Login.EXPIRES_IN, 0);

        if (accessToken == null && refreshToken == null) {
            BoxHelper.authorize(this);
        } else {
            helper = new APIHelper(this);
            helper.setLoginDetails(new LoginDetails(accessToken, refreshToken, expiresIn));
            openFolder(mCurrentFolder, mCurrentFolderName);
        }

////        homeButton = addPathButton(mCurrentFolderName, mCurrentFolder);
//        if (savedInstanceState != null && savedInstanceState.containsKey(KeyHelper.JSON)) {
//            mJson = savedInstanceState.getString(KeyHelper.JSON);
////            displayFileStructure(BoxHelper.getFolderItems(mJson));
//        } else if (isNetworkConnected()) {
//            if (mRefreshToken == null) {
//                helper.authorize();
//            } else {
//                helper.getFolderItems(mCurrentFolder);
//            }
//        } else {
//            Toast.makeText(MainActivity.this, getString(R.string.no_internet_connection), Toast.LENGTH_LONG).show();
//        }
//
        mFileListView.setOnRefreshListener(new PullToRefreshBase.OnRefreshListener<ListView>() {
            @Override
            public void onRefresh(PullToRefreshBase<ListView> refreshView) {
                openFolder(mCurrentFolder, mCurrentFolderName);
            }
        });

        ListView actualListView = mFileListView.getRefreshableView();
        actualListView.setOnCreateContextMenuListener(this);
        registerForContextMenu(actualListView);

    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;

        Item item = mAdapter.getItem(info.position - 1);
        String name = item.getName();
        long itemId = item.getId();
        Item.Type type = item.getType();
        menu.setHeaderTitle(getString(R.string.select_option));

        if (type == Item.Type.FILE) {
            if (isFileOnDevice(itemId))
                menu.add(0, OPEN, Menu.NONE, R.string.open);
            else
                menu.add(0, DOWNLOAD, Menu.NONE, R.string.download);
        } else if (type.equals(KeyHelper.FOLDER)) {
            menu.add(0, OPEN, Menu.NONE, R.string.open);
        }
        menu.add(0, COPY, Menu.NONE, R.string.copy);
        if (mCopyId != 0) {
            menu.add(0, PASTE, Menu.NONE, R.string.paste);
        }
        menu.add(0, DELETE, Menu.NONE, R.string.delete);
        menu.add(0, RENAME, Menu.NONE, R.string.rename);
    }

    @Override
    public boolean onContextItemSelected(MenuItem menuItem) {
        super.onContextItemSelected(menuItem);
        AdapterView.AdapterContextMenuInfo info;
        info = (AdapterView.AdapterContextMenuInfo) menuItem.getMenuInfo();
        int optionSelected = menuItem.getItemId();

        Item item = mAdapter.getItem(info.position - 1);
        long itemId = item.getId();
        String name = item.getName();
        Item.Type type = item.getType();
        int etag = item.getEtag();
        MenuItem itemPaste = menu.findItem(PASTE_OPTION);

        switch (optionSelected) {
            case (OPEN):
                if (type.equals(KeyHelper.FILE)) {
                    if (isFileOnDevice(itemId)) {
                        openFile(name);
                    }
                } else if (type.equals(KeyHelper.FOLDER)) {
                    openFolder(itemId, name);
                }
                return true;

            case (COPY):
                mCopyId = itemId;
                mCopyType = type;
                itemPaste.setVisible(true);
                return true;

            case (PASTE):
                mCopyId = 0;
                itemPaste.setVisible(false);
                helper.copyFile(String.valueOf(mCopyId), String.valueOf(mCurrentFolder));
                return true;

            case (DELETE):
                helper.deleteItem(String.valueOf(itemId), String.valueOf(etag), type == Item.Type.FOLDER);

                return true;

            case (DOWNLOAD):
                if (type == Item.Type.FILE)
                    helper.downloadFile(mDownloadService, String.valueOf(itemId), name);
                return true;

            case (RENAME):
//                rename(ident, name, type);
                return true;
        }
        return false;
    }

    private File createFile(String path, String fileName) {
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        this.menu = menu;
        return super.onCreateOptionsMenu(menu);
    }

//    @Override
//    public boolean onPrepareOptionsMenu(Menu menu) {
//        super.onPrepareOptionsMenu(menu);
//        return true;
//    }

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
                String data = "{\"parent\": {\"id\" : " + mCurrentFolder + "}}";
//                mCopyId = null;
                MenuItem itemPaste = menu.findItem(PASTE_OPTION);
                itemPaste.setVisible(false);
                if (data != null)
//                    helper.createItem(url, data);
                    break;

            case (LOGOUT):
                SharedPreferences userDetails = getSharedPreferences(KeyHelper.USER_DETAILS, MODE_PRIVATE);
                Editor edit = userDetails.edit();
                edit.clear();
                edit.commit();
                BoxHelper.authorize(this);
        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (data != null) {
            String path = data.getStringExtra(KeyHelper.PATH);
//            mUploadService.uploadFile(Credentials.UPLOAD_URL, mAccessToken, mCurrentFolder, path);
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
                        helper.createFolder(folderName, "" + mCurrentFolder);
//                        helper.createItem(Credentials.ROOT_URL + "folders/", "{\"name\":\"" + folderName + "\", \"parent\": " +
//                                "{\"id\": \"" + mCurrentFolder + "\"}}");
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

//    /**
//     * Renames file or folder
//     *
//     * @param ident, folder or file id
//     * @param name,  folder or file name
//     * @param type,  type of data: file or folder.
//     */
//    private void rename(final String ident, final String name, final String type) {
//        LayoutInflater inflater = getLayoutInflater();
//        View dialog = inflater.inflate(R.layout.rename_dialog, null);
//        final EditText folderNameEdit = (EditText) dialog.findViewById(R.id.folder_name_box);
//        folderNameEdit.setText(name);
//        AlertDialog.Builder renameDialogBuilder = new AlertDialog.Builder(this);
//        renameDialogBuilder.setTitle(R.string.rename);
//        renameDialogBuilder
//                .setCancelable(true)
//                .setView(dialog)
//                .setPositiveButton(R.string.rename, new DialogInterface.OnClickListener() {
//
//                    public void onClick(DialogInterface dialog, int id) {
//                        String newName = folderNameEdit.getText().toString();
//                        helper.renameItem(Credentials.ROOT_URL + type + "s/" + ident, "{\"name\":\"" + newName + "\"}",
//                                name, newName, ident);
//                    }
//                })
//                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
//                    public void onClick(DialogInterface dialog, int id) {
//                        dialog.cancel();
//                    }
//                });
//        AlertDialog alertDialog = renameDialogBuilder.create();
//        alertDialog.show();
//    }

//    private void updateFolderList(){
//        for(Folder folder : mPathList){
//            String label = folder.getLabel();
//            final String id = folder.getId();
//
//            TextView pathTv = new TextView(this);
//            SpannableString content = new SpannableString(label);
//            content.setSpan(new UnderlineSpan(), 0, label.length(), 0);
//            content.setSpan(new StyleSpan(Typeface.BOLD), 0, label.length(), 0);
//            pathTv.setText(content);
//            pathTv.setTextSize(getResources().getDimension(R.dimen.font_size));
//            pathTv.setTextColor(getResources().getColor(R.color.path_label));
//
//            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
//            params.setMargins(0, 0, 10, 0);
//            pathTv.setLayoutParams(params);
//            pathTv.setOnClickListener(new OnClickListener() {
//                @Override
//                public void onClick(View v) {
//                    TextView pathClicked = (TextView) v;
////                    updatePathList(pathClicked);
//                    setListVisibility(false);
//                    openFolder(id);
//                }
//            });
//        }
//    }

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

//    private void setListVisibility(boolean isReady) {
//        findViewById(R.id.loadLayout).setVisibility(isReady ? View.INVISIBLE : View.VISIBLE);
//        mFileListView.setVisibility(isReady ? View.VISIBLE : View.INVISIBLE);
//    }

//    private void updatePathList(TextView tv) {
//        ArrayList<TextView> tempList = new ArrayList<TextView>();
//        for (int i = 0; i < mFolderList.size(); i++)
//            if (tv.equals(mFolderList.get(i)))
//                for (int k = 0; k <= i; k++)
//                    tempList.add(mFolderList.get(k));
//        mFolderList = tempList;
//    }

//    private void openFolder(long folderId) {
//        mCurrentFolder = folderId;
//
//        helper.getFolderItems(mCurrentFolder);
//    }

    private void openFolder(long folderId, String folderName) {
        mParentFolder = mCurrentFolder;
        mCurrentFolder = folderId;
        mCurrentFolderName = folderName;
//        mPathList.add(new Folder(mCurrentFolder, mCurrentFolderName));
        mFileListView.setEnabled(false);
        mFileListView.setFocusable(false);
        mFileListView.setOnItemClickListener(null);
        helper.getFolderItems(mCurrentFolder);
    }

    /**
     * @TODO Fix here
     * @param id
     * @param name
     */
    private void openFile(String id, String name) {
        Intent openIntent = new Intent(android.content.Intent.ACTION_VIEW);
        java.io.File file = new java.io.File(KeyHelper.EXT_STORAGE_PATH + "/" + id);
        String extension = android.webkit.MimeTypeMap.getFileExtensionFromUrl(Uri.fromFile(file).toString().toLowerCase());
        String mimetype = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        openIntent.setDataAndType(Uri.fromFile(file), mimetype);
        startActivity(openIntent);
    }

    private boolean isFileOnDevice(long fileId) {
        java.io.File data = new java.io.File(KeyHelper.EXT_STORAGE_PATH + "/" + String.valueOf(fileId).hashCode());
        return data.exists();
//        SharedPreferences sharedPreferences = getSharedPreferences(KeyHelper.DOWNLOADED_FILES, MODE_PRIVATE);
//        Map<String, ?> idList = sharedPreferences.getAll();
//        String curName = (String) idList.get(String.valueOf(fileId));
//        return curName != null && curName.equals(String.valueOf(String.valueOf(fileId).hashCode()));
    }

    private boolean isNetworkConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm.getActiveNetworkInfo() != null;
    }

    @Override
    public void onUploadStarted(String name) {
        Intent intent = new Intent(BoxWidgetProvider.ACTION_STATUS_CHANGED);
        intent.putExtra(KeyHelper.STATUS, getString(R.string.uploading) + " " + name);
        sendBroadcast(intent);
    }

    @Override
    public void onProgressChanged(Integer progress, String name, String action) {
        Intent intent = new Intent(BoxWidgetProvider.ACTION_STATUS_CHANGED);
        intent.putExtra(KeyHelper.STATUS, action + " " + name);
        intent.putExtra(KeyHelper.PROGRESS, progress);
        sendBroadcast(intent);
    }

    @Override
    public void onUploadCompleted(String name) {
        Intent intent = new Intent(BoxWidgetProvider.ACTION_STATUS_CHANGED);
        intent.putExtra(KeyHelper.STATUS, getString(R.string.upload_completed) + " " + name);
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
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Adapter adapter = parent.getAdapter();
        Item item = (Item) adapter.getItem(position);
        if (adapter.getItemViewType(position) == ItemAdapter.BACK) {
            if (id == 0)
                mParentFolder = -1;
            adapter.getItemViewType(position);
            openFolder(item.getId(), "");
        } else {
            String name = item.getName();
            Item.Type type = item.getType();
            if (type == Item.Type.FOLDER) {
                mParentFolder = mCurrentFolder;
                openFolder(id, name);
            } else {
                try {
                    if (isFileOnDevice(id)) {
                        openFile(String.valueOf(String.valueOf(id).hashCode()), type);
                    } else {
                        item.setDownloadStatus(new DownloadStatus(false, 1));
                        mAdapter.notifyDataSetChanged();
                        helper.downloadFile(mDownloadService, String.valueOf(id), name);
                    }
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, getString(R.string.no_app_found),
                            Toast.LENGTH_LONG).show();
                    Log.e(TAG, e.getMessage());
                }
            }
        }
    }

    @Override
    public void onFolderItemReceived(List<Item> items, Response response) {
        String url = response.getUrl();
        if (url.startsWith(Credentials.ROOT_URL)) {
            for (Item item : items) {
                if (item.getType() == Item.Type.FILE)
                    item.setDownloadStatus(new DownloadStatus(isFileOnDevice(item.getId())));
            }
            mAdapter = mParentFolder != -1 ?
                    new ItemAdapter(this, items, new Item(mParentFolder)) :
                    new ItemAdapter(this, items);
            mFileListView.setAdapter(mAdapter);
        }
        mFileListView.setOnItemClickListener(this);
        mFileListView.onRefreshComplete();
    }

    @Override
    public void onProgressUpdate(int progress, String fileName) {
        BoxHelper.updateDownloadNotification(this, fileName, getString(R.string.downloading),
                progress, android.R.drawable.stat_sys_download, false);
        Log.d("Progress", "" + progress);

    }
}