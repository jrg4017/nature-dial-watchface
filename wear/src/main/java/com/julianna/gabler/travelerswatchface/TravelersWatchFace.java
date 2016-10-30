/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.julianna.gabler.travelerswatchface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class TravelersWatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<TravelersWatchFace.Engine> mWeakReference;

        public EngineHandler(TravelersWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            TravelersWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        /**
         * backgrounds for watchface
         */
        final int [] mBackgroundIDs = {R.drawable.nz1, R.drawable.nz2};
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mTimeTextPaint;
        Paint mDateTextPaint;
        boolean mAmbient;
        Calendar mCalendar;
        Bitmap mBackground;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        float mXOffset;
        float mYOffset;
        float mDateYOffset;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(TravelersWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = TravelersWatchFace.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);
            mDateYOffset = resources.getDimensionPixelOffset(R.dimen.digital_y_date_offset);

            mBackground = Bitmap.createScaledBitmap(
                    BitmapFactory.decodeResource(getResources(),
                            mBackgroundIDs[(int)(mBackgroundIDs.length *
                                    Math.random())]), 320, 320, false);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mTimeTextPaint = new Paint();
            mTimeTextPaint = createTextPaint(resources.getColor(R.color.digital_text));

            mDateTextPaint = new Paint();
            mDateTextPaint = createTextPaint(resources.getColor(R.color.digital_text));

            mCalendar = Calendar.getInstance();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            TravelersWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            TravelersWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = TravelersWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);

            float timeTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);
            float dateTextSize = resources.getDimension(isRound
                ? R.dimen.digital_date_text_size_round : R.dimen.digital_date_text_size);

            mTimeTextPaint.setTextSize(timeTextSize);
            mDateTextPaint.setTextSize(dateTextSize);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTimeTextPaint.setAntiAlias(!inAmbientMode);
                    mDateTextPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    // TODO: Add code to handle the tap gesture.
                    Toast.makeText(getApplicationContext(), R.string.message, Toast.LENGTH_SHORT)
                            .show();
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            if (mBackground == null || mBackground.getWidth() != bounds.width()
                    || mBackground.getHeight() != bounds.height())
                mBackground = Bitmap.createScaledBitmap(mBackground, bounds.width(),
                        bounds.height(), false);
            canvas.drawBitmap(mBackground, 0, 0, null);

            String timeText = mAmbient
                    ? String.format("%d:%02d", mCalendar.get(Calendar.HOUR),
                    mCalendar.get(Calendar.MINUTE))
                    : String.format("%d:%02d:%02d", mCalendar.get(Calendar.HOUR),
                    mCalendar.get(Calendar.MINUTE), mCalendar.get(Calendar.SECOND));
            canvas.drawText(timeText, mXOffset, mYOffset, mTimeTextPaint);

            String dateText = String.format("%s, %d %d",
                    fetchDayOfWeek(), mCalendar.MONTH, mCalendar.DAY_OF_MONTH);
            canvas.drawText(dateText, mXOffset, mDateYOffset, mDateTextPaint);
        }

        private String fetchDayOfWeek() {
            int day = mCalendar.DAY_OF_WEEK;
            switch(day) {
                case 1:
                    return "Sunday";
                case 2:
                    return "Monday";
                case 3:
                    return "Tuesday";
                case 4:
                    return "Wednesday";
                case 5:
                    return "Thursday";
                case 6:
                    return "Friday";
                case 7:
                    return "Saturday";
                default:
                    return "";
            }
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }
//}
//public class TravelersWatchFace extends CanvasWatchFaceService {
//    private static final long UPDATE_INTERVAL = TimeUnit.SECONDS.toMillis(1);
//    private static final Typeface TIME_TYPEFACE =
//            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
//    private static final Typeface NORMAL_TYPEFACE =
//            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
//
//    @Override
//    public Engine onCreateEngine() {
//        return new Engine();
//    }
//
//    private class Engine extends CanvasWatchFaceService.Engine {
//
//        static final int MESSAGE_ID_UPDATE_TIME = 10000;
//
//        Paint mDigitalPaint;
//        Paint mDigitalPaintOuter;
//        boolean mMute;
//        Calendar mCalendar;
//
//        float mXOffset;
//        float mYOffset;
//
//        final int [] mBackgroundIDs = {R.drawable.nz1, R.drawable.nz2};
//        Bitmap mBG;
//
////        final Handler mUpdateTimeHandler = new Handler() {
////            @Override
////            public void handleMessage(Message message) {
////                switch (message.what) {
////                    case MESSAGE_ID_UPDATE_TIME:
////                        invalidate();
////                        if (isVisible() && !isInAmbientMode()) {
////                            long delay = UPDATE_INTERVAL
////                                    - (System.currentTimeMillis()
////                                    % UPDATE_INTERVAL);
////                            mUpdateTimeHandler.sendEmptyMessageDelayed
////                                    (MESSAGE_ID_UPDATE_TIME, delay);
////                        }
////                        break;
////                }
////            }
////        };
//
//        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
//            @Override
//            public void onReceive(Context context, Intent intent) {
//                mCalendar.setTimeZone(TimeZone.getDefault());
//                invalidate();
//            }
//        };
//
//        boolean mRegisteredTimeZoneReceiver = false;
//        boolean mLowBitAmbient = false;
//
//        /**
//         * @Override
//         * @param holder SurfaceHolder
//         */
//        @Override
//        public void onCreate(SurfaceHolder holder) {
//            super.onCreate(holder);
//
//            setWatchFaceStyle(new WatchFaceStyle.Builder
//                    (TravelersWatchFace.this)
//                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
//                    .setAmbientPeekMode(WatchFaceStyle.AMBIENT_PEEK_MODE_HIDDEN)
//                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
//                    .setShowSystemUiTime(false)
//                    .build());
//
//            mBG = Bitmap.createScaledBitmap(
//                    BitmapFactory.decodeResource(getResources(),
//                            mBackgroundIDs[(int)(mBackgroundIDs.length *
//                                    Math.random())]), 320, 320, false);
//
//            mDigitalPaint = new Paint();
//
//            mDigitalPaint.setARGB(255, 255, 255, 255);
//            mDigitalPaint.setStrokeWidth(5.f);
//            mDigitalPaint.setTextSize(36);
//            mDigitalPaint.setStyle(Paint.Style.FILL);
//            mDigitalPaint.setAntiAlias(true);
////
//            mDigitalPaintOuter = new Paint();
//            mDigitalPaintOuter.setARGB(255, 0, 0, 0);
//            mDigitalPaintOuter.setStrokeWidth(5.f);
//            mDigitalPaintOuter.setTextSize(36);
//            mDigitalPaintOuter.setStyle(Paint.Style.FILL);
//            mDigitalPaintOuter.setAntiAlias(true);
//
//            mCalendar = Calendar.getInstance();
//        }
//
////        @Override
////        public void onApplyWindowInsets(WindowInsets insets) {
////            super.onApplyWindowInsets(insets);
////
////            // Load resources that have alternate values for round watches.
////            Resources resources = TravelersWatchFace.this.getResources();
////            boolean isRound = insets.isRound();
////            mXOffset = resources.getDimension(isRound
////                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
//////            float textSize = resources.getDimension(isRound
//////                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);
////
//////            mTimeTextPaint.setTextSize(textSize);
////        }
//
//        /**
//         * creates the Paint with default values
//         * @param textColor int
//         * @param typeface Typeface
//         * @return Paint
//         */
//        private Paint createTextPaint(int textColor, Typeface typeface) {
//            Paint paint = new Paint();
//            paint.setColor(textColor);
//            paint.setTypeface(typeface);
//            paint.setAntiAlias(true);
//            paint.setStyle(Paint.Style.FILL);
//
//            return paint;
//        }
//
//        @Override
//        public void onDestroy() {
//            mUpdateTimeHandler.removeMessages
//                    (MESSAGE_ID_UPDATE_TIME);
//            super.onDestroy();
//        }
//
//        @Override
//        public void onPropertiesChanged(Bundle properties) {
//            super.onPropertiesChanged(properties);
//            mLowBitAmbient = properties.getBoolean
//                    (PROPERTY_LOW_BIT_AMBIENT, false);
//        }
//
//        @Override
//        public void onTimeTick() {
//            super.onTimeTick();
//            invalidate();
//        }
//
//        @Override
//        public void onAmbientModeChanged(boolean inAmbientMode) {
//            super.onAmbientModeChanged(inAmbientMode);
//
//            if (mLowBitAmbient) {
//                mDigitalPaint.setAntiAlias(!inAmbientMode);
//                mDigitalPaintOuter.setAntiAlias(!inAmbientMode);
//            }
//
//            invalidate();
//            updateTimer();
//        }
//
//        @Override
//        public void onDraw(Canvas canvas, Rect bounds) {
//
//            if (isInAmbientMode()) {
//                canvas.drawColor(Color.BLACK);
//            } else {
//                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mDigitalPaintOuter);
//            }
//
//            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
//            long now = System.currentTimeMillis();
//            mCalendar.setTimeInMillis(now);
//
//            // draw the background image
//            if (mBG == null || mBG.getWidth() != bounds.width()
//                    || mBG.getHeight() != bounds.height())
//                mBG = Bitmap.createScaledBitmap(mBG, bounds.width(),
//                        bounds.height(), false);
//            canvas.drawBitmap(mBG, 0, 0, null);
//
//            // draw the time
//            String ts1 = String.format("%02d:%02d:%02d",
//                    mCalendar.HOUR, mCalendar.MINUTE,
//                    mCalendar.SECOND); //PM);
//            float tw1 = mDigitalPaint.measureText(ts1);
//            float tx1 = (bounds.width() - tw1) / 2 + 50;
//            float ty1 = bounds.height() / 2 - 80;
//            canvas.drawText(ts1, tx1 - 1, ty1 - 1, mDigitalPaintOuter);
//            canvas.drawText(ts1, tx1 + 1, ty1 - 1, mDigitalPaintOuter);
//            canvas.drawText(ts1, tx1 - 1, ty1 + 1, mDigitalPaintOuter);
//            canvas.drawText(ts1, tx1 + 1, ty1 + 1, mDigitalPaintOuter);
//            canvas.drawText(ts1, tx1, ty1, mDigitalPaint);
//
//            // draw the date //TODO: fix to Sunday, Dec 12
//            String ts2 = String.format("%02d/%02d/%04d",
//                    mCalendar.MONTH, mCalendar.DAY_OF_MONTH, mCalendar.YEAR);
//            float tw2 = mDigitalPaint.measureText(ts2);
//            float tx2 = (bounds.width() - tw2) / 2 + 50;
//            float ty2 = bounds.height() / 2 - 50;
//            canvas.drawText(ts2, tx2 - 1, ty2 - 1, mDigitalPaintOuter);
//            canvas.drawText(ts2, tx2 + 1, ty2 - 1, mDigitalPaintOuter);
//            canvas.drawText(ts2, tx2 - 1, ty2 + 1, mDigitalPaintOuter);
//            canvas.drawText(ts2, tx2 + 1, ty2 + 1, mDigitalPaintOuter);
//            canvas.drawText(ts2, tx2, ty2, mDigitalPaint);
//        }
//
//        @Override
//        public void onVisibilityChanged(boolean visible) {
//            super.onVisibilityChanged(visible);
//
//            if (visible) {
//                registerReceiver();
//
//                // Update time zone in case it changed while we weren't visible.
//                mCalendar.setTimeZone(TimeZone.getDefault());
//                invalidate();
//            } else {
//                unregisterReceiver();
//            }
//
//            // Whether the timer should be running depends on whether we're visible (as well as
//            // whether we're in ambient mode), so we may need to start or stop the timer.
//            updateTimer();
//        }
//
//        private void registerReceiver() {
//            if (mRegisteredTimeZoneReceiver)
//                return;
//
//            mRegisteredTimeZoneReceiver = true;
//            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
//            TravelersWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
//        }
//
//        private void unregisterReceiver() {
//            if (!mRegisteredTimeZoneReceiver)
//                return;
//
//            mRegisteredTimeZoneReceiver = false;
//            TravelersWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
//        }
//
//        private void updateTimer() {
//            mUpdateTimeHandler.removeMessages(MESSAGE_ID_UPDATE_TIME);
//
//            if (isVisible() && !isInAmbientMode())
//                mUpdateTimeHandler.sendEmptyMessage(MESSAGE_ID_UPDATE_TIME);
//        }
//    }
}