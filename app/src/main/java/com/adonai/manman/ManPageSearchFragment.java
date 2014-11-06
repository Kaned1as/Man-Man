package com.adonai.manman;

import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import com.adonai.manman.entities.Description;
import com.adonai.manman.entities.SearchResult;
import com.adonai.manman.entities.SearchResultList;
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
 * Fragment to show search results in a handy list view
 * All loaders for search content are implemented here
 *
 * @author Adonai
 */
public class ManPageSearchFragment extends Fragment implements AdapterView.OnItemClickListener {
    private final static String SEARCH_COMMAND = "search.command";
    private final static String SEARCH_ONELINER = "search.oneliner";

    private final static int MESSAGE_LOAD_DELAYED = 0;

    private final static String SEARCH_COMMAND_PREFIX = "https://www.mankier.com/api/mans/?q=";
    private final static String SEARCH_ONELINER_PREFIX = "https://www.mankier.com/api/explain/?cols=80&q=";
    private final static String SEARCH_DESCRIPTION_PREFIX = "https://www.mankier.com/api/mans/";

    private final SearchLoaderCallback mSearchCommandCallback = new SearchLoaderCallback();
    private final SearchOnelinerLoaderCallback mSearchOnelinerCallback = new SearchOnelinerLoaderCallback();
    private final Gson mJsonConverter = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();

    private SearchView mSearchView;
    private ImageView mSearchImage;
    private Drawable mSearchDefaultDrawable;
    private ListView mSearchList;

    private Handler mUiHandler;

    private Map<String, String> cachedChapters;

