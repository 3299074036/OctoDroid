package com.gh4a.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.gh4a.R;
import com.gh4a.fragment.SearchFragment;
import com.meisolsson.githubsdk.model.Repository;
import com.meisolsson.githubsdk.model.SearchCode;
import com.meisolsson.githubsdk.model.TextMatch;
import com.meisolsson.githubsdk.model.User;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class SearchAdapterTest {
    private Context context;
    private LayoutInflater inflater;
    private ViewGroup parent;
    private List<SearchCode> clickedResults;
    private List<Integer> clickedIndices;
    private SearchAdapter adapter;

    @Before
    public void setUp() {
        context = AdapterTestSupport.context();
        inflater = AdapterTestSupport.inflater(context);
        parent = AdapterTestSupport.parent(context);
        clickedResults = new ArrayList<>();
        clickedIndices = new ArrayList<>();
        adapter = new SearchAdapter(context, (result, matchIndex) -> {
            clickedResults.add(result);
            clickedIndices.add(matchIndex);
        });
    }

    private Repository repo() {
        return Repository.builder().fullName("o/r").name("r")
                .owner(User.builder().login("o").build()).build();
    }

    private SearchCode code(String path, List<TextMatch> matches) {
        SearchCode c = mock(SearchCode.class);
        when(c.path()).thenReturn(path);
        when(c.repository()).thenReturn(repo());
        when(c.textMatches()).thenReturn(matches);
        return c;
    }

    private TextMatch match(String fragment, int start, int end) {
        TextMatch m = mock(TextMatch.class);
        when(m.fragment()).thenReturn(fragment);
        TextMatch.MatchItem item = mock(TextMatch.MatchItem.class);
        when(item.getStartPos()).thenReturn(start);
        when(item.getEndPos()).thenReturn(end);
        when(m.matches()).thenReturn(Collections.singletonList(item));
        return m;
    }

    @Test
    public void getItemViewType_byItemClass() {
        assertEquals(10000 + 2, adapter.getItemViewType(User.builder().login("u").build()));
        assertEquals(20000 + 2, adapter.getItemViewType(repo()));
        assertEquals(30000 + 2, adapter.getItemViewType(code("p", null)));
        assertEquals(30000 + 2, adapter.getItemViewType("unknown"));
        assertEquals(30000 + 2, adapter.getItemViewType(null));
    }

    @Test
    public void setMode_clearsItems() {
        adapter.add("x");
        assertEquals(1, adapter.getCount());
        adapter.setMode(SearchFragment.SEARCH_TYPE_USER);
        assertEquals(0, adapter.getCount());
    }

    @Test
    public void onCreateViewHolder_repoMode_createsRepoHolder() {
        adapter.setMode(SearchFragment.SEARCH_TYPE_REPO);
        RecyclerView.ViewHolder h = adapter.onCreateViewHolder(inflater, parent, 20002);
        assertTrue(h.itemView.findViewById(R.id.tv_title) != null);
        assertTrue(h.itemView.findViewById(R.id.tv_meta) != null);
    }

    @Test
    public void onCreateViewHolder_userMode_createsUserHolder() {
        adapter.setMode(SearchFragment.SEARCH_TYPE_USER);
        RecyclerView.ViewHolder h = adapter.onCreateViewHolder(inflater, parent, 10002);
        assertTrue(h.itemView.findViewById(R.id.tv_subtitle) != null);
    }

    @Test
    public void onCreateViewHolder_codeMode_createsCodeHolder() {
        adapter.setMode(SearchFragment.SEARCH_TYPE_CODE);
        RecyclerView.ViewHolder h = adapter.onCreateViewHolder(inflater, parent, 30002);
        assertTrue(h.itemView.findViewById(R.id.matches_container) != null);
    }

    @Test
    public void onBindViewHolder_user_delegatesToUserAdapter() {
        adapter.setMode(SearchFragment.SEARCH_TYPE_USER);
        RecyclerView.ViewHolder h = adapter.onCreateViewHolder(inflater, parent, 10002);
        User user = User.builder().login("octocat").name("Octo Cat").build();
        adapter.onBindViewHolder(h, user);
        assertEquals("octocat - Octo Cat",
                ((TextView) h.itemView.findViewById(R.id.tv_title)).getText().toString());
    }


    @Test
    public void onBindViewHolder_unknownType_noop() {
        adapter.setMode(SearchFragment.SEARCH_TYPE_REPO);
        RecyclerView.ViewHolder h = adapter.onCreateViewHolder(inflater, parent, 20002);
        adapter.onBindViewHolder(h, "unknown");
        adapter.onBindViewHolder(h, null);
    }

    // ---- CodeSearchAdapter ----

    private SearchAdapter.CodeSearchAdapter codeAdapter() {
        return new SearchAdapter.CodeSearchAdapter(context, (result, matchIndex) -> {
            clickedResults.add(result);
            clickedIndices.add(matchIndex);
        });
    }

    @Test
    public void codeBind_noMatches_hidesContainer() {
        SearchAdapter.CodeSearchAdapter a = codeAdapter();
        SearchAdapter.CodeSearchAdapter.ViewHolder h =
                a.onCreateViewHolder(inflater, parent, 2);
        a.onBindViewHolder(h, code("a/b.java", null));
        assertEquals(View.GONE, h.itemView.findViewById(R.id.matches_container).getVisibility());

        a.onBindViewHolder(h, code("a/b.java", Collections.emptyList()));
        assertEquals(View.GONE, h.itemView.findViewById(R.id.matches_container).getVisibility());
    }

    @Test
    public void codeBind_withMatch_showsFragmentAndBoldSpan() {
        SearchAdapter.CodeSearchAdapter a = codeAdapter();
        SearchAdapter.CodeSearchAdapter.ViewHolder h =
                a.onCreateViewHolder(inflater, parent, 2);
        SearchCode result = code("src/Main.java",
                Collections.singletonList(match("hello world", 0, 5)));
        a.onBindViewHolder(h, result);
        assertEquals("src/Main.java",
                ((TextView) h.itemView.findViewById(R.id.tv_title)).getText().toString());
        assertEquals("o/r",
                ((TextView) h.itemView.findViewById(R.id.tv_repo)).getText().toString());
        ViewGroup container = h.itemView.findViewById(R.id.matches_container);
        assertEquals(View.VISIBLE, container.getVisibility());
        assertEquals(1, container.getChildCount());
        TextView tvMatch = container.getChildAt(0).findViewById(R.id.tv_match);
        assertEquals("hello world", tvMatch.getText().toString());
        assertEquals(1, ((android.text.Spanned) tvMatch.getText())
                .getSpans(0, 11, android.text.style.StyleSpan.class).length);
    }

    @Test
    public void codeBind_nullMatchSkipped_nullFragmentEmpty() {
        SearchAdapter.CodeSearchAdapter a = codeAdapter();
        SearchAdapter.CodeSearchAdapter.ViewHolder h =
                a.onCreateViewHolder(inflater, parent, 2);
        TextMatch nullFragment = mock(TextMatch.class);
        when(nullFragment.fragment()).thenReturn(null);
        when(nullFragment.matches()).thenReturn(null);
        SearchCode result = code("p", Arrays.asList(null, nullFragment));
        a.onBindViewHolder(h, result);
        ViewGroup container = h.itemView.findViewById(R.id.matches_container);
        assertEquals(View.VISIBLE, container.getVisibility());
        // null match skipped (i=0 creates no row); the null-fragment match at
        // i=1 inflates the first (and only) row
        assertEquals(1, container.getChildCount());
        TextView tvMatch = container.getChildAt(0).findViewById(R.id.tv_match);
        assertEquals("", tvMatch.getText().toString());
    }

    @Test
    public void codeBind_outOfRangeIndices_noSpanNoCrash() {
        SearchAdapter.CodeSearchAdapter a = codeAdapter();
        SearchAdapter.CodeSearchAdapter.ViewHolder h =
                a.onCreateViewHolder(inflater, parent, 2);
        SearchCode result = code("p",
                Collections.singletonList(match("short", 2, 99)));
        a.onBindViewHolder(h, result);
        ViewGroup container = h.itemView.findViewById(R.id.matches_container);
        TextView tvMatch = container.getChildAt(0).findViewById(R.id.tv_match);
        assertEquals("short", tvMatch.getText().toString());
    }

    @Test
    public void codeBind_negativeAndReversedIndices_noSpan() {
        SearchAdapter.CodeSearchAdapter a = codeAdapter();
        SearchAdapter.CodeSearchAdapter.ViewHolder h =
                a.onCreateViewHolder(inflater, parent, 2);
        TextMatch m1 = match("abcdef", -1, 3);
        TextMatch m2 = match("abcdef", 4, 2);
        SearchCode result = code("p", Arrays.asList(m1, m2));
        a.onBindViewHolder(h, result);
        ViewGroup container = h.itemView.findViewById(R.id.matches_container);
        assertEquals(2, container.getChildCount());
    }

    @Test
    public void codeBind_rebindFewerMatches_hidesExtraRows() {
        SearchAdapter.CodeSearchAdapter a = codeAdapter();
        SearchAdapter.CodeSearchAdapter.ViewHolder h =
                a.onCreateViewHolder(inflater, parent, 2);
        a.onBindViewHolder(h, code("p", Arrays.asList(match("one", 0, 1), match("two", 0, 1))));
        ViewGroup container = h.itemView.findViewById(R.id.matches_container);
        assertEquals(2, container.getChildCount());
        a.onBindViewHolder(h, code("p", Collections.singletonList(match("one", 0, 1))));
        assertEquals(View.VISIBLE, container.getChildAt(0).getVisibility());
        assertEquals(View.GONE, container.getChildAt(1).getVisibility());
    }

    @Test
    public void codeOnClick_matchRow_callsCallbackWithIndex() {
        SearchAdapter.CodeSearchAdapter a = codeAdapter();
        SearchAdapter.CodeSearchAdapter.ViewHolder h =
                a.onCreateViewHolder(inflater, parent, 2);
        SearchCode result = code("p", Arrays.asList(match("one", 0, 1), match("two", 0, 1)));
        a.onBindViewHolder(h, result);
        ViewGroup container = h.itemView.findViewById(R.id.matches_container);
        TextView second = container.getChildAt(1).findViewById(R.id.tv_match);
        second.performClick();
        assertEquals(1, clickedResults.size());
        assertEquals(result, clickedResults.get(0));
        assertEquals(Integer.valueOf(1), clickedIndices.get(0));
    }

    @Test
    public void codeOnClick_otherView_delegatesToSuper() {
        SearchAdapter.CodeSearchAdapter a = codeAdapter();
        SearchAdapter.CodeSearchAdapter.ViewHolder h =
                a.onCreateViewHolder(inflater, parent, 2);
        View title = h.itemView.findViewById(R.id.tv_title);
        title.setTag(h);
        a.onClick(title); // NO_POSITION -> no-op, no crash
        assertTrue(clickedResults.isEmpty());
    }

    @Test
    public void onBindViewHolder_repo_delegatesToRepoAdapter() {
        Repository repo = repo();
        RecyclerView.ViewHolder h = adapter.onCreateViewHolder(inflater, parent,
                adapter.getItemViewType(repo));
        adapter.onBindViewHolder(h, repo);
        assertEquals("o/r", ((TextView) h.itemView.findViewById(R.id.tv_title)).getText().toString());
    }

    @Test
    public void onBindViewHolder_code_delegatesToCodeAdapter() {
        adapter.setMode(SearchFragment.SEARCH_TYPE_CODE);
        SearchCode result = code("src/Main.java",
                Collections.singletonList(match("hello world", 0, 5)));
        RecyclerView.ViewHolder h = adapter.onCreateViewHolder(inflater, parent,
                adapter.getItemViewType(result));
        adapter.onBindViewHolder(h, result);
        assertEquals("src/Main.java",
                ((TextView) h.itemView.findViewById(R.id.tv_title)).getText().toString());
    }

    @Test
    public void codeBind_invalidMatchIndices_noBoldSpan() {
        SearchAdapter.CodeSearchAdapter a = codeAdapter();
        SearchAdapter.CodeSearchAdapter.ViewHolder h =
                a.onCreateViewHolder(inflater, parent, 2);
        TextMatch bad = mock(TextMatch.class);
        when(bad.fragment()).thenReturn("hello world");
        TextMatch.MatchItem i1 = mock(TextMatch.MatchItem.class);
        when(i1.getStartPos()).thenReturn(5);
        when(i1.getEndPos()).thenReturn(5);
        TextMatch.MatchItem i2 = mock(TextMatch.MatchItem.class);
        when(i2.getStartPos()).thenReturn(-1);
        when(i2.getEndPos()).thenReturn(3);
        TextMatch.MatchItem i3 = mock(TextMatch.MatchItem.class);
        when(i3.getStartPos()).thenReturn(0);
        when(i3.getEndPos()).thenReturn(99);
        when(bad.matches()).thenReturn(java.util.Arrays.asList(i1, i2, i3));
        a.onBindViewHolder(h, code("src/Main.java", Collections.singletonList(bad)));
        ViewGroup container = h.itemView.findViewById(R.id.matches_container);
        TextView tvMatch = container.getChildAt(0).findViewById(R.id.tv_match);
        assertEquals(0, ((android.text.Spanned) tvMatch.getText())
                .getSpans(0, 11, android.text.style.StyleSpan.class).length);
    }
}
