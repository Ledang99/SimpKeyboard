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

package org.pocketworkstation.pckeyboard

import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.util.Log
import android.view.InflateException
import java.lang.ref.SoftReference
import java.util.Arrays
import java.util.HashMap

class KeyboardSwitcher private constructor() : SharedPreferences.OnSharedPreferenceChangeListener {

    private var mInputView: LatinKeyboardView? = null
    private lateinit var mInputMethodService: LatinIME

    private var mSymbolsId: KeyboardId? = null
    private var mSymbolsShiftedId: KeyboardId? = null

    private var mCurrentId: KeyboardId? = null
    private val mKeyboards = HashMap<KeyboardId, SoftReference<LatinKeyboard>>()

    private var mMode = MODE_NONE
    private var mImeOptions = 0
    private var mIsSymbols = false
    private var mFullMode = 0
    private var mIsAutoCompletionActive = false
    private var mHasVoice = false
    private var mVoiceOnPrimary = false
    private var mPreferSymbols = false

    private var mAutoModeSwitchState = AUTO_MODE_SWITCH_STATE_ALPHA

    private var mHasSettingsKey = false
    private var mLastDisplayWidth = 0
    private var mLanguageSwitcher: LanguageSwitcher? = null
    private var mLayoutId = 0

    private class KeyboardId(
        val mXml: Int,
        val mKeyboardMode: Int,
        val mEnableShiftLock: Boolean,
        val mHasVoice: Boolean
    ) {
        val mKeyboardHeightPercent: Float = LatinIME.sKeyboardSettings.keyboardHeightPercent
        val mUsingExtension: Boolean = LatinIME.sKeyboardSettings.useExtension
        private val mHashCode: Int = Arrays.hashCode(arrayOf(mXml, mKeyboardMode, mEnableShiftLock, mHasVoice))

        override fun equals(other: Any?): Boolean {
            return other is KeyboardId && equals(other)
        }

        private fun equals(other: KeyboardId?): Boolean {
            return other != null &&
                    other.mXml == mXml &&
                    other.mKeyboardMode == mKeyboardMode &&
                    other.mUsingExtension == mUsingExtension &&
                    other.mEnableShiftLock == mEnableShiftLock &&
                    other.mHasVoice == mHasVoice
        }

        override fun hashCode(): Int = mHashCode
    }

    fun setLanguageSwitcher(languageSwitcher: LanguageSwitcher?) {
        mLanguageSwitcher = languageSwitcher
        languageSwitcher?.inputLocale
    }

    private fun makeSymbolsId(hasVoice: Boolean): KeyboardId {
        return when {
            mFullMode == 1 -> KeyboardId(KBD_COMPACT_FN, KEYBOARDMODE_SYMBOLS, true, hasVoice)
            mFullMode == 2 -> KeyboardId(KBD_FULL_FN, KEYBOARDMODE_SYMBOLS, true, hasVoice)
            else -> KeyboardId(
                KBD_SYMBOLS,
                if (mHasSettingsKey) KEYBOARDMODE_SYMBOLS_WITH_SETTINGS_KEY else KEYBOARDMODE_SYMBOLS,
                false, hasVoice
            )
        }
    }

    private fun makeSymbolsShiftedId(hasVoice: Boolean): KeyboardId? {
        if (mFullMode > 0) return null
        return KeyboardId(
            KBD_SYMBOLS_SHIFT,
            if (mHasSettingsKey) KEYBOARDMODE_SYMBOLS_WITH_SETTINGS_KEY else KEYBOARDMODE_SYMBOLS,
            false, hasVoice
        )
    }

    fun makeKeyboards(forceCreate: Boolean) {
        mFullMode = LatinIME.sKeyboardSettings.keyboardMode
        mSymbolsId = makeSymbolsId(mHasVoice && !mVoiceOnPrimary)
        mSymbolsShiftedId = makeSymbolsShiftedId(mHasVoice && !mVoiceOnPrimary)

        if (forceCreate) mKeyboards.clear()

        val displayWidth = mInputMethodService.maxWidth
        if (displayWidth == mLastDisplayWidth) return
        mLastDisplayWidth = displayWidth
        if (!forceCreate) mKeyboards.clear()
    }

