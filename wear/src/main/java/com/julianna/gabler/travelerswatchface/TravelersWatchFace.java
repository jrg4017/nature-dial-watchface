package com.julianna.gabler.travelerswatchface;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.complications.ComplicationData;
import android.support.wearable.complications.ComplicationHelperActivity;
import android.support.wearable.complications.ComplicationText;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class TravelersWatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
    private static final Typeface BOLD_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    //for logging
    private static final String TAG = "TravellersWatchFace";

    private static final int LEFT_DIAL_COMPLICATION = 0;
    private static final int RIGHT_DIAL_COMPLICATION = 2;
    private static final int COMPLICATION_TAP_BUFFER = 40;

    public static final int[] COMPLICATION_IDS = {
            LEFT_DIAL_COMPLICATION,
            RIGHT_DIAL_COMPLICATION
    };
    // Left, middle, and right dial supported types.
    public static final int[][] COMPLICATION_SUPPORTED_TYPES = {
            {ComplicationData.TYPE_SHORT_TEXT},
            {ComplicationData.TYPE_SHORT_TEXT}
    };

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.MINUTES.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    /**
     * @return Engine
     */
    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    /**
     * @Class EngineHandler
     * @see Handler
     */
    private static class EngineHandler extends Handler {
        private final WeakReference<TravelersWatchFace.Engine> mWeakReference;

        /**
         * constructor
         * @param reference TravelersWatchFace.Engine
         */
        public EngineHandler(TravelersWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        /**
         * @param msg Message
         */
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

    /**
     * @Class Engine
     * @see CanvasWatchFaceService.Engine
     */
    private class Engine extends CanvasWatchFaceService.Engine {
        final Handler mUpdateTimeHandler = new EngineHandler(this);

        /**
         * backgrounds for watchface
         */
        final int [] mBackgroundIDs = {
                R.drawable.bckgrd1,
                R.drawable.bckgrd2,
                R.drawable.bckgrd3,
                R.drawable.bckgrd4,
                R.drawable.bckgrd5
        };
        int mComplicationsY;
        int mWidth;
        int mHeight;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;
        /*
         * Whether the display supports burn in protection in ambient mode.
         * When true, remove the background in ambient mode.
         */
        private boolean mBurnInProtection;
        boolean mRegisteredTimeZoneReceiver = false;
        boolean mAmbient;

        Paint mBackgroundPaint;
        Paint mTimeTextPaint;
        Paint mDateTextPaint;
        Paint mComplicationPaint;

        SparseArray<ComplicationData> mActiveComplicationDataSparseArray;
        Calendar mCalendar;
        Bitmap mBackgroundBitmap;
        Bitmap mGrayBackgroundBitmap;

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };

        float mCenterX;
        float mCenterY;
        float mScale = 1;
        float mXOffset;
        float mYOffset;
        float mDateYOffset;

        /**
         * @param holder SurfaceHolder
         */
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

            //initialize and draw items on the watchface
            initializeBackground(resources);
            initializeDateTime(resources);
            initializeComplications(resources);

            mCalendar = Calendar.getInstance();
        }

        /**
         * initialize the background image and draw it on the watchface
         * @param resources Resources
         */
        private void initializeBackground(Resources resources) {
            // draw the background image of the watch
            mBackgroundBitmap = Bitmap.createScaledBitmap(
                    BitmapFactory.decodeResource(getResources(),
                            mBackgroundIDs[(int)(mBackgroundIDs.length *
                                    Math.random())]), 320, 320, false);
            //TODO see if we need to initialize different background
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));
        }

        /**
         * @param resources Resources
         */
        private void initializeComplications(Resources resources) {
            //logging for debugging
            Log.d(TAG, "initializeComplications()");

            mActiveComplicationDataSparseArray = new SparseArray<>(COMPLICATION_IDS.length);

            mComplicationPaint =
                    createTextPaint(resources.getColor(R.color.secondary_text_color), BOLD_TYPEFACE);
            mComplicationPaint.setTextSize(
                    resources.getDimension(R.dimen.complication_text_size)
            );

            setActiveComplications(COMPLICATION_IDS);
        }

        /**
         * initialize and draw the time and the date strings
         * @param resources Resources
         */
        private void initializeDateTime(Resources resources) {
            //draw the time and the date resources
            mTimeTextPaint = createTextPaint(
                resources.getColor(R.color.primary_text_color),
                NORMAL_TYPEFACE
            );
            mDateTextPaint = createTextPaint(
                resources.getColor(R.color.primary_text_color),
                NORMAL_TYPEFACE
            );
        }

        /**
         * @param textColor int
         * @return Paint
         */
        private Paint createTextPaint(int textColor, Typeface typeface) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(typeface);
            paint.setAntiAlias(true);

            return paint;
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        /**
         * adds or updates "complication" data in the array
         * @param complicationID int
         * @param complicationData ComplicationData
         */
        @Override
        public void onComplicationDataUpdate(
                int complicationID,
                ComplicationData complicationData
        ) {
            //for debugging
            Log.d(TAG, "onComplicationDataUpdate() id: " + complicationID);

            mActiveComplicationDataSparseArray.put(complicationID, complicationData);
            invalidate();
        }

        /**
         * @param visible boolean
         */
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

        /**
         * register our receiver or just return
         */
        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            TravelersWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        /**
         * unregister our receiver or return on fail
         */
        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            TravelersWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /**
         * @param insets WindowsInsets
         */
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

        /**
         * @param properties Bundle
         */
        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            //including this since developing for lower apis for wear
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        /**
         * @param inAmbientMode boolean
         */
        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTimeTextPaint.setAntiAlias(!inAmbientMode);
                    mDateTextPaint.setAntiAlias(!inAmbientMode);
                    mComplicationPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * @param holder SurfaceHolder
         * @param format int
         * @param width int
         * @param height imt
         */
        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            mWidth = width;
            mHeight = height;

            //the watch face is centered on the entire screen with these coords
            mCenterX = mWidth / 2f;
            mCenterY = mHeight / 2f;

            mScale = ((float) width) / (float) mBackgroundBitmap.getWidth();

            // the height of the complications text does not change, only need to
            // recalculate when the surface changes.
            int temp = (int) ((mHeight / 2) + (mComplicationPaint.getTextSize() / 2));
            // we want the complications to render about 75% the way down
            mComplicationsY = temp + (temp / 4);

            //let's make it gray if it is so
            if (!mBurnInProtection || !mLowBitAmbient) {
                initGrayBackgroundBitmap();
            }
        }

        /**
         * draws a gray bitmap for the background
         */
        private void initGrayBackgroundBitmap() {
            mGrayBackgroundBitmap = Bitmap.createBitmap(
                mBackgroundBitmap.getWidth(),
                mBackgroundBitmap.getHeight(),
                Bitmap.Config.ARGB_8888
            );

            Canvas canvas = new Canvas(mGrayBackgroundBitmap);
            Paint grayPaint = new Paint();

            ColorMatrix colorMatrix = new ColorMatrix();
            colorMatrix.setSaturation(0);
            ColorMatrixColorFilter filter = new ColorMatrixColorFilter(colorMatrix);

            grayPaint.setColorFilter(filter);

            canvas.drawBitmap(mBackgroundBitmap, 0, 0, grayPaint);
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap. Also allows the complications to be tapped and launch the application if there's one
         * @param tapType int
         * @param x int
         * @param y int
         * @param eventTime long
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
                    int tappedComplicationID = fetchTappedComplicationID(x, y);
                    if (tappedComplicationID != -1) {
                        onComplicationTap(tappedComplicationID);
                    }
                    break;
            }
            invalidate();
        }

        /**
         * determines whether the complication dial was tapped or outside of it (returns -1 if this)
         * @param x int
         * @param y int
         * @return int
         */
        private int fetchTappedComplicationID(int x, int y) {
            ComplicationData complicationData;
            long currentTimeMillis = System.currentTimeMillis();

            for (int i = 0; i < COMPLICATION_IDS.length; i++) {

                complicationData = mActiveComplicationDataSparseArray.get(COMPLICATION_IDS[i]);

                if (
                    complicationData != null &&
                    complicationData.isActive(currentTimeMillis) &&
                    complicationData.getType() != ComplicationData.TYPE_NOT_CONFIGURED &&
                    complicationData.getType() != ComplicationData.TYPE_EMPTY
                ) {

                    Rect complicationBoundingRect = fetchComplicationBoundingRectangle(i);

                    if (
                        complicationBoundingRect.width() > 0 &&
                        complicationBoundingRect.contains(x, y)
                    ) {
                        return COMPLICATION_IDS[i];
                    } else {
                        //for debugging
                        Log.e(TAG, "Not a recognized complication id.");
                    }
                }
            }
            return -1;
        }

        /**
         * gets the boundaries for the complication dial touched
         * @param dialPosition int
         * @return Rect
         */
        private Rect fetchComplicationBoundingRectangle(int dialPosition) {
            Rect complicationBoundingRect = new Rect(0, 0, 0, 0);

            Resources resources = TravelersWatchFace.this.getResources();
            float complicationTextSize =
                    resources.getDimension(R.dimen.complication_text_size);

            switch (COMPLICATION_IDS[dialPosition]) {
                case LEFT_DIAL_COMPLICATION:
                    complicationBoundingRect.set(
                        0,                                          // left
                        mComplicationsY - COMPLICATION_TAP_BUFFER,  // top
                        (mWidth / 2),                               // right
                        ((int) complicationTextSize + mComplicationsY + COMPLICATION_TAP_BUFFER) //bottom
                    );
                    break;
                case RIGHT_DIAL_COMPLICATION:
                    complicationBoundingRect.set(
                        (mWidth / 2),                               // left
                        mComplicationsY - COMPLICATION_TAP_BUFFER,  // top
                        mWidth,                                     // right
                        ((int) complicationTextSize + mComplicationsY + COMPLICATION_TAP_BUFFER)
                    );
                    break;
            }

            return complicationBoundingRect;
        }

        /**
         * @param complicationID int
         */
        private void onComplicationTap(int complicationID) {
            // for debugging
            Log.d(TAG, "onComplicationTap()");

            ComplicationData complicationData =
                    mActiveComplicationDataSparseArray.get(complicationID);

            if (
                complicationData != null &&
                complicationData.getTapAction() != null
            ) {
                try {
                    complicationData.getTapAction().send();
                } catch (PendingIntent.CanceledException e) {
                    //debugging
                    Log.e(TAG, "onComplicationTap() tap action error: " + e);
                }

            } else if (
                complicationData != null &&
                complicationData.getType() == ComplicationData.TYPE_NO_PERMISSION
            ) {
                askForPermissions();
            } else {
                //for debugging
                Log.d(TAG, "No PendingIntent for complication " + complicationID + ".");
            }
        }

        /**
         * asks for needed permissions if the permissions have not been granted
         */
        private void askForPermissions() {
            ComponentName componentName = new ComponentName(
                    getApplicationContext(),
                    TravelersWatchFace.class
            );

            Intent permissionRequestIntent =
                    ComplicationHelperActivity.createPermissionRequestHelperIntent(
                            getApplicationContext(),
                            componentName
                    );

            startActivity(permissionRequestIntent);
        }

        /**
         * @param canvas Canvas
         * @param bounds rect
         */
        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // set so we can draw H:MM in ambient mode or H:MM:SS in interactive mode.
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            //draw the necessary items
            drawBackground(canvas, bounds);
            drawDateTime(canvas);
            drawComplications(canvas, now);
        }

        /**
         * draw the background based on whether in ambient mode or not
         * @param canvas
         * @param bounds
         */
        private void drawBackground(Canvas canvas, Rect bounds) {
            if (mAmbient && (mLowBitAmbient || mBurnInProtection)) {
                canvas.drawColor(Color.BLACK);
            } else if (mAmbient) {
                canvas.drawBitmap(mGrayBackgroundBitmap, 0, 0, mBackgroundPaint);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
                drawBackgroundBitmap(canvas, bounds);
            }
        }

        /**
         * draws the image selected from the array for the background
         * @param canvas Canvas
         * @param bounds Rect
         */
        private void drawBackgroundBitmap(Canvas canvas, Rect bounds) {
            if (mBackgroundBitmap == null || mBackgroundBitmap.getWidth() != bounds.width()
                    || mBackgroundBitmap.getHeight() != bounds.height())
                mBackgroundBitmap = Bitmap.createScaledBitmap(mBackgroundBitmap, bounds.width(),
                        bounds.height(), false);
            canvas.drawBitmap(mBackgroundBitmap, 0, 0, null);
        }

        /**
         * draws both the date and the time strings
         * @param canvas
         */
        private void drawDateTime(Canvas canvas) {
            SimpleDateFormat sdfTime = new SimpleDateFormat("hh:mm a");
            String timeText = sdfTime.format(mCalendar.getTime());
            canvas.drawText(timeText, mXOffset, mYOffset, mTimeTextPaint);

            SimpleDateFormat sdfDate = new SimpleDateFormat(" EEEE, MMMM dd", Locale.US);
            String dateText = sdfDate.format(mCalendar.getTime());

            canvas.drawText(dateText, mXOffset, mDateYOffset, mDateTextPaint);
        }

        /**
         * @param canvas Canvas
         * @param currentTimeMillis long
         */
        private void drawComplications(Canvas canvas, long currentTimeMillis)
        {
            ComplicationData complicationData;

            for (int i = 0; i < COMPLICATION_IDS.length; i++) {

                complicationData = mActiveComplicationDataSparseArray.get(COMPLICATION_IDS[i]);

                if (
                    complicationData != null &&
                    complicationData.isActive(currentTimeMillis) &&
                    complicationData.getType() == ComplicationData.TYPE_SHORT_TEXT
                ) {
                    CharSequence complicationMessage =
                            buildComplicationMessage(complicationData, currentTimeMillis);
                    int complicationsX = fetchComplicationsX(complicationMessage, i);

                    canvas.drawText(
                        complicationMessage,
                        0,
                        complicationMessage.length(),
                        complicationsX,
                        mComplicationsY,
                        mComplicationPaint
                    );
                }
            }
        }

        /**
         * build the complication message (main and subheaders) for the dial on the watch face
         * @param complicationData ComplicationData
         * @param currentTimeMillis long
         * @return CharSequence
         */
        private CharSequence buildComplicationMessage(
                ComplicationData complicationData,
                long currentTimeMillis
        ) {
            ComplicationText mainText = complicationData.getShortText();
            ComplicationText subText = complicationData.getShortTitle();

            CharSequence complicationMessage =
                    mainText.getText(getApplicationContext(), currentTimeMillis);

            if (subText != null) {
                complicationMessage = TextUtils.concat(
                        complicationMessage,
                        " ",
                        subText.getText(getApplicationContext(), currentTimeMillis)
                );
            }

            return complicationMessage;
        }

        /**
         * fetch the complication X measurement
         * @param complicationMessage CharSequence
         * @param dialPosition int
         * @return int
         */
        private int fetchComplicationsX(CharSequence complicationMessage, int dialPosition) {

            double textWidth = mComplicationPaint.measureText(
                complicationMessage,
                0,
                complicationMessage.length()
            );

            int complicationsX;

            if (COMPLICATION_IDS[dialPosition] == LEFT_DIAL_COMPLICATION) {
                complicationsX = (int) ((mWidth / 2) - textWidth) / 2;
            } else {
                // RIGHT_DIAL_COMPLICATION calculations
                int offset = (int) ((mWidth / 2) - textWidth) / 2;
                complicationsX = (mWidth / 2) + offset;
            }

            return complicationsX;
        }

        /**
         * fetches the day of the week based on the number returned
         * @return String
         */
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
}