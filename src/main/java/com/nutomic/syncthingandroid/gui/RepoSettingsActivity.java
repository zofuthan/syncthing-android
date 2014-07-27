package com.nutomic.syncthingandroid.gui;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.storage.StorageManager;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.syncthing.RestApi;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;
import com.nutomic.syncthingandroid.syncthing.SyncthingServiceBinder;
import com.nutomic.syncthingandroid.util.ExtendedCheckBoxPreference;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Shows repo details and allows changing them.
 */
public class RepoSettingsActivity extends PreferenceActivity
		implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener,
		SyncthingService.OnApiChangeListener {

	private static final int DIRECTORY_REQUEST_CODE = 234;

	public static final String ACTION_CREATE = "create";

	public static final String ACTION_EDIT = "edit";

	public static final String KEY_REPO_ID = "repo_id";

	private static final String KEY_NODE_SHARED = "node_shared";

	private SyncthingService mSyncthingService;

	private final ServiceConnection mSyncthingServiceConnection = new ServiceConnection() {

		public void onServiceConnected(ComponentName className, IBinder service) {
			SyncthingServiceBinder binder = (SyncthingServiceBinder) service;
			mSyncthingService = binder.getService();
			mSyncthingService.registerOnApiChangeListener(RepoSettingsActivity.this);
		}

		public void onServiceDisconnected(ComponentName className) {
			mSyncthingService = null;
		}
	};

	private RestApi.Repo mRepo;

	private EditTextPreference mRepoId;

	private Preference mDirectory;

	private CheckBoxPreference mRepoMaster;

	private PreferenceScreen mNodes;

	private CheckBoxPreference mVersioning;

	private EditTextPreference mVersioningKeep;

	private Preference mDelete;

	@Override
	@SuppressLint("AppCompatMethod")
	@TargetApi(11)
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			getActionBar().setDisplayHomeAsUpEnabled(true);
		}

		if (getIntent().getAction().equals(ACTION_CREATE)) {
			addPreferencesFromResource(R.xml.repo_settings_create);
		}
		else if (getIntent().getAction().equals(ACTION_EDIT)) {
			addPreferencesFromResource(R.xml.repo_settings_edit);
		}

		mRepoId = (EditTextPreference) findPreference("repo_id");
		mRepoId.setOnPreferenceChangeListener(this);
		mDirectory = findPreference("directory");
		mDirectory.setOnPreferenceClickListener(this);
		mRepoMaster = (CheckBoxPreference) findPreference("repo_master");
		mRepoMaster.setOnPreferenceChangeListener(this);
		mNodes = (PreferenceScreen) findPreference("nodes");
		mNodes.setOnPreferenceClickListener(this);
		mVersioning = (CheckBoxPreference) findPreference("versioning");
		mVersioning.setOnPreferenceChangeListener(this);
		mVersioningKeep = (EditTextPreference) findPreference("versioning_keep");
		mVersioningKeep.setOnPreferenceChangeListener(this);
		if (getIntent().getAction().equals(ACTION_EDIT)) {
			mDelete = findPreference("delete");
			mDelete.setOnPreferenceClickListener(this);
		}

		bindService(new Intent(this, SyncthingService.class),
				mSyncthingServiceConnection, Context.BIND_AUTO_CREATE);
	}

	@Override
	public void onApiChange(boolean isAvailable) {
		if (!isAvailable) {
			finish();
			return;
		}

		if (getIntent().getAction().equals(ACTION_CREATE)) {
			setTitle(R.string.create_repo);
			mRepo = new RestApi.Repo();
			mRepo.ID = "";
			mRepo.Directory = "";
			mRepo.Nodes = new ArrayList<RestApi.Node>();
			mRepo.Versioning = new RestApi.Versioning();
		}
		else if (getIntent().getAction().equals(ACTION_EDIT)) {
			setTitle(R.string.edit_repo);
			List<RestApi.Repo> repos = mSyncthingService.getApi().getRepos();
			for (int i = 0; i < repos.size(); i++) {
				if (repos.get(i).ID.equals(getIntent().getStringExtra(KEY_REPO_ID))) {
					mRepo = repos.get(i);
					break;
				}
			}
		}

		mRepoId.setText(mRepo.ID);
		mRepoId.setSummary(mRepo.ID);
		mDirectory.setSummary(mRepo.Directory);
		mRepoMaster.setChecked(mRepo.ReadOnly);
		List<RestApi.Node> nodesList = mSyncthingService.getApi().getNodes();
		for (RestApi.Node n : nodesList) {
			ExtendedCheckBoxPreference cbp =
					new ExtendedCheckBoxPreference(RepoSettingsActivity.this, n);
			cbp.setTitle(n.Name);
			cbp.setKey(KEY_NODE_SHARED);
			cbp.setOnPreferenceChangeListener(RepoSettingsActivity.this);
			cbp.setChecked(false);
			for (RestApi.Node n2 : mRepo.Nodes) {
				if (n2.NodeID.equals(n.NodeID)) {
					cbp.setChecked(true);
				}
			}
			mNodes.addPreference(cbp);
		}
		mVersioning.setChecked(mRepo.Versioning instanceof RestApi.SimpleVersioning);
		if (mVersioning.isChecked()) {
			mVersioningKeep.setText(mRepo.Versioning.getParams().get("keep"));
			mVersioningKeep.setSummary(mRepo.Versioning.getParams().get("keep"));
			mVersioningKeep.setEnabled(true);
		}
		else {
			mVersioningKeep.setEnabled(false);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.repo_settings, menu);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.findItem(R.id.create).setVisible(getIntent().getAction().equals(ACTION_CREATE));
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.create:
				if (mRepo.ID.equals("")) {
					Toast.makeText(this, R.string.repo_id_required, Toast.LENGTH_LONG).show();
					return true;
				}
				if (mRepo.Directory.equals("")) {
					Toast.makeText(this, R.string.repo_path_required, Toast.LENGTH_LONG).show();
					return true;
				}
				mSyncthingService.getApi().editRepo(mRepo, true, this);
				finish();
				return true;
			case android.R.id.home:
				finish();
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		unbindService(mSyncthingServiceConnection);
	}

	@Override
	public boolean onPreferenceChange(Preference preference, Object o) {
		if (preference instanceof EditTextPreference) {
			EditTextPreference pref = (EditTextPreference) preference;
			pref.setSummary((String) o);
		}

		if (preference.equals(mRepoId)) {
			mRepo.ID = (String) o;
			repoUpdated();
			return true;
		}
		else if (preference.equals(mDirectory)) {
			mRepo.Directory = (String) o;
			repoUpdated();
			return true;
		}
		else if (preference.equals(mRepoMaster)) {
			mRepo.ReadOnly = (Boolean) o;
			repoUpdated();
			return true;
		}
		else if (preference.getKey().equals(KEY_NODE_SHARED)) {
			ExtendedCheckBoxPreference pref = (ExtendedCheckBoxPreference) preference;
			RestApi.Node node = (RestApi.Node) pref.getObject();
			if ((Boolean) o) {
				mRepo.Nodes.add(node);
			}
			else {
				for (RestApi.Node n : mRepo.Nodes) {
					if (n.NodeID.equals(node.NodeID)) {
						mRepo.Nodes.remove(n);
					}
				}
			}
			repoUpdated();
			return true;
		}
		else if (preference.equals(mVersioning)) {
			mVersioningKeep.setEnabled((Boolean) o);
			if ((Boolean) o) {
				RestApi.SimpleVersioning v = new RestApi.SimpleVersioning();
				mRepo.Versioning = v;
				v.setParams(5);
				mVersioningKeep.setText("5");
				mVersioningKeep.setSummary("5");
			}
			else {
				mRepo.Versioning = new RestApi.Versioning();
			}
			repoUpdated();
			return true;
		}
		else if (preference.equals(mVersioningKeep)) {
			((RestApi.SimpleVersioning) mRepo.Versioning)
					.setParams(Integer.parseInt((String) o));
			repoUpdated();
			return true;
		}

		return false;
	}

	@Override
	public boolean onPreferenceClick(Preference preference) {
		if (preference.equals(mDirectory)) {
			Intent intent;
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
				intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
			}
			else {
				intent = new Intent(Intent.ACTION_GET_CONTENT);
			}
			intent.addCategory(Intent.CATEGORY_OPENABLE)
					.setType(DocumentsContract.Document.MIME_TYPE_DIR)
					.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
			startActivityForResult(intent, DIRECTORY_REQUEST_CODE);
			return true;
		}
		else if (preference.equals(mNodes) && mSyncthingService.getApi().getNodes().isEmpty()) {
			Toast.makeText(this, R.string.no_nodes, Toast.LENGTH_SHORT).show();
			return true;
		}
		else if (preference.equals(mDelete)) {
			new AlertDialog.Builder(this)
					.setMessage(R.string.delete_repo_confirm)
					.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialogInterface, int i) {
							mSyncthingService.getApi().deleteRepo(mRepo, RepoSettingsActivity.this);
							finish();
						}
					})
					.setNegativeButton(android.R.string.no, null)
					.show();
			return true;
		}
		return false;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == DIRECTORY_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
			Uri uri = data.getData();
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
				getContentResolver().takePersistableUriPermission(uri, data.getFlags()
						& (Intent.FLAG_GRANT_READ_URI_PERMISSION
						| Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
				//mRepo.Directory = getPath(uri);
				mRepo.Directory = uri.toString();
			}
			else {
				// TODO: properly get path (content:// URI)
				mRepo.Directory = uri.getPath();
			}
			mDirectory.setSummary(mRepo.Directory);
		}
	}

	private void repoUpdated() {
		if (getIntent().getAction().equals(ACTION_EDIT)) {
			mSyncthingService.getApi().editRepo(mRepo, false, this);
		}
	}

	/**
	 * Get a file path from a Uri. This will get the the path for Storage Access
	 * Framework Documents, as well as the _data field for the MediaStore and
	 * other file-based ContentProviders.<br>
	 * <br>
	 * Callers should check whether the path is local before assuming it
	 * represents a local file.
	 *
	 * @param uri The Uri to query.
	 * @author paulburke
	 */
	@TargetApi(19)
	public String getPath(final Uri uri) {

		// DocumentProvider
		if (DocumentsContract.isDocumentUri(this, uri)) {
			// ExternalStorageProvider
			if (isExternalStorageDocument(uri)) {
				final String docId = DocumentsContract.getDocumentId(uri);
				final String[] split = docId.split(":");

				if ("primary".equalsIgnoreCase(split[0])) {
					return Environment.getExternalStorageDirectory() + "/" + split[1];
				}

				// TODO handle non-primary volumes
			}
			// DownloadsProvider
			else if (isDownloadsDocument(uri)) {

				final String id = DocumentsContract.getDocumentId(uri);
				final Uri contentUri = ContentUris.withAppendedId(
						Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));

				return getDataColumn(contentUri, null, null);
			}
			// MediaProvider
			else if (isMediaDocument(uri)) {
				final String docId = DocumentsContract.getDocumentId(uri);
				final String[] split = docId.split(":");
				final String type = split[0];

				Uri contentUri = null;
				if ("image".equals(type)) {
					contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
				} else if ("video".equals(type)) {
					contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
				} else if ("audio".equals(type)) {
					contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
				}

				final String selection = "_id=?";
				final String[] selectionArgs = new String[] {
						split[1]
				};

				return getDataColumn(contentUri, selection, selectionArgs);
			}
		}
		// MediaStore (and general)
		else if ("content".equalsIgnoreCase(uri.getScheme())) {

			// Return the remote address
			if (isGooglePhotosUri(uri))
				return uri.getLastPathSegment();

			return getDataColumn(uri, null, null);
		}
		// File
		else if ("file".equalsIgnoreCase(uri.getScheme())) {
			return uri.getPath();
		}

		return null;
	}

	/**
	 * @param uri The Uri to check.
	 * @return Whether the Uri authority is ExternalStorageProvider.
	 * @author paulburke
	 */
	public static boolean isExternalStorageDocument(Uri uri) {
		return "com.android.externalstorage.documents".equals(uri.getAuthority());
	}

	/**
	 * @param uri The Uri to check.
	 * @return Whether the Uri authority is DownloadsProvider.
	 * @author paulburke
	 */
	public static boolean isDownloadsDocument(Uri uri) {
		return "com.android.providers.downloads.documents".equals(uri.getAuthority());
	}

	/**
	 * @param uri The Uri to check.
	 * @return Whether the Uri authority is MediaProvider.
	 * @author paulburke
	 */
	public static boolean isMediaDocument(Uri uri) {
		return "com.android.providers.media.documents".equals(uri.getAuthority());
	}

	/**
	 * @param uri The Uri to check.
	 * @return Whether the Uri authority is Google Photos.
	 */
	public static boolean isGooglePhotosUri(Uri uri) {
		return "com.google.android.apps.photos.content".equals(uri.getAuthority());
	}/**
	 * Get the value of the data column for this Uri. This is useful for
	 * MediaStore Uris, and other file-based ContentProviders.
	 *
	 * @param uri The Uri to query.
	 * @param selection (Optional) Filter used in the query.
	 * @param selectionArgs (Optional) Selection arguments used in the query.
	 * @return The value of the _data column, which is typically a file path.
	 * @author paulburke
	 */
	public String getDataColumn(Uri uri, String selection,
									   String[] selectionArgs) {
		Cursor cursor = null;

		try {
			cursor = getContentResolver().query(uri, new String[]{"_data"}, selection, selectionArgs,
					null);
			if (cursor != null && cursor.moveToFirst()) {
				final int column_index = cursor.getColumnIndexOrThrow("_data");
				return cursor.getString(column_index);
			}
		} finally {
			if (cursor != null)
				cursor.close();
		}
		return null;
	}

}