    fun setVoiceMode(enableVoice: Boolean, voiceOnPrimary: Boolean) {
        if (enableVoice != mHasVoice || voiceOnPrimary != mVoiceOnPrimary) {
            mKeyboards.clear()
        }
        mHasVoice = enableVoice
        mVoiceOnPrimary = voiceOnPrimary
        setKeyboardMode(mMode, mImeOptions, mHasVoice, mIsSymbols)
    }

    private fun hasVoiceButton(isSymbols: Boolean): Boolean {
        return mHasVoice && (isSymbols != mVoiceOnPrimary)
    }

    fun setKeyboardMode(mode: Int, imeOptions: Int, enableVoice: Boolean) {
        mAutoModeSwitchState = AUTO_MODE_SWITCH_STATE_ALPHA
        mPreferSymbols = mode == MODE_SYMBOLS
        var actualMode = mode
        if (actualMode == MODE_SYMBOLS) {
            actualMode = MODE_TEXT
        }
        try {
            setKeyboardMode(actualMode, imeOptions, enableVoice, mPreferSymbols)
        } catch (e: RuntimeException) {
            Log.e(TAG, "Got exception: $actualMode,$imeOptions,$mPreferSymbols msg=${e.message}")
        }
    }

    private fun setKeyboardMode(mode: Int, imeOptions: Int, enableVoice: Boolean, isSymbols: Boolean) {
        if (mInputView == null) return
        mMode = mode
        mImeOptions = imeOptions
        if (enableVoice != mHasVoice) {
            setVoiceMode(enableVoice, mVoiceOnPrimary)
        }
        mIsSymbols = isSymbols

        mInputView?.setPreviewEnabled(mInputMethodService.popupOn)

        val id = getKeyboardId(mode, imeOptions, isSymbols)
        val keyboard = getKeyboard(id!!)

        if (mode == MODE_PHONE) {
            mInputView?.setPhoneKeyboard(keyboard)
        }

        mCurrentId = id
        mInputView?.keyboard = keyboard
        keyboard.setShiftState(Keyboard.SHIFT_OFF)
        keyboard.setImeOptions(mInputMethodService.resources, mMode, imeOptions)
        keyboard.updateSymbolIcons(mIsAutoCompletionActive)
    }

    private fun getKeyboard(id: KeyboardId): LatinKeyboard {
        val ref = mKeyboards[id]
        var keyboard = ref?.get()
        if (keyboard == null) {
            val orig = mInputMethodService.resources
            val conf = orig.configuration
            val saveLocale = conf.locale
            conf.locale = LatinIME.sKeyboardSettings.inputLocale
            orig.updateConfiguration(conf, null)
            keyboard = LatinKeyboard(mInputMethodService, id.mXml, id.mKeyboardMode, id.mKeyboardHeightPercent)
            keyboard.setVoiceMode(hasVoiceButton(id.mXml == R.xml.kbd_symbols), mHasVoice)
            keyboard.setLanguageSwitcher(mLanguageSwitcher, mIsAutoCompletionActive)

            if (id.mEnableShiftLock) {
                keyboard.enableShiftLock()
            }
            mKeyboards[id] = SoftReference(keyboard)

            conf.locale = saveLocale
            orig.updateConfiguration(conf, null)
        }
        return keyboard
    }

    fun isFullMode(): Boolean = mFullMode > 0

