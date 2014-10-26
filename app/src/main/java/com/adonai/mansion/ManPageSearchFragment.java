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
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import com.adonai.mansion.entities.SearchResult;
import com.adonai.mansion.entities.SearchResultList;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ManPageSearchFragment#newInstance} factory method to
 * create an instance of this fragment.
 *
 */
public class ManPageSearchFragment extends Fragment {
    private final static String SEARCH_COMMAND = "search.command";
    private final static String SEARCH_ONELINER = "search.oneliner";

    private final static int SEARCH_COMMAND_LOADER = 0;
    private final static int SEARCH_ONELINER_LOADER = 1;

    private final static String SEARCH_COMMAND_PREFIX = "https://www.mankier.com/api/mans/?q=";
    private final static String SEARCH_ONELINER_PREFIX = "https://www.mankier.com/api/explain/?cols=80&q=";
    private final static String SEARCH_DESCRIPTION_PREFIX = "https://www.mankier.com/api/mans/";

    private final SearchLoaderCallback mSearchCommandCallback = new SearchLoaderCallback();
    private final SearchOnelinerLoaderCallback mSearchOnelinerCallback = new SearchOnelinerLoaderCallback();
    private final Gson mJsonConverter = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();

    private SearchView mSearchView;
    private AutoCompleteTextView mSearchEdit;
    private ListView mSearchList;

    private Map<String, String> cachedChapters;

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
        getLoaderManager().initLoader(SEARCH_COMMAND_LOADER, Bundle.EMPTY, mSearchCommandCallback);
        getLoaderManager().initLoader(SEARCH_ONELINER_LOADER, Bundle.EMPTY, mSearchOnelinerCallback);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        cachedChapters = Utils.parseStringArray(getActivity(), R.array.man_page_chapters);

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
        public Loader<SearchResultList> onCreateLoader(int id, final Bundle args) {
            return new AsyncTaskLoader<SearchResultList>(getActivity()) {
                @Override
                protected void onStartLoading() {
                    forceLoad();
                }

                @Override
                public SearchResultList loadInBackground() {
                    if(args.containsKey(SEARCH_COMMAND)) { // just searching for a command
                        final String command = args.getString(SEARCH_COMMAND);
                        try {
                            DefaultHttpClient httpClient = new DefaultHttpClient();
                            HttpUriRequest post = new HttpGet(SEARCH_COMMAND_PREFIX + command);
                            HttpResponse response = httpClient.execute(post);
                            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                                String result = EntityUtils.toString(response.getEntity());
                                return mJsonConverter.fromJson(result, SearchResultList.class);
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                            Toast.makeText(getActivity(), R.string.connection_error, Toast.LENGTH_SHORT).show();
                        }
                    }
                    return null;
                }
            };
        }

        @Override
        public void onLoadFinished(Loader<SearchResultList> loader, SearchResultList data) {
            mSearchEdit.setCompoundDrawables(null, null, null, null); // finish animation
            if(data != null) {
                ArrayAdapter<SearchResult> adapter = new ArrayAdapter<SearchResult>(getActivity(), R.layout.man_list_item, R.id.command_name_label, data.getResults()) {
                    @Override
                    public View getView(int position, View convertView, ViewGroup parent) {
                        View root = super.getView(position, convertView, parent);
                        SearchResult res = getItem(position);
                        String regex = "(\\w+)\\((.+)\\)";
                        Pattern parser = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
                        Matcher matcher = parser.matcher(res.getText());
                        if(matcher.find()) {
                            // extract needed data
                            String chapterName = cachedChapters.get(matcher.group(2));

                            TextView command = (TextView) root.findViewById(R.id.command_name_label);
                            command.setText(matcher.group(1));

                            TextView chapter = (TextView) root.findViewById(R.id.command_chapter_label);
                            chapter.setText(chapterName);

                            ImageView descriptionRequest = (ImageView) root.findViewById(R.id.request_description_button);
                            descriptionRequest.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {

                                }
                            });
                        }
                        return root;
                    }
                };
                mSearchList.setAdapter(adapter);
            }
        }

        @Override
        public void onLoaderReset(Loader<SearchResultList> loader) {

        }
    }

    private class SearchOnelinerLoaderCallback implements LoaderManager.LoaderCallbacks<String> {
        @Override
        public Loader<String> onCreateLoader(int id, final Bundle args) {
            return new AsyncTaskLoader<String>(getActivity()) {
                @Override
                public String loadInBackground() {
                    if(args.containsKey(SEARCH_ONELINER)) { // just searching for a command
                        final String script = args.getString(SEARCH_ONELINER);
                        try {
                            DefaultHttpClient httpClient = new DefaultHttpClient();
                            HttpUriRequest post = new HttpGet(SEARCH_ONELINER_PREFIX + script);
                            HttpResponse response = httpClient.execute(post);
                            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                                return EntityUtils.toString(response.getEntity());
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                            Toast.makeText(getActivity(), R.string.connection_error, Toast.LENGTH_SHORT).show();
                        }
                    }
                    return null;
                }
            };
        }

        @Override
        public void onLoadFinished(Loader<String> loader, String data) {
            mSearchEdit.setCompoundDrawables(null, null, null, null); // finish animation
        }

        @Override
        public void onLoaderReset(Loader<String> loader) {

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
                getLoaderManager().restartLoader(SEARCH_COMMAND_LOADER, Bundle.EMPTY, mSearchCommandCallback);
                return true;
            }

            if(TextUtils.equals(currentText, newText))
                return false;

            currentText = newText;
            mSearchEdit.setCompoundDrawables(null, null, getResources().getDrawable(R.drawable.rotating_wait), null);
            Bundle argsForLoader = new Bundle();
            if(!currentText.contains(" ")) { // this is a single command query, just search
                argsForLoader.putString(SEARCH_COMMAND, currentText);
                getLoaderManager().restartLoader(SEARCH_COMMAND_LOADER, argsForLoader, mSearchCommandCallback);
            } else { // this is oneliner with arguments/other commands
                argsForLoader.putString(SEARCH_ONELINER, currentText);
                getLoaderManager().restartLoader(SEARCH_ONELINER_LOADER, argsForLoader, mSearchOnelinerCallback);
            }
            return true;
        }
    }
}
