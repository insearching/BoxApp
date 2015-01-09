package com.boxapp.utils;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;

import com.boxapp.MainActivity;
import com.boxapp.R;

public class BoxWidgetProvider extends AppWidgetProvider {

	public static final String ACTION_STATUS_CHANGED = "com.boxapp.utils.STATUS_CHANGED";
	public static final String ACTION_TEXT_CLICKED = "com.boxapp.utils.CLICKED";
	private String downloadStatus = null;
	private int progress = 0;
	
	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager,
			int[] appWidgetIds) {
	
		// Get all ids
		ComponentName thisWidget = new ComponentName(context,
				BoxWidgetProvider.class);
		int[] allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);
		
		for (int widgetId : allWidgetIds) {
			// create some random data
			RemoteViews remoteViews = new RemoteViews(context.getPackageName(),
					R.layout.widget_layout);
			// Set the text
			if(downloadStatus == null){
				remoteViews.setTextViewText(R.id.update, "");
			} 
			else {
				if(downloadStatus.startsWith(context.getString(R.string.downloading))){
					remoteViews.setViewVisibility(R.id.downloadBar, View.VISIBLE);
					remoteViews.setProgressBar(R.id.downloadBar, 100, progress, false);
					remoteViews.setTextViewText(R.id.update, downloadStatus);
				}
				if(downloadStatus.endsWith(context.getString(R.string.downloaded))){
					remoteViews.setViewVisibility(R.id.downloadBar, View.INVISIBLE);
					remoteViews.setTextViewText(R.id.update, downloadStatus);
				}
			}
			if(progress != 0){
				remoteViews.setProgressBar(R.id.downloadBar, 100, progress, false);
			}
			// Register an onClickListener
			Intent intent = new Intent(context, BoxWidgetProvider.class);
			intent.setAction(ACTION_TEXT_CLICKED);
			intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
			
			PendingIntent pendingIntent = PendingIntent.getBroadcast(context,
					0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
			remoteViews.setOnClickPendingIntent(R.id.widget_layout, pendingIntent);
			appWidgetManager.updateAppWidget(widgetId, remoteViews);
		}
	}
	
	@Override
	public void onReceive(Context context, Intent intent) {
		super.onReceive(context, intent);
		AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
		ComponentName thisAppWidget = new ComponentName(context.getPackageName(), BoxWidgetProvider.class.getName());
		int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget);
		if(intent.getAction().equals(ACTION_STATUS_CHANGED)){
			if(intent.hasExtra(KeyHelper.MESSAGE)){
				downloadStatus = intent.getStringExtra(KeyHelper.MESSAGE);
				onUpdate(context, appWidgetManager, appWidgetIds);
			}
			if(intent.hasExtra(KeyHelper.PROGRESS)){
				progress = intent.getIntExtra(KeyHelper.PROGRESS, 0);
				onUpdate(context, appWidgetManager, appWidgetIds);
			}
		}
		else if(intent.getAction().equals(ACTION_TEXT_CLICKED)){
			downloadStatus = null;
			Intent i = new Intent(context, MainActivity.class);
			i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			context.startActivity(i);
			onUpdate(context, appWidgetManager, appWidgetIds);
		}
	}
}