    private fun getKeyboardId(mode: Int, imeOptions: Int, isSymbols: Boolean): KeyboardId? {
        val hasVoice = hasVoiceButton(isSymbols)
        if (mFullMode > 0) {
            when (mode) {
                MODE_TEXT, MODE_URL, MODE_EMAIL, MODE_IM, MODE_WEB ->
                    return KeyboardId(
                        if (mFullMode == 1) KBD_COMPACT else KBD_FULL,
                        KEYBOARDMODE_NORMAL, true, hasVoice
                    )
            }
        }
        val keyboardRowsResId = KBD_QWERTY
        if (isSymbols) {
            return if (mode == MODE_PHONE) {
                KeyboardId(KBD_PHONE_SYMBOLS, 0, false, hasVoice)
            } else {
                KeyboardId(
                    KBD_SYMBOLS,
                    if (mHasSettingsKey) KEYBOARDMODE_SYMBOLS_WITH_SETTINGS_KEY else KEYBOARDMODE_SYMBOLS,
                    false, hasVoice
                )
            }
        }
        return when (mode) {
            MODE_NONE, MODE_TEXT -> KeyboardId(
                keyboardRowsResId,
                if (mHasSettingsKey) KEYBOARDMODE_NORMAL_WITH_SETTINGS_KEY else KEYBOARDMODE_NORMAL,
                true, hasVoice
            )
            MODE_SYMBOLS -> KeyboardId(
                KBD_SYMBOLS,
                if (mHasSettingsKey) KEYBOARDMODE_SYMBOLS_WITH_SETTINGS_KEY else KEYBOARDMODE_SYMBOLS,
                false, hasVoice
            )
            MODE_PHONE -> KeyboardId(KBD_PHONE, 0, false, hasVoice)
            MODE_URL -> KeyboardId(
                keyboardRowsResId,
                if (mHasSettingsKey) KEYBOARDMODE_URL_WITH_SETTINGS_KEY else KEYBOARDMODE_URL,
                true, hasVoice
            )
            MODE_EMAIL -> KeyboardId(
                keyboardRowsResId,
                if (mHasSettingsKey) KEYBOARDMODE_EMAIL_WITH_SETTINGS_KEY else KEYBOARDMODE_EMAIL,
                true, hasVoice
            )
            MODE_IM -> KeyboardId(
                keyboardRowsResId,
                if (mHasSettingsKey) KEYBOARDMODE_IM_WITH_SETTINGS_KEY else KEYBOARDMODE_IM,
                true, hasVoice
            )
            MODE_WEB -> KeyboardId(
                keyboardRowsResId,
                if (mHasSettingsKey) KEYBOARDMODE_WEB_WITH_SETTINGS_KEY else KEYBOARDMODE_WEB,
                true, hasVoice
            )
            else -> null
        }
    }

    fun getKeyboardMode(): Int = mMode

    fun isAlphabetMode(): Boolean {
        if (mCurrentId == null) return false
        val currentMode = mCurrentId!!.mKeyboardMode
        if (mFullMode > 0 && currentMode == KEYBOARDMODE_NORMAL) return true
        for (mode in ALPHABET_MODES) {
            if (currentMode == mode) return true
        }
        return false
    }

    fun setShiftState(shiftState: Int) {
        mInputView?.setShiftState(shiftState)
    }

    fun setFn(useFn: Boolean) {
        val inputView = mInputView ?: return
        val oldShiftState = inputView.shiftState
        if (useFn) {
            val kbd = getKeyboard(mSymbolsId!!)
            kbd.enableShiftLock()
            mCurrentId = mSymbolsId
            inputView.keyboard = kbd
            inputView.setShiftState(oldShiftState)
        } else {
            setKeyboardMode(mMode, mImeOptions, mHasVoice, false)
            inputView.setShiftState(oldShiftState)
        }
    }

    fun setCtrlIndicator(active: Boolean) {
        mInputView?.setCtrlIndicator(active)
    }

    fun setAltIndicator(active: Boolean) {
        mInputView?.setAltIndicator(active)
    }

    fun setMetaIndicator(active: Boolean) {
        mInputView?.setMetaIndicator(active)
    }

