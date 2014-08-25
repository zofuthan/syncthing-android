package com.nutomic.syncthingandroid.fragments;

import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.support.v4.app.NavUtils;
import android.support.v4.preference.PreferenceFragment;
import android.text.InputType;
import android.view.MenuItem;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.activities.SyncthingActivity;
import com.nutomic.syncthingandroid.syncthing.RestApi;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;

public class SettingsFragment extends PreferenceFragment
        implements SyncthingActivity.OnServiceConnectedListener,
        SyncthingService.OnApiChangeListener, Preference.OnPreferenceChangeListener {

    private static final String SYNCTHING_OPTIONS_KEY = "syncthing_options";

    private static final String SYNCTHING_GUI_KEY = "syncthing_gui";

    private static final String SYNCTHING_VERSION_KEY = "syncthing_version";

    private CheckBoxPreference mStopNotCharging;

    private CheckBoxPreference mStopMobileData;

    private Preference mVersion;

    private PreferenceScreen mOptionsScreen;

    private PreferenceScreen mGuiScreen;

    private SyncthingService mSyncthingService;

    @Override
    public void onApiChange(SyncthingService.State currentState) {
        mOptionsScreen.setEnabled(currentState == SyncthingService.State.ACTIVE);
        mGuiScreen.setEnabled(currentState == SyncthingService.State.ACTIVE);

        mVersion.setSummary(mSyncthingService.getApi().getVersion());

        if (currentState == SyncthingService.State.ACTIVE) {
            for (int i = 0; i < mOptionsScreen.getPreferenceCount(); i++) {
                Preference pref = mOptionsScreen.getPreference(i);
                pref.setOnPreferenceChangeListener(SettingsFragment.this);
                String value = mSyncthingService.getApi()
                        .getValue(RestApi.TYPE_OPTIONS, pref.getKey());
                applyPreference(pref, value);
            }

            for (int i = 0; i < mGuiScreen.getPreferenceCount(); i++) {
                Preference pref = mGuiScreen.getPreference(i);
                pref.setOnPreferenceChangeListener(SettingsFragment.this);
                String value = mSyncthingService.getApi()
                        .getValue(RestApi.TYPE_GUI, pref.getKey());
                applyPreference(pref, value);
            }
        }
    }

    /**
     * Applies the given value to the preference.
     * <p/>
     * If pref is an EditTextPreference, setText is used and the value shown as summary. If pref is
     * a CheckBoxPreference, setChecked is used (by parsing value as Boolean).
     */
    private void applyPreference(Preference pref, String value) {
        if (pref instanceof EditTextPreference) {
            ((EditTextPreference) pref).setText(value);
            pref.setSummary(value);
        } else if (pref instanceof CheckBoxPreference) {
            ((CheckBoxPreference) pref).setChecked(Boolean.parseBoolean(value));
        }
    }


    /**
     * Loads layout, sets version from Rest API.
     * <p/>
     * Manual target API as we manually check if ActionBar is available (for ActionBar back button).
     */
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        ((SyncthingActivity) getActivity()).registerOnServiceConnectedListener(this);

        addPreferencesFromResource(R.xml.app_settings);
        PreferenceScreen screen = getPreferenceScreen();
        mStopNotCharging = (CheckBoxPreference) findPreference("stop_sync_on_mobile_data");
        mStopNotCharging.setOnPreferenceChangeListener(this);
        mStopMobileData = (CheckBoxPreference) findPreference("stop_sync_while_not_charging");
        mStopMobileData.setOnPreferenceChangeListener(this);
        mVersion = screen.findPreference(SYNCTHING_VERSION_KEY);
        mOptionsScreen = (PreferenceScreen) screen.findPreference(SYNCTHING_OPTIONS_KEY);
        mGuiScreen = (PreferenceScreen) screen.findPreference(SYNCTHING_GUI_KEY);
    }

    @Override
    public void onServiceConnected() {
        mSyncthingService = ((SyncthingActivity) getActivity()).getService();
        mSyncthingService.registerOnApiChangeListener(this);
    }

    /**
     * Handles ActionBar back selected.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(getActivity());
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Sends the updated value to {@link }RestApi}, and sets it as the summary
     * for EditTextPreference.
     */
    @Override
    public boolean onPreferenceChange(Preference preference, Object o) {
        if (preference instanceof EditTextPreference) {
            String value = (String) o;
            preference.setSummary(value);
            EditTextPreference etp = (EditTextPreference) preference;
            if (etp.getEditText().getInputType() == InputType.TYPE_CLASS_NUMBER) {
                o = Integer.parseInt((String) o);
            }
        }

        if (preference.equals(mStopNotCharging) || preference.equals(mStopMobileData)) {
            mSyncthingService.updateState();
        } else if (mOptionsScreen.findPreference(preference.getKey()) != null) {
            mSyncthingService.getApi().setValue(RestApi.TYPE_OPTIONS, preference.getKey(), o,
                    preference.getKey().equals("ListenAddress"), getActivity());
        } else if (mGuiScreen.findPreference(preference.getKey()) != null) {
            mSyncthingService.getApi().setValue(
                    RestApi.TYPE_GUI, preference.getKey(), o, false, getActivity());
        }

        return true;
    }
}
