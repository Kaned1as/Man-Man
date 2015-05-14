package com.adonai.manman;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Browser;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.SlidingPaneLayout;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import com.adonai.manman.database.DbProvider;
import com.adonai.manman.entities.ManPage;
import com.adonai.manman.misc.AbstractNetworkAsyncLoader;
import com.adonai.manman.parser.Man2Html;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.GZIPInputStream;

/**
 * Dialog fragment for showing web page with man content
 * Retrieves info from DB (if cached) or network (if not)
 *
 * @see com.adonai.manman.entities.ManPage
 * @author Adonai
 */
public class ManPageDialogFragment extends DialogFragment {
    private static final String USER_LEARNED_SLIDER = "user.learned.slider";

    private static final String PARAM_ADDRESS = "param.address";
    private static final String PARAM_NAME = "param.name";

    private RetrieveManPageCallback manPageCallback = new RetrieveManPageCallback();
    private String mAddressUrl;
    private String mCommandName;

    private LinearLayout mLinkContainer;
    private SlidingPaneLayout mSlider;
    private ViewFlipper mFlipper;
    private WebView mContent;

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
        setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Translucent);
        if(getArguments() != null) {
            mAddressUrl = getArguments().getString(PARAM_ADDRESS);
            mCommandName = getArguments().getString(PARAM_NAME);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_man_page_show, container, false);
        mLinkContainer = (LinearLayout) root.findViewById(R.id.link_list);
        mSlider = (SlidingPaneLayout) root.findViewById(R.id.sliding_pane);
        mFlipper = (ViewFlipper) root.findViewById(R.id.flipper);
        mContent = (WebView) root.findViewById(R.id.man_content_web);
        mContent.setWebViewClient(new ManPageChromeClient());
        mContent.getSettings().setJavaScriptEnabled(true);

        // Lollipop blocks mixed content but we should load CSS from filesystem
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mContent.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        getLoaderManager().initLoader(MainPagerActivity.MAN_PAGE_RETRIEVER_LOADER, null, manPageCallback);
        return root;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dia = super.onCreateDialog(savedInstanceState);
        dia.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dia.getWindow().setWindowAnimations(R.style.ManPageFadeAnimation);
        return dia;
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
                            ManPage result = new ManPage(input.getName(), "file://" + mAddressUrl);
                            result.setWebContent(parser.getHtml()); // we're not using it in DB!
                            // no side pane with links for now
                            br.close(); // closes all the IS hierarchy
                            return result;
                        } catch (FileNotFoundException e) {
                            Log.e("Man Man", "Filesystem", e);
                            Toast.makeText(getActivity(), R.string.file_not_found, Toast.LENGTH_SHORT).show();
                        } catch (IOException e) {
                            Log.e("Man Man", "Filesystem", e);
                            Toast.makeText(getActivity(), R.string.wrong_file_format, Toast.LENGTH_SHORT).show();
                        }
                        return null; // no further querying
                    }

                    try { // query cache database for corresponding command
                        ManPage cached = DbProvider.getHelper().getManPagesDao().queryForId(mAddressUrl);
                        if(cached != null) {
                            return cached;
                        }
                    } catch (RuntimeException e) { // it's RuntimeExceptionDao, so catch runtime exceptions
                        Log.e("Man Man", "Database", e);
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
                            if (man != null) { // it's actually a man page
                                String webContent = man.html();
                                // retrieve links
                                Elements links = man.select("a[href*=#]");
                                TreeSet<String> linkContainer = new TreeSet<>();
                                for (Element link : links) {
                                    if (!TextUtils.isEmpty(link.text()) && link.attr("href").contains("#" + link.text())) { // it's like <a href="http://ex.com/#a">-x</a>
                                        linkContainer.add(link.text());
                                    }
                                }

                                // save to DB for caching
                                ManPage toCache = new ManPage(mCommandName, mAddressUrl);
                                toCache.setLinks(linkContainer);
                                toCache.setWebContent(webContent);
                                DbProvider.getHelper().getManPagesDao().createIfNotExists(toCache);
                                LocalBroadcastManager.getInstance(getActivity()).sendBroadcast(new Intent(MainPagerActivity.DB_CHANGE_NOTIFY));

                                return toCache;
                            }
                        }
                    } catch (IOException e) {
                        Log.e("Man Man", "Database", e);
                        // can't show a toast from a thread without looper
                        Utils.showToastFromAnyThread(getActivity(), R.string.connection_error);
                    }
                    return null;
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
                dismissAllowingStateLoss(); // can't perform transactions from onLoadFinished
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
}
