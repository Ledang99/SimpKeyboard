/*
 * Copyright (C) 2008 Google Inc.
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

package com.android.inputmethod.voice;

import android.content.Context;
import android.content.Intent;

/**
 * Provides the logging facility for voice input events. This fires broadcasts back to
 * the voice search app which then logs on our behalf.
 *
 * Note that debug console logging does not occur in this class. If you want to
 * see console output of these logging events, there is a boolean switch to turn
 * on on the VoiceSearch side.
 */
public class VoiceInputLogger {
    private static final String TAG = VoiceInputLogger.class.getSimpleName();

    private static VoiceInputLogger sVoiceInputLogger;

    private final Context mContext;

    // The base intent used to form all broadcast intents to the logger
    // in VoiceSearch.
    private final Intent mBaseIntent;

    // This flag is used to indicate when there are voice events that
    // need to be flushed.
    private boolean mHasLoggingInfo = false;

    /**
     * Returns the singleton of the logger.
     *
     * @param contextHint a hint context used when creating the logger instance.
     * Ignored if the singleton instance already exists.
     */
    public static synchronized VoiceInputLogger getLogger(Context contextHint) {
        if (sVoiceInputLogger == null) {
            sVoiceInputLogger = new VoiceInputLogger(contextHint);
        }
        return sVoiceInputLogger;
    }

    public VoiceInputLogger(Context context) {
        mContext = context.getApplicationContext();
        
        mBaseIntent = new Intent(LoggingEvents.ACTION_LOG_EVENT);
        // The AOSP logger used an implicit broadcast to a privileged system app.
        // Keep events private in a standalone build so typed text metadata cannot
        // be observed by third-party broadcast receivers.
        mBaseIntent.setPackage(mContext.getPackageName());
        mBaseIntent.putExtra(LoggingEvents.EXTRA_APP_NAME, LoggingEvents.VoiceIme.APP_NAME);
    }
    
    private Intent newLoggingBroadcast(int event) {
        Intent i = new Intent(mBaseIntent);
        i.putExtra(LoggingEvents.EXTRA_EVENT, event);
        return i;
    }

    public void flush() {
        if (hasLoggingInfo()) {
            Intent i = new Intent(mBaseIntent);
            i.putExtra(LoggingEvents.EXTRA_FLUSH, true);
            mContext.sendBroadcast(i);
            setHasLoggingInfo(false);
        }
    }
    
    public void keyboardWarningDialogShown() {
        setHasLoggingInfo(true);
        mContext.sendBroadcast(newLoggingBroadcast(
                LoggingEvents.VoiceIme.KEYBOARD_WARNING_DIALOG_SHOWN));
    }
    
    public void keyboardWarningDialogDismissed() {
        setHasLoggingInfo(true);
        mContext.sendBroadcast(newLoggingBroadcast(
                LoggingEvents.VoiceIme.KEYBOARD_WARNING_DIALOG_DISMISSED));
    }

    public void keyboardWarningDialogOk() {
        setHasLoggingInfo(true);
        mContext.sendBroadcast(newLoggingBroadcast(
                LoggingEvents.VoiceIme.KEYBOARD_WARNING_DIALOG_OK));
    }

    public void keyboardWarningDialogCancel() {
        setHasLoggingInfo(true);
        mContext.sendBroadcast(newLoggingBroadcast(
                LoggingEvents.VoiceIme.KEYBOARD_WARNING_DIALOG_CANCEL));
    }

    public void settingsWarningDialogShown() {
        setHasLoggingInfo(true);
        mContext.sendBroadcast(newLoggingBroadcast(
                LoggingEvents.VoiceIme.SETTINGS_WARNING_DIALOG_SHOWN));
    }
    
    public void settingsWarningDialogDismissed() {
        setHasLoggingInfo(true);
        mContext.sendBroadcast(newLoggingBroadcast(
                LoggingEvents.VoiceIme.SETTINGS_WARNING_DIALOG_DISMISSED));
    }

    public void settingsWarningDialogOk() {
        setHasLoggingInfo(true);
        mContext.sendBroadcast(newLoggingBroadcast(
                LoggingEvents.VoiceIme.SETTINGS_WARNING_DIALOG_OK));
    }

    public void settingsWarningDialogCancel() {
        setHasLoggingInfo(true);
        mContext.sendBroadcast(newLoggingBroadcast(
                LoggingEvents.VoiceIme.SETTINGS_WARNING_DIALOG_CANCEL));
    }
    
    public void swipeHintDisplayed() {
        setHasLoggingInfo(true);
        mContext.sendBroadcast(newLoggingBroadcast(LoggingEvents.VoiceIme.SWIPE_HINT_DISPLAYED));
    }
    
    public void cancelDuringListening() {
        setHasLoggingInfo(true);
        mContext.sendBroadcast(newLoggingBroadcast(LoggingEvents.VoiceIme.CANCEL_DURING_LISTENING));
    }