    fun toggleShift() {
        if (isAlphabetMode()) return
        if (mFullMode > 0) {
            val shifted = mInputView?.isShiftAll == true
            mInputView?.setShiftState(if (shifted) Keyboard.SHIFT_OFF else Keyboard.SHIFT_ON)
            return
        }
        if (mCurrentId == mSymbolsId || mCurrentId != mSymbolsShiftedId) {
            val symbolsShiftedKeyboard = getKeyboard(mSymbolsShiftedId!!)
            mCurrentId = mSymbolsShiftedId
            mInputView?.keyboard = symbolsShiftedKeyboard
            symbolsShiftedKeyboard.enableShiftLock()
            symbolsShiftedKeyboard.setShiftState(Keyboard.SHIFT_LOCKED)
            symbolsShiftedKeyboard.setImeOptions(mInputMethodService.resources, mMode, mImeOptions)
        } else {
            val symbolsKeyboard = getKeyboard(mSymbolsId!!)
            mCurrentId = mSymbolsId
            mInputView?.keyboard = symbolsKeyboard
            symbolsKeyboard.enableShiftLock()
            symbolsKeyboard.setShiftState(Keyboard.SHIFT_OFF)
            symbolsKeyboard.setImeOptions(mInputMethodService.resources, mMode, mImeOptions)
        }
    }

    fun onCancelInput() {
        if (mAutoModeSwitchState == AUTO_MODE_SWITCH_STATE_MOMENTARY && pointerCount == 1)
            mInputMethodService.changeKeyboardMode()
    }

    fun toggleSymbols() {
        setKeyboardMode(mMode, mImeOptions, mHasVoice, !mIsSymbols)
        if (mIsSymbols && !mPreferSymbols) {
            mAutoModeSwitchState = AUTO_MODE_SWITCH_STATE_SYMBOL_BEGIN
        } else {
            mAutoModeSwitchState = AUTO_MODE_SWITCH_STATE_ALPHA
        }
    }

    fun hasDistinctMultitouch(): Boolean = mInputView?.hasDistinctMultitouch() == true

    fun setAutoModeSwitchStateMomentary() {
        mAutoModeSwitchState = AUTO_MODE_SWITCH_STATE_MOMENTARY
    }

    fun isInMomentaryAutoModeSwitchState(): Boolean =
        mAutoModeSwitchState == AUTO_MODE_SWITCH_STATE_MOMENTARY

    fun isInChordingAutoModeSwitchState(): Boolean =
        mAutoModeSwitchState == AUTO_MODE_SWITCH_STATE_CHORDING

    fun isVibrateAndSoundFeedbackRequired(): Boolean =
        mInputView != null && !mInputView!!.isInSlidingKeyInput

    private val pointerCount: Int
        get() = mInputView?.pointerCount ?: 0

    fun onKey(key: Int) {
        when (mAutoModeSwitchState) {
            AUTO_MODE_SWITCH_STATE_MOMENTARY -> {
                if (key == LatinKeyboard.KEYCODE_MODE_CHANGE) {
                    mAutoModeSwitchState = if (mIsSymbols) {
                        AUTO_MODE_SWITCH_STATE_SYMBOL_BEGIN
                    } else {
                        AUTO_MODE_SWITCH_STATE_ALPHA
                    }
                } else if (pointerCount == 1) {
                    mInputMethodService.changeKeyboardMode()
                } else {
                    mAutoModeSwitchState = AUTO_MODE_SWITCH_STATE_CHORDING
                }
            }
            AUTO_MODE_SWITCH_STATE_SYMBOL_BEGIN -> {
                if (key != LatinIME.ASCII_SPACE && key != LatinIME.ASCII_ENTER && key >= 0) {
                    mAutoModeSwitchState = AUTO_MODE_SWITCH_STATE_SYMBOL
                }
            }
            AUTO_MODE_SWITCH_STATE_SYMBOL -> {
                if (key == LatinIME.ASCII_ENTER || key == LatinIME.ASCII_SPACE) {
                    mInputMethodService.changeKeyboardMode()
                }
            }
        }
    }

    fun getInputView(): LatinKeyboardView? = mInputView

    fun recreateInputView() {
        changeLatinKeyboardView(mLayoutId, true)
    }