    @NonNull
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
        getLoaderManager().initLoader(MainPagerActivity.SEARCH_COMMAND_LOADER, Bundle.EMPTY, mSearchCommandCallback);
        getLoaderManager().initLoader(MainPagerActivity.SEARCH_ONELINER_LOADER, Bundle.EMPTY, mSearchOnelinerCallback);
    }

    @NonNull
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        cachedChapters = Utils.parseStringArray(getActivity(), R.array.man_page_chapters);

        // Inflate the layout for this fragment
        View root = inflater.inflate(R.layout.fragment_man_page_search, container, false);
        mSearchView = (SearchView) root.findViewById(R.id.query_edit);
        mSearchView.setOnQueryTextListener(new SearchQueryTextListener());
        mSearchImage = (ImageView) mSearchView.findViewById(Resources.getSystem().getIdentifier("search_mag_icon", "id", "android"));
        mSearchDefaultDrawable = mSearchImage.getDrawable();
        mSearchList = (ListView) root.findViewById(R.id.search_results_list);
        mSearchList.setOnItemClickListener(this);

        mUiHandler = new Handler();
        return root;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        SearchResult sr = (SearchResult) parent.getItemAtPosition(position);
        Pair<String, String> nameChapter = getNameChapterFromResult(sr);
        if(nameChapter != null) {
            ManPageDialogFragment.newInstance(nameChapter.first, sr.getUrl()).show(getFragmentManager(), "manPage");
        }
    }

    private class SearchLoaderCallback implements LoaderManager.LoaderCallbacks<SearchResultList> {

        @Override
        public Loader<SearchResultList> onCreateLoader(int id, @NonNull final Bundle args) {
            return new AsyncTaskLoader<SearchResultList>(getActivity()) {
                @Override
                protected void onStartLoading() {
                    forceLoad();
                }

                @Override
                protected void onStopLoading() {
                    super.onStopLoading();
                    cancelLoad();
                }

                @Override
                public SearchResultList loadInBackground() {
                    if(args.containsKey(SEARCH_COMMAND)) { // just searching for a command
                        final String command = args.getString(SEARCH_COMMAND);
                        args.remove(SEARCH_COMMAND); // load only once
                        try {
                            DefaultHttpClient httpClient = new DefaultHttpClient();
                            HttpUriRequest post = new HttpGet(SEARCH_COMMAND_PREFIX + command);
                            HttpResponse response = httpClient.execute(post);
                            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                                String result = EntityUtils.toString(response.getEntity());
                                return mJsonConverter.fromJson(result, SearchResultList.class);
                            }
                        } catch (IOException e) {
                            Log.e("Man Man", "Network", e);
                            // can't show a toast from a thread without looper
                            Utils.showToastFromAnyThread(getActivity(), R.string.connection_error);
                        }
                    }
                    return null;
                }
            };
        }

        @Override
        public void onLoadFinished(Loader<SearchResultList> loader, SearchResultList data) {
            mSearchImage.setImageDrawable(mSearchDefaultDrawable); // finish animation
            if(data != null && data.getResults() != null) {
                ArrayAdapter<SearchResult> adapter = new SearchResultArrayAdapter(data);
                mSearchList.setAdapter(adapter);
            }
        }

        @Override
        public void onLoaderReset(Loader<SearchResultList> loader) {
            // no need to clear data
        }

    }

    private class SearchOnelinerLoaderCallback implements LoaderManager.LoaderCallbacks<String> {
        @Override
        public Loader<String> onCreateLoader(int id, @NonNull final Bundle args) {
            return new AsyncTaskLoader<String>(getActivity()) {
                @Override
                public String loadInBackground() {
                    if(args.containsKey(SEARCH_ONELINER)) { // just searching for a command
                        final String script = args.getString(SEARCH_ONELINER);
                        args.remove(SEARCH_ONELINER); // load only once
                        try {
                            DefaultHttpClient httpClient = new DefaultHttpClient();
                            HttpUriRequest post = new HttpGet(SEARCH_ONELINER_PREFIX + script);
                            HttpResponse response = httpClient.execute(post);
                            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                                return EntityUtils.toString(response.getEntity());
                            }
                        } catch (IOException e) {
                            Log.e("Man Man", "Network", e);
                            // can't show a toast from a thread without looper
                            Utils.showToastFromAnyThread(getActivity(), R.string.connection_error);
                        }
                    }
                    return null;
                }
            };
        }

        @Override
        public void onLoadFinished(Loader<String> loader, String data) {
            mSearchImage.setImageDrawable(mSearchDefaultDrawable); // finish animation
        }

        @Override
        public void onLoaderReset(Loader<String> loader) {

        }
    }

    private class SearchQueryTextListener implements SearchView.OnQueryTextListener {
        private String currentText;

        @Override
        public boolean onQueryTextSubmit(String query) {
            currentText = query;
            fireLoader(true);
            return true;
        }

        @Override
        public boolean onQueryTextChange(String newText) {
            if(TextUtils.isEmpty(newText)) {
                currentText = newText;
                getLoaderManager().restartLoader(MainPagerActivity.SEARCH_COMMAND_LOADER, Bundle.EMPTY, mSearchCommandCallback);
                return true;
            }

            if(TextUtils.equals(currentText, newText))
                return false;

            currentText = newText;
            fireLoader(false);
            return true;
        }

        // make a delay for not spamming requests to server so fast
        private void fireLoader(boolean immediate) {
            final Bundle argsForLoader = new Bundle();
            mUiHandler.removeMessages(MESSAGE_LOAD_DELAYED);
            mUiHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mSearchImage.setImageResource(R.drawable.rotating_wait);
                    if(!currentText.contains(" ")) { // this is a single command query, just search
                        argsForLoader.putString(SEARCH_COMMAND, currentText);
                        getLoaderManager().restartLoader(MainPagerActivity.SEARCH_COMMAND_LOADER, argsForLoader, mSearchCommandCallback);
                    } else { // this is oneliner with arguments/other commands
                        argsForLoader.putString(SEARCH_ONELINER, currentText);
                        getLoaderManager().restartLoader(MainPagerActivity.SEARCH_ONELINER_LOADER, argsForLoader, mSearchOnelinerCallback);
                    }
                }
            }, immediate ? 0 : 800);

        }
    }

    /**
     * Search result text comes in form of &lt;command-name&gt;(&lt;chapter_index&gt;)
     * so we should extract name and index explicitly
     * @param sr search result to be parsed
     * @return pair of command-name and chapter index or null if nothing matches
     *         (actually doesn't happen)
     */
    @Nullable
    private Pair<String, String> getNameChapterFromResult(SearchResult sr) {
        String regex = "(\\w+)\\((.+)\\)";
        Pattern parser = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        final Matcher matcher = parser.matcher(sr.getText());
        if(matcher.find()) {
            return Pair.create(matcher.group(1), matcher.group(2));
        }
        return null;
    }

    private class SearchResultArrayAdapter extends ArrayAdapter<SearchResult> {
        public SearchResultArrayAdapter(SearchResultList data) {
            super(getActivity(), R.layout.man_list_item, R.id.command_name_label, data.getResults());
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final View root = super.getView(position, convertView, parent);
            final Pair<String, String> nameAndIndex = getNameChapterFromResult(getItem(position));
            if(nameAndIndex != null) {
                // extract needed data
                String chapterName = cachedChapters.get(nameAndIndex.second);

                TextView command = (TextView) root.findViewById(R.id.command_name_label);
                command.setText(nameAndIndex.first);
                TextView chapter = (TextView) root.findViewById(R.id.command_chapter_label);
                chapter.setText(chapterName);
                final WebView description = (WebView) root.findViewById(R.id.description_text_web);
                description.setBackgroundColor(0);
                description.setVisibility(View.GONE);
                final ImageView descriptionRequest = (ImageView) root.findViewById(R.id.request_description_button);
                descriptionRequest.setImageResource(android.R.drawable.ic_menu_help);
                descriptionRequest.setVisibility(View.VISIBLE);

                // download a description on question mark click
                descriptionRequest.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ImageView imageView = (ImageView) v;
                        imageView.setImageResource(R.drawable.rotating_wait);
                        final String descriptionCommand = nameAndIndex.first + "." + nameAndIndex.second;
                        // run desc download in another thread...
                        Thread thr = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    DefaultHttpClient httpClient = new DefaultHttpClient();
                                    HttpUriRequest post = new HttpGet(SEARCH_DESCRIPTION_PREFIX + descriptionCommand);
                                    HttpResponse response = httpClient.execute(post);
                                    if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                                        String result = EntityUtils.toString(response.getEntity());
                                        final Description descAnswer = mJsonConverter.fromJson(result, Description.class);
                                        // load description back into listview
                                        getActivity().runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                description.loadData(descAnswer.getHtmlDescription(), "text/html", "UTF-8");
                                                description.setVisibility(View.VISIBLE);
                                                descriptionRequest.setVisibility(View.GONE);
                                            }
                                        });
                                    }
                                } catch (IOException e) {
                                    Log.e("Man Man", "Network", e);
                                    // can't show a toast from a thread without looper
                                    // show error and change drawable back to normal
                                    getActivity().runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            Toast.makeText(getActivity(), R.string.connection_error, Toast.LENGTH_SHORT).show();
                                            descriptionRequest.setImageResource(android.R.drawable.ic_menu_help);
                                        }
                                    });
                                }
                            }
                        });
                        thr.start();
                    }
                });
            }
            return root;
        }
    }
}
