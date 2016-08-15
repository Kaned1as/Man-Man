package com.adonai.manman;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Browser;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.content.LocalBroadcastManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import com.adonai.manman.database.DbProvider;
import com.adonai.manman.entities.ManPage;
import com.adonai.manman.misc.AbstractNetworkAsyncLoader;
import com.adonai.manman.parser.Man2Html;
import com.adonai.manman.views.PassiveSlidingPane;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Dialog fragment for showing web page with man content
 * Retrieves info from DB (if cached) or network (if not)
 *
 * @see com.adonai.manman.entities.ManPage
 * @author Oleg Chernovskiy
 */
public class ManPageDialogFragment extends Fragment {
    private static final String USER_LEARNED_SLIDER = "user.learned.slider";

    private static final String PARAM_ADDRESS = "param.address";
    private static final String PARAM_NAME = "param.name";

    private RetrieveManPageCallback manPageCallback = new RetrieveManPageCallback();
    private File mLocalArchive;
    private String mAddressUrl;
    private String mCommandName;

    private LinearLayout mLinkContainer;
    private PassiveSlidingPane mSlider;
    private ViewFlipper mFlipper;
    private WebView mContent;

    private LinearLayout mSearchContainer;
    private EditText mSearchEdit;
    private ImageView mCloseSearchBar;
    private ImageView mFindNext;
    private ImageView mFindPrevious;

    @NonNull
    public static ManPageDialogFragment newInstance(@NonNull String commandName, @NonNull String address) {
        ManPageDialogFragment fragment = new ManPageDialogFragment();
        Bundle args = new Bundle();
        args.putString(PARAM_ADDRESS, address);
        args.putString(PARAM_NAME, commandName);
        fragment.setArguments(args);
        return fragment;
    }

