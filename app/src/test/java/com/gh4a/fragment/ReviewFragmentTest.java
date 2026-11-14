package com.gh4a.fragment;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;

import com.gh4a.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** ReviewFragment：PR 评审页（Robolectric，CREATED 态挂载）。 */
@RunWith(RobolectricTestRunner.class)
public class ReviewFragmentTest {
    private FragmentActivity mActivity;

    @Before
    public void setUp() {
        mActivity = Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    // Fragment.SavedState(Bundle) 在此 androidx 版本是包私有构造，
    // 只能反射创建；用于给未挂载的 Fragment 预置恢复状态。
    private static androidx.fragment.app.Fragment.SavedState savedState(
            android.os.Bundle bundle) throws Exception {
        java.lang.reflect.Constructor<androidx.fragment.app.Fragment.SavedState> c =
                androidx.fragment.app.Fragment.SavedState.class
                        .getDeclaredConstructor(android.os.Bundle.class);
        c.setAccessible(true);
        return c.newInstance(bundle);
    }

    private <F extends Fragment> F attach(F fragment) {
        mActivity.getSupportFragmentManager().beginTransaction().add(fragment, "test")
                .setMaxLifecycle(fragment, Lifecycle.State.CREATED).commitNow();
        return fragment;
    }

    private static ReviewFragment fragment() {
        return ReviewFragment.newInstance("octo", "cat", 42, null, null);
    }

    private static Object field(ReviewFragment f, String name) throws Exception {
        Field field = ReviewFragment.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(f);
    }

    @Test
    public void newInstance_storesAllArguments() {
        ReviewFragment f = fragment();
        assertEquals("octo", f.getArguments().getString("repo_owner"));
        assertEquals("cat", f.getArguments().getString("repo_name"));
        assertEquals(42, f.getArguments().getInt("issue_number"));
    }

    @Test
    public void onCreate_readsArgumentsAndRemovesInitialComment() throws Exception {
        ReviewFragment f = attach(fragment());
        assertEquals("octo", field(f, "mRepoOwner"));
        assertEquals("cat", field(f, "mRepoName"));
        assertEquals(42, field(f, "mIssueNumber"));
        assertFalse(f.getArguments().containsKey("initial_comment"));
    }

    @Test
    public void onCreate_restoresSelectedReplyIdFromSavedState() throws Exception {
        // 未挂载的 Fragment 直接调 onCreate(saved) 会在 Fragment.onCreate 内
        // 触 mNonConfig 抛 NPE；正确做法是 setInitialSavedState 后挂载。
        ReviewFragment f = ReviewFragment.newInstance("o", "r", 1, null, null);
        Bundle saved = new Bundle();
        saved.putLong("selected_reply_comment_id", 99L);
        f.setInitialSavedState(savedState(saved));
        attach(f);
        assertEquals(99L, (long) (Long) field(f, "mSelectedReplyCommentId"));
    }

    @Test
    public void getCommentEditorHintResId_defaultIsReviewReplyHint() {
        ReviewFragment f = attach(fragment());
        assertEquals(R.string.review_reply_hint, f.getCommentEditorHintResId());
    }

    @Test
    public void getEditorErrorMessageResId() {
        ReviewFragment f = attach(fragment());
        assertEquals(R.string.issue_error_comment, f.getEditorErrorMessageResId());
    }

    @Test
    public void getSelectedReplyCommentId_defaultIsZero() {
        ReviewFragment f = attach(fragment());
        assertEquals(0L, f.getSelectedReplyCommentId());
    }

    @Test
    public void collectThreadItems_noAdapterReturnsEmpty() {
        ReviewFragment f = attach(fragment());
        assertNotNull(f.collectThreadItems());
        assertTrue(f.collectThreadItems().isEmpty());
    }

    @Test
    public void getTranslator_isNullBeforeViewCreated() {
        ReviewFragment f = attach(fragment());
        assertNull(f.getTranslator());
    }

    @Test
    public void onTranslationsChanged_noAdapterIsNoop() {
        ReviewFragment f = attach(fragment());
        f.onTranslationsChanged();
    }

    @Test
    public void onBackPressed_noBottomSheetReturnsFalse() {
        ReviewFragment f = attach(fragment());
        assertFalse(f.onBackPressed());
    }

    @Test
    public void canChildScrollUp_noViewReturnsFalse() {
        ReviewFragment f = attach(fragment());
        assertFalse(f.canChildScrollUp());
    }

    @Test
    public void onSaveInstanceState_storesSelectedReplyId() {
        ReviewFragment f = attach(fragment());
        Bundle out = new Bundle();
        f.onSaveInstanceState(out);
        assertTrue(out.containsKey("selected_reply_comment_id"));
        assertEquals(0L, out.getLong("selected_reply_comment_id"));
    }

    @Test
    public void getEmptyTextResId_isZeroFromBase() {
        ReviewFragment f = attach(fragment());
        assertEquals(0, f.getEmptyTextResId());
    }
}
