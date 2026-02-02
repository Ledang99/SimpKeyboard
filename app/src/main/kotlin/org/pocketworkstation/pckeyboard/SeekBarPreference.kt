package org.pocketworkstation.pckeyboard

import android.content.Context
import android.content.res.TypedArray
import android.preference.DialogPreference
import android.util.AttributeSet
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.round

/**
 * SeekBarPreference provides a dialog for editing float-valued preferences with a slider.
 */
open class SeekBarPreference(context: Context, attrs: AttributeSet) : DialogPreference(context, attrs) {

    private var mMinText: TextView? = null
    private var mMaxText: TextView? = null
    private var mValText: TextView? = null
    private var mSeek: SeekBar? = null
    private var mMin: Float = 0f
    private var mMax: Float = 0f
    protected var mVal: Float = 0f
    private var mPrevVal: Float = 0f
    private var mStep: Float = 0f
    private var mAsPercent: Boolean = false
    private var mLogScale: Boolean = false
    private var mDisplayFormat: String? = null

    init {
        init(context, attrs)
    }

    protected open fun init(context: Context, attrs: AttributeSet) {
        setDialogLayoutResource(R.layout.seek_bar_dialog)

        val a = context.obtainStyledAttributes(attrs, R.styleable.SeekBarPreference)
        mMin = a.getFloat(R.styleable.SeekBarPreference_minValue, 0.0f)
        mMax = a.getFloat(R.styleable.SeekBarPreference_maxValue, 100.0f)
        mStep = a.getFloat(R.styleable.SeekBarPreference_step, 0.0f)
        mAsPercent = a.getBoolean(R.styleable.SeekBarPreference_asPercent, false)
        mLogScale = a.getBoolean(R.styleable.SeekBarPreference_logScale, false)
        mDisplayFormat = a.getString(R.styleable.SeekBarPreference_displayFormat)
        a.recycle()
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Float {
        return a.getFloat(index, 0.0f)
    }

    override fun onSetInitialValue(restorePersistedValue: Boolean, defaultValue: Any?) {
        if (restorePersistedValue) {
            setVal(getPersistedFloat(0.0f))
        } else {
            setVal(defaultValue as Float)
        }
        savePrevVal()
    }

    private fun formatFloatDisplay(value: Float): String {
        // Use current locale for format, this is for display only.
        return when {
            mAsPercent -> String.format("%d%%", (value * 100).toInt())
            mDisplayFormat != null -> String.format(mDisplayFormat!!, value)
            else -> value.toString()
        }
    }

    private fun showVal() {
        mValText?.text = formatFloatDisplay(mVal)
    }

    protected open fun setVal(value: Float) {
        mVal = value
    }

    protected fun savePrevVal() {
        mPrevVal = mVal
    }

    protected fun restoreVal() {
        mVal = mPrevVal
    }

    protected open fun getValString(): String = mVal.toString()

    private fun percentToSteppedVal(percent: Int, min: Float, max: Float, step: Float, logScale: Boolean): Float {
        val value: Float = if (logScale) {
            exp(percentToSteppedVal(percent, ln(min), ln(max), step, false).toDouble()).toFloat()
        } else {
            var delta = percent * (max - min) / 100
            if (step != 0.0f) {
                delta = round(delta / step) * step
            }
            min + delta
        }
        // Hack: Round number to 2 significant digits so that it looks nicer.
        return String.format(Locale.US, "%.2g", value).toFloat()
    }

    private fun getPercent(value: Float, min: Float, max: Float): Int {
        return (100 * (value - min) / (max - min)).toInt()
    }

    private fun getProgressVal(): Int {
        return if (mLogScale) {
            getPercent(ln(mVal), ln(mMin), ln(mMax))
        } else {
            getPercent(mVal, mMin, mMax)
        }
    }

    override fun onBindDialogView(view: View) {
        mSeek = view.findViewById(R.id.seekBarPref)
        mMinText = view.findViewById(R.id.seekMin)
        mMaxText = view.findViewById(R.id.seekMax)
        mValText = view.findViewById(R.id.seekVal)

        showVal()
        mMinText?.text = formatFloatDisplay(mMin)
        mMaxText?.text = formatFloatDisplay(mMax)
        mSeek?.progress = getProgressVal()

        mSeek?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val newVal = percentToSteppedVal(progress, mMin, mMax, mStep, mLogScale)
                    if (newVal != mVal) {
                        onChange(newVal)
                    }
                    setVal(newVal)
                    mSeek?.progress = getProgressVal()
                }
                showVal()
            }
        })

        super.onBindDialogView(view)
    }

    open fun onChange(value: Float) {
        // override in subclasses
    }

    override fun getSummary(): CharSequence = formatFloatDisplay(mVal)

    override fun onDialogClosed(positiveResult: Boolean) {
        if (!positiveResult) {
            restoreVal()
            return
        }
        if (shouldPersist()) {
            persistFloat(mVal)
            savePrevVal()
        }
        notifyChanged()
    }
}
