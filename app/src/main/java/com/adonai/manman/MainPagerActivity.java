package com.adonai.manman;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.adonai.manman.database.DbProvider;
import com.android.vending.util.IabHelper;
import com.android.vending.util.IabResult;
import com.android.vending.util.Inventory;
import com.android.vending.util.Purchase;
import com.astuetz.PagerSlidingTabStrip;

import java.util.Date;

/**
 * Main activity where everything takes place
 *
 * @author Adonai
 */
public class MainPagerActivity extends FragmentActivity {

    final static int SEARCH_COMMAND_LOADER = 0;
    final static int SEARCH_ONELINER_LOADER = 1;
    final static int MAN_PAGE_RETRIEVER_LOADER = 2;
    final static int CONTENTS_RETRIEVER_LOADER = 3;
    final static int CACHE_RETRIEVER_LOADER = 4;

    final static String DB_CHANGE_NOTIFY = "database.updated";

    // helpers for donations (from android vending tutorial)
    private static final String SKU_DONATE = "small";
    private IabHelper mHelper;
    private boolean mCanBuy = false;


    private SharedPreferences mPrefs;
    private ViewPager mPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_pager);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        mPager = (ViewPager) findViewById(R.id.page_holder);
        mPager.setAdapter(new ManFragmentPagerAdapter(getSupportFragmentManager()));
        PagerSlidingTabStrip tabs = (PagerSlidingTabStrip) findViewById(R.id.tabs);
        tabs.setViewPager(mPager);

        // setting up vending
        String base64EncodedPublicKey = "";
        mHelper = new IabHelper(this, base64EncodedPublicKey);
        mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
            @Override
            public void onIabSetupFinished(IabResult result) {
                if (result.isSuccess())
                    mCanBuy = true;
            }
        });

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
        }

        return super.onOptionsItemSelected(item);
    }

    private class ManFragmentPagerAdapter extends FragmentPagerAdapter {

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
            }
            throw new IllegalArgumentException(String.format("No such fragment, index was %d", i));
        }

        @Override
        public int getCount() {
            return 3;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch(position) {
                case 0: return getString(R.string.search);
                case 1: return getString(R.string.contents);
                case 2: return getString(R.string.cached);
                default: return null;
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        DbProvider.setHelper(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        DbProvider.releaseHelper();
    }

    private void showAbout() {
        // Inflate the about message contents
        View messageView = getLayoutInflater().inflate(R.layout.about_dialog, null, false);

        // When linking text, force to always use default color. This works
        // around a pressed color state bug.
        TextView textView = (TextView) messageView.findViewById(R.id.about_credits);
        int defaultColor = textView.getTextColors().getDefaultColor();
        textView.setTextColor(defaultColor);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setIcon(R.drawable.ic_launcher_small);
        builder.setTitle(R.string.app_name);
        builder.setView(messageView);
        builder.create();
        builder.show();
    }

    private void purchaseGift() {
        if (mCanBuy) {
            mHelper.launchPurchaseFlow(MainPagerActivity.this, SKU_DONATE, 6666, new IabHelper.OnIabPurchaseFinishedListener() {
                @Override
                public void onIabPurchaseFinished(IabResult result, Purchase info) {
                    if (result.isSuccess()) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(MainPagerActivity.this);
                        builder.setTitle(R.string.completed).setMessage(R.string.thanks_for_pledge);
                        builder.setPositiveButton(android.R.string.ok, null);
                        builder.create().show();
                    }

                    mHelper.queryInventoryAsync(false, new IabHelper.QueryInventoryFinishedListener() {
                        @Override
                        public void onQueryInventoryFinished(IabResult result, Inventory inv) {
                            if (result.isSuccess()) {
                                if (inv.getPurchase(SKU_DONATE) != null)
                                    mHelper.consumeAsync(inv.getPurchase(SKU_DONATE), null);
                            }
                        }
                    });
                }
            }, "ManManDonation " + new Date());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Pass on the activity result to the helper for handling
        if (!mHelper.handleActivityResult(requestCode, resultCode, data)) {
            // not handled, so handle it ourselves (here's where you'd
            // perform any handling of activity results not related to in-app
            // billing...
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCanBuy)
            mHelper.dispose();
    }
}
