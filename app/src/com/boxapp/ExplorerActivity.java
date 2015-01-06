package com.boxapp;

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

import com.boxapp.entity.Item;
import com.boxapp.utils.FileListAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        try {
            getDir(root);
        } catch (Exception e) {
            e.printStackTrace();
        }

        mFileListView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> a,  View v,int position, long id) {
				java.io.File file = new java.io.File(path.get(position));
				Intent intent = getIntent();
				Log.i("TAG", path.get(position));
				if (file.isDirectory()) {
					if(file.canRead()) {
                        try {
                            getDir(path.get(position));
                        }catch (Exception ex){
                            ex.printStackTrace();
                        }
					}
				}
				else {
                    intent.putExtra("path", path.get(position));
					setResult(RESULT_OK, intent);
					finish();
				}
			}
		});
	}
	
	private void getDir(String dirPath) throws Exception{
//		ArrayList<Item> fileList = new ArrayList<Item>();
//		path = new ArrayList<String>();
//		java.io.File f = new java.io.File(dirPath);
//		java.io.File[] files = f.listFiles();
//
//		if(!dirPath.equals(root)) {
//			//path.add(root);
//			path.add(f.getParent());
//			Item fi = new Item("../", "folder");
//			fileList.add(fi);
//		}
//
//		for(int i=0; i < files.length; i++) {
//			java.io.File file = files[i];
//			String name = file.getName();
//			path.add(file.getPath());
//			Item fi = null;
//			if(file.isDirectory()) {
//				fi = new Item(name, "folder");
//			}
//			else {
//				fi = new Item(name, "file");
//			}
//			fileList.add(fi);
//		}
//		displayFileStructure(fileList);
	}
	
	/**
	 * Shows file structure, including file and folders
	 * Also shows download status.
	 * @param fileList, ArrayList of files and folders info
	 * which have to be represented
	 */
	private void displayFileStructure(ArrayList<Item> fileList){
		final String ATTRIBUTE_NAME_TITLE = "title";
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
		
		// packing data into structure for adapter
		ArrayList<Map<String, Object>> data = new ArrayList<Map<String, Object>>(fileList.size());
		Map<String, Object> itemMap;
		for (int i = 0; i < fileList.size(); i++){
			itemMap = new HashMap<String, Object>();
			Item fi = (Item)fileList.get(i);
			String name = fi.getName();
			Item.Type type = fi.getType();
			itemMap.put(ATTRIBUTE_NAME_TITLE, name);
			
			if(type == Item.Type.FOLDER) {
				itemMap.put(ATTRIBUTE_NAME_IMAGE, R.drawable.ic_folder);
			}
			else {
				String fileType = name.toLowerCase().substring(name.lastIndexOf(""), name.length());
				Integer format = drawableList.get(fileType);
				if(format == null)
					format = R.drawable.ic_unknown_file_type;
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