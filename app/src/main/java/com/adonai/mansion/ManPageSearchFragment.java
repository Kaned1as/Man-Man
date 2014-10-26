package com.adonai.mansion;

import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.text.TextUtils;
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

import com.adonai.mansion.entities.Description;
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
public class ManPageSearchFragment extends Fragment implements AdapterView.OnItemClickListener {
    private final static String SEARCH_COMMAND = "search.command";
    private final static String SEARCH_ONELINER = "search.oneliner";

    private final static int SEARCH_COMMAND_LOADER = 0;
    private final static int SEARCH_ONELINER_LOADER = 1;

    private final static int TAG_DESCRIPTION = 0;

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
        mSearchImage = (ImageView) mSearchView.findViewById(Resources.getSystem().getIdentifier("search_mag_icon", "id", "android"));
        mSearchDefaultDrawable = mSearchImage.getDrawable();
        mSearchList = (ListView) root.findViewById(R.id.search_results_list);
        mSearchList.setOnItemClickListener(this);

        mUiHandler = new Handler();
        return root;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
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
            fireLoader();
            return true;
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
            fireLoader();
            return true;
        }

        // make a delay for not spamming requests to server so fast
        private void fireLoader() {
            final Bundle argsForLoader = new Bundle();
            mUiHandler.removeMessages(MESSAGE_LOAD_DELAYED);
            mUiHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mSearchImage.setImageResource(R.drawable.rotating_wait);
                    if(!currentText.contains(" ")) { // this is a single command query, just search
                        argsForLoader.putString(SEARCH_COMMAND, currentText);
                        getLoaderManager().restartLoader(SEARCH_COMMAND_LOADER, argsForLoader, mSearchCommandCallback);
                    } else { // this is oneliner with arguments/other commands
                        argsForLoader.putString(SEARCH_ONELINER, currentText);
                        getLoaderManager().restartLoader(SEARCH_ONELINER_LOADER, argsForLoader, mSearchOnelinerCallback);
                    }
                }
            }, 500);

        }
    }

    private class SearchResultArrayAdapter extends ArrayAdapter<SearchResult> {
        public SearchResultArrayAdapter(SearchResultList data) {
            super(getActivity(), R.layout.man_list_item, R.id.command_name_label, data.getResults());
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final View root = super.getView(position, convertView, parent);
            SearchResult res = getItem(position);
            String regex = "(\\w+)\\((.+)\\)";
            Pattern parser = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            final Matcher matcher = parser.matcher(res.getText());
            if(matcher.find()) {
                // extract needed data
                String chapterName = cachedChapters.get(matcher.group(2));

                TextView command = (TextView) root.findViewById(R.id.command_name_label);
                command.setText(matcher.group(1));
                TextView chapter = (TextView) root.findViewById(R.id.command_chapter_label);
                chapter.setText(chapterName);
                final WebView description = (WebView) root.findViewById(R.id.description_text_web);
                description.setBackgroundColor(0);
                description.setVisibility(View.GONE);
                final ImageView descriptionRequest = (ImageView) root.findViewById(R.id.request_description_button);
                descriptionRequest.setImageResource(android.R.drawable.ic_menu_help);
                descriptionRequest.setVisibility(View.VISIBLE);

                descriptionRequest.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ImageView imageView = (ImageView) v;
                        imageView.setImageResource(R.drawable.rotating_wait);
                        final String descriptionCommand = matcher.group(1) + "." + matcher.group(2);
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
                                    e.printStackTrace();
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
