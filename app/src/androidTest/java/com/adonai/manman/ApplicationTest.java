package com.adonai.manman;


import android.app.Activity;
import android.support.test.filters.LargeTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.View;
import android.webkit.WebView;
import android.widget.ListView;
import android.widget.TextView;

import com.adonai.manman.entities.SearchResult;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static android.support.test.espresso.Espresso.onData;
import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.*;
import static android.support.test.espresso.assertion.ViewAssertions.*;
import static android.support.test.espresso.matcher.RootMatchers.*;
import static android.support.test.espresso.matcher.ViewMatchers.*;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.*;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class ApplicationTest {

    @Rule
    public ActivityTestRule mActivityRule = new ActivityTestRule<>(MainPagerActivity.class);

    @Test
    public void searchForCommand() throws InterruptedException {
        final Activity act = mActivityRule.getActivity();
        final ListView searchList = (ListView) act.findViewById(R.id.search_results_list);

        onView(withId(R.id.query_edit)).perform(typeText("tar"));

        // wait until list is loaded with results
        await().atMost(5, TimeUnit.SECONDS).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return searchList.getChildCount() > 0;
            }
        });

        // click on first result
        onData(instanceOf(SearchResult.class))
                .inAdapterView(withId(R.id.search_results_list))
                .atPosition(0)
                .check(matches(hasDescendant(withText(containsString("tar")))));

        // click on "load description"
        onView(is(searchList.getChildAt(0).findViewById(R.id.popup_menu))).perform(click());
        onView(withText(act.getResources().getString(R.string.load_description)))
                .inRoot(isPlatformPopup())
                .perform(click());

        // wait for it to load
        await().atMost(5, TimeUnit.SECONDS).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                TextView desc = (TextView) act.findViewById(R.id.description_text_web); // first view
                return desc.getVisibility() == View.VISIBLE;
            }
        });

        onView(withId(R.id.search_results_list))
                .perform(swipeUp());

        // this will be tar - File Formats
        onData(instanceOf(SearchResult.class))
                .inAdapterView(withId(R.id.search_results_list))
                .atPosition(1)
                .perform(click(), click());

        // wait for page to load
        await().atMost(5, TimeUnit.SECONDS).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return act.findViewById(R.id.man_content_web) != null;
            }
        });

        // check one of link names
        onView(withId(R.id.link_list))
                .check(matches(hasDescendant(withText("atime"))));
    }

    @Test
    public void checkLocalChapter() throws InterruptedException {
        final Activity act = mActivityRule.getActivity();

        onView(withText(R.string.contents)).perform(click());
        onView(withText("General commands")).perform(click());
        Thread.sleep(5000);
    }
}