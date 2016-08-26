package com.adonai.manman;


import android.app.Activity;
import android.support.test.filters.LargeTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.adonai.manman.adapters.ChapterContentsArrayAdapter;
import com.adonai.manman.adapters.ChapterContentsCursorAdapter;
import com.adonai.manman.entities.ManPage;
import com.adonai.manman.entities.ManSectionItem;
import com.adonai.manman.entities.SearchResult;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static android.support.test.espresso.Espresso.onData;
import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.action.ViewActions.closeSoftKeyboard;
import static android.support.test.espresso.action.ViewActions.pressBack;
import static android.support.test.espresso.action.ViewActions.typeText;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.RootMatchers.isPlatformPopup;
import static android.support.test.espresso.matcher.ViewMatchers.hasDescendant;
import static android.support.test.espresso.matcher.ViewMatchers.hasSibling;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withParent;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class ApplicationTest {

    @Rule
    public ActivityTestRule mActivityRule = new ActivityTestRule<>(MainPagerActivity.class);

    @Before
    public void unlockScreen() {
        final Activity activity = mActivityRule.getActivity();
        Runnable wakeUpDevice = new Runnable() {
            public void run() {
                activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        };
        activity.runOnUiThread(wakeUpDevice);
    }

    @Test
    public void checkSearchForCommand() throws InterruptedException {
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

        // this will be tar - File Formats
        onData(instanceOf(SearchResult.class))
                .inAdapterView(withId(R.id.search_results_list))
                .atPosition(1)
                .perform(click(), click());

        // wait for page to load
        await().atMost(5, TimeUnit.SECONDS).until(new WebViewVisible(act));

        // check one of link names
        onView(withId(R.id.link_list))
                .check(matches(hasDescendant(withText("atime"))));
    }

    @Test
    public void checkChapter() throws InterruptedException {
        final Activity act = mActivityRule.getActivity();

        onView(withText(R.string.contents)).perform(click());
        onView(withText("General commands")).perform(click());

        // wait for page to load
        await().atMost(5, TimeUnit.SECONDS).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                FrameLayout fl = (FrameLayout) act.findViewById(R.id.chapter_fragment_frame); // first view
                ListView lv = (ListView) fl.getChildAt(0); // can't find it with findViewById for unknown reason
                return lv != null &&
                        (lv.getAdapter() instanceof ChapterContentsArrayAdapter || lv.getAdapter() instanceof ChapterContentsCursorAdapter);
            }
        });

        onData(allOf(instanceOf(ManSectionItem.class), new ManSectionItemMatcher("at")))
                .inAdapterView(withParent(withId(R.id.chapter_fragment_frame)))
                .perform(click());

        // check one of link names
        onView(withId(R.id.link_list))
                .check(matches(hasDescendant(withText("Synopsis"))));
    }

    @Test
    public void checkLocalArchive() throws InterruptedException {
        final Activity act = mActivityRule.getActivity();

        // first we need to clear archive
        final File localArchive = new File(act.getCacheDir(), "manpages.zip");
        localArchive.delete();

        onView(withText(R.string.local_storage)).perform(click());
        onView(withText(R.string.download_archive)).perform(click());
        onView(withText(android.R.string.ok)).perform(click());

        // wait for local archive to load
        await().atMost(2, TimeUnit.MINUTES).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                final ListView searchList = (ListView) act.findViewById(R.id.local_storage_page_list);
                return searchList.getChildCount() > 0;
            }
        });

        onView(withId(R.id.local_search_edit)).perform(typeText("tar"), closeSoftKeyboard());
        onView(allOf(withText("local:/man1"), hasSibling(withText("tar"))))
                .check(matches(isDisplayed()))
                .perform(click());

        // wait for page to load
        await().atMost(5, TimeUnit.SECONDS).until(new WebViewVisible(act));

        // check one of link names
        onView(withId(R.id.link_list))
                .check(matches(allOf(
                        hasDescendant(withText("SYNOPSIS")),
                        hasDescendant(withText("--blocking-factor")),
                        hasDescendant(withText("--index-file")),
                        hasDescendant(withText("--pax-option")),
                        hasDescendant(withText("--to-command")),
                        hasDescendant(withText("--uncompress")),
                        hasDescendant(withText("-G"))
                                )));
    }

    @Test
    public void checkCachedPage() throws InterruptedException {
        final Activity act = mActivityRule.getActivity();
        final ListView searchList = (ListView) act.findViewById(R.id.search_results_list);

        onView(withId(R.id.query_edit)).perform(typeText("grep"));

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
                .check(matches(hasDescendant(withText(containsString("grep")))))
                .perform(click());

        // wait for page to load
        await().atMost(5, TimeUnit.SECONDS).until(new WebViewVisible(act));

        onView(withId(R.id.man_content_web))
                .check(matches(isDisplayed()))
                .perform(pressBack())
                .perform(pressBack());

        onView(withText(R.string.cached)).perform(click());

        onData(allOf(instanceOf(ManPage.class), new ManPageMatcher("grep")))
                .inAdapterView(withId(R.id.cached_pages_list))
                .check(matches(hasDescendant(withText("https://www.mankier.com/1/grep"))));

        // click on popup overflow button
        onView(allOf(withId(R.id.popup_menu), hasSibling(withText("grep")), hasSibling(withText("https://www.mankier.com/1/grep"))))
                .perform(click());

        // click on "delete from cache"
        onView(withText(act.getResources().getString(R.string.delete)))
                .inRoot(isPlatformPopup())
                .perform(click());

        // check page actually got deleted
        onView(withId(R.id.cached_pages_list))
                .check(matches(not(hasDescendant(withText("https://www.mankier.com/1/grep")))));
    }

    private static class ManSectionItemMatcher extends BaseMatcher<ManSectionItem> {

        private final String name;

        private ManSectionItemMatcher(String name) {
            this.name = name;
        }

        @Override
        public boolean matches(Object item) {
            return item instanceof ManSectionItem && ((ManSectionItem) item).getName().equals(name);
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("is a ").appendText(name);
        }
    }

    private static class ManPageMatcher extends BaseMatcher<ManPage> {

        private final String name;

        private ManPageMatcher(String name) {
            this.name = name;
        }

        @Override
        public boolean matches(Object item) {
            return item instanceof ManPage && ((ManPage) item).getName().equals(name);
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("is a ").appendText(name);
        }
    }

    private static class WebViewVisible implements Callable<Boolean> {
        private final Activity act;

        public WebViewVisible(Activity act) {
            this.act = act;
        }

        @Override
        public Boolean call() throws Exception {
            return act.findViewById(R.id.man_content_web) != null;
        }
    }
}