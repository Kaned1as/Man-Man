package com.adonai.mansion;

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

import com.adonai.mansion.database.DbProvider;
import com.astuetz.PagerSlidingTabStrip;


public class MainPagerActivity extends FragmentActivity {

    private SharedPreferences mPrefs;
    private ViewPager mPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // preparations
        setContentView(R.layout.activity_main_pager);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        DbProvider.setHelper(this);

        mPager = (ViewPager) findViewById(R.id.page_holder);
        PagerSlidingTabStrip tabs = (PagerSlidingTabStrip) findViewById(R.id.tabs);
        tabs.setViewPager(mPager);
        mPager.setAdapter(new ManFragmentPagerAdapter(getSupportFragmentManager()));
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_pager, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private class ManFragmentPagerAdapter extends FragmentPagerAdapter {

        public ManFragmentPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int i)
        {
            switch (i) {
                case 0:
                    return new ManPageSearchFragment();
                case 1:
                    return new ManPageExplainFragment();
            }
            throw new IllegalArgumentException(String.format("No such fragment, index was %d", i));
        }

        @Override
        public int getCount()
        {
            return 2;
        }

        @Override
        public CharSequence getPageTitle(int position)
        {
            switch(position)
            {
                case 0: return getString(R.string.search);
                case 1: return getString(R.string.explain);
                default: return null;
            }
        }
    }

    @Override
    protected void onDestroy() {
        DbProvider.releaseHelper();
    }
}