    public void cancelDuringWorking() {
        setHasLoggingInfo(true);
        mContext.sendBroadcast(newLoggingBroadcast(LoggingEvents.VoiceIme.CANCEL_DURING_WORKING));
    }

    public void cancelDuringError() {
        setHasLoggingInfo(true);
        mContext.sendBroadcast(newLoggingBroadcast(LoggingEvents.VoiceIme.CANCEL_DURING_ERROR));
    }
    
    public void punctuationHintDisplayed() {
        setHasLoggingInfo(true);
        mContext.sendBroadcast(newLoggingBroadcast(
                LoggingEvents.VoiceIme.PUNCTUATION_HINT_DISPLAYED));
    }
    
    public void error(int code) {
        setHasLoggingInfo(true);
        Intent i = newLoggingBroadcast(LoggingEvents.VoiceIme.ERROR);
        i.putExtra(LoggingEvents.VoiceIme.EXTRA_ERROR_CODE, code);
        mContext.sendBroadcast(i);
    }

    public void start(String locale, boolean swipe) {
        setHasLoggingInfo(true);
        Intent i = newLoggingBroadcast(LoggingEvents.VoiceIme.START);
        i.putExtra(LoggingEvents.VoiceIme.EXTRA_START_LOCALE, locale);
        i.putExtra(LoggingEvents.VoiceIme.EXTRA_START_SWIPE, swipe);
        i.putExtra(LoggingEvents.EXTRA_TIMESTAMP, System.currentTimeMillis());
        mContext.sendBroadcast(i);
    }
    
    public void voiceInputDelivered(int length) {
        setHasLoggingInfo(true);
        Intent i = newLoggingBroadcast(LoggingEvents.VoiceIme.VOICE_INPUT_DELIVERED);
        i.putExtra(LoggingEvents.VoiceIme.EXTRA_TEXT_MODIFIED_LENGTH, length);
        mContext.sendBroadcast(i);
    }

    public void textModifiedByTypingInsertion(int length) {
        setHasLoggingInfo(true);
        Intent i = newLoggingBroadcast(LoggingEvents.VoiceIme.TEXT_MODIFIED);
        i.putExtra(LoggingEvents.VoiceIme.EXTRA_TEXT_MODIFIED_LENGTH, length);
        i.putExtra(LoggingEvents.VoiceIme.EXTRA_TEXT_MODIFIED_TYPE,
                LoggingEvents.VoiceIme.TEXT_MODIFIED_TYPE_TYPING_INSERTION);
        mContext.sendBroadcast(i);
    }

    public void textModifiedByTypingInsertionPunctuation(int length) {
        setHasLoggingInfo(true);
        Intent i = newLoggingBroadcast(LoggingEvents.VoiceIme.TEXT_MODIFIED);
        i.putExtra(LoggingEvents.VoiceIme.EXTRA_TEXT_MODIFIED_LENGTH, length);
        i.putExtra(LoggingEvents.VoiceIme.EXTRA_TEXT_MODIFIED_TYPE,
                LoggingEvents.VoiceIme.TEXT_MODIFIED_TYPE_TYPING_INSERTION_PUNCTUATION);
        mContext.sendBroadcast(i);
    }

    public void textModifiedByTypingDeletion(int length) {
        setHasLoggingInfo(true);
        Intent i = newLoggingBroadcast(LoggingEvents.VoiceIme.TEXT_MODIFIED);
        i.putExtra(LoggingEvents.VoiceIme.EXTRA_TEXT_MODIFIED_LENGTH, length);
        i.putExtra(LoggingEvents.VoiceIme.EXTRA_TEXT_MODIFIED_TYPE,
                LoggingEvents.VoiceIme.TEXT_MODIFIED_TYPE_TYPING_DELETION);

        mContext.sendBroadcast(i);
    }

    public void textModifiedByChooseSuggestion(int suggestionLength, int replacedPhraseLength,
                                               int index, String before, String after) {
        setHasLoggingInfo(true);
        Intent i = newLoggingBroadcast(LoggingEvents.VoiceIme.TEXT_MODIFIED);
        i.putExtra(LoggingEvents.VoiceIme.EXTRA_TEXT_MODIFIED_TYPE,
                   LoggingEvents.VoiceIme.TEXT_MODIFIED_TYPE_CHOOSE_SUGGESTION);
        i.putExtra(LoggingEvents.VoiceIme.EXTRA_TEXT_MODIFIED_LENGTH, suggestionLength);
        i.putExtra(LoggingEvents.VoiceIme.EXTRA_TEXT_REPLACED_LENGTH, replacedPhraseLength);
        i.putExtra(LoggingEvents.VoiceIme.EXTRA_N_BEST_CHOOSE_INDEX, index);
        i.putExtra(LoggingEvents.VoiceIme.EXTRA_BEFORE_N_BEST_CHOOSE, before);
        i.putExtra(LoggingEvents.VoiceIme.EXTRA_AFTER_N_BEST_CHOOSE, after);
        mContext.sendBroadcast(i);
    }

