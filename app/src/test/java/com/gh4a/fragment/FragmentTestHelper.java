package com.gh4a.fragment;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import org.robolectric.Robolectric;

/**
 * Shared harness for fragment/ tests (F1 batch).
 *
 * Attaching a fragment normally triggers its initial load, which would
 * subscribe a Retrofit Single and attempt a real network request. To keep
 * every test offline, each test file defines a tiny no-load subclass that
 * overrides the load entry points before attaching:
 * <ul>
 *   <li>{@link PagedDataBaseFragment} subclasses: override
 *       {@code shouldDoInitialLoad} to return false.</li>
 *   <li>{@link ListDataBaseFragment} subclasses: override
 *       {@code onGetInitialData} → null, {@code onCreateDataObservable}
 *       → null, {@code onCreateDataSingle} → {@code Single.never()}.</li>
 * </ul>
 * Tests that exercise loading itself call the load methods directly on an
 * attached fragment instead (the returned Single is never subscribed).
 *
 * NOTE: do NOT use Mockito spies on fragments. A spy's constructor-registered
 * lifecycle observers still point at the original instance, so the
 * SavedStateHandleAttacher fires on the detached original and
 * {@code getViewModelStore()} throws "Can't access ViewModels from detached
 * fragment" during attach (verified 2026-11-10).
 */
public final class FragmentTestHelper {
    private FragmentTestHelper() {
    }

    /** Adds the fragment to a resumed FragmentActivity and returns the activity. */
    public static FragmentActivity attach(Fragment fragment) {
        return attachTo(FragmentActivity.class, fragment);
    }

    /**
     * Adds the fragment to a resumed activity of the given class and returns
     * the activity. Needed by fragments whose view setup requires a
     * {@code BaseActivity} host (e.g. IssueFragmentBase calls
     * {@code getBaseActivity().addAppBarOffsetListener(...)} in
     * {@code onViewCreated}, which NPEs with a plain FragmentActivity).
     */
    public static <A extends FragmentActivity> A attachTo(Class<A> activityClass,
            Fragment fragment) {
        A activity = Robolectric.buildActivity(activityClass).setup().get();
        activity.getSupportFragmentManager().beginTransaction()
                .add(fragment, "test").commitNow();
        return activity;
    }

    /** Runs {@code r} and returns the thrown exception, failing if none is thrown. */
    public static <E extends Throwable> E assertThrows(Class<E> type, Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            if (type.isInstance(t)) {
                return type.cast(t);
            }
            throw new AssertionError("Expected " + type.getName()
                    + " but got " + t.getClass().getName(), t);
        }
        throw new AssertionError("Expected " + type.getName() + " but nothing was thrown");
    }
}
