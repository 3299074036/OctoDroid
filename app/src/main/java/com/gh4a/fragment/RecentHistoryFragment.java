package com.gh4a.fragment;

import androidx.recyclerview.widget.RecyclerView;

import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.gh4a.R;
import com.gh4a.activities.IssueActivity;
import com.gh4a.activities.PullRequestActivity;
import com.gh4a.activities.RepositoryActivity;
import com.gh4a.adapter.RecentHistoryAdapter;
import com.gh4a.adapter.RootAdapter;
import com.gh4a.utils.RecentHistoryManager;

import java.util.List;

import io.reactivex.Single;

/**
 * Shows locally recorded browsing history: repositories, issues and
 * pull requests viewed recently, newest first.
 */
public class RecentHistoryFragment extends ListDataBaseFragment<RecentHistoryManager.Entry>
        implements RootAdapter.OnItemClickListener<RecentHistoryManager.Entry> {

    public static RecentHistoryFragment newInstance() {
        return new RecentHistoryFragment();
    }

    @Override
    public void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.recent_history_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.clear_history) {
            RecentHistoryManager.clear(getActivity());
            onRefresh();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected RootAdapter<RecentHistoryManager.Entry, ? extends RecyclerView.ViewHolder> onCreateAdapter() {
        RecentHistoryAdapter adapter = new RecentHistoryAdapter(getActivity());
        adapter.setOnItemClickListener(this);
        return adapter;
    }

    @Override
    protected Single<List<RecentHistoryManager.Entry>> onCreateDataSingle(boolean bypassCache) {
        return Single.just(RecentHistoryManager.getEntries(getActivity()));
    }

    @Override
    protected int getEmptyTextResId() {
        return R.string.no_recent_history;
    }

    @Override
    public void onItemClick(RecentHistoryManager.Entry entry) {
        switch (entry.type) {
            case RecentHistoryManager.TYPE_ISSUE:
                startActivity(IssueActivity.makeIntent(getActivity(),
                        entry.owner, entry.repo, entry.number));
                break;
            case RecentHistoryManager.TYPE_PR:
                startActivity(PullRequestActivity.makeIntent(getActivity(),
                        entry.owner, entry.repo, entry.number));
                break;
            default:
                startActivity(RepositoryActivity.makeIntent(getActivity(),
                        entry.owner, entry.repo));
                break;
        }
    }
}
