package com.boxapp.utils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Map;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.boxapp.R;

public class FileListAdapter extends BaseAdapter implements Serializable{

	private static final long serialVersionUID = 1L;
	private ArrayList<Map<String, Object>> fileList;
	private LayoutInflater inflater = null;
	private View convertView;
	
	final String ATTRIBUTE_NAME_IMAGE = "image";
	final String ATTRIBUTE_NAME_TITLE = "title";
	final String ATTRIBUTE_NAME_DOWNLOADED = "status";
	
	public FileListAdapter(Context context, ArrayList<Map<String, Object>> fileList){
		this.fileList = fileList;
		inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	}
	@Override
	public int getCount() {
		return fileList.size();
	}

	@Override
	public Object getItem(int position) {
		return fileList.get(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}
	
	public void setDownloaded(int position, boolean isDownloaded){
		if(convertView != null){
			if(isDownloaded)
				fileList.get(position).put(ATTRIBUTE_NAME_DOWNLOADED, R.drawable.file_downloaded);
			else
				fileList.get(position).put(ATTRIBUTE_NAME_DOWNLOADED, R.drawable.non_downloaded);
		}
			
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		this.convertView = convertView;
		convertView = inflater.inflate(R.layout.filelist_item, null);
		ImageView iconView = (ImageView) convertView.findViewById(R.id.file_icon);
		TextView nameView = (TextView) convertView.findViewById(R.id.file_name);
		ImageView statusView = (ImageView) convertView.findViewById(R.id.download_status);
		
		Map<String, Object> item = fileList.get(position);
		iconView.setImageResource((Integer)item.get(ATTRIBUTE_NAME_IMAGE));
		nameView.setText((String)item.get(ATTRIBUTE_NAME_TITLE));
		Integer downloadStatus = (Integer)item.get(ATTRIBUTE_NAME_DOWNLOADED);
		if(downloadStatus != null)
			statusView.setImageResource(downloadStatus);
		else
			statusView.setVisibility(View.INVISIBLE);
		return convertView;
	}

}
