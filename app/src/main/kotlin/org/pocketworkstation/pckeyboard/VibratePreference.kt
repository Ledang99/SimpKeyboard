package org.pocketworkstation.pckeyboard

import android.content.Context
import android.util.AttributeSet

class VibratePreference(context: Context, attrs: AttributeSet) : SeekBarPreferenceString(context, attrs) {

    override fun onChange(value: Float) {
        val ime = LatinIME.sInstance
        ime?.vibrate(value.toInt())
    }
}