    private fun changeLatinKeyboardView(newLayout: Int, forceReset: Boolean) {
        var layout = newLayout
        if (mLayoutId != layout || mInputView == null || forceReset) {
            mInputView?.closing()
            if (THEMES.size <= layout) {
                layout = DEFAULT_LAYOUT_ID.toInt()
            }

            LatinIMEUtil.GCUtils.getInstance().reset()
            var tryGC = true
            for (i in 0 until LatinIMEUtil.GCUtils.GC_TRY_LOOP_MAX) {
                if (!tryGC) break
                try {
                    mInputView = mInputMethodService.layoutInflater.inflate(
                        THEMES[layout], null
                    ) as LatinKeyboardView
                    tryGC = false
                } catch (e: OutOfMemoryError) {
                    tryGC = LatinIMEUtil.GCUtils.getInstance().tryGCOrWait("$mLayoutId,$layout", e)
                } catch (e: InflateException) {
                    tryGC = LatinIMEUtil.GCUtils.getInstance().tryGCOrWait("$mLayoutId,$layout", e)
                }
            }
            mInputView?.setExtensionLayoutResId(THEMES[layout])
            mInputView?.setOnKeyboardActionListener(mInputMethodService)
            mInputView?.setPadding(0, 0, 0, 0)
            mLayoutId = layout
        }
        mInputMethodService.mHandler.post {
            mInputView?.let {
                mInputMethodService.setInputView(it)
            }
            mInputMethodService.updateInputViewShown()
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == PREF_KEYBOARD_LAYOUT) {
            changeLatinKeyboardView(
                sharedPreferences?.getString(key, DEFAULT_LAYOUT_ID)?.toInt() ?: 0,
                true
            )
        } else if (key == LatinIMESettings.PREF_SETTINGS_KEY) {
            updateSettingsKeyState(sharedPreferences!!)
            recreateInputView()
        }
    }

    fun onAutoCompletionStateChanged(isAutoCompletion: Boolean) {
        if (isAutoCompletion != mIsAutoCompletionActive) {
            val keyboardView = inputView ?: return
            mIsAutoCompletionActive = isAutoCompletion
            keyboardView.invalidateKey(
                (keyboardView.keyboard as LatinKeyboard).onAutoCompletionStateChanged(isAutoCompletion)
            )
        }
    }

    private fun updateSettingsKeyState(prefs: SharedPreferences) {
        val resources = mInputMethodService.resources
        val settingsKeyMode = prefs.getString(
            LatinIMESettings.PREF_SETTINGS_KEY,
            resources.getString(DEFAULT_SETTINGS_KEY_MODE)
        )
        mHasSettingsKey = settingsKeyMode == resources.getString(SETTINGS_KEY_MODE_ALWAYS_SHOW) ||
                settingsKeyMode == resources.getString(SETTINGS_KEY_MODE_AUTO)
    }

    val inputView: LatinKeyboardView?
        get() = mInputView

