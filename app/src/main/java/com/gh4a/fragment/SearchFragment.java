package com.gh4a.fragment;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MergeCursor;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.cursoradapter.widget.CursorAdapter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.SearchView;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FilterQueryProvider;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import com.gh4a.R;
import com.gh4a.Gh4Application;
import com.gh4a.ServiceFactory;
import com.gh4a.activities.FileViewerActivity;
import com.gh4a.activities.RepositoryActivity;
import com.gh4a.activities.UserActivity;
import com.gh4a.adapter.RootAdapter;
import com.gh4a.adapter.SearchAdapter;
import com.gh4a.db.SuggestionsProvider;
import com.gh4a.utils.ApiHelpers;
import com.gh4a.utils.RxUtils;
import com.gh4a.utils.StringUtils;
import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.model.Repository;
import com.meisolsson.githubsdk.model.SearchCode;
import com.meisolsson.githubsdk.model.TextMatch;
import com.meisolsson.githubsdk.model.User;
import com.meisolsson.githubsdk.service.search.SearchService;
import com.meisolsson.githubsdk.service.repositories.RepositoryService;

import io.reactivex.Single;
import retrofit2.Response;

public class SearchFragment extends PagedDataBaseFragment<Object> implements
        SearchView.OnQueryTextListener, SearchView.OnCloseListener,
        SearchView.OnSuggestionListener, FilterQueryProvider,
        AdapterView.OnItemSelectedListener, SearchAdapter.Callback {
    public static SearchFragment newInstance(int initialType, String initialQuery,
            boolean startSearchImmediately) {
        SearchFragment f = new SearchFragment();
        Bundle args = new Bundle();
        args.putInt("search_type", initialType);
        args.putString("initial_search", initialQuery);
        args.putBoolean("do_initial_load", startSearchImmediately);
        f.setArguments(args);
        return f;
    }

    public static final int SEARCH_TYPE_REPO = 0;
    public static final int SEARCH_TYPE_USER = 1;
    public static final int SEARCH_TYPE_CODE = 2;

    private static final int[][] HINT_AND_EMPTY_TEXTS = {
        { R.string.search_hint_repo, R.string.no_search_repos_found },
        { R.string.search_hint_user, R.string.no_search_users_found },
        { R.string.search_hint_code, R.string.no_search_code_found }
    };

    private static final String[] SUGGESTION_PROJECTION = {
            SuggestionsProvider.Columns._ID, SuggestionsProvider.Columns.SUGGESTION
    };
    private static final String SUGGESTION_SELECTION =
            SuggestionsProvider.Columns.TYPE + " = ? AND " +
                    SuggestionsProvider.Columns.SUGGESTION + " LIKE ?";
    private static final String SUGGESTION_ORDER = SuggestionsProvider.Columns.DATE + " DESC";

    private static final String STATE_KEY_QUERY = "query";
    private static final String STATE_KEY_SEARCH_TYPE = "search_type";

    private static final String PREF_KEY_SORT_PREFIX = "search_sort_";

    private static final int[] SORT_MENU_RES_IDS = {
        R.menu.search_sort_repo,
        R.menu.search_sort_user,
        R.menu.search_sort_code
    };

    // Maps sort menu item id -> { sort, order }; { null, null } means best match (default)
    private static final SparseArray<String[]> SORT_LOOKUP = new SparseArray<>();
    static {
        SORT_LOOKUP.put(R.id.search_sort_best_match, new String[] { null, null });

        SORT_LOOKUP.put(R.id.search_sort_repo_stars_desc, new String[] { "stars", "desc" });
        SORT_LOOKUP.put(R.id.search_sort_repo_stars_asc, new String[] { "stars", "asc" });
        SORT_LOOKUP.put(R.id.search_sort_repo_forks_desc, new String[] { "forks", "desc" });
        SORT_LOOKUP.put(R.id.search_sort_repo_forks_asc, new String[] { "forks", "asc" });
        SORT_LOOKUP.put(R.id.search_sort_repo_updated_desc, new String[] { "updated", "desc" });
        SORT_LOOKUP.put(R.id.search_sort_repo_updated_asc, new String[] { "updated", "asc" });

        SORT_LOOKUP.put(R.id.search_sort_user_followers_desc, new String[] { "followers", "desc" });
        SORT_LOOKUP.put(R.id.search_sort_user_followers_asc, new String[] { "followers", "asc" });
        SORT_LOOKUP.put(R.id.search_sort_user_repositories_desc, new String[] { "repositories", "desc" });
        SORT_LOOKUP.put(R.id.search_sort_user_repositories_asc, new String[] { "repositories", "asc" });
        SORT_LOOKUP.put(R.id.search_sort_user_joined_desc, new String[] { "joined", "desc" });
        SORT_LOOKUP.put(R.id.search_sort_user_joined_asc, new String[] { "joined", "asc" });

        // Code search only supports sort=indexed
        SORT_LOOKUP.put(R.id.search_sort_code_indexed_desc, new String[] { "indexed", "desc" });
        SORT_LOOKUP.put(R.id.search_sort_code_indexed_asc, new String[] { "indexed", "asc" });
    }

    private SearchAdapter mAdapter;

    private Spinner mSearchType;
    private SearchView mSearch;
    private Menu mOptionsMenu;
    private int mSelectedSearchType;
    private String mQuery;
    private String mSort;
    private String mOrder;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        if (savedInstanceState != null) {
            mQuery = savedInstanceState.getString(STATE_KEY_QUERY);
            mSelectedSearchType = savedInstanceState.getInt(STATE_KEY_SEARCH_TYPE, SEARCH_TYPE_REPO);
        } else {
            Bundle args = getArguments();
            mSelectedSearchType = args.getInt("search_type", SEARCH_TYPE_REPO);
            mQuery = args.getString("initial_search");
        }
        restoreSortSelection(mSelectedSearchType);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.search, menu);

        mSearchType = (Spinner) menu.findItem(R.id.type).getActionView();
        mSearchType.setAdapter(new SearchTypeAdapter(mSearchType.getContext(), getActivity()));
        mSearchType.setOnItemSelectedListener(this);
        mSearchType.setSelection(mSelectedSearchType);

        SuggestionAdapter adapter = new SuggestionAdapter(getActivity());
        adapter.setFilterQueryProvider(this);

        mSearch = (SearchView) menu.findItem(R.id.search).getActionView();
        mSearch.setIconifiedByDefault(true);
        mSearch.requestFocus();
        mSearch.onActionViewExpanded();
        mSearch.setIconified(false);
        mSearch.setOnQueryTextListener(this);
        mSearch.setOnCloseListener(this);
        mSearch.setOnSuggestionListener(this);
        mSearch.setSuggestionsAdapter(adapter);
        if (mQuery != null) {
            mSearch.setQuery(mQuery, false);
        }

        updateSearchViewHint();

        mOptionsMenu = menu;
        updateSortSubMenu();

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        updateSortSubMenu();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        String[] sortAndOrder = SORT_LOOKUP.get(item.getItemId());
        if (sortAndOrder != null) {
            setSortSelection(sortAndOrder[0], sortAndOrder[1]);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(STATE_KEY_SEARCH_TYPE, mSelectedSearchType);
        outState.putString(STATE_KEY_QUERY, mQuery);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected RootAdapter<Object, ? extends RecyclerView.ViewHolder> onCreateAdapter() {
        mAdapter = new SearchAdapter(getActivity(), this);
        mAdapter.setMode(mSelectedSearchType);
        return mAdapter;
    }

    @Override
    protected boolean shouldDoInitialLoad(Bundle savedInstanceState) {
        return (savedInstanceState != null && mQuery != null) // always restore previous load results
                || getArguments().getBoolean("do_initial_load", true);
    }

    @Override
    protected Single<Response<Page<Object>>> loadPage(int page, boolean bypassCache) {
        if (TextUtils.isEmpty(mQuery)) {
            // Show user's own repositories by default when searching repos with empty query
            if (mSelectedSearchType == SEARCH_TYPE_REPO) {
                return makeDefaultRepoSingle(page, bypassCache);
            }
            return Single.just(Response.success(new ApiHelpers.DummyPage<>()));
        }
        switch (mSelectedSearchType) {
            case SEARCH_TYPE_REPO: return makeRepoSearchSingle(page, bypassCache);
            case SEARCH_TYPE_USER: return makeUserSearchSingle(page, bypassCache);
            case SEARCH_TYPE_CODE: return makeCodeSearchSingle(page, bypassCache);
        }
        throw new IllegalStateException("Unexpected search type " + mSelectedSearchType);
    }

    @SuppressWarnings("unchecked")
    private Single<Response<Page<Object>>> makeDefaultRepoSingle(int page, boolean bypassCache) {
        RepositoryService service = ServiceFactory.get(RepositoryService.class, bypassCache);
        String login = Gh4Application.get().getAuthLogin();
        if (login == null) {
            // 未登录时没有默认用户，直接返回空页（null 传给 Retrofit @Path 会崩溃）
            return Single.just(Response.success(new ApiHelpers.DummyPage<>()));
        }
        // Page<Repository> is safely readable as Page<Object>
        return (Single<Response<Page<Object>>>) (Single<?>)
                service.getUserRepositories(login, null, page);
    }

    @Override
    protected int getEmptyTextResId() {
        return 0; // will be updated later
    }

    @Override
    public void onItemClick(Object item) {
        if (item instanceof Repository) {
            Repository repository = (Repository) item;
            startActivity(RepositoryActivity.makeIntent(getActivity(), repository));
        } else if (item instanceof SearchCode) {
            openFileViewer((SearchCode) item, -1);
        } else {
            User user = (User) item;
            startActivity(UserActivity.makeIntent(getActivity(), user));
        }
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        mQuery = query;
        if (!StringUtils.isBlank(query)) {
            final ContentResolver cr = getActivity().getContentResolver();
            final ContentValues cv = new ContentValues();
            cv.put(SuggestionsProvider.Columns.TYPE, mSelectedSearchType);
            cv.put(SuggestionsProvider.Columns.SUGGESTION, query);
            cv.put(SuggestionsProvider.Columns.DATE, System.currentTimeMillis());

            new Thread() {
                @Override
                public void run() {
                    cr.insert(SuggestionsProvider.Columns.CONTENT_URI, cv);
                }
            }.start();
        }
        loadResults();
        return true;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        mQuery = newText;
        return true;
    }

    @Override
    public boolean onClose() {
        if (mAdapter != null) {
            mAdapter.clear();
        }
        mQuery = null;
        return true;
    }

    @Override
    public boolean onSuggestionSelect(int position) {
        return false;
    }

    @Override
    public boolean onSuggestionClick(int position) {
        Cursor cursor = mSearch.getSuggestionsAdapter().getCursor();
        if (cursor.moveToPosition(position)) {
            if (position == cursor.getCount() - 1) {
                final int type = mSelectedSearchType;
                final ContentResolver cr = getActivity().getContentResolver();
                new Thread() {
                    @Override
                    public void run() {
                        cr.delete(SuggestionsProvider.Columns.CONTENT_URI,
                                SuggestionsProvider.Columns.TYPE + " = ?",
                                new String[] { String.valueOf(type) });
                    }
                }.start();
            } else {
                mQuery = cursor.getString(1);
                mSearch.setQuery(mQuery, true);
            }
        }
        return true;
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        updateSelectedSearchType();
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        updateSelectedSearchType();
    }

    @Override
    public void onSearchFragmentClick(SearchCode result, int matchIndex) {
        openFileViewer(result, matchIndex);
    }

    @Override
    public Cursor runQuery(CharSequence query) {
        if (TextUtils.isEmpty(query)) {
            return null;
        }
        return getContext().getContentResolver().query(SuggestionsProvider.Columns.CONTENT_URI,
                SUGGESTION_PROJECTION, SUGGESTION_SELECTION,
                new String[] { String.valueOf(mSelectedSearchType), query + "%" }, SUGGESTION_ORDER);
    }

    private void openFileViewer(SearchCode result, int matchIndex) {
        Repository repo = result.repository();
        Uri uri = Uri.parse(result.url());
        String ref = uri.getQueryParameter("ref");
        TextMatch textMatch = matchIndex >= 0 ? result.textMatches().get(matchIndex) : null;
        startActivity(FileViewerActivity.makeIntentWithSearchMatch(getActivity(),
                repo.owner().login(), repo.name(), ref, result.path(),
                textMatch));
    }

    private void loadResults() {
        mSearch.clearFocus();
        onRefresh();
    }

    private void updateSearchViewHint() {
        int[] hintAndEmptyTextResIds = HINT_AND_EMPTY_TEXTS[mSelectedSearchType];
        mSearch.setQueryHint(getString(hintAndEmptyTextResIds[0]));
        updateEmptyText(hintAndEmptyTextResIds[1]);
    }

    private void updateSelectedSearchType() {
        int newType = mSearchType.getSelectedItemPosition();
        if (newType == mSelectedSearchType) {
            return;
        }
        mSelectedSearchType = newType;
        mAdapter.setMode(newType);
        restoreSortSelection(newType);
        updateSortSubMenu();

        updateSearchViewHint();
        updateEmptyState();
        resetSubject();

        // force re-filtering of the view
        mSearch.setQuery(mQuery, true);
    }

    private void updateEmptyText(@StringRes int emptyTextResId) {
        TextView emptyView = getView().findViewById(android.R.id.empty);
        emptyView.setText(emptyTextResId);
    }

    private void setSortSelection(String sort, String order) {
        if (TextUtils.equals(sort, mSort) && TextUtils.equals(order, mOrder)) {
            return;
        }
        mSort = sort;
        mOrder = order;
        saveSortSelection(mSelectedSearchType);
        checkCurrentSortItem();
        loadResults();
    }

    private void restoreSortSelection(int searchType) {
        SharedPreferences prefs = getActivity()
                .getSharedPreferences(SettingsFragment.PREF_NAME, Context.MODE_PRIVATE);
        mSort = prefs.getString(PREF_KEY_SORT_PREFIX + searchType + "_sort", null);
        mOrder = prefs.getString(PREF_KEY_SORT_PREFIX + searchType + "_order", null);
    }

    private void saveSortSelection(int searchType) {
        getActivity().getSharedPreferences(SettingsFragment.PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_KEY_SORT_PREFIX + searchType + "_sort", mSort)
                .putString(PREF_KEY_SORT_PREFIX + searchType + "_order", mOrder)
                .apply();
    }

    private void updateSortSubMenu() {
        if (mOptionsMenu == null) {
            return;
        }
        SubMenu subMenu = mOptionsMenu.findItem(R.id.sort).getSubMenu();
        subMenu.clear();
        getActivity().getMenuInflater().inflate(SORT_MENU_RES_IDS[mSelectedSearchType], subMenu);
        checkCurrentSortItem();
    }

    private void checkCurrentSortItem() {
        if (mOptionsMenu == null) {
            return;
        }
        int itemId = findSortMenuItemId(mSort, mOrder);
        if (itemId != 0) {
            mOptionsMenu.findItem(R.id.sort).getSubMenu().findItem(itemId).setChecked(true);
        }
    }

    private static int findSortMenuItemId(String sort, String order) {
        for (int i = 0; i < SORT_LOOKUP.size(); i++) {
            String[] value = SORT_LOOKUP.valueAt(i);
            if (TextUtils.equals(value[0], sort) && TextUtils.equals(value[1], order)) {
                return SORT_LOOKUP.keyAt(i);
            }
        }
        return 0;
    }

    private Single<Response<Page<Object>>> makeRepoSearchSingle(long page, boolean bypassCache) {
        SearchService service = ServiceFactory.get(SearchService.class, bypassCache);
        String params = mQuery + " fork:true";

        return service.searchRepositories(params, mSort, mOrder, page)
                .compose(result -> RxUtils.<Repository, Object>searchPageAdapter(result, item -> item))
                // With that status code, Github wants to tell us there are no
                // repositories to search in. Just pretend no error and return
                // an empty list in that case.
                .compose(RxUtils.mapFailureToValue(422, Response.success(new ApiHelpers.DummyPage<>())));
    }

    private Single<Response<Page<Object>>> makeUserSearchSingle(long page, boolean bypassCache) {
        final SearchService service = ServiceFactory.get(SearchService.class, bypassCache);
        return service.searchUsers(mQuery, mSort, mOrder, page)
                .compose(result -> RxUtils.searchPageAdapter(result, item -> item));
    }

    private Single<Response<Page<Object>>> makeCodeSearchSingle(long page, boolean bypassCache) {
        SearchService service = ServiceFactory.get(SearchService.class, bypassCache,
                "application/vnd.github.v3.text-match+json", null, null);

        return service.searchCode(mQuery, mSort, mOrder, page)
                .compose(result -> RxUtils.searchPageAdapter(result, item -> item));
    }

    private static class SearchTypeAdapter extends BaseAdapter implements SpinnerAdapter {
        private final Context mContext;
        private final LayoutInflater mInflater;
        private final LayoutInflater mPopupInflater;

        private final int[][] mResources = new int[][] {
            { R.string.search_type_repo, R.drawable.menu_search_repos, R.drawable.icon_repositories },
            { R.string.search_type_user, R.drawable.menu_search_users, R.drawable.search_users },
            { R.string.search_type_code, R.drawable.menu_search_code, R.drawable.search_code }
        };

        private SearchTypeAdapter(Context context, Context popupContext) {
            mContext = context;
            mInflater = LayoutInflater.from(context);
            mPopupInflater = LayoutInflater.from(popupContext);
        }

        @Override
        public int getCount() {
            return mResources.length;
        }

        @Override
        public CharSequence getItem(int position) {
            return mContext.getString(mResources[position][0]);
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.search_type_small, null);
            }

            ImageView icon = convertView.findViewById(R.id.icon);
            icon.setImageResource(mResources[position][1]);

            return convertView;
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = mPopupInflater.inflate(R.layout.search_type_popup, null);
            }

            ImageView icon = convertView.findViewById(R.id.icon);
            icon.setImageResource(mResources[position][2]);

            TextView label = convertView.findViewById(R.id.label);
            label.setText(mResources[position][0]);

            return convertView;
        }
    }

    private class SuggestionAdapter extends CursorAdapter {
        private final LayoutInflater mInflater;

        public SuggestionAdapter(Context context) {
            super(context, null, false);
            mInflater = LayoutInflater.from(context);
        }

        @Override
        public Cursor swapCursor(Cursor newCursor) {
            if (newCursor != null && newCursor.getCount() > 0) {
                MatrixCursor clearRowCursor = new MatrixCursor(SUGGESTION_PROJECTION);
                clearRowCursor.addRow(new Object[] {
                        Long.MAX_VALUE,
                        mInflater.getContext().getString(R.string.clear_suggestions)
                });
                newCursor = new MergeCursor(new Cursor[] { newCursor, clearRowCursor });
            }
            return super.swapCursor(newCursor);
        }

        @Override
        public int getItemViewType(int position) {
            return isClearRow(position) ? 1 : 0;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            @LayoutRes int layoutResId = isClearRow(cursor.getPosition())
                    ? R.layout.row_suggestion_clear : R.layout.row_suggestion;
            return mInflater.inflate(layoutResId, parent, false);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            int columnIndex = cursor.getColumnIndexOrThrow(SuggestionsProvider.Columns.SUGGESTION);
            String suggestionText = cursor.getString(columnIndex);
            if (isClearRow(cursor.getPosition())) {
                bindClearSuggestionsRow(view, suggestionText);
            } else {
                bindSuggestionRow(view, suggestionText);
            }
        }

        private void bindSuggestionRow(View view, String suggestionText) {
            TextView textView = (TextView) view.findViewById(R.id.suggestion_text);
            textView.setText(suggestionText);
            view.findViewById(R.id.select_suggestion_button).setOnClickListener(btn -> {
                mQuery = suggestionText;
                mSearch.setQuery(mQuery, false);
            });
        }

        private void bindClearSuggestionsRow(View view, String suggestionText) {
            TextView textView = (TextView) view;
            textView.setText(suggestionText);
        }

        private boolean isClearRow(int position) {
            return position == getCount() - 1;
        }
    }
}
