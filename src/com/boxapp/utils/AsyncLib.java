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
package com.boxapp.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.boxapp.MainActivity;
import com.boxapp.R;

public final class AsyncLib {

	//Request URLs
	private static final String AUTH_URL = "https://api.box.com/oauth2/authorize";
	private static final String SERVICE_REQUEST_URL = "https://api.box.com/oauth2/token";
	private static final String ROOT_URL = "https://api.box.com/2.0/";
	
	//Application info on service
	public static final String CLIENT_ID = "8js646cfoogrnoi6zvnnb2yfl4f3uuok";
	public static final String CLIENT_SECRET = "KXMvD9FyBLCtsnHVtY27lBIdPyBLGjMK";
	public static final String TAG = "My log";
	public static final String EXT_STORAGE_PATH = Environment.getExternalStorageDirectory().getPath() + "/BoxApp";
	
	private Context mContext;
	private String mAccessToken = null;
	private String mRefreshToken = null;
	private String mCurDirId = "0";
	
	private ArrayList<TextView> mFolderList;
	private ListView mFileListView;
	private RelativeLayout mProgressLayout;
	private LinearLayout mTopMenu;
	private DownloadService ds;
	private boolean isSameFolder = true;
	
	public AsyncLib(Context context, String refreshToken){
		mContext = context;
		mRefreshToken = refreshToken;
	}
	
	public AsyncLib(Context context, String accessToken, String refreshToken){
		mContext = context;
		mAccessToken = accessToken;
		mRefreshToken = refreshToken;
		mFolderList = new ArrayList<TextView>();
		mFileListView = (ListView) ((Activity) mContext).findViewById(R.id.fileListView);
		mProgressLayout = (RelativeLayout) ((Activity) mContext).findViewById(R.id.loadFilesProgress);
		mTopMenu = (LinearLayout)((Activity) mContext).findViewById(R.id.pathLayout);
	}
	
	public void getData(String requestUrl, String curDir, ArrayList<TextView> folderList) {
		mCurDirId = curDir;
		mFolderList = folderList;
		GetData gd = new GetData();
		gd.execute(requestUrl + curDir);
	}
	
	public void downloadFile(String requestUrl, String ident, String fileName, String position){
		ServiceConnection mConn = new ServiceConnection() {
			@Override
			public void onServiceConnected(ComponentName name, IBinder binder) {
				ds = ((DownloadService.FileDownloadBinder) binder).getService();
				ds.attachListener(mContext);
			}
			
			@Override
			public void onServiceDisconnected(ComponentName name) {
			}
		};
		Intent intent = new Intent(mContext, DownloadService.class)
				.putExtra("request_url", requestUrl)
				.putExtra("access_token", mAccessToken)
				.putExtra("file_ident", ident)
				.putExtra("file_name", fileName)
				.putExtra("position", position);
		mContext.bindService(intent, mConn, Context.BIND_AUTO_CREATE);
		mContext.startService(intent);
	}
	
	public void uploadFile(String requestUrl, String folder_id, String path){
		UploadData upload = new UploadData();
		upload.execute(requestUrl, folder_id, path);
	}
	
	public void renameItem(String requestUrl, String data,
			String oldName, String newName, String ident){
		PutData put = new PutData();
		put.execute(requestUrl, data, oldName, newName, ident);
	}
	
	public void createItem(String requestUrl, String data){
		PostData post = new PostData();
		post.execute(requestUrl, data);
	}
	
//	public void paste(String requestUrl, String data){
//		PostData post = new PostData();
//		post.execute(requestUrl, data);
//	}
	
	public void deleteData(String requestUrl){
		DeleteData delete = new DeleteData();
		delete.execute(requestUrl);
	}
	
	public void deleteData(String requestUrl, String etag){
		DeleteData delete = new DeleteData();
		delete.execute(requestUrl, etag);
	}
	