    companion object {
        private const val TAG = "PCKeyboardKbSw"

        const val MODE_NONE = 0
        const val MODE_TEXT = 1
        const val MODE_SYMBOLS = 2
        const val MODE_PHONE = 3
        const val MODE_URL = 4
        const val MODE_EMAIL = 5
        const val MODE_IM = 6
        const val MODE_WEB = 7

        @JvmField val KEYBOARDMODE_NORMAL = R.id.mode_normal
        @JvmField val KEYBOARDMODE_URL = R.id.mode_url
        @JvmField val KEYBOARDMODE_EMAIL = R.id.mode_email
        @JvmField val KEYBOARDMODE_IM = R.id.mode_im
        @JvmField val KEYBOARDMODE_WEB = R.id.mode_webentry
        @JvmField val KEYBOARDMODE_NORMAL_WITH_SETTINGS_KEY = R.id.mode_normal_with_settings_key
        @JvmField val KEYBOARDMODE_URL_WITH_SETTINGS_KEY = R.id.mode_url_with_settings_key
        @JvmField val KEYBOARDMODE_EMAIL_WITH_SETTINGS_KEY = R.id.mode_email_with_settings_key
        @JvmField val KEYBOARDMODE_IM_WITH_SETTINGS_KEY = R.id.mode_im_with_settings_key
        @JvmField val KEYBOARDMODE_WEB_WITH_SETTINGS_KEY = R.id.mode_webentry_with_settings_key
        @JvmField val KEYBOARDMODE_SYMBOLS = R.id.mode_symbols
        @JvmField val KEYBOARDMODE_SYMBOLS_WITH_SETTINGS_KEY = R.id.mode_symbols_with_settings_key

        const val DEFAULT_LAYOUT_ID = "0"
        const val PREF_KEYBOARD_LAYOUT = "pref_keyboard_layout"

        private val THEMES = intArrayOf(
            R.layout.input_ics,
            R.layout.input_gingerbread,
            R.layout.input_stone_bold,
            R.layout.input_trans_neon,
            R.layout.input_material_dark,
            R.layout.input_material_light,
            R.layout.input_ics_darker,
            R.layout.input_material_black
        )

        private const val KBD_PHONE = R.xml.kbd_phone
        private const val KBD_PHONE_SYMBOLS = R.xml.kbd_phone_symbols
        private const val KBD_SYMBOLS = R.xml.kbd_symbols
        private const val KBD_SYMBOLS_SHIFT = R.xml.kbd_symbols_shift
        private const val KBD_QWERTY = R.xml.kbd_qwerty
        private const val KBD_FULL = R.xml.kbd_full
        private const val KBD_FULL_FN = R.xml.kbd_full_fn
        private const val KBD_COMPACT = R.xml.kbd_compact
        private const val KBD_COMPACT_FN = R.xml.kbd_compact_fn

        private val ALPHABET_MODES = intArrayOf(
            KEYBOARDMODE_NORMAL,
            KEYBOARDMODE_URL, KEYBOARDMODE_EMAIL, KEYBOARDMODE_IM,
            KEYBOARDMODE_WEB, KEYBOARDMODE_NORMAL_WITH_SETTINGS_KEY,
            KEYBOARDMODE_URL_WITH_SETTINGS_KEY,
            KEYBOARDMODE_EMAIL_WITH_SETTINGS_KEY,
            KEYBOARDMODE_IM_WITH_SETTINGS_KEY,
            KEYBOARDMODE_WEB_WITH_SETTINGS_KEY
        )

        private const val AUTO_MODE_SWITCH_STATE_ALPHA = 0
        private const val AUTO_MODE_SWITCH_STATE_SYMBOL_BEGIN = 1
        private const val AUTO_MODE_SWITCH_STATE_SYMBOL = 2
        private const val AUTO_MODE_SWITCH_STATE_MOMENTARY = 3
        private const val AUTO_MODE_SWITCH_STATE_CHORDING = 4

        private const val SETTINGS_KEY_MODE_AUTO = R.string.settings_key_mode_auto
        private const val SETTINGS_KEY_MODE_ALWAYS_SHOW = R.string.settings_key_mode_always_show
        private const val DEFAULT_SETTINGS_KEY_MODE = SETTINGS_KEY_MODE_AUTO

        private val sInstance = KeyboardSwitcher()

        @JvmStatic
        fun getInstance(): KeyboardSwitcher = sInstance

        @JvmStatic
        fun init(ims: LatinIME) {
            sInstance.mInputMethodService = ims

            val prefs = PreferenceManager.getDefaultSharedPreferences(ims)
            sInstance.mLayoutId = prefs.getString(PREF_KEYBOARD_LAYOUT, DEFAULT_LAYOUT_ID)?.toInt() ?: 0

            sInstance.updateSettingsKeyState(prefs)
            prefs.registerOnSharedPreferenceChangeListener(sInstance)

            sInstance.mSymbolsId = sInstance.makeSymbolsId(false)
            sInstance.mSymbolsShiftedId = sInstance.makeSymbolsShiftedId(false)
        }
    }
}
