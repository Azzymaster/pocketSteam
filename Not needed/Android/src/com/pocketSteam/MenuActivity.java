package com.pocketSteam;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Date;

import com.pocketSteam.gson.Gson;
import com.pocketSteam.gson.reflect.TypeToken;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class MenuActivity extends Activity {	
	
	AlertDialog connectingDialog = null;
	Runnable avatarRunnable;
	
	Boolean avatarCheckInProgress = false;
	Boolean apiCheckInProgress = false;
	Date lastAvatarCheck = new Date();
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setTitle(getString(R.string.app_name) + " / Menu");
		setContentView(R.layout.menu);
		Thread serverHandlerThread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					while(API.connected) {
						if(!apiCheckInProgress)
						{
							new BackgroundTask().execute();
						}
						
						Thread.sleep(1250);
					}
				}
				catch(Exception ex) { MenuActivity.this.finish(); }
			} }, "Communication Thread");
		serverHandlerThread.start();
		
		avatarRunnable = new Runnable() {
			@Override
			public void run() {
				avatarCheckInProgress = true;
				
				String rootFilePath = null;
				final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
				
				if(settings.getBoolean("avatarSDCard", false) && android.os.Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED)) {
					rootFilePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/pocketSteam/AvatarCache/";
				} else {
					rootFilePath = getApplicationContext().getFilesDir() + "/AvatarCache/";
				}
				
				for(SteamUserData friend : User.friends) {
					try {
						int position = User.friends.indexOf(friend);
			        	
						//Database shenanigans!
			        	String[] avatarNameSplit = friend.AvatarURL.split("/");							        	
			        	String avatarName = avatarNameSplit[avatarNameSplit.length-1];
			        	
			        	Database dbHelper = new Database(MenuActivity.this);
			        	SQLiteDatabase db = dbHelper.getWritableDatabase();
			        	Cursor cursor = db.rawQuery("SELECT * FROM Avatars WHERE SteamID='" + friend.SteamID + "'", null);
			        	Boolean exists = cursor.moveToPosition(0);
			        	if(!exists) {
			        		friend.Avatar = API.DownloadImage(friend.AvatarURL);
			        		
			        		ContentValues cv = new ContentValues();
				        	cv.put("SteamID", friend.SteamID);
				        	cv.put("Avatar", avatarName);
				        	db.insert("Avatars", "SteamID", cv);
				        	
				        	File directoryCreate = new File(rootFilePath);
				        	directoryCreate.mkdirs(); //Create the directories for the cache
				        	
				        	String filepath = rootFilePath + friend.SteamID.replaceAll(":", "_");
				        	FileOutputStream fos = null;
				        	fos = new FileOutputStream(filepath); 
				        	((BitmapDrawable)friend.Avatar).getBitmap().compress(CompressFormat.PNG, 75, fos);

			        	}
			        	if(!cursor.getString(2).equals(avatarName) && !avatarName.equals("fef49e7fa7e1997310d705b2a6158ff8dc1cdfeb.jpg")) {
			        		db.delete("Avatars", "SteamID='" + friend.SteamID + "'", null);
			        		
			        		friend.Avatar = API.DownloadImage(friend.AvatarURL);
			        		
			        		ContentValues cv = new ContentValues();
				        	cv.put("SteamID", friend.SteamID);
				        	cv.put("Avatar", avatarName);
				        	db.insert("Avatars", "SteamID", cv);
				        	
				        	File directoryCreate = new File(rootFilePath);
				        	directoryCreate.mkdirs(); //Create the directories for the cache
				        	
				        	String filepath = rootFilePath + friend.SteamID.replaceAll(":", "_");
				        	FileOutputStream fos = null;
				        	fos = new FileOutputStream(filepath); 
				        	((BitmapDrawable)friend.Avatar).getBitmap().compress(CompressFormat.PNG, 75, fos);
			        		
			        	} else {
			        		String filepath = rootFilePath + cursor.getString(1).replaceAll(":", "_");
			        		
			        		Bitmap bitmap = BitmapFactory.decodeFile(filepath);
			        		if(bitmap == null) {
			        			db.delete("Avatars", "SteamID='" + friend.SteamID + "'", null);
			        		} else {
			        			friend.Avatar = new BitmapDrawable(bitmap);
			        		}
			        	}
			        				        	
			        	User.friends.set(position, friend);
			        	cursor.close();
			        	db.close();
			        	dbHelper.close();
			        	
			        	runOnUiThread(new Runnable() {
							@Override
							public void run() {
								Toast.makeText(getApplicationContext(), "Avatar check complete", Toast.LENGTH_SHORT).show();
							} });
			        	
			        	lastAvatarCheck = new Date();
			        	avatarCheckInProgress = false;
			        	
					} catch(Exception ex) { }
				}
				if(User.friendsListOpen) {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							FriendsListActivity.adapter.notifyDataSetChanged();
						} });
				}
			}
		};
	}
	
	public void FriendsButton(View view) {
		Intent friendsIntent = new Intent(MenuActivity.this, com.pocketSteam.FriendsListActivity.class);
        startActivity(friendsIntent);
	}
	public void ChatLogsButton(View view) {
		Toast.makeText(getApplicationContext(), "Not yet implemented", Toast.LENGTH_LONG).show();
	}
	public void SettingsButton(View view) {
		Intent settingsIntent = new Intent(MenuActivity.this, com.pocketSteam.SettingsActivity.class);
        startActivity(settingsIntent);
	}
	public void DisconnectButton(View view) {
		moveTaskToBack(true);
        API.connected = false;
        try {
			API.Contact("/AjaxCommand/" + API.SessionToken + "/1", "");
		} catch (Exception e) { }
    	this.finish();
	}
	/*
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
	    if (keyCode == KeyEvent.KEYCODE_BACK) {
	        moveTaskToBack(true);
	        //TODO: Disconnect!
	        API.connected = false;
	        try {
				API.Contact("/AjaxCommand/" + API.SessionToken + "/1", "");
			} catch (Exception e) { }
	    	this.finish();
	        return true;
	    }
	    return super.onKeyDown(keyCode, event);
	}
	*/
	private class BackgroundTask extends AsyncTask<Void, Void, String> {

		@Override
		protected String doInBackground(Void... arg0) {
			apiCheckInProgress = true;
			try {
				String reply = API.Contact("/AjaxReply/" + API.SessionToken, "");
				
				return reply;
			} catch(Exception ex) { }
			return null;
		}
		protected void onPostExecute(String rawResult) {
			Gson gson = new Gson();
			JsonReply result;
			
			if(rawResult.equals("No such session")) {
				API.connected = false;
				new AlertDialog.Builder(MenuActivity.this)
                .setTitle(R.string.app_name)
                .setMessage(R.string.ConnectionExpired)
                .create().show();
				
				result = null;
				return;
			}
			
			try {
				result = gson.fromJson(rawResult, JsonReply.class);
			} catch(Exception ex) { 
				new AlertDialog.Builder(MenuActivity.this)
                .setTitle(R.string.app_name)
                .setMessage("DEBUG: Invalid Json")
                .create().show();
				
				result = null;
				return;
			}
			
			if(result != null) {
				Button enableButtons;
				enableButtons = (Button)findViewById(R.id.buttonFriends);
				
				if(connectingDialog == null && result.Status == 1) {
					connectingDialog = ProgressDialog.show(MenuActivity.this,    
		        						getString(R.string.PleaseWait), getString(R.string.GettingData), true);
				} else if(connectingDialog != null && result.Status != 1 && User.Data != null) {
					connectingDialog.cancel();
					connectingDialog = null;
					
					enableButtons.setEnabled(true);
					enableButtons = (Button)findViewById(R.id.buttonSettings);
					enableButtons.setEnabled(true);
					enableButtons = (Button)findViewById(R.id.buttonDisconnect);
					enableButtons.setEnabled(true);
					enableButtons = (Button)findViewById(R.id.buttonChatLogs);
					enableButtons.setEnabled(true);
					
					API.Started = true;
					
					if(!avatarCheckInProgress) {
						Thread avatarThread = new Thread(avatarRunnable);
						avatarThread.start();
					}
				}
				if(API.Started == true && !enableButtons.isEnabled() && result.Status != 1 && User.Data != null) {
					enableButtons.setEnabled(true);
					enableButtons = (Button)findViewById(R.id.buttonSettings);
					enableButtons.setEnabled(true);
					enableButtons = (Button)findViewById(R.id.buttonDisconnect);
					enableButtons.setEnabled(true);
					enableButtons = (Button)findViewById(R.id.buttonChatLogs);
					enableButtons.setEnabled(true);
				}
			}
			
			for(Message msg : result.Messages) {
				if(msg.Type == 1) {
					SteamUserData userData = gson.fromJson(msg.MessageValue, SteamUserData.class);
					
					User.Data = userData;
				} else if(msg.Type == 2 || msg.Type == 3) {
					Database dbHelper = new Database(MenuActivity.this);
		        	SQLiteDatabase db = dbHelper.getWritableDatabase();
		        	ChatMessageData messageData = gson.fromJson(msg.MessageValue, ChatMessageData.class);
		        	
		        	ContentValues cv = new ContentValues();
		        	cv.put("SteamID", messageData.SteamID);
		        	cv.put("Type", msg.Type);
		        	if(msg.Type == 2) {
		        		cv.put("Message", messageData.SteamName + ": " + messageData.Message);
		        	} else {
		        		cv.put("Message", messageData.SteamName + " " + messageData.Message);
		        	}
		        	db.insert("Messages", "SteamID", cv);
		        	
		        	if(User.chatOpen) {
		        		FriendChatActivity.LoadChatWindow();
		        	}
		        	
		        	//TODO: Make a better notification!
		        	Toast.makeText(getApplicationContext(), "New message from: " + messageData.SteamName, Toast.LENGTH_SHORT).show();
				} else if(msg.Type == 4) {
					Type collectionType = new TypeToken<ArrayList<SteamUserData>>(){}.getType();
					ArrayList<SteamUserData> friends = gson.fromJson(msg.MessageValue, collectionType);
					
					User.friends = friends;
					
					final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
					
					Date now = new Date();
					
					if(settings.getBoolean("displayAvatar", true) && API.Started && !avatarCheckInProgress && (lastAvatarCheck.getSeconds()-now.getSeconds()) >= 20) {
						Thread avatarThread = new Thread(avatarRunnable);
						avatarThread.start();
					}
					
					if(User.friendsListOpen) {
						FriendsListActivity.adapter.friends = User.friends;
						FriendsListActivity.adapter.notifyDataSetChanged();
					}
					if(User.chatOpen) {
						FriendChatActivity.Refresh();
					}
				}
			}
			
			apiCheckInProgress = false;
		}
	}
}