    public void inputEnded() {
        setHasLoggingInfo(true);
        mContext.sendBroadcast(newLoggingBroadcast(LoggingEvents.VoiceIme.INPUT_ENDED));
    }
    
    public void voiceInputSettingEnabled() {
        setHasLoggingInfo(true);
        mContext.sendBroadcast(newLoggingBroadcast(
                LoggingEvents.VoiceIme.VOICE_INPUT_SETTING_ENABLED));
    }
    
    public void voiceInputSettingDisabled() {
        setHasLoggingInfo(true);
        mContext.sendBroadcast(newLoggingBroadcast(
                LoggingEvents.VoiceIme.VOICE_INPUT_SETTING_DISABLED));
    }

    private void setHasLoggingInfo(boolean hasLoggingInfo) {
        mHasLoggingInfo = hasLoggingInfo;
        // If applications that call UserHappinessSignals.userAcceptedImeText
        // make that call after VoiceInputLogger.flush() calls this method with false, we
        // will lose those happiness signals. For example, consider the gmail sequence:
        // 1. compose message
        // 2. speak message into message field
        // 3. type subject into subject field
        // 4. press send
        // We will NOT get the signal that the user accepted the voice inputted message text
        // because when the user tapped on the subject field, the ime's flush will be triggered
        // and the hasLoggingInfo will be then set to false. So by the time the user hits send
        // we have essentially forgotten about any voice input.
        // However the following (more common) use case is properly logged
        // 1. compose message
        // 2. type subject in subject field
        // 3. speak message in message field
        // 4. press send
        UserHappinessSignals.setHasVoiceLoggingInfo(hasLoggingInfo);
    }

    private boolean hasLoggingInfo(){
        return mHasLoggingInfo;
    }

}

/**
 * Local compatibility constants for the platform-private android-common library
 * used by the original AOSP build. These broadcasts are package-scoped above.
 */
final class LoggingEvents {
    static final String ACTION_LOG_EVENT =
            "com.android.inputmethod.latin.action.LOG_VOICE_EVENT";
    static final String EXTRA_APP_NAME = "app_name";
    static final String EXTRA_EVENT = "event";
    static final String EXTRA_FLUSH = "flush";
    static final String EXTRA_TIMESTAMP = "timestamp";

    private LoggingEvents() {}

    static final class VoiceIme {
        static final String APP_NAME = "SimpKeyboard";
        static final int KEYBOARD_WARNING_DIALOG_SHOWN = 1;
        static final int KEYBOARD_WARNING_DIALOG_DISMISSED = 2;
        static final int KEYBOARD_WARNING_DIALOG_OK = 3;
        static final int KEYBOARD_WARNING_DIALOG_CANCEL = 4;
        static final int SETTINGS_WARNING_DIALOG_SHOWN = 5;
        static final int SETTINGS_WARNING_DIALOG_DISMISSED = 6;
        static final int SETTINGS_WARNING_DIALOG_OK = 7;
        static final int SETTINGS_WARNING_DIALOG_CANCEL = 8;
        static final int SWIPE_HINT_DISPLAYED = 9;
        static final int CANCEL_DURING_LISTENING = 10;
        static final int CANCEL_DURING_WORKING = 11;
        static final int CANCEL_DURING_ERROR = 12;
        static final int PUNCTUATION_HINT_DISPLAYED = 13;
        static final int ERROR = 14;
        static final int START = 15;
        static final int VOICE_INPUT_DELIVERED = 16;
        static final int TEXT_MODIFIED = 17;
        static final int INPUT_ENDED = 18;
        static final int VOICE_INPUT_SETTING_ENABLED = 19;
        static final int VOICE_INPUT_SETTING_DISABLED = 20;
        static final int TEXT_MODIFIED_TYPE_TYPING_INSERTION = 21;
        static final int TEXT_MODIFIED_TYPE_TYPING_INSERTION_PUNCTUATION = 22;
        static final int TEXT_MODIFIED_TYPE_TYPING_DELETION = 23;
        static final int TEXT_MODIFIED_TYPE_CHOOSE_SUGGESTION = 24;

        static final String EXTRA_ERROR_CODE = "error_code";
        static final String EXTRA_START_LOCALE = "start_locale";
        static final String EXTRA_START_SWIPE = "start_swipe";
        static final String EXTRA_TEXT_MODIFIED_LENGTH = "text_modified_length";
        static final String EXTRA_TEXT_MODIFIED_TYPE = "text_modified_type";
        static final String EXTRA_TEXT_REPLACED_LENGTH = "text_replaced_length";
        static final String EXTRA_N_BEST_CHOOSE_INDEX = "n_best_choose_index";
        static final String EXTRA_BEFORE_N_BEST_CHOOSE = "before_n_best_choose";
        static final String EXTRA_AFTER_N_BEST_CHOOSE = "after_n_best_choose";

        private VoiceIme() {}
    }
}

final class UserHappinessSignals {
    private UserHappinessSignals() {}

    static void setHasVoiceLoggingInfo(boolean hasLoggingInfo) {
        // Platform-only analytics were intentionally removed from the standalone app.
    }
}
