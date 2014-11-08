package com.adonai.manman;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Browser;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ViewFlipper;

import com.adonai.manman.database.DbProvider;
import com.adonai.manman.entities.ManPage;
import com.adonai.manman.misc.AbstractNetworkAsyncLoader;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.net.URISyntaxException;

/**
 * Dialog fragment for showing web page with man content
 * Retrieves info from DB (if cached) or network (if not)
 *
 * @see com.adonai.manman.entities.ManPage
 */
public class ManPageDialogFragment extends DialogFragment {
    private static final String PARAM_ADDRESS = "param.address";
    private static final String PARAM_NAME = "param.name";

    private RetrieveManPageCallback manPageCallback = new RetrieveManPageCallback();
    private String mAddressUrl;
    private String mCommandName;

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

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_man_page_show, container, false);
        mFlipper = (ViewFlipper) root.findViewById(R.id.flipper);
        mContent = (WebView) root.findViewById(R.id.man_content_web);
        mContent.setWebViewClient(new ManPageChromeClient());
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
    private class RetrieveManPageCallback implements LoaderManager.LoaderCallbacks<String> {
        @NonNull
        @Override
        public Loader<String> onCreateLoader(int id, Bundle args) {
            return new AbstractNetworkAsyncLoader<String>(getActivity()) {

                @Nullable
                @Override
                public String loadInBackground() {
                    try { // query cache database for corresponding command
                        ManPage cached = DbProvider.getHelper().getManPagesDao().queryForId(mAddressUrl);
                        if(cached != null) {
                            return cached.getWebContent();
                        }
                    } catch (RuntimeException e) { // it's RuntimeExceptionDao, so catch runtime exceptions
                        Log.e("Man Man", "Database", e);
                        Utils.showToastFromAnyThread(getActivity(), R.string.database_retrieve_error);
                    }

                    try {
                        Document root = Jsoup.connect(mAddressUrl).timeout(10000).get();
                        Element man = root.select("div.man-page").first();
                        if(man != null) { // it's actually a man page
                            String webContent = man.html();

                            // save to DB for caching
                            ManPage toCache = new ManPage(mCommandName, mAddressUrl);
                            toCache.setWebContent(webContent);
                            DbProvider.getHelper().getManPagesDao().createIfNotExists(toCache);
                            LocalBroadcastManager.getInstance(getActivity()).sendBroadcast(new Intent(MainPagerActivity.DB_CHANGE_NOTIFY));

                            return webContent;
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
        public void onLoadFinished(Loader<String> loader, String data) {
            if(data != null) {
                mContent.loadDataWithBaseURL(mAddressUrl, data, "text/html", "UTF-8", null);
                mFlipper.showNext();
            } else {
                dismissAllowingStateLoss(); // can't perform transactions from onLoadFinished
            }
        }

        @Override
        public void onLoaderReset(Loader<String> loader) {
            // never used
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