	class PerformAsyncRequest extends AsyncTask<String, String, Integer> {
		@Override
		protected void onPreExecute(){
			super.onPreExecute();
			mFileListView.setVisibility(View.INVISIBLE);
			mProgressLayout.setVisibility(View.VISIBLE);
		}
		@Override
		protected Integer doInBackground(String... params) {
			return null;
		}
		@Override
		protected void onPostExecute(Integer result) {
			super.onPostExecute(result);
			if (result == 401) {
				GNATAfterGetData gnat = new GNATAfterGetData();
				gnat.execute(SERVICE_REQUEST_URL);
			}
			else if(result >= 200 || result <= 210){
				mProgressLayout.setVisibility(View.INVISIBLE);
				mFileListView.setVisibility(View.VISIBLE);

			}
		}
	}
	
	class AsyncGetData extends PerformAsyncRequest { 
		@Override
		protected void onPostExecute(Integer result) {
			super.onPostExecute(result);
			if (result >= 200 || result <= 210) {
				GetData gd = new GetData();
				gd.execute(ROOT_URL + "folders/" + mCurDirId);
			}
		}
	}
	
	/**
	 * Get new access token using refresh token
	 * refresh token - 14 days
	 * param[0] service request URL
	 */
	class GetAccessToken extends AsyncTask<String, String, Integer> {
		String responseStr = null;
		@Override
		protected Integer doInBackground(String... param) {
			String requestUrl = param[0];
			Integer result = 0;
			List<NameValuePair> params = new ArrayList<NameValuePair>();
			params.add(new BasicNameValuePair("client_id", CLIENT_ID));
			params.add(new BasicNameValuePair("client_secret", CLIENT_SECRET));
			params.add(new BasicNameValuePair("grant_type", "refresh_token"));
			params.add(new BasicNameValuePair("refresh_token", mRefreshToken));
			
			HttpClient client = new DefaultHttpClient();
			HttpPost post = new HttpPost(requestUrl);
			try {
				UrlEncodedFormEntity entity = new UrlEncodedFormEntity(params, HTTP.UTF_8);
				post.setEntity(entity);
				HttpResponse response = client.execute(post); 
				result = response.getStatusLine().getStatusCode();
				HttpEntity resEntity = response.getEntity();  
				if (resEntity != null) {
					responseStr = EntityUtils.toString(resEntity);
				}
			} catch(ClientProtocolException e) {
				Log.e(TAG, e.getMessage());
			} catch(IOException e) {
				Log.e(TAG, e.getMessage());
			}
			return result;
		}

		@Override
		protected void onPostExecute(Integer result) {
			super.onPostExecute(result);
			if(result == 400) {
				authorize();
			}
			else if(result == 200) {
				mAccessToken = getToken(responseStr, "access_token");
				mRefreshToken = getToken(responseStr, "refresh_token");
				recordPreferences(mAccessToken, mRefreshToken);
			}
		}
	}
	
	class GNATAfterGetData extends GetAccessToken{
		@Override
		protected void onPostExecute(Integer result) {
			super.onPostExecute(result);
			if(result == 200) {
				GetData gd = new GetData();
				gd.execute(ROOT_URL + "folders/" + mCurDirId);
			}
		}
	}
	
	class GNATAfterDownload extends GetAccessToken{
		@Override
		protected void onPostExecute(Integer result) {
			super.onPostExecute(result);
			if(result == 200) {
				GetData gd = new GetData();
				gd.execute(ROOT_URL + "folders/" + mCurDirId);
			}
		}
	}
	
	/**
	 * Opens new web-view to authorize user. When user is successfully
	 * authorized, access & refresh tokens will be recorded to the configuration
	 * file. 
	 */
	public void authorize() {
		String response_type = "code";
		Uri uri = Uri.parse(AUTH_URL + "?response_type=" + response_type + "&client_id=" + CLIENT_ID);  
		Intent intent = new Intent(Intent.ACTION_VIEW, uri);
		mContext.startActivity(intent);
		System.exit(0);
	}
	
