/*
 * Copyright (C) 2008 The Android Open Source Project
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.inputmethod.latin;

import android.app.backup.BackupManager;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.text.AutoText;

public class LatinIMESettings extends PreferenceActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String QUICK_FIXES_KEY = "quick_fixes";
    private static final String PREDICTION_SETTINGS_KEY = "prediction_settings";
    private static final String VOICE_SETTINGS_KEY = "voice_mode";
    /* package */ static final String PREF_SETTINGS_KEY = "settings_key";

    private CheckBoxPreference mQuickFixes;
    private ListPreference mSettingsKeyPreference;

    @Override
    protected boolean isValidFragment(String fragmentName) {
        // This legacy settings screen does not use fragments. Reject externally
        // supplied fragment names to prevent PreferenceActivity injection.
        return false;
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.prefs);
        mQuickFixes = (CheckBoxPreference) findPreference(QUICK_FIXES_KEY);
        mSettingsKeyPreference = (ListPreference) findPreference(PREF_SETTINGS_KEY);
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();

        String voiceModeOff = getString(R.string.voice_mode_off);
        try {
            if (!voiceModeOff.equals(prefs.getString(VOICE_SETTINGS_KEY, voiceModeOff))) {
                prefs.edit().putString(VOICE_SETTINGS_KEY, voiceModeOff).apply();
            }
        } catch (ClassCastException ignored) {
            prefs.edit().remove(VOICE_SETTINGS_KEY)
                    .putString(VOICE_SETTINGS_KEY, voiceModeOff).apply();
        }

        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        int autoTextSize = AutoText.getSize(getListView());
        if (autoTextSize < 1) {
            ((PreferenceGroup) findPreference(PREDICTION_SETTINGS_KEY))
                    .removePreference(mQuickFixes);
        }
        Preference voicePreference = findPreference(VOICE_SETTINGS_KEY);
        if (voicePreference != null) {
            getPreferenceScreen().removePreference(voicePreference);
        }
        updateSettingsKeySummary();
    }

    @Override
    protected void onDestroy() {
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(
                this);
        super.onDestroy();
    }

    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        (new BackupManager(this)).dataChanged();
        if (PREF_SETTINGS_KEY.equals(key)) {
            updateSettingsKeySummary();
        }
    }

    private void updateSettingsKeySummary() {
        if (mSettingsKeyPreference == null) {
            return;
        }
        String[] summaries = getResources().getStringArray(R.array.settings_key_modes);
        int index = mSettingsKeyPreference.findIndexOfValue(mSettingsKeyPreference.getValue());
        if (index < 0 || index >= summaries.length) {
            mSettingsKeyPreference.setValue(getString(R.string.settings_key_mode_auto));
            index = mSettingsKeyPreference.findIndexOfValue(mSettingsKeyPreference.getValue());
        }
        if (index >= 0 && index < summaries.length) {
            mSettingsKeyPreference.setSummary(summaries[index]);
        }
    }
}
