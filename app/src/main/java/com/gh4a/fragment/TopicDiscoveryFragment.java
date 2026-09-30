package com.gh4a.fragment;

import android.os.Bundle;
import androidx.recyclerview.widget.RecyclerView;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import com.gh4a.R;
import com.gh4a.ServiceFactory;
import com.gh4a.activities.RepositoryActivity;
import com.gh4a.adapter.RepositoryAdapter;
import com.gh4a.adapter.RootAdapter;
import com.gh4a.utils.ApiHelpers;
import com.gh4a.utils.RxUtils;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.model.Repository;
import com.meisolsson.githubsdk.service.search.SearchService;

import io.reactivex.Single;
import retrofit2.Response;

/**
 * Discovers repositories by GitHub topic. Shows a set of popular topics as
 * chips plus a free-form topic input; results are the topic's repositories
 * sorted by stars.
 */
public class TopicDiscoveryFragment extends PagedDataBaseFragment<Repository> {
    private static final String STATE_KEY_TOPIC = "topic";

    private static final String DEFAULT_TOPIC = "android";

    private static final String[] POPULAR_TOPICS = {
            "android", "bilibili", "rss", "magisk", "lsposed", "tweak",
            "downloader", "music", "flutter", "kotlin", "tv", "adblock"
    };

    public static TopicDiscoveryFragment newInstance() {
        return new TopicDiscoveryFragment();
    }

    private RepositoryAdapter mRepositoryAdapter;
    private String mTopic = DEFAULT_TOPIC;
    private ChipGroup mChipGroup;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mTopic = savedInstanceState.getString(STATE_KEY_TOPIC, DEFAULT_TOPIC);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_KEY_TOPIC, mTopic);
    }

    @Override
    protected RootAdapter<Repository, ? extends RecyclerView.ViewHolder> onCreateAdapter() {
        mRepositoryAdapter = new RepositoryAdapter(getActivity());
        return mRepositoryAdapter;
    }

    @Override
    protected void onRecyclerViewInflated(RecyclerView view, LayoutInflater inflater) {
        super.onRecyclerViewInflated(view, inflater);

        View header = inflater.inflate(R.layout.topic_discovery_header, view, false);
        mRepositoryAdapter.setHeaderView(header);

        EditText topicInput = header.findViewById(R.id.topic_input);
        topicInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean isSearchAction = actionId == EditorInfo.IME_ACTION_SEARCH;
            boolean isEnterKey = event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (isSearchAction || isEnterKey) {
                String topic = v.getText().toString().trim().toLowerCase();
                if (!TextUtils.isEmpty(topic)) {
                    selectTopic(topic, null);
                }
                return true;
            }
            return false;
        });

        mChipGroup = header.findViewById(R.id.topic_chips);
        for (String topic : POPULAR_TOPICS) {
            Chip chip = new Chip(getActivity());
            chip.setText(topic);
            chip.setCheckable(true);
            chip.setChecked(topic.equals(mTopic));
            chip.setOnClickListener(v -> selectTopic(topic, chip));
            mChipGroup.addView(chip);
        }
    }

    private void selectTopic(String topic, Chip selectedChip) {
        if (TextUtils.equals(mTopic, topic)) {
            return;
        }
        mTopic = topic;
        if (mChipGroup != null) {
            for (int i = 0; i < mChipGroup.getChildCount(); i++) {
                View child = mChipGroup.getChildAt(i);
                if (child instanceof Chip) {
                    ((Chip) child).setChecked(child == selectedChip);
                }
            }
        }
        onRefresh();
    }

    @Override
    protected Single<Response<Page<Repository>>> loadPage(int page, boolean bypassCache) {
        SearchService service = ServiceFactory.get(SearchService.class, bypassCache);
        String params = "topic:" + mTopic;

        return service.searchRepositories(params, "stars", "desc", page)
                .compose(RxUtils::searchPageAdapter)
                // With that status code, Github wants to tell us there are no
                // repositories to search in. Just pretend no error and return
                // an empty list in that case.
                .compose(RxUtils.mapFailureToValue(422, Response.success(new ApiHelpers.DummyPage<>())));
    }

    @Override
    protected int getEmptyTextResId() {
        return R.string.no_topic_repos_found;
    }

    @Override
    public void onItemClick(Repository item) {
        startActivity(RepositoryActivity.makeIntent(getActivity(), item));
    }
}
