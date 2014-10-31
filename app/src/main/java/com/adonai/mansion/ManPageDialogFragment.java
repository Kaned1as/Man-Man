package com.adonai.mansion;

import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebView;
import android.widget.ViewFlipper;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;

/**
 * Created by adonai on 27.10.14.
 */
public class ManPageDialogFragment extends DialogFragment {
    private static final String PARAM_ADDRESS = "param.address";

    private RetrieveManPageCallback manPageCallback = new RetrieveManPageCallback();
    private String mOriginalAddress;

    private ViewFlipper mFlipper;
    private WebView mContent;

    @NonNull
    public static ManPageDialogFragment newInstance(String address) {
        ManPageDialogFragment fragment = new ManPageDialogFragment();
        Bundle args = new Bundle();
        args.putString(PARAM_ADDRESS, address);
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
            mOriginalAddress = getArguments().getString(PARAM_ADDRESS);
            getLoaderManager().initLoader(MainPagerActivity.MAN_PAGE_RETRIEVER_LOADER, null, manPageCallback);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_man_page_show, container, false);
        mFlipper = (ViewFlipper) root.findViewById(R.id.flipper);
        mContent = (WebView) root.findViewById(R.id.man_content_web);
        return root;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dia = super.onCreateDialog(savedInstanceState);
        dia.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dia.getWindow().setWindowAnimations(android.R.style.Animation_Translucent);
        return dia;
    }

    @Override
    public void onStart() {
        super.onStart();
        getLoaderManager().restartLoader(MainPagerActivity.MAN_PAGE_RETRIEVER_LOADER, null, manPageCallback);
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
            return new AsyncTaskLoader<String>(getActivity()) {
                @Override
                protected void onStartLoading() {
                    forceLoad();
                }

                @Nullable
                @Override
                public String loadInBackground() {
                    if(mOriginalAddress != null) { // just searching for a command
                        try {
                            Document root = Jsoup.connect(mOriginalAddress).timeout(10000).get();
                            Element man = root.select("div.man-page").first();
                            if(man != null)
                                return man.html();
                        } catch (IOException e) {
                            e.printStackTrace();
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
            if(data != null) {
                mContent.loadDataWithBaseURL(mOriginalAddress, data, "text/html", "UTF-8", null);
                mFlipper.showNext();
            } else {
                dismiss();
            }
        }

        @Override
        public void onLoaderReset(Loader<String> loader) {
            // never used
        }
    }
}
