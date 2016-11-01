package com.julianna.gabler.travelerswatchface;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.wearable.complications.ComplicationHelperActivity;
import android.support.wearable.complications.ComplicationProviderInfo;
import android.support.wearable.complications.ProviderChooserIntent;
import android.support.wearable.view.WearableListView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/**
 * @Class WatchFaceConfigActivity
 * @see Activity
 * @see WearableListView.ClickListener
 */
public class WatchFaceConfigActivity extends Activity implements WearableListView.ClickListener {

    private static final String TAG = "WatchFaceConfig";

    private static final int PROVIDER_CHOOSER_REQUEST_CODE = 1;

    private WearableListView mWearableConfigListView;
    private ConfigurationAdapter mAdapter;

    /**
     * @param savedInstanceState Bundle
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_watch_face_config);

        mAdapter = new ConfigurationAdapter(getApplicationContext(), fetchComplicationItems());

        mWearableConfigListView = (WearableListView) findViewById(R.id.wearable_list);
        mWearableConfigListView.setAdapter(mAdapter);
        mWearableConfigListView.setClickListener(this);
    }

    /**
     * @param requestCode int
     * @param resultCode int
     * @param data Intent
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PROVIDER_CHOOSER_REQUEST_CODE
                && resultCode == RESULT_OK) {

            //retrieves information for selected complication dial's source
            ComplicationProviderInfo complicationProviderInfo =
                    data.getParcelableExtra(ProviderChooserIntent.EXTRA_PROVIDER_INFO);

            //debugging
            Log.d(TAG, "Selected Provider: " + complicationProviderInfo);

            finish();
        }
    }

    /**
     * @param viewHolder WearableListView.ViewHolder
     */
    @Override
    public void onClick(WearableListView.ViewHolder viewHolder) {
        //for debugging
        Log.d(TAG, "onClick()");

        Integer tag = (Integer) viewHolder.itemView.getTag();
        ComplicationItem complicationItem = mAdapter.getItem(tag);

        //allows the user to choose the provider for the complication
        startActivityForResult(
                ProviderChooserIntent.createProviderChooserIntent(
                    complicationItem.watchFace,
                    complicationItem.complicationID,
                    complicationItem.supportedTypes
                ),
            PROVIDER_CHOOSER_REQUEST_CODE
        );
    }

    /**
     * retrieves the list of complications we want to print on the screen
     * @return List<ComplicationItem>
     */
    private List<ComplicationItem> fetchComplicationItems() {
        ComponentName watchFace = new ComponentName(
                getApplicationContext(), TravelersWatchFace.class
        );

        int[] complicationIDs = TravelersWatchFace.COMPLICATION_IDS;

        TypedArray icons = getResources().obtainTypedArray(R.array.complication_icons);

        List<ComplicationItem> items = new ArrayList<>();
        for (int i = 0; i < complicationIDs.length; i++) {
            items.add(new ComplicationItem(
                    watchFace,
                    complicationIDs[i],
                    TravelersWatchFace.COMPLICATION_SUPPORTED_TYPES[i],
                    icons.getDrawable(i),
                    ""
                )
            );
        }

        return items;
    }

    /**
     * honestly I don't really care about the function but I need to override it
     */
    @Override
    public void onTopEmptyRegionClick() {}

    /**
     * @Class ComplicationItem
     */
    private final class ComplicationItem {
        ComponentName watchFace;

        int complicationID;
        int[] supportedTypes;

        Drawable icon;
        String title;

        /**
         * constructor
         * @param watchFace ComponentName
         * @param complicationID int
         * @param supportedTypes int[]
         * @param icon Drawable
         * @param title String
         */
        public ComplicationItem(
                ComponentName watchFace,
                int complicationID,
                int[] supportedTypes,
                Drawable icon,
                String title
        ) {
            this.watchFace = watchFace;
            this.complicationID = complicationID;
            this.supportedTypes = supportedTypes;
            this.icon = icon;
            this.title = title;
        }
    }

    /**
     * @Class ConfigurationAdapter
     * @see WearableListView.Adapter
     */
    private static class ConfigurationAdapter extends WearableListView.Adapter {
        private Context mContext;
        private final LayoutInflater mInflater;
        private List<ComplicationItem> mItems;

        /**
         * constructor
         * @param context Context
         * @param items <code>List<ComplicationItem></code>
         */
        public ConfigurationAdapter(Context context, List<ComplicationItem> items) {
            this.mContext = context;
            this.mInflater = LayoutInflater.from(mContext);
            this.mItems = items;
        }

        /**
         * @Class ItemViewHolder
         * @see WearableListView.ViewHolder
         */
        public static class ItemViewHolder extends WearableListView.ViewHolder {
            private ImageView iconImageView;

            /**
             * constructor
             * @param itemView
             */
            public ItemViewHolder(View itemView) {
                super(itemView);
                this.iconImageView = (ImageView) itemView.findViewById(R.id.icon);
            }
        }

        /**
         * @param parent ViewGroup
         * @param viewType int
         * @return WearableListView.ViewHolder
         */
        @Override
        public WearableListView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

            // Inflate custom layout for list items.
            return new ItemViewHolder(
                    mInflater.inflate(R.layout.activity_watch_face_config_list_item, null));
        }

        /**
         * @param holder WearableListView.ViewHolder
         * @param position
         */
        @Override
        public void onBindViewHolder(WearableListView.ViewHolder holder, int position) {

            ItemViewHolder itemHolder = (ItemViewHolder) holder;

            ImageView imageView = itemHolder.iconImageView;
            imageView.setImageDrawable(mItems.get(position).icon);

            holder.itemView.setTag(position);
        }

        /**
         * @return <code>List<ComplicationItem></code>
         */
        @Override
        public int getItemCount() { return mItems.size(); }

        /**
         * @param position
         * @return <code>List<ComplicationItem></code>
         */
        public ComplicationItem getItem(int position) {
            return mItems.get(position);
        }
    }
}