    public ManPageDialogFragment() {
        // mandatory empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(getArguments() != null) {
            mAddressUrl = getArguments().getString(PARAM_ADDRESS);
            mCommandName = getArguments().getString(PARAM_NAME);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        setHasOptionsMenu(true);

        mLocalArchive = new File(getActivity().getCacheDir(), "manpages.zip");

        View root = inflater.inflate(R.layout.fragment_man_page_show, container, false);
        mLinkContainer = (LinearLayout) root.findViewById(R.id.link_list);
        mFlipper = (ViewFlipper) root.findViewById(R.id.flipper);
        mContent = (WebView) root.findViewById(R.id.man_content_web);
        mContent.setWebViewClient(new ManPageChromeClient());
        mContent.getSettings().setJavaScriptEnabled(true);
        mSlider = (PassiveSlidingPane) root.findViewById(R.id.sliding_pane);
        mSlider.setTrackedView(mContent);

        // search-specific
        mSearchContainer = (LinearLayout) root.findViewById(R.id.search_bar_layout);
        mSearchEdit = (EditText) mSearchContainer.findViewById(R.id.search_edit);
        mCloseSearchBar = (ImageView) mSearchContainer.findViewById(R.id.close_search_bar);
        mFindNext = (ImageView) mSearchContainer.findViewById(R.id.find_next_occurrence);
        mFindPrevious = (ImageView) mSearchContainer.findViewById(R.id.find_previous_occurrence);

        mCloseSearchBar.setOnClickListener(new SearchBarCloser());
        mSearchEdit.addTextChangedListener(new SearchExecutor());
        mFindNext.setOnClickListener(new SearchFurtherExecutor(true));
        mFindPrevious.setOnClickListener(new SearchFurtherExecutor(false));

        // Lollipop blocks mixed content but we should load CSS from filesystem
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mContent.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        getLoaderManager().initLoader(MainPagerActivity.MAN_PAGE_RETRIEVER_LOADER, null, manPageCallback);
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        // hide keyboard on fragment show, window token is hopefully present at this moment
        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(mContent.getWindowToken(), 0);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.man_page_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.show_search_bar:
                mSearchContainer.setVisibility(View.VISIBLE);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Class for creating a loader that performs async loading of man page from www.mankier.com
     * On finish passes data to web content and makes it active
     * On fail dismisses parent dialog
     *
     */
    private class RetrieveManPageCallback implements LoaderManager.LoaderCallbacks<ManPage> {
        @NonNull
        @Override
        public Loader<ManPage> onCreateLoader(int id, Bundle args) {
            return new AbstractNetworkAsyncLoader<ManPage>(getActivity()) {

                @Nullable
                @Override
                public ManPage loadInBackground() {
                    // handle special case when it's a local file
                    if(mAddressUrl.startsWith("/")) { // TODO: rewrite with URI
                        try {
                            File input = new File(mAddressUrl);
                            String charset = Utils.detectEncodingOfArchive(input);
                            FileInputStream fis = new FileInputStream(input);
                            GZIPInputStream gis = new GZIPInputStream(fis);
                            BufferedReader br = charset == null ? new BufferedReader(new InputStreamReader(gis)) : new BufferedReader(new InputStreamReader(gis, charset));
                            Man2Html parser = new Man2Html(br);
                            Document parsed = parser.getDoc();
                            ManPage result = new ManPage(input.getName(), "file://" + mAddressUrl);
                            result.setLinks(getLinks(parsed.select("div.man-page").first()));
                            result.setWebContent(parsed.html());
                            br.close(); // closes all the IS hierarchy
                            return result;
                        } catch (FileNotFoundException e) {
                            Log.e(Utils.MM_TAG, "File with man page was not found in local folder", e);
                            Toast.makeText(getActivity(), R.string.file_not_found, Toast.LENGTH_SHORT).show();
                        } catch (IOException e) {
                            Log.e(Utils.MM_TAG, "Exception while loading man page file from local folder", e);
                            Toast.makeText(getActivity(), R.string.wrong_file_format, Toast.LENGTH_SHORT).show();
                        }
                        return null; // no further querying
                    }

                    if(mAddressUrl.startsWith("local:")) { // local man archive
                        try {
                            ZipFile zip = new ZipFile(mLocalArchive);
                            ZipEntry zEntry = zip.getEntry(mAddressUrl.substring(7));
                            InputStream is =  zip.getInputStream(zEntry);
                            // can't use java's standard GZIPInputStream around zip IS because of inflating issue
                            GzipCompressorInputStream gis = new GzipCompressorInputStream(is); // manpage files are .gz
                            BufferedReader br = new BufferedReader(new InputStreamReader(gis));
                            Man2Html parser = new Man2Html(br);
                            Document parsed = parser.getDoc();
                            ManPage result = new ManPage(zEntry.getName(), mAddressUrl);
                            result.setLinks(getLinks(parsed.select("div.man-page").first()));
                            result.setWebContent(parsed.html());
                            br.close(); // closes all the IS hierarchy
                            return result;
                        } catch (IOException e) {
                            Log.e(Utils.MM_TAG, "Error while loading man page from local archive", e);
                            Toast.makeText(getActivity(), R.string.wrong_file_format, Toast.LENGTH_SHORT).show();
                        }
                    }

                    try { // query cache database for corresponding command
                        ManPage cached = DbProvider.getHelper().getManPagesDao().queryForId(mAddressUrl);
                        if(cached != null) {
                            return cached;
                        }
                    } catch (RuntimeException e) { // it's RuntimeExceptionDao, so catch runtime exceptions
                        Log.e(Utils.MM_TAG, "Exception while querying the DB", e);
                        Utils.showToastFromAnyThread(getActivity(), R.string.database_retrieve_error);
                    }

                    try {
                        OkHttpClient client = new OkHttpClient();
                        Request request = new Request.Builder().url(mAddressUrl).build();
                        Response response = client.newCall(request).execute();
                        if (response.isSuccessful()) {
                            String result = response.body().string();
                            Document root = Jsoup.parse(result, mAddressUrl);
                            Element man = root.select("div.man-page").first();
                            if (man == null) { // not actually a man page
                                return null;
                            }

                            String webContent = man.html();
                            TreeSet<String> linkContainer = getLinks(man);

                            // save to DB for caching
                            ManPage toCache = new ManPage(mCommandName, mAddressUrl);
                            toCache.setLinks(linkContainer);
                            toCache.setWebContent(webContent);
                            DbProvider.getHelper().getManPagesDao().createIfNotExists(toCache);
                            LocalBroadcastManager.getInstance(getActivity()).sendBroadcast(new Intent(MainPagerActivity.DB_CHANGE_NOTIFY));

                            return toCache;
                        }
                    } catch (IOException e) {
                        Log.e(Utils.MM_TAG, "Exception while saving cached page to DB", e);
                        // can't show a toast from a thread without looper
                        Utils.showToastFromAnyThread(getActivity(), R.string.connection_error);
                    }
                    return null;
                }

                // retrieve link set from manpage
                @NonNull
                private TreeSet<String> getLinks(Element man) {
                    Elements links = man.select("a[href*=#]");
                    TreeSet<String> linkContainer = new TreeSet<>();
                    for (Element link : links) {
                        if (!TextUtils.isEmpty(link.text()) && link.attr("href").contains("#" + link.text())) { // it's like <a href="http://ex.com/#a">-x</a>
                            linkContainer.add(link.text());
                        }
                    }
                    return linkContainer;
                }
            };
        }

        @Override
        public void onLoadFinished(Loader<ManPage> loader, ManPage data) {
            if(data != null) {
                mContent.loadDataWithBaseURL(mAddressUrl, Utils.getWebWithCss(getActivity(), data.getUrl(), data.getWebContent()), "text/html", "UTF-8", null);
                mContent.setBackgroundColor(Utils.getThemedValue(getActivity(), R.attr.fill_color)); // prevent flickering
                fillLinkPane(data.getLinks());
                mFlipper.showNext();
                shakeSlider();
            } else {
                mContent.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        getFragmentManager().popBackStack(); // can't perform transactions from onLoadFinished
                    }
                }, 0);
            }
        }

        @Override
        public void onLoaderReset(Loader<ManPage> loader) {
            // never used
        }
    }

    private void shakeSlider() {
        if(mLinkContainer.getChildCount() == 0) // nothing to show in the links pane
            return;

        SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        if(mPrefs.contains(USER_LEARNED_SLIDER))
            return;

        mSlider.postDelayed(new Runnable() {
            @Override
            public void run() {
                mSlider.openPane();
            }
        }, 1000);
        mSlider.postDelayed(new Runnable() {
            @Override
            public void run() {
                mSlider.closePane();
            }
        }, 2000);
        mPrefs.edit().putBoolean(USER_LEARNED_SLIDER, true).apply();
    }

    private void fillLinkPane(Set<String> links) {
        mLinkContainer.removeAllViews();

        if(links == null || links.isEmpty())
            return;

        for (final String link : links) {
            // hack  for https://code.google.com/p/android/issues/detail?id=36660 - place inside of FrameLayout
            View root = LayoutInflater.from(getActivity()).inflate(R.layout.link_text_item, mLinkContainer, false);
            TextView linkLabel = (TextView) root.findViewById(R.id.link_text);
            linkLabel.setText(link);
            root.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mContent.loadUrl("javascript:(function(){" +
                            "l=document.querySelector('a[href$=\"#" + link + "\"]');" +
                            "e=document.createEvent('HTMLEvents');" +
                            "e.initEvent('click',true,true);" +
                            "l.dispatchEvent(e);" +
                            "})()");
                }
            });
            mLinkContainer.addView(root);
        }
    }

    /**
     * Class to load URLs inside of already active webview
     * Calls original browser intent for the URLs it can't handle
     */
    private class ManPageChromeClient extends WebViewClient {

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (url.matches("https://www\\.mankier\\.com/.+/.+")) { // it's an address of the command
                mFlipper.showPrevious();
                mAddressUrl = url;
                mCommandName = url.substring(url.lastIndexOf('/') + 1);
                getLoaderManager().getLoader(MainPagerActivity.MAN_PAGE_RETRIEVER_LOADER).onContentChanged();
                return true;
            }
            return shouldOverrideUrlLoadingOld(view, url);
        }

        /**
         * Copied from WebViewContentsClientAdapter (internal android class)
         * to handle URLs in old way if it's not a man page
         */
        public boolean shouldOverrideUrlLoadingOld(WebView view, String url) {
            Intent intent;
            // Perform generic parsing of the URI to turn it into an Intent.
            try {
                intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
            } catch (URISyntaxException ex) {
                Log.w("WebViewCallback", "Bad URI " + url + ": " + ex.getMessage());
                return false;
            }
            // Sanitize the Intent, ensuring web pages can not bypass browser
            // security (only access to BROWSABLE activities).
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            intent.setComponent(null);

            // Pass the package name as application ID so that the intent from the
            // same application can be opened in the same tab.
            intent.putExtra(Browser.EXTRA_APPLICATION_ID, view.getContext().getPackageName());
            try {
                view.getContext().startActivity(intent);
            } catch (ActivityNotFoundException ex) {
                Log.w("WebViewCallback", "No application can handle " + url);
                return false;
            }
            return true;
        }
    }

    /**
     * Closes the search bar
     */
    private class SearchBarCloser implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            mSearchContainer.setVisibility(View.GONE);
            mContent.clearMatches();
        }
    }

    /**
     * Finds next occurrence depending on direction
     */
    private class SearchFurtherExecutor implements View.OnClickListener {

        private boolean goDown;

        public SearchFurtherExecutor(boolean goDown) {
            this.goDown = goDown;
        }

        @Override
        public void onClick(View v) {
            mContent.findNext(goDown);
        }
    }

    /**
     * Executes search on string change
     */
    private class SearchExecutor implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {

        }

        @SuppressWarnings("deprecation")
        @Override
        public void afterTextChanged(Editable s) {
            // Lollipop blocks mixed content but we should load CSS from filesystem
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                mContent.findAllAsync(s.toString()); // API 16
            } else {
                mContent.findAll(s.toString());
            }
        }
    }
}