	/**
	 * Gets value of token from JSON
	 * @param json, query from response
	 * @param tokenType, name of token that has to be taken
	 * @return value of token
	 */
	private String getToken(String json, String tokenType){
		String token = null;
		JSONObject data;
		try {
			data = new JSONObject(json);
			token = data.getString(tokenType);
		} catch (JSONException e) {
			Log.e(TAG, e.getMessage());
		}
		return token;
	}
	
	private void recordPreferences(String access_token, String refresh_token){
		SharedPreferences userPrefs = mContext.getSharedPreferences("userdetails", Context.MODE_PRIVATE);
		Editor edit = userPrefs.edit();
		edit.clear();
		edit.putString("access_token", access_token.trim());
		edit.putString("refresh_token", refresh_token.trim());
		edit.commit();
		Toast.makeText(mContext, "Login details are saved...", Toast.LENGTH_LONG).show();
	}
	
	/**
	 * Gets data asynchronously through HTTP-get request
	 * Gets data from server using the access token and specified URL
	 * param[0] request URL
	 */
	class GetData extends PerformAsyncRequest {
		String responseStr = null;
		@Override
		protected Integer doInBackground(String... param) {
			Integer result = null;
			String requestUrl = param[0];
			try {
				HttpClient client = new DefaultHttpClient();
				HttpGet get = new HttpGet(requestUrl);
				get.setHeader("Authorization", "Bearer " + mAccessToken);
				HttpResponse responseGet = client.execute(get);
				result = responseGet.getStatusLine().getStatusCode();
				HttpEntity resEntityGet = responseGet.getEntity();
				if (resEntityGet != null && result == 200) {
					responseStr = EntityUtils.toString(resEntityGet);
				}
			} catch (Exception e) {
				Log.e(TAG, e.getMessage());
			}
			return result;
		}
		@Override
		protected void onPostExecute(Integer result) {
			super.onPostExecute(result);
			if(result == 200) {
				MainActivity.jsonQuery = responseStr;
				getFolderItems(MainActivity.jsonQuery);
			}
		}
	}
	
	private void getFolderItems(String json){
		JSONObject data;
		String name = null;
		String type = null;
		String id = null;
		ArrayList<FileInfo> fileList = new ArrayList<FileInfo>();
		try {
			data = new JSONObject(json).getJSONObject("item_collection");
			JSONArray files = data.getJSONArray("entries");
			int fileCount = files.length();
			JSONObject text = null;
			for (int i = 0; i < fileCount; i++) {
				text = files.getJSONObject(i);
				name = text.getString("name");
				type = text.getString("type");
				id = text.getString("id");
				FileInfo fi = new FileInfo(name, type, id);
				fileList.add(fi);
				MainActivity.fileList = fileList;
			}
			displayFileStructure(fileList);
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}
	
	public void getFileName(String json){
		JSONObject data;
		String name = null;
		ArrayList<FileInfo> fileList = new ArrayList<FileInfo>();
		try {
			data = new JSONObject(json).getJSONObject("item_collection");
			JSONArray files = data.getJSONArray("entries");
			int fileCount = files.length();
			JSONObject text = null;
			for (int i = 0; i < fileCount; i++) {
				name = text.getString("name");
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Shows file structure, including file and folders
	 * Also shows download status.
	 * @param fileList, ArrayList of files and folders info
	 * which have to be represented
	 */
	private void displayFileStructure(ArrayList<FileInfo> fileList){
		final String ATTRIBUTE_NAME_TITLE = "title";
		final String ATTRIBUTE_NAME_DOWNLOADED = "status";
		final String ATTRIBUTE_NAME_IMAGE = "image";
		
		isSameFolder = !isSameFolder;
		
		Map<String, Integer> formats = new HashMap<String, Integer>();
		formats.put(".jpg", R.drawable.jpeg_icon);
		formats.put(".jpeg", R.drawable.jpeg_icon);
		formats.put(".doc", R.drawable.docx_icon);
		formats.put(".docx", R.drawable.docx_icon);
		formats.put(".png", R.drawable.png_icon);
		formats.put(".pdf", R.drawable.pdf_icon);
		formats.put(".txt", R.drawable.txt_icon);	
		
		final int folderImg = R.drawable.folder;
		final int not_downloaded = R.drawable.non_downloaded;
		final int downloaded = R.drawable.file_downloaded;
		
		// packing data into structure for adapter
		ArrayList<Map<String, Object>> data = new ArrayList<Map<String, Object>>(fileList.size());
		Map<String, Object> itemMap;
		for (int i = 0; i < fileList.size(); i++){
			itemMap = new HashMap<String, Object>();
			FileInfo fi = (FileInfo)fileList.get(i);
			String name = fi.getName();
			String type = fi.getType();
			String ident = fi.getId();
			itemMap.put(ATTRIBUTE_NAME_TITLE, name);
			
			if(type.equals("folder")) {
				itemMap.put(ATTRIBUTE_NAME_IMAGE, folderImg);
			}
			else if(type.equals("file")) {
				String fileType = null;
				Integer format = null;
				if(name.contains(".")){
					fileType = name.toLowerCase().substring(name.lastIndexOf("."), name.length());
					format = formats.get(fileType);
				}
				if(format == null)
					format = R.drawable.default_icon;
				itemMap.put(ATTRIBUTE_NAME_IMAGE, format);
			}
			
			if(type.equals("folder")) {
				itemMap.put(ATTRIBUTE_NAME_DOWNLOADED, null);
			} else if(type.equals("file")) {
				if(isFileOnDevice(name, ident, i)) {
					itemMap.put(ATTRIBUTE_NAME_DOWNLOADED, downloaded);
				}
				else {
					itemMap.put(ATTRIBUTE_NAME_DOWNLOADED, not_downloaded);
				}
			}
			data.add(itemMap);
		}
		
		FileListAdapter adapter = new FileListAdapter(mContext, data);
		mFileListView.setAdapter(adapter);
		displayPathButtons();
	}
	
	/**
	 * Shows directory buttons
	 */
	private void displayPathButtons(){
		// Add path buttons to navigate
		mTopMenu.removeAllViewsInLayout();
		for(int i=0; i<mFolderList.size(); i++)
			mTopMenu.addView(mFolderList.get(i));
	}
	
	/**
	 * Deletes file or directory
	 */
	class DeleteData extends AsyncGetData {
		@Override
		protected Integer doInBackground(String... param) {
			Integer result = 0;
			if(param.length == 2)
				result = deleteFile(param[0], param[1]);
			else
				result = deleteDir(param[0]);
			return result;
		}
	}
	
	private boolean isFileOnDevice(String name, String ident, final int num){
		boolean result = false;
		File data = new File(EXT_STORAGE_PATH + "/" + name);
//		new FileObserver(EXT_STORAGE_PATH + "/" + name) {
//			@Override
//			public void onEvent(int event, String path) {
//				if (event == FileObserver.DELETE){
//					Toast.makeText(mContext, "File deleted", Toast.LENGTH_SHORT).show();
//					FileListAdapter adapter = (FileListAdapter) mFileListView.getAdapter();
//					adapter.setDownloaded(num, false);
//				}
//			}
//		}.startWatching();
		if(data.exists()){
			SharedPreferences downloadPrefs = mContext.getSharedPreferences("downloaded_files", Context.MODE_MULTI_PROCESS);
			String _name = downloadPrefs.getString(ident, null);
			if(_name != null && _name.equals(name)) {
				result = true;
			}
		}
		return result;
	}
	
	/**
	 * Gets info about file or folder, without array 
	 * in JSON query.
	 * @param jsonQuery, JSON data got from response
	 * @param number, number of file or folder in JSON
	 * @return FileInfo object with info about file
	 */
	private static FileInfo findObject(String json, int number){
		FileInfo fi = null;
		String name = null;
		String type = null;
		String id = null;
		String etag = null;
		try {
			JSONArray files = new JSONObject(json).getJSONArray("entries");
			JSONObject text = null;
			text = files.getJSONObject(number);
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
	
	private static void copyFileOnDevice(String srcPath, String destPath){
		try{
			File srcFile = new File(srcPath);
			File destFile = new File(destPath);
			InputStream in = new FileInputStream(srcFile);
			OutputStream out = new FileOutputStream(destFile);
			byte[] buf = new byte[4096];
			int len;
			while ((len = in.read(buf)) > 0) {
				out.write(buf, 0, len);
			}
			in.close();
			out.close();
		}catch(FileNotFoundException ex){
			String x = ex.getMessage();
			Log.d("File copy", x);
		}
		catch(IOException e){
			Log.e(TAG, "IO error");
		}
	}
	/**
	 * Saves file id and file name when file is downloaded
	 * of uploaded (exists on device)
	 * @param ident of file
	 * @param name of file
	 */
	private void saveFileData(String ident, String name){
		SharedPreferences downloadPrefs = mContext.getSharedPreferences("downloaded_files", Context.MODE_MULTI_PROCESS);
		Editor edit = downloadPrefs.edit();
		edit.putString(ident, name);
		edit.commit();
	}
	/**
	 * Deletes file info from shared preferences when
	 * file deleting from device
	 * @param ident of file to delete
	 */
	private void deleteFileData(String ident){
		SharedPreferences downloadPrefs = mContext.getSharedPreferences("downloaded_files", Context.MODE_MULTI_PROCESS);
		Editor edit = downloadPrefs.edit();
		edit.remove(ident);
		edit.commit();
	}
	
	/**
	 * Deletes file from service
	 * @param requestUrl, URL to make a request
	 * @param etag, string value to prevent race conditions
	 */
	public Integer deleteFile(String requestUrl, String etag){
		Integer result = 0;
		HttpClient http = new DefaultHttpClient();
		HttpDelete delete = new HttpDelete(requestUrl);
		try {
			HttpResponse response = null;
			delete.setHeader("Authorization", "Bearer " + mAccessToken);
			delete.setHeader("If-Match", etag);
			response = http.execute(delete);
			result = response.getStatusLine().getStatusCode();
			if(result == 204) {
				String files = "files/";
				String ident = requestUrl.substring(requestUrl.indexOf(files) + files.length());
				deleteFileData(ident);
			}
		} catch(Exception e){
			Log.e(TAG, e.getMessage());
		}
		return result;
	}
	
	/**
	 * Deletes folder from service
	 * @param requestUrl, URL to make a request
	 */
	public Integer deleteDir(String requestUrl){
		Integer result = 0;
		HttpClient http = new DefaultHttpClient();
		HttpDelete delete = new HttpDelete(requestUrl);
		try {
			HttpResponse response = null;
			delete.setHeader("Authorization", "Bearer " + mAccessToken);
			response = http.execute(delete);
			result = response.getStatusLine().getStatusCode();
		}catch(Exception e){
			Log.e(TAG, e.getMessage());
		}
		return result;
	}

	/**
	 * Send request on server to update data
	 * @param requestUrl, URL to update data
	 * @param mAccessToken, token to authorize
	 * @param data, data to change
	 * @param oldName
	 * @param newName
	 * @param ident
	 */
	class PutData extends AsyncGetData {
		String oldName = null;
		String newName = null;
		String ident = null;
		@Override
		protected Integer doInBackground(String... param) {
			Integer result = null;
			final String requestUrl = param[0];
			final String accessToken = param[1];
			final String data = param[2];
			oldName = param[3];
			newName = param[4];
			ident = param[5];
			HttpClient client = new DefaultHttpClient();
			HttpPut put = new HttpPut(requestUrl);
			try {
				put.setEntity(new StringEntity(data));
				HttpResponse response = null;
				put.setHeader("Authorization", "Bearer " + accessToken);
				response = client.execute(put);
				result = response.getStatusLine().getStatusCode(); 
				Log.i("RESULT RENAME", String.valueOf(result));
			} catch(Exception e){
				Log.e("PUT DATA", e.getMessage());
			}
			return result;
		}
		@Override
		protected void onPostExecute(Integer result) {
			super.onPostExecute(result);
			if(result == 400) {
				Toast.makeText(mContext, "Failed to rename file or folder.", 
						Toast.LENGTH_LONG).show();
			}
			else if(result == 200) {
				Toast.makeText(mContext, "Item successfully have been renamed.", 
						Toast.LENGTH_LONG).show();
				renameFileOnDevice(ident, oldName, newName);
			}
		}
	}
	
	private boolean renameFileOnDevice(String ident, String oldName, String newName){
		SharedPreferences downloadPref = mContext.getSharedPreferences("downloaded_files", Context.MODE_MULTI_PROCESS);
		Editor edit = downloadPref.edit();
		Map<String,?> idList = downloadPref.getAll();
		if(idList.get(ident) != null){
			edit.putString(ident, newName);
			edit.commit();
		}
		
		File dir = new File(EXT_STORAGE_PATH);
		if(dir.exists()){
			File from = new File(dir, oldName);
			File to = new File(dir, newName);
			if(from.exists()){
				from.renameTo(to);
				return true;
			}
		}
		return false;
	}

	/**
	 * Creates new file or folder
	 * param[0] - request URL
	 * param[1] - a raw data
	 */
	class PostData extends AsyncGetData {
		@Override
		protected Integer doInBackground(String... param) {
			String requestUrl = param[0];
			String data = param[1];
			Integer result = 0;
			// Create a new HttpClient and Post Header
			HttpClient client = new DefaultHttpClient();
			HttpPost post = new HttpPost(requestUrl);
			try {
				post.setEntity(new StringEntity(data));
				post.setHeader("Authorization", "Bearer " + mAccessToken);
				HttpResponse response = client.execute(post);
				result = response.getStatusLine().getStatusCode();
			} catch(ClientProtocolException e) {
				Log.e(TAG, e.getMessage().toString());
			} catch(IOException e) {
				Log.e(TAG, e.getMessage().toString());
			}
			return result;
		}

		@Override
		protected void onPostExecute(Integer result) {
			super.onPostExecute(result);
			if(result == 409) {
				Toast.makeText(mContext, "Item with the same name already exists.", 
						Toast.LENGTH_LONG).show();
			}
		}
	}

	/**
	 * Upload file on service using HTTP-post method
	 * @param param[0] - request URL
	 * @param param[1] - the ID of folder where this file should be uploaded
	 * @param param[2] - the path of the file to be uploaded
	 */
	class UploadData extends AsyncGetData {
		@Override
		protected Integer doInBackground(String... param) {
			Integer result = 0;
			String json = null;
			String requestUrl = param[0];
			String folder_id = param[1];
			String path = param[2];
			File file = new File(path);
			HttpClient client = new DefaultHttpClient();
			HttpPost post = new HttpPost(requestUrl);
			try {
				post.setHeader("Authorization", "Bearer " + mAccessToken);
				MultipartEntity entity = new MultipartEntity();
				entity.addPart("folder_id", new StringBody(folder_id));
				entity.addPart("filename", new FileBody(file));
				post.setEntity(entity);
				
				HttpResponse response = client.execute(post);
				result = response.getStatusLine().getStatusCode();
				if (result == 201) { 
					HttpEntity resEntityPost = response.getEntity();  
					if (resEntityPost != null) {
						json = EntityUtils.toString(resEntityPost);
						FileInfo fi = findObject(json, 0);
						String fileName = fi.getName();
						String ident = fi.getId();
						saveFileData(ident, fileName);
						if(!path.equals(EXT_STORAGE_PATH + "/" + fileName)) {
							copyFileOnDevice(path, EXT_STORAGE_PATH + "/" + fileName);
						}
					}
				}
			}catch (ClientProtocolException e) {
				Log.e(TAG, e.getMessage());
			}catch (IOException e) {
				Log.e(TAG, e.getMessage());
			}
			return result;
		}
		@Override
		protected void onPostExecute(Integer result) {
			super.onPostExecute(result);
			if(result == 400)
				Toast.makeText(mContext, "Failed to upload file.",
						Toast.LENGTH_LONG).show();	
		}
	}
}