package com.adonai.manman.preferences

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import com.adonai.manman.MainPagerActivity
import com.adonai.manman.R
import com.adonai.manman.database.DbProvider
import com.adonai.manman.misc.resolveAttr
import java.io.File

/**
 * Fragment for showing and managing global preferences
 *
 * @author Kanedias
 */
class GlobalPrefsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.global_prefs)

        findPreference<Preference>("clear.cache")!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            val builder = AlertDialog.Builder(activity)
            builder.setTitle(R.string.confirm_action).setMessage(R.string.clear_cache_question)
                    .setNegativeButton(android.R.string.no, null).setPositiveButton(android.R.string.yes) { _, _ ->
                        DbProvider.helper.clearAllTables()
                        LocalBroadcastManager.getInstance(requireActivity()).sendBroadcast(Intent(MainPagerActivity.DB_CHANGE_NOTIFY))
                    }
                    .create()
                    .show()
            true
        }

        val localArchive = File(requireActivity().cacheDir, "manpages.zip")
        findPreference<Preference>("delete.local.archive")!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            if (localArchive.delete()) {
                LocalBroadcastManager.getInstance(requireActivity()).sendBroadcast(Intent(MainPagerActivity.LOCAL_CHANGE_NOTIFY))
                Toast.makeText(activity, R.string.deleted, Toast.LENGTH_SHORT).show()
            }
            true
        }

        val color = requireContext().resolveAttr(R.attr.colorPrimary)
        tintIcons(preferenceScreen, color)
    }

    private fun tintIcons(preference: Preference, color: Int) {
        if (preference is PreferenceGroup) {
            for (i in 0 until preference.preferenceCount) {
                tintIcons(preference.getPreference(i), color)
            }
        } else {
            preference.icon.setTint(color)
        }
    }
}