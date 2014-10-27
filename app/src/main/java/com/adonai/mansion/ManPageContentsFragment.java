package com.adonai.mansion;



import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;



/**
 * Fragment to show table of contents and navigate into it
 * Note: works slower that just search!
 *
 * @author Adonai
 */
public class ManPageContentsFragment extends Fragment {

    public static ManPageContentsFragment newInstance() {
        ManPageContentsFragment fragment = new ManPageContentsFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }
    public ManPageContentsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_man_page_show, container, false);
    }


}
