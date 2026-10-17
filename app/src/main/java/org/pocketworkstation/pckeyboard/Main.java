/*
 * Copyright (C) 2011 The Android Open Source Project
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

package org.pocketworkstation.pckeyboard;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Html;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.TextView.BufferType;

public class Main extends Activity {

    private static final String MARKET_URI = "market://search?q=pub:\"Klaus Weidner\"";
    private static final int REQUEST_POST_NOTIFICATIONS = 1;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private TextView mStatusView;
    private Button mEnableButton;
    private Button mSelectButton;
    private boolean mAskedNotificationPermission;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mStatusView = findViewById(R.id.main_setup_status);
        mEnableButton = findViewById(R.id.main_setup_btn_configure_imes);
        mSelectButton = findViewById(R.id.main_setup_btn_set_ime);

        String html = getString(R.string.main_body);
        html += "<p><i>Version: " + getString(R.string.auto_version) + "</i></p>";
        Spanned content;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            content = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY);
        } else {
            content = Html.fromHtml(html);
        }
        TextView description = findViewById(R.id.main_description);
        description.setMovementMethod(LinkMovementMethod.getInstance());
        description.setText(content, BufferType.SPANNABLE);

        mEnableButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                openInputMethodSettings();
            }
        });

        mSelectButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                selectInputMethod();
            }
        });

        findViewById(R.id.main_setup_btn_input_lang).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(Main.this, InputLanguageSelection.class));
            }
        });

        findViewById(R.id.main_setup_btn_get_dicts).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent it = new Intent(Intent.ACTION_VIEW, Uri.parse(MARKET_URI));
                try {
                    startActivity(it);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(getApplicationContext(),
                            getResources().getString(R.string.no_market_warning),
                            Toast.LENGTH_LONG).show();
                }
            }
        });

        findViewById(R.id.main_setup_btn_settings).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(Main.this, LatinIMESettings.class));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshSetupState();
        if (isImeEnabled() && !mAskedNotificationPermission) {
            maybeRequestNotificationPermission();
        }
    }

    private void refreshSetupState() {
        boolean enabled = isImeEnabled();
        boolean current = isImeCurrent();
        if (current) {
            mStatusView.setText(R.string.setup_status_current);
        } else if (enabled) {
            mStatusView.setText(R.string.setup_status_enabled);
        } else {
            mStatusView.setText(R.string.setup_status_disabled);
        }
        mEnableButton.setEnabled(!enabled);
        mSelectButton.setEnabled(enabled && !current);
        mEnableButton.setText(enabled
                ? R.string.main_setup_configure_imes_done
                : R.string.main_setup_configure_imes);
        mSelectButton.setText(current
                ? R.string.main_setup_set_ime_done
                : R.string.main_setup_set_ime);
    }

    private boolean isImeEnabled() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) {
            return false;
        }
        String myId = imeId();
        for (InputMethodInfo info : imm.getEnabledInputMethodList()) {
            if (myId.equals(info.getId())) {
                return true;
            }
        }
        return false;
    }

    private boolean isImeCurrent() {
        String current = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.DEFAULT_INPUT_METHOD);
        return imeId().equals(current);
    }

    private String imeId() {
        return getPackageName() + "/" + LatinIME.class.getName();
    }

    private void openInputMethodSettings() {
        Toast.makeText(this, R.string.setup_enable_hint, Toast.LENGTH_LONG).show();
        Intent[] attempts = new Intent[] {
                new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS),
                new Intent(Settings.ACTION_INPUT_METHOD_SUBTYPE_SETTINGS),
                languageInputSettingsIntent(),
                new Intent(Settings.ACTION_SETTINGS)
        };
        for (Intent intent : attempts) {
            if (intent == null) {
                continue;
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (launch(intent)) {
                return;
            }
        }
        Toast.makeText(this, R.string.setup_open_settings_failed, Toast.LENGTH_LONG).show();
    }

    private Intent languageInputSettingsIntent() {
        Intent intent = new Intent(Settings.ACTION_LOCALE_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    private boolean launch(Intent intent) {
        try {
            if (intent.resolveActivity(getPackageManager()) == null) {
                startActivity(intent);
                return true;
            }
            startActivity(intent);
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        } catch (SecurityException e) {
            return false;
        }
    }

    private void selectInputMethod() {
        if (!isImeEnabled()) {
            Toast.makeText(this, R.string.setup_enable_first, Toast.LENGTH_LONG).show();
            openInputMethodSettings();
            return;
        }
        Toast.makeText(this, R.string.setup_select_hint, Toast.LENGTH_LONG).show();
        final InputMethodManager imm =
                (InputMethodManager) getApplicationContext().getSystemService(INPUT_METHOD_SERVICE);
        if (imm == null) {
            Toast.makeText(this, R.string.setup_open_settings_failed, Toast.LENGTH_LONG).show();
            return;
        }
        // Some Android versions drop the picker unless it is posted after the click.
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                imm.showInputMethodPicker();
            }
        }, 150);
    }

    private void maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mAskedNotificationPermission = true;
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                REQUEST_POST_NOTIFICATIONS);
    }
}
