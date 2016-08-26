package com.adonai.manman;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.adonai.manman.database.DbProvider;
import com.adonai.manman.preferences.PreferencesActivity;
import com.astuetz.PagerSlidingTabStrip;

/**
 * Main activity where everything takes place
 *
 * @author Oleg Chernovskiy
 */
@SuppressWarnings("FieldCanBeLocal")
public class MainPagerActivity extends AppCompatActivity {

    public static final String FOLDER_LIST_KEY = "folder.list";

    static final int SEARCH_COMMAND_LOADER          = 0;
    static final int SEARCH_ONELINER_LOADER         = 1;
    static final int MAN_PAGE_RETRIEVER_LOADER      = 2;
    static final int CONTENTS_RETRIEVER_LOADER      = 3;
    static final int CACHE_RETRIEVER_LOADER         = 4;
    static final int LOCAL_PACKAGE_LOADER           = 5;

    public static final String DB_CHANGE_NOTIFY = "database.updated";
    public static final String LOCAL_CHANGE_NOTIFY = "locals.updated";
    public static final String BACK_BUTTON_NOTIFY = "back.button.pressed";

    private ViewPager mPager;
    private Toolbar mActionBar;
    private DonateHelper mDonateHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // should set theme prior to instantiating compat actionbar etc.
        Utils.setupTheme(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_pager);

        mActionBar = (Toolbar) findViewById(R.id.app_toolbar);
        setSupportActionBar(mActionBar);

        mPager = (ViewPager) findViewById(R.id.page_holder);
        mPager.setAdapter(new ManFragmentPagerAdapter(getSupportFragmentManager()));
        PagerSlidingTabStrip tabs = (PagerSlidingTabStrip) findViewById(R.id.tabs);
        tabs.setViewPager(mPager);

        // setting up vending
        mDonateHelper = new DonateHelper(this);

        // applying default tab
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final String index = prefs.getString("app.default.tab", "0");
        mPager.setCurrentItem(Integer.valueOf(index));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.global_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.about_menu_item:
                showAbout();
                return true;
            case R.id.donate_menu_item:
                mDonateHelper.purchaseGift();
                return true;
            case R.id.settings_menu_item:
                startActivity(new Intent(this, PreferencesActivity.class));
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private class ManFragmentPagerAdapter extends FragmentPagerAdapter {
        private Fragment oldPrimary;

        public ManFragmentPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int i) {
            switch (i) {
                case 0:
                    return ManPageSearchFragment.newInstance();
                case 1:
                    return ManChaptersFragment.newInstance();
                case 2:
                    return ManPageCacheFragment.newInstance();
                case 3:
                    return ManLocalArchiveFragment.newInstance();
            }
            throw new IllegalArgumentException(String.format("No such fragment, index was %d", i));
        }

        @Override
        public int getCount() {
            return 4;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch(position) {
                case 0: return getString(R.string.search);
                case 1: return getString(R.string.contents);
                case 2: return getString(R.string.cached);
                case 3: return getString(R.string.local_storage);
                default: return null;
            }
        }

        /**
         * A way to notify fragments when they become visible to user in this pager
         */
        @Override
        public void setPrimaryItem(ViewGroup container, int position, @NonNull Object object) {
            Fragment newPrimary = (Fragment) object;
            if(oldPrimary != newPrimary) {
                if(oldPrimary != null) {
                    oldPrimary.setUserVisibleHint(false);
                    oldPrimary.onPause();
                }

                newPrimary.setUserVisibleHint(true);
                newPrimary.onResume();

                oldPrimary = newPrimary;
            }
            super.setPrimaryItem(container, position, object);
        }
    }

    /**
     * Shows about dialog, with description, author and stuff
     */
    @SuppressLint("InflateParams")
    private void showAbout() {
        // Inflate the about message contents
        View messageView = getLayoutInflater().inflate(R.layout.about_dialog, null, false);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setIcon(R.drawable.ic_launcher_notification_icon);
        builder.setTitle(R.string.app_name);
        builder.setView(messageView);
        builder.create();
        builder.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Pass on the activity result to the helper for handling
        if (!mDonateHelper.handleActivityResult(requestCode, resultCode, data)) {
            // not handled, so handle it ourselves (here's where you'd
            // perform any handling of activity results not related to in-app
            // billing...
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onBackPressed() {
        if(!LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(BACK_BUTTON_NOTIFY))) {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mDonateHelper.handleActivityDestroy();
    }
}
