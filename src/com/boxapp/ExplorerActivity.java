package com.boxapp;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

import com.boxapp.utils.FileInfo;
import com.boxapp.utils.FileListAdapter;

public class ExplorerActivity extends Activity {

	private List<String> path = null;
	private String root = Environment.getExternalStorageDirectory().getPath();
	private Context mContext = this;
	private ListView mFileListView;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.explorer);
		mFileListView = (ListView)findViewById(R.id.fileList);
		getDir(root);
		
		mFileListView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> a,  View v,int position, long id) {
				File file = new File(path.get(position));
				Intent i = getIntent();
				Log.i("TAG", path.get(position));
				if (file.isDirectory()) {
					if(file.canRead()) {
						getDir(path.get(position));
					}
				}
				else {
					i.putExtra("path", path.get(position));
					setResult(RESULT_OK, i);
					finish();
				}
			}
		});
	}
	
	private void getDir(String dirPath) {
		ArrayList<FileInfo> fileList = new ArrayList<FileInfo>();
		path = new ArrayList<String>();
		File f = new File(dirPath);
		File[] files = f.listFiles();
		
		if(!dirPath.equals(root)) {
			//path.add(root);
			path.add(f.getParent());
			FileInfo fi = new FileInfo("../", "folder");
			fileList.add(fi);
		}
		
		for(int i=0; i < files.length; i++) {
			File file = files[i];
			String name = file.getName();
			path.add(file.getPath());
			FileInfo fi = null;
			if(file.isDirectory()) {
				fi = new FileInfo(name, "folder");
			}
			else {
				fi = new FileInfo(name, "file");
			}
			fileList.add(fi);
		}
		displayFileStructure(fileList);
	}
	
	/**
	 * Shows file structure, including file and folders
	 * Also shows download status.
	 * @param fileList, ArrayList of files and folders info
	 * which have to be represented
	 */
	private void displayFileStructure(ArrayList<FileInfo> fileList){
		final String ATTRIBUTE_NAME_TITLE = "title";
		final String ATTRIBUTE_NAME_IMAGE = "image";
		
		Map<String, Integer> formats = new HashMap<String, Integer>();
		formats.put(".jpg", R.drawable.jpeg_icon);
		formats.put(".jpeg", R.drawable.jpeg_icon);
		formats.put(".doc", R.drawable.docx_icon);
		formats.put(".docx", R.drawable.docx_icon);
		formats.put(".png", R.drawable.png_icon);
		formats.put(".pdf", R.drawable.pdf_icon);
		formats.put(".txt", R.drawable.txt_icon);	
		
		final int folderImg = R.drawable.folder;
		
		// packing data into structure for adapter
		ArrayList<Map<String, Object>> data = new ArrayList<Map<String, Object>>(fileList.size());
		Map<String, Object> itemMap;
		for (int i = 0; i < fileList.size(); i++){
			itemMap = new HashMap<String, Object>();
			FileInfo fi = (FileInfo)fileList.get(i);
			String name = fi.getName();
			String type = fi.getType();
			itemMap.put(ATTRIBUTE_NAME_TITLE, name);
			
			if(type.equals("folder")) {
				itemMap.put(ATTRIBUTE_NAME_IMAGE, folderImg);
			}
			else if(type.equals("file")) {
				String fileType = name.toLowerCase().substring(name.lastIndexOf("."), name.length());
				Integer format = formats.get(fileType);
				if(format == null)
					format = R.drawable.default_icon;
				itemMap.put(ATTRIBUTE_NAME_IMAGE, format);
			}
			data.add(itemMap);
		}
		
		FileListAdapter adapter = new FileListAdapter(mContext, data);
		mFileListView.setAdapter(adapter);
	}

	/*@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		File file = new File(path.get(position));
		Intent i = getIntent();
		if (file.isDirectory()) {
			if(file.canRead()) {
				getDir(path.get(position));
			}
			else {
				new AlertDialog.Builder(this)
						.setIcon(R.drawable.icon)
						.setTitle("[" + file.getName() + "] folder can't be read!")
						.setPositiveButton("OK", 
								new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
							}
						}).show();
			}
		}
		else {
			i.putExtra("path", path.get(position));
			setResult(RESULT_OK, i);
			finish();
		}
	}*/
}