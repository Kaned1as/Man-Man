package com.adonai.manman

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.delay

/**
 * Main activity where everything takes place
 *
 * @author Kanedias
 */
class MainPagerActivity : ThemedActivity() {

    private lateinit var mPager: ViewPager2
    private lateinit var mActionBar: Toolbar
    private lateinit var mDonateHelper: DonateHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        // should set theme prior to instantiating compat actionbar etc.
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_pager)

        mActionBar = findViewById<View>(R.id.app_toolbar) as Toolbar
        setSupportActionBar(mActionBar)

        mPager = findViewById<View>(R.id.page_holder) as ViewPager2
        mPager.adapter = ManFragmentPagerAdapter()
        val tabs = findViewById<View>(R.id.tabs) as TabLayout
        TabLayoutMediator(tabs, mPager)
            { tab, position ->
                tab.text = when (position) {
                    0 -> getString(R.string.search)
                    1 -> getString(R.string.contents)
                    2 -> getString(R.string.cached)
                    3 -> getString(R.string.local_storage)
                    else -> null
                }
            }.attach()

        // setting up vending
        mDonateHelper = DonateHelper(this)

        // applying default tab
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val index = prefs.getString("app.default.tab", "0")!!
        mPager.currentItem = Integer.valueOf(index)

        handleIntent(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.global_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.about_menu_item -> {
                showAbout()
                return true
            }
            R.id.donate_menu_item -> {
                mDonateHelper.donate()
                return true
            }
            R.id.settings_menu_item -> {
                startActivity(Intent(this, PreferencesActivity::class.java))
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * Handle the passed intent. This is invoked whenever we need to actually react to the intent that was
     * passed to this activity, this can be just activity start from the app manager, click on a link or
     * on a notification belonging to this app
     * @param cause the passed intent. It will not be modified within this function.
     */
    private fun handleIntent(cause: Intent?) {
        if (cause == null)
            return

        if (cause.type != "text/plain")
            return

        val text = when (cause.action) {
            Intent.ACTION_SEND -> cause.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_PROCESS_TEXT -> cause.getStringExtra(Intent.EXTRA_PROCESS_TEXT)
            else -> null
        } ?: return

        mPager.currentItem = 0
        lifecycleScope.launchWhenResumed {
            // wait till fragments are instantiated
            while(supportFragmentManager.fragments.isEmpty())
                delay(100)

            val currFragment = supportFragmentManager.findFragmentByTag("f${mPager.currentItem}")
            currFragment?.view?.let { root ->
                val search = root.findViewById<SearchView>(R.id.search_edit)
                search?.setQuery(text, false)
            }
        }
    }

    override fun onNewIntent(received: Intent) {
        super.onNewIntent(received)
        handleIntent(received)
    }


    private inner class ManFragmentPagerAdapter : FragmentStateAdapter(this) {

        override fun getItemCount() = 4

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> ManPageSearchFragment()
                1 -> ManChaptersFragment()
                2 -> ManCacheFragment()
                else /* 3 */ -> ManLocalArchiveFragment()
            }
        }
    }

    /**
     * Shows about dialog, with description, author and stuff
     */
    @SuppressLint("InflateParams")
    private fun showAbout() {
        // Inflate the about message contents
        val messageView = layoutInflater.inflate(R.layout.about_dialog, null, false)
        val builder = AlertDialog.Builder(this)
        builder.setIcon(R.drawable.ic_launcher_notification_icon)
        builder.setTitle(R.string.app_name)
        builder.setView(messageView)
        builder.create()
        builder.show()
    }

    override fun onBackPressed() {
        if (!LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(BACK_BUTTON_NOTIFY))) {
            super.onBackPressed()
        }
    }

    companion object {
        const val FOLDER_LIST_KEY = "folder.list"
        const val DB_CHANGE_NOTIFY = "database.updated"
        const val LOCAL_CHANGE_NOTIFY = "locals.updated"
        const val BACK_BUTTON_NOTIFY = "back.button.pressed"
    }
}