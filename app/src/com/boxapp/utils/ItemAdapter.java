package com.boxapp.utils;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.boxapp.R;
import com.boxapp.entity.DownloadStatus;
import com.boxapp.entity.Item;

import java.io.Serializable;
import java.util.List;

public class ItemAdapter extends BaseAdapter implements Serializable {

    private static final long serialVersionUID = 1L;
    private List<Item> items;
    private LayoutInflater inflater = null;
    private Context context;
    private boolean isEnabled = true;

    public static final int BACK = 0;
    public static final int ITEM = 1;

    public ItemAdapter(Context context, List<Item> items) {
        this.context = context;
        this.items = items;
        inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    public ItemAdapter(Context context, List<Item> items, Item prevFolder) {
        this.context = context;
        this.items = items;
        this.items.add(0, prevFolder);
        inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0 && getItem(position).getName() == null) {
            return BACK;
        }
        return ITEM;
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public Item getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).getId();
    }

    public void setStatus(int position, boolean isDownloaded) {
        getItem(position).setDownloadStatus(new DownloadStatus(isDownloaded, isDownloaded ? 100 : 0));
    }

    public int getPositionById(long id) {
        for (int i = 0; i < getCount(); i++) {
            if (getItem(i).getId() == id)
                return i;
        }
        return -1;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder = new ViewHolder();
        if (getItemViewType(position) == BACK) {
            convertView = new TextView(context);
        } else if (getItemViewType(position) == ITEM) {
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.file_item, parent, false);
                holder.iconIv = (ImageView) convertView.findViewById(R.id.iconIv);
                holder.nameTv = (TextView) convertView.findViewById(R.id.nameTv);
                holder.statusIv = (ImageView) convertView.findViewById(R.id.statusIv);
                holder.progressBar = (ProgressBar) convertView.findViewById(R.id.progressBar);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
        }

        if (getItemViewType(position) == BACK) {
            ((TextView) convertView).setText("../");
        } else {
            Item item = items.get(position);
            if (item.getType() == Item.Type.FOLDER) {
                holder.iconIv.setImageResource(R.drawable.ic_folder);
                holder.statusIv.setVisibility(View.INVISIBLE);
                holder.progressBar.setVisibility(View.INVISIBLE);
            } else {
                int resourceId = R.drawable.ic_unknown_file_type;
                String fileName = item.getName();
                if (fileName.contains(".")) {
                    String fileType = fileName.toLowerCase()
                            .substring(fileName.lastIndexOf(".") + 1, fileName.length());
                    resourceId = context.getResources()
                            .getIdentifier(fileType, "drawable", context.getPackageName());
                    if (resourceId == 0)
                        resourceId = R.drawable.ic_unknown_file_type;
                }
                holder.iconIv.setImageResource(resourceId);
                DownloadStatus status = item.getStatus();
                if (status.getProgress() > 0 && status.getProgress() < 100) {
                    holder.progressBar.setVisibility(View.VISIBLE);
                    holder.statusIv.setVisibility(View.INVISIBLE);
                } else {
                    holder.progressBar.setVisibility(View.INVISIBLE);
                    holder.statusIv.setVisibility(View.VISIBLE);
                    holder.statusIv.setImageResource(item.getStatus().isDownloaded() ? R.drawable.file_downloaded : R.drawable.non_downloaded);
                }
            }
            holder.nameTv.setText(item.getName());
        }
        return convertView;
    }

    class ViewHolder {
        ImageView iconIv;
        TextView nameTv;
        ImageView statusIv;
        ProgressBar progressBar;
    }
}
