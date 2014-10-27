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

/**
 * Main activity where everything takes place
 *
 * @author Adonai
 */
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
        mPager.setAdapter(new ManFragmentPagerAdapter(getSupportFragmentManager()));
        PagerSlidingTabStrip tabs = (PagerSlidingTabStrip) findViewById(R.id.tabs);
        tabs.setViewPager(mPager);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_pager, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
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
                    return ManPageSearchFragment.newInstance();
                case 1:
                    return ManPageContentsFragment.newInstance();
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
                case 1: return getString(R.string.contents);
                default: return null;
            }
        }
    }

    @Override
    protected void onDestroy() {
        DbProvider.releaseHelper();
    }
}
