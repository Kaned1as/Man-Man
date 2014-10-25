package com.adonai.mansion;

import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.ListView;
import android.widget.SearchView;

import com.adonai.mansion.entities.SearchResultList;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;


/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ManPageSearchFragment#newInstance} factory method to
 * create an instance of this fragment.
 *
 */
public class ManPageSearchFragment extends Fragment {
    private final static String SEARCH_COMMAND = "search.command";
    private final static String SEARCH_ONELINER = "search.oneliner";

    private final static int SEARCH_LOADER = 0;

    private final static String SEARCH_COMMAND_PREFIX = "https://www.mankier.com/api/";
    private final static String SEARCH_ONELINER_PREFIX = "https://www.mankier.com/api/";

    private final SearchLoaderCallback mSearchCallback = new SearchLoaderCallback();
    private final Gson mJsonConverter = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();

    private SearchView mSearchView;
    private AutoCompleteTextView mSearchEdit;
    private ListView mSearchList;

    public static ManPageSearchFragment newInstance() {
        ManPageSearchFragment fragment = new ManPageSearchFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    public ManPageSearchFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getLoaderManager().initLoader(SEARCH_LOADER, Bundle.EMPTY, mSearchCallback);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View root = inflater.inflate(R.layout.fragment_man_page_search, container, false);
        mSearchView = (SearchView) root.findViewById(R.id.query_edit);
        mSearchView.setOnQueryTextListener(new SearchQueryTextListener());
        mSearchEdit = (AutoCompleteTextView) mSearchView.findViewById(Resources.getSystem().getIdentifier("search_src_text", "id", "android"));
        mSearchList = (ListView) root.findViewById(R.id.search_results_list);
        return root;
    }

    private class SearchLoaderCallback implements LoaderManager.LoaderCallbacks<SearchResultList> {
        @Override
        public Loader<SearchResultList> onCreateLoader(int id, Bundle args) {
            if(args.containsKey(SEARCH_COMMAND)) { // just searching for a command
                final String query = args.getString(SEARCH_COMMAND);
                return new AsyncTaskLoader<SearchResultList>(getActivity()) {
                    @Override
                    public SearchResultList loadInBackground() {
                        DefaultHttpClient httpClient = new DefaultHttpClient();
                        HttpPost post = new HttpPost();
                    }
                };
            } else if (args.containsKey(SEARCH_ONELINER)) {

            } else // show old results
        }

        @Override
        public void onLoadFinished(Loader<SearchResultList> loader, SearchResultList data) {
            mSearchEdit.setCompoundDrawables(null, null, null, null); // finish animation
        }

        @Override
        public void onLoaderReset(Loader<SearchResultList> loader) {

        }
    }

    private class SearchQueryTextListener implements SearchView.OnQueryTextListener {
        private String currentText;

        @Override
        public boolean onQueryTextSubmit(String query) {
            return false;
        }

        @Override
        public boolean onQueryTextChange(String newText) {
            if(TextUtils.isEmpty(newText)) {
                currentText = newText;
                getLoaderManager().restartLoader(SEARCH_LOADER, Bundle.EMPTY, mSearchCallback);
                return true;
            }

            if(TextUtils.equals(currentText, newText))
                return false;

            currentText = newText;
            mSearchEdit.setCompoundDrawables(null, null, getResources().getDrawable(R.drawable.rotating_wait), null);
            Bundle argsForLoader = new Bundle();
            if(!currentText.contains(" ")) { // this is a single command query, just search
                argsForLoader.putString(SEARCH_COMMAND, currentText);
                getLoaderManager().restartLoader(SEARCH_LOADER, argsForLoader, mSearchCallback);
            } else { // this is oneliner with arguments/other commands
                argsForLoader.putString(SEARCH_ONELINER, currentText);
                getLoaderManager().restartLoader(SEARCH_LOADER, argsForLoader, mSearchCallback);
            }
            return true;
        }
    }
}
