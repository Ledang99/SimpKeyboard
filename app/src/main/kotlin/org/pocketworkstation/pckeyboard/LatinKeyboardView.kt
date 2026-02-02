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

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.os.Handler
import android.os.Message
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.widget.PopupWindow
import android.widget.TextView

class LatinKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LatinKeyboardBaseView(context, attrs, defStyle) {

    private var mPhoneKeyboard: Keyboard? = null
    private var mExtensionVisible = false
    private var mExtension: LatinKeyboardView? = null
    private var mExtensionPopup: PopupWindow? = null
    private var mIsExtensionType = false
    private var mFirstEvent = false
    private var mDroppingEvents = false
    private var mDisableDisambiguation = false
    private var mJumpThresholdSquare = Int.MAX_VALUE
    private var mLastRowY = 0
    private var mExtensionLayoutResId = 0
    private var mExtensionKeyboard: LatinKeyboard? = null

    // Instrumentation
    private var mHandler2: Handler? = null
    private var mStringToPlay: String? = null
    private var mStringIndex = 0
    private var mDownDelivered = false
    private val mAsciiKeys = arrayOfNulls<Keyboard.Key>(256)
    private var mPlaying = false
    private var mLastX = 0
    private var mLastY = 0
    private var mPaint: Paint? = null

    init {
        val a = context.obtainStyledAttributes(
            attrs, R.styleable.LatinKeyboardBaseView, defStyle, R.style.LatinKeyboardBaseView
        )
        val inflate = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

        var previewLayout = 0
        val n = a.indexCount
        for (i in 0 until n) {
            val attr = a.getIndex(i)
            when (attr) {
                R.styleable.LatinKeyboardBaseView_keyPreviewLayout -> {
                    previewLayout = a.getResourceId(attr, 0)
                    if (previewLayout == R.layout.null_layout) previewLayout = 0
                }
                R.styleable.LatinKeyboardBaseView_keyPreviewOffset -> {
                    mPreviewOffset = a.getDimensionPixelOffset(attr, 0)
                }
                R.styleable.LatinKeyboardBaseView_keyPreviewHeight -> {
                    mPreviewHeight = a.getDimensionPixelSize(attr, 80)
                }
                R.styleable.LatinKeyboardBaseView_popupLayout -> {
                    mPopupLayout = a.getResourceId(attr, 0)
                    if (mPopupLayout == R.layout.null_layout) mPopupLayout = 0
                }
            }
        }
        a.recycle()

        val res = resources
        val clippingEnabled = Build.VERSION.SDK_INT >= 28

        if (previewLayout != 0) {
            mPreviewPopup = PopupWindow(context)
            if (!isInEditMode)
                Log.i(TAG, "new mPreviewPopup $mPreviewPopup from $this")
            mPreviewText = inflate.inflate(previewLayout, null) as TextView
            mPreviewTextSizeLarge = res.getDimension(R.dimen.key_preview_text_size_large).toInt()
            mPreviewPopup!!.contentView = mPreviewText
            mPreviewPopup!!.setBackgroundDrawable(null)
            mPreviewPopup!!.isTouchable = false
            mPreviewPopup!!.animationStyle = R.style.KeyPreviewAnimation
            mPreviewPopup!!.isClippingEnabled = clippingEnabled
        } else {
            mShowPreview = false
        }

        if (mPopupLayout != 0) {
            mMiniKeyboardParent = this
            mMiniKeyboardPopup = PopupWindow(context)
            if (!isInEditMode)
                Log.i(TAG, "new mMiniKeyboardPopup $mMiniKeyboardPopup from $this")
            mMiniKeyboardPopup!!.setBackgroundDrawable(null)
            mMiniKeyboardPopup!!.animationStyle = R.style.MiniKeyboardAnimation
            mMiniKeyboardPopup!!.isClippingEnabled = clippingEnabled
            mMiniKeyboardVisible = false
        }
    }

    fun setPhoneKeyboard(phoneKeyboard: Keyboard?) {
        mPhoneKeyboard = phoneKeyboard
    }

    fun setExtensionLayoutResId(id: Int) {
        mExtensionLayoutResId = id
    }

    override fun setPreviewEnabled(previewEnabled: Boolean) {
        if (keyboard == mPhoneKeyboard) {
            super.setPreviewEnabled(false)
        } else {
            super.setPreviewEnabled(previewEnabled)
        }
    }

    override fun setKeyboard(newKeyboard: Keyboard) {
        val oldKeyboard = keyboard
        if (oldKeyboard is LatinKeyboard) {
            oldKeyboard.keyReleased()
        }
        super.setKeyboard(newKeyboard)
        mJumpThresholdSquare = newKeyboard.minWidth / 7
        mJumpThresholdSquare *= mJumpThresholdSquare
        val numRows = newKeyboard.mRowCount
        mLastRowY = (newKeyboard.height * (numRows - 1)) / numRows
        mExtensionKeyboard = (newKeyboard as LatinKeyboard).extension
        if (mExtensionKeyboard != null && mExtension != null) {
            mExtension!!.keyboard = mExtensionKeyboard!!
        }
        setKeyboardLocal(newKeyboard)
    }

    override fun enableSlideKeyHack(): Boolean = true

    override fun onLongPress(key: Keyboard.Key): Boolean {
        PointerTracker.clearSlideKeys()

        val primaryCode = key.codes!![0]
        return when {
            primaryCode == KEYCODE_OPTIONS -> invokeOnKey(KEYCODE_OPTIONS_LONGPRESS)
            primaryCode == KEYCODE_DPAD_CENTER -> invokeOnKey(KEYCODE_COMPOSE)
            primaryCode == '0'.code && keyboard == mPhoneKeyboard -> invokeOnKey('+'.code)
            else -> super.onLongPress(key)
        }
    }

    private fun invokeOnKey(primaryCode: Int): Boolean {
        onKeyboardActionListener?.onKey(
            primaryCode, null,
            NOT_A_TOUCH_COORDINATE,
            NOT_A_TOUCH_COORDINATE
        )
        return true
    }

    private fun handleSuddenJump(me: MotionEvent): Boolean {
        val action = me.action
        val x = me.x.toInt()
        val y = me.y.toInt()
        var result = false

        if (me.pointerCount > 1) {
            mDisableDisambiguation = true
        }
        if (mDisableDisambiguation) {
            if (action == MotionEvent.ACTION_UP) mDisableDisambiguation = false
            return false
        }

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                mDroppingEvents = false
                mDisableDisambiguation = false
            }
            MotionEvent.ACTION_MOVE -> {
                val distanceSquare = (mLastX - x) * (mLastX - x) + (mLastY - y) * (mLastY - y)
                if (distanceSquare > mJumpThresholdSquare && (mLastY < mLastRowY || y < mLastRowY)) {
                    if (!mDroppingEvents) {
                        mDroppingEvents = true
                        val translated = MotionEvent.obtain(
                            me.eventTime, me.eventTime,
                            MotionEvent.ACTION_UP,
                            mLastX.toFloat(), mLastY.toFloat(), me.metaState
                        )
                        super.onTouchEvent(translated)
                        translated.recycle()
                    }
                    result = true
                } else if (mDroppingEvents) {
                    result = true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (mDroppingEvents) {
                    val translated = MotionEvent.obtain(
                        me.eventTime, me.eventTime,
                        MotionEvent.ACTION_DOWN,
                        x.toFloat(), y.toFloat(), me.metaState
                    )
                    super.onTouchEvent(translated)
                    translated.recycle()
                    mDroppingEvents = false
                }
            }
        }
        mLastX = x
        mLastY = y
        return result
    }

    override fun onTouchEvent(me: MotionEvent): Boolean {
        val keyboard = keyboard as LatinKeyboard
        if (LatinIME.sKeyboardSettings.showTouchPos || DEBUG_LINE) {
            mLastX = me.x.toInt()
            mLastY = me.y.toInt()
            invalidate()
        }

        if (!mExtensionVisible && !mIsExtensionType && handleSuddenJump(me)) return true

        if (me.action == MotionEvent.ACTION_DOWN) {
            keyboard.keyReleased()
        }

        if (me.action == MotionEvent.ACTION_UP) {
            val languageDirection = keyboard.languageChangeDirection
            if (languageDirection != 0) {
                onKeyboardActionListener?.onKey(
                    if (languageDirection == 1) KEYCODE_NEXT_LANGUAGE else KEYCODE_PREV_LANGUAGE,
                    null, mLastX, mLastY
                )
                me.action = MotionEvent.ACTION_CANCEL
                keyboard.keyReleased()
                return super.onTouchEvent(me)
            }
        }

        if (keyboard.extension == null) {
            return super.onTouchEvent(me)
        }

        if (me.y < 0 && (mExtensionVisible || me.action != MotionEvent.ACTION_UP)) {
            if (mExtensionVisible) {
                var action = me.action
                if (mFirstEvent) action = MotionEvent.ACTION_DOWN
                mFirstEvent = false
                val translated = MotionEvent.obtain(
                    me.eventTime, me.eventTime,
                    action,
                    me.x, me.y + mExtension!!.height, me.metaState
                )
                if (me.actionIndex > 0) return true
                val result = mExtension!!.onTouchEvent(translated)
                translated.recycle()
                if (me.action == MotionEvent.ACTION_UP || me.action == MotionEvent.ACTION_CANCEL) {
                    closeExtension()
                }
                return result
            } else {
                if (swipeUp()) {
                    return true
                } else if (openExtension()) {
                    val cancel = MotionEvent.obtain(
                        me.downTime, me.eventTime,
                        MotionEvent.ACTION_CANCEL, me.x - 100, me.y - 100, 0
                    )
                    super.onTouchEvent(cancel)
                    cancel.recycle()
                    if (mExtension!!.height > 0) {
                        val translated = MotionEvent.obtain(
                            me.eventTime, me.eventTime,
                            MotionEvent.ACTION_DOWN,
                            me.x, me.y + mExtension!!.height,
                            me.metaState
                        )
                        mExtension!!.onTouchEvent(translated)
                        translated.recycle()
                    } else {
                        mFirstEvent = true
                    }
                    mDisableDisambiguation = true
                }
                return true
            }
        } else if (mExtensionVisible) {
            closeExtension()
            val down = MotionEvent.obtain(
                me.eventTime, me.eventTime,
                MotionEvent.ACTION_DOWN,
                me.x, me.y, me.metaState
            )
            super.onTouchEvent(down, true)
            down.recycle()
            return super.onTouchEvent(me)
        } else {
            return super.onTouchEvent(me)
        }
    }

    private fun setExtensionType(isExtensionType: Boolean) {
        mIsExtensionType = isExtensionType
    }

    private fun openExtension(): Boolean {
        if (!isShown || popupKeyboardIsShowing()) {
            return false
        }
        PointerTracker.clearSlideKeys()
        if ((keyboard as LatinKeyboard).extension == null) return false
        makePopupWindow()
        mExtensionVisible = true
        return true
    }

    private fun makePopupWindow() {
        dismissPopupKeyboard()
        if (mExtensionPopup == null) {
            val windowLocation = IntArray(2)
            mExtensionPopup = PopupWindow(context)
            mExtensionPopup!!.setBackgroundDrawable(null)
            val li = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            mExtension = li.inflate(
                if (mExtensionLayoutResId == 0) R.layout.input_trans else mExtensionLayoutResId,
                null
            ) as LatinKeyboardView
            val kbd = mExtensionKeyboard!!
            mExtension!!.keyboard = kbd
            mExtension!!.setExtensionType(true)
            mExtension!!.setPadding(0, 0, 0, 0)
            mExtension!!.setOnKeyboardActionListener(
                ExtensionKeyboardListener(onKeyboardActionListener!!)
            )
            mExtension!!.setPopupParent(this)
            mExtension!!.setPopupOffset(0, -windowLocation[1])
            mExtensionPopup!!.contentView = mExtension
            mExtensionPopup!!.width = width
            mExtensionPopup!!.height = kbd.height
            mExtensionPopup!!.animationStyle = -1
            getLocationInWindow(windowLocation)
            mExtension!!.setPopupOffset(0, -windowLocation[1] - 30)
            mExtensionPopup!!.showAtLocation(
                this, 0, 0, -kbd.height + windowLocation[1] + this.paddingTop
            )
        } else {
            mExtension!!.visibility = VISIBLE
        }
        mExtension!!.setShiftState(shiftState)
    }

    override fun closing() {
        super.closing()
        if (mExtensionPopup != null && mExtensionPopup!!.isShowing) {
            mExtensionPopup!!.dismiss()
            mExtensionPopup = null
        }
    }

    private fun closeExtension() {
        mExtension!!.closing()
        mExtension!!.visibility = INVISIBLE
        mExtensionVisible = false
    }

    private class ExtensionKeyboardListener(private val mTarget: OnKeyboardActionListener) :
        OnKeyboardActionListener {
        override fun onKey(primaryCode: Int, keyCodes: IntArray?, x: Int, y: Int) {
            mTarget.onKey(primaryCode, keyCodes, x, y)
        }

        override fun onPress(primaryCode: Int) {
            mTarget.onPress(primaryCode)
        }

        override fun onRelease(primaryCode: Int) {
            mTarget.onRelease(primaryCode)
        }

        override fun onText(text: CharSequence?) {
            mTarget.onText(text)
        }

        override fun onCancel() {
            mTarget.onCancel()
        }

        override fun swipeDown(): Boolean = true
        override fun swipeLeft(): Boolean = true
        override fun swipeRight(): Boolean = true
        override fun swipeUp(): Boolean = true
    }

    private fun setKeyboardLocal(k: Keyboard) {
        if (DEBUG_AUTO_PLAY) {
            findKeys()
            if (mHandler2 == null) {
                mHandler2 = object : Handler() {
                    override fun handleMessage(msg: Message) {
                        removeMessages(MSG_TOUCH_DOWN)
                        removeMessages(MSG_TOUCH_UP)
                        if (!mPlaying) return

                        when (msg.what) {
                            MSG_TOUCH_DOWN -> {
                                if (mStringIndex >= (mStringToPlay?.length ?: 0)) {
                                    mPlaying = false
                                    return
                                }
                                var c = mStringToPlay!![mStringIndex]
                                while (c.code > 255 || mAsciiKeys[c.code] == null) {
                                    mStringIndex++
                                    if (mStringIndex >= mStringToPlay!!.length) {
                                        mPlaying = false
                                        return
                                    }
                                    c = mStringToPlay!![mStringIndex]
                                }
                                val x = mAsciiKeys[c.code]!!.x + 10
                                val y = mAsciiKeys[c.code]!!.y + 26
                                val me = MotionEvent.obtain(
                                    SystemClock.uptimeMillis(),
                                    SystemClock.uptimeMillis(),
                                    MotionEvent.ACTION_DOWN, x.toFloat(), y.toFloat(), 0
                                )
                                this@LatinKeyboardView.dispatchTouchEvent(me)
                                me.recycle()
                                sendEmptyMessageDelayed(MSG_TOUCH_UP, 500)
                                mDownDelivered = true
                            }
                            MSG_TOUCH_UP -> {
                                val cUp = mStringToPlay!![mStringIndex]
                                val x2 = mAsciiKeys[cUp.code]!!.x + 10
                                val y2 = mAsciiKeys[cUp.code]!!.y + 26
                                mStringIndex++

                                val me2 = MotionEvent.obtain(
                                    SystemClock.uptimeMillis(),
                                    SystemClock.uptimeMillis(),
                                    MotionEvent.ACTION_UP, x2.toFloat(), y2.toFloat(), 0
                                )
                                this@LatinKeyboardView.dispatchTouchEvent(me2)
                                me2.recycle()
                                sendEmptyMessageDelayed(MSG_TOUCH_DOWN, 500)
                                mDownDelivered = false
                            }
                        }
                    }
                }
            }
        }
    }

    private fun findKeys() {
        val keys = keyboard?.keys ?: return
        for (i in keys.indices) {
            val code = keys[i].codes!![0]
            if (code in 0..255) {
                mAsciiKeys[code] = keys[i]
            }
        }
    }

    fun startPlaying(s: String?) {
        if (DEBUG_AUTO_PLAY) {
            if (s == null) return
            mStringToPlay = s.lowercase()
            mPlaying = true
            mDownDelivered = false
            mStringIndex = 0
            mHandler2?.sendEmptyMessageDelayed(MSG_TOUCH_DOWN, 10)
        }
    }

    override fun draw(c: Canvas) {
        LatinIMEUtil.GCUtils.getInstance().reset()
        var tryGC = true
        for (i in 0 until LatinIMEUtil.GCUtils.GC_TRY_LOOP_MAX) {
            if (!tryGC) break
            try {
                super.draw(c)
                tryGC = false
            } catch (e: OutOfMemoryError) {
                tryGC = LatinIMEUtil.GCUtils.getInstance().tryGCOrWait("LatinKeyboardView", e)
            }
        }
        if (DEBUG_AUTO_PLAY) {
            if (mPlaying) {
                mHandler2?.removeMessages(MSG_TOUCH_DOWN)
                mHandler2?.removeMessages(MSG_TOUCH_UP)
                if (mDownDelivered) {
                    mHandler2?.sendEmptyMessageDelayed(MSG_TOUCH_UP, 20)
                } else {
                    mHandler2?.sendEmptyMessageDelayed(MSG_TOUCH_DOWN, 20)
                }
            }
        }
        if (LatinIME.sKeyboardSettings.showTouchPos || DEBUG_LINE) {
            if (mPaint == null) {
                mPaint = Paint()
                mPaint!!.color = 0x80FFFFFF.toInt()
                mPaint!!.isAntiAlias = false
            }
            c.drawLine(mLastX.toFloat(), 0f, mLastX.toFloat(), height.toFloat(), mPaint!!)
            c.drawLine(0f, mLastY.toFloat(), width.toFloat(), mLastY.toFloat(), mPaint!!)
        }
    }

    companion object {
        const val TAG = "HK/LatinKeyboardView"

        const val KEYCODE_OPTIONS = -100
        const val KEYCODE_OPTIONS_LONGPRESS = -101
        const val KEYCODE_VOICE = -102
        const val KEYCODE_F1 = -103
        const val KEYCODE_NEXT_LANGUAGE = -104
        const val KEYCODE_PREV_LANGUAGE = -105
        const val KEYCODE_COMPOSE = -10024

        const val KEYCODE_DPAD_UP = -19
        const val KEYCODE_DPAD_DOWN = -20
        const val KEYCODE_DPAD_LEFT = -21
        const val KEYCODE_DPAD_RIGHT = -22
        const val KEYCODE_DPAD_CENTER = -23
        const val KEYCODE_ALT_LEFT = -57
        const val KEYCODE_PAGE_UP = -92
        const val KEYCODE_PAGE_DOWN = -93
        const val KEYCODE_ESCAPE = -111
        const val KEYCODE_FORWARD_DEL = -112
        const val KEYCODE_CTRL_LEFT = -113
        const val KEYCODE_CAPS_LOCK = -115
        const val KEYCODE_SCROLL_LOCK = -116
        const val KEYCODE_META_LEFT = -117
        const val KEYCODE_FN = -119
        const val KEYCODE_SYSRQ = -120
        const val KEYCODE_BREAK = -121
        const val KEYCODE_HOME = -122
        const val KEYCODE_END = -123
        const val KEYCODE_INSERT = -124
        const val KEYCODE_FKEY_F1 = -131
        const val KEYCODE_FKEY_F2 = -132
        const val KEYCODE_FKEY_F3 = -133
        const val KEYCODE_FKEY_F4 = -134
        const val KEYCODE_FKEY_F5 = -135
        const val KEYCODE_FKEY_F6 = -136
        const val KEYCODE_FKEY_F7 = -137
        const val KEYCODE_FKEY_F8 = -138
        const val KEYCODE_FKEY_F9 = -139
        const val KEYCODE_FKEY_F10 = -140
        const val KEYCODE_FKEY_F11 = -141
        const val KEYCODE_FKEY_F12 = -142
        const val KEYCODE_NUM_LOCK = -143

        const val DEBUG_AUTO_PLAY = false
        const val DEBUG_LINE = false
        private const val MSG_TOUCH_DOWN = 1
        private const val MSG_TOUCH_UP = 2
    }
}
