/*
 * Copyright 2011 Azwan Adli Abdullah
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gh4a.fragment;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.gh4a.BaseActivity;
import com.gh4a.Gh4Application;
import com.gh4a.R;
import com.gh4a.ServiceFactory;
import com.gh4a.activities.UserActivity;
import com.gh4a.adapter.RootAdapter;
import com.gh4a.adapter.timeline.TimelineItemAdapter;
import com.gh4a.model.TimelineItem;
import com.gh4a.utils.ActivityResultHelpers;
import com.gh4a.utils.ApiHelpers;
import com.gh4a.utils.AvatarHandler;
import com.gh4a.utils.HttpImageGetter;
import com.gh4a.utils.IntentUtils;
import com.gh4a.utils.RxUtils;
import com.gh4a.utils.StringUtils;
import com.gh4a.utils.UiUtils;
import com.gh4a.utils.translate.CommentTranslator;
import com.gh4a.utils.translate.ThreadTranslateController;
import com.gh4a.widget.EditorBottomSheet;
import com.gh4a.widget.ReactionBar;
import com.meisolsson.githubsdk.model.GitHubCommentBase;
import com.meisolsson.githubsdk.model.Issue;
import com.meisolsson.githubsdk.model.Review;
import com.meisolsson.githubsdk.model.IssueEventType;
import com.meisolsson.githubsdk.model.Label;
import com.meisolsson.githubsdk.model.Reaction;
import com.meisolsson.githubsdk.model.Reactions;
import com.meisolsson.githubsdk.model.User;
import com.meisolsson.githubsdk.model.request.CommentRequest;
import com.meisolsson.githubsdk.model.request.ReactionRequest;
import com.meisolsson.githubsdk.service.issues.IssueCommentService;
import com.meisolsson.githubsdk.service.reactions.ReactionService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.recyclerview.widget.RecyclerView;
import io.reactivex.Single;
import retrofit2.Response;

public abstract class IssueFragmentBase extends ListDataBaseFragment<TimelineItem> implements
        View.OnClickListener, TimelineItemAdapter.OnCommentAction,
        ConfirmationDialogFragment.Callback,
        EditorBottomSheet.Callback, EditorBottomSheet.Listener,
        ReactionBar.Callback, ReactionBar.Item, ReactionBar.ReactionDetailsCache.Listener,
        ThreadTranslateController.Host {
    protected static final List<IssueEventType> INTERESTING_EVENTS = Arrays.asList(
            IssueEventType.Closed, IssueEventType.Reopened, IssueEventType.Merged,
            IssueEventType.Referenced, IssueEventType.Assigned, IssueEventType.Unassigned,
            IssueEventType.Labeled, IssueEventType.Unlabeled, IssueEventType.Locked,
            IssueEventType.Unlocked, IssueEventType.Milestoned, IssueEventType.Demilestoned,
            IssueEventType.Renamed, IssueEventType.HeadRefDeleted, IssueEventType.HeadRefRestored,
            IssueEventType.HeadRefForcePushed, IssueEventType.AutoMergeDisabled,
            IssueEventType.AutoMergeEnabled, IssueEventType.AutoRebaseEnabled,
            IssueEventType.AutoSquashEnabled, IssueEventType.AddedToMergeQueue,
            IssueEventType.RemovedFromMergeQueue, IssueEventType.CommentDeleted,
            IssueEventType.ReviewRequested, IssueEventType.ReviewRequestRemoved,
            IssueEventType.ConvertToDraft, IssueEventType.ReadyForReview,
            IssueEventType.ReviewDismissed, IssueEventType.CrossReferenced,
            IssueEventType.Transferred, IssueEventType.Commented
    );

    protected View mListHeaderView;
    protected Issue mIssue;
    protected String mRepoOwner;
    protected String mRepoName;
    private IntentUtils.InitialCommentMarker mInitialComment;
    private boolean mIsCollaborator;
    private boolean mListShown;
    private ReactionBar.AddReactionMenuHelper mReactionMenuHelper;
    private final ReactionBar.ReactionDetailsCache mReactionDetailsCache =
            new ReactionBar.ReactionDetailsCache(this);
    private TimelineItemAdapter mAdapter;
    private HttpImageGetter mImageGetter;
    private EditorBottomSheet mBottomSheet;
    private CommentTranslator mCommentTranslator;
    private MenuItem mTranslateThreadItem;
    private TextView mTranslateActionView;
    private final ThreadTranslateController mThreadController =
            new ThreadTranslateController(this);

    protected final ActivityResultLauncher<Intent> mEditLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultHelpers.ActivityResultSuccessCallback(() -> {
                reloadEvents(true);
                getActivity().setResult(Activity.RESULT_OK);
            }));

    protected static Bundle buildArgs(String repoOwner, String repoName,
            Issue issue, boolean isCollaborator, IntentUtils.InitialCommentMarker initialComment) {
        Bundle args = new Bundle();
        args.putString("owner", repoOwner);
        args.putString("repo", repoName);
        args.putParcelable("issue", issue);
        args.putBoolean("collaborator", isCollaborator);
        args.putParcelable("initial_comment", initialComment);
        return args;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        mRepoOwner = args.getString("owner");
        mRepoName = args.getString("repo");
        mIssue = args.getParcelable("issue");
        mIsCollaborator = args.getBoolean("collaborator");
        mInitialComment = args.getParcelable("initial_comment");
        args.remove("initial_comment");

        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View listContent = super.onCreateView(inflater, container, savedInstanceState);
        View v = inflater.inflate(R.layout.comment_list, container, false);

        FrameLayout listContainer = v.findViewById(R.id.list_container);
        listContainer.addView(listContent);

        mBottomSheet = v.findViewById(R.id.bottom_sheet);
        mBottomSheet.setCallback(this);
        mBottomSheet.setResizingView(listContainer);
        mBottomSheet.setListener(this);

        mImageGetter = new HttpImageGetter(inflater.getContext());
        updateCommentSectionVisibility(v);
        updateCommentLockState();

        return v;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mCommentTranslator = new CommentTranslator();
        if (mInitialComment == null) {
            // We want to make the user able to read the issue/PR while the rest of the conversation is still loading,
            // but at the same time we want to avoid item pop-in when the conversation loads quickly
            View contentContainer = view.findViewById(R.id.content_container);
            contentContainer.postDelayed(
                    () -> UiUtils.updateViewVisibility(contentContainer, isResumed(), true),
                    800);
        }

        BaseActivity activity = getBaseActivity();
        activity.addAppBarOffsetListener(mBottomSheet);
        mBottomSheet.post(() -> {
            // Fix an issue where the bottom sheet is initially located outside of the visible screen area
            mBottomSheet.resetPeekHeight(activity.getAppBarTotalScrollRange());
        });

        fillData();
        fillLabels(mIssue.labels());
        updateCommentLockState();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mCommentTranslator != null) {
            mCommentTranslator.destroy();
            mCommentTranslator = null;
        }
        mReactionDetailsCache.destroy();
        mImageGetter.destroy();
        mImageGetter = null;
        if (mAdapter != null) {
            mAdapter.destroy();
            mAdapter = null;
        }

        getBaseActivity().removeAppBarOffsetListener(mBottomSheet);
    }

    @Override
    protected void onRecyclerViewInflated(RecyclerView view, LayoutInflater inflater) {
        super.onRecyclerViewInflated(view, inflater);

        mListHeaderView = inflater.inflate(R.layout.issue_comment_list_header, view, false);
        mAdapter.setHeaderView(mListHeaderView);
        View loadingView = inflater.inflate(R.layout.list_loading_view, view, false);
        showLoadingIndicator(loadingView);
    }

    @Override
    public boolean onBackPressed() {
        if (mBottomSheet != null && mBottomSheet.isInAdvancedMode()) {
            mBottomSheet.setAdvancedMode(false);
            return true;
        }
        return false;
    }

    @Override
    public void onRefresh() {
        if (mListHeaderView != null) {
            getActivity().invalidateOptionsMenu();
            fillLabels(null);
        }
        if (mImageGetter != null) {
            mImageGetter.clearHtmlCache();
        }
        mReactionDetailsCache.clear();
        super.onRefresh();
    }

    @Override
    public void onResume() {
        super.onResume();
        mImageGetter.resume();
        mAdapter.resume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mImageGetter.pause();
        mAdapter.pause();
    }

    @Override
    public boolean canChildScrollUp() {
        return (mBottomSheet != null && mBottomSheet.isExpanded()) || super.canChildScrollUp();
    }

    @Override
    public CoordinatorLayout getRootLayout() {
        return getBaseActivity().getRootLayout();
    }

    @Override
    protected void setHighlightColors(int colorAttrId, int statusBarColorAttrId) {
        super.setHighlightColors(colorAttrId, statusBarColorAttrId);
        mBottomSheet.setHighlightColor(colorAttrId);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.issue_fragment_menu, menu);

        MenuItem reactItem = menu.findItem(R.id.react);
        if (!Gh4Application.get().isAuthorized() || isLocked()) {
            reactItem.setVisible(false);
        } else {
            inflater.inflate(R.menu.reaction_menu, reactItem.getSubMenu());
            mReactionMenuHelper = new ReactionBar.AddReactionMenuHelper(getActivity(),
                    reactItem.getSubMenu(), this, this, mReactionDetailsCache);
            mReactionMenuHelper.startLoadingIfNeeded();
        }

        mTranslateThreadItem = menu.findItem(R.id.translate_thread);
        // Direct toolbar button (not hidden in the overflow menu) so it
        // supports tap-to-translate and long-press-to-pick-provider.
        mTranslateThreadItem.setActionView(R.layout.translate_action_view);
        View translateActionView = mTranslateThreadItem.getActionView();
        translateActionView.setOnClickListener(v -> mThreadController.toggle());
        translateActionView.setOnLongClickListener(v -> {
            mThreadController.onLongPress();
            return true;
        });
        mTranslateActionView = (TextView) translateActionView;
        updateTranslateThreadItem();
    }

    private void updateTranslateThreadItem() {
        if (mTranslateActionView == null) {
            return;
        }
        switch (mThreadController.getState()) {
            case ThreadTranslateController.STATE_TRANSLATING:
                mTranslateActionView.setText(getString(R.string.translate_thread_progress,
                        mThreadController.getDone(), mThreadController.getTotal()));
                break;
            case ThreadTranslateController.STATE_DONE:
                mTranslateActionView.setText(R.string.show_original);
                break;
            default:
                mTranslateActionView.setText(R.string.translate);
                break;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mReactionMenuHelper != null && mReactionMenuHelper.onItemClick(item)) {
            return true;
        }
        if (item.getItemId() == R.id.translate_thread) {
            // Fallback: taps normally go to the action view's own listener.
            mThreadController.toggle();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void reloadEvents(boolean alsoClearCaches) {
        if (mAdapter != null && !alsoClearCaches) {
            // Don't clear adapter's cache, we're only interested in the new event
            mAdapter.suppressCacheClearOnNextClear();
        }
        super.onRefresh();
    }

    @Override
    protected RootAdapter<TimelineItem, ? extends RecyclerView.ViewHolder> onCreateAdapter() {
        mAdapter = new TimelineItemAdapter(getActivity(), mRepoOwner, mRepoName, mIssue.number(),
                mIssue.pullRequest() != null, true, this);
        mAdapter.setLocked(isLocked());
        return mAdapter;
    }

    @Override
    protected void onAddData(RootAdapter<TimelineItem, ?> adapter, List<TimelineItem> data) {
        super.onAddData(adapter, data);
        if (mInitialComment != null) {
            for (int i = 0; i < data.size(); i++) {
                TimelineItem item = data.get(i);
                long itemId = 0;
                if (item instanceof TimelineItem.TimelineComment) {
                    itemId = ((TimelineItem.TimelineComment) item).comment().id();
                } else if (item instanceof TimelineItem.TimelineReview) {
                    itemId = ((TimelineItem.TimelineReview) item).review().id();
                }
                if (mInitialComment.matches(itemId, item.getCreatedAt())) {
                    scrollToAndHighlightPosition(i + 1 /* adjust for header view */);
                    break;
                }
            }
            mInitialComment = null;
        }

        updateMentionUsers();
        removeLoadingIndicator(adapter);

        // Newly paged-in comments follow the whole-thread translation mode.
        if (mThreadController.getState() != ThreadTranslateController.STATE_IDLE) {
            List<CommentTranslator.TranslatableItem> newItems = new ArrayList<>();
            for (TimelineItem item : data) {
                collectItem(item, newItems);
            }
            mThreadController.onNewItems(newItems);
        }
    }

    private void showLoadingIndicator(View loadingView) {
        loadingView.setVisibility(View.VISIBLE);
        mAdapter.setFooterView(loadingView, null);
    }

    private void removeLoadingIndicator(RootAdapter<TimelineItem, ?> adapter) {
        adapter.setFooterView(null, null);
    }

    @Override
    protected int getEmptyTextResId() {
        return 0;
    }

    @Override
    protected void updateEmptyState() {
        // we're never empty -> don't call super
    }

    @Override
    protected void setContentShown(boolean shown) {
        super.setContentShown(shown);
        mListShown = shown;
        updateCommentSectionVisibility(getView());
    }

    private void updateCommentSectionVisibility(View v) {
        if (v == null) {
            return;
        }

        int commentVisibility = mListShown && Gh4Application.get().isAuthorized()
                ? View.VISIBLE : View.GONE;
        mBottomSheet.setVisibility(commentVisibility);
    }

    private boolean isLocked() {
        return mIssue.locked() && !mIsCollaborator;
    }

    private void updateMentionUsers() {
        Set<User> users = mAdapter.getUsers();
        if (mIssue.user() != null) {
            users.add(mIssue.user());
        }
        mBottomSheet.setMentionUsers(users);
    }

    private void updateCommentLockState() {
        mBottomSheet.setLocked(isLocked(), R.string.comment_editor_locked_hint);
    }

    private void fillData() {
        ImageView ivGravatar = mListHeaderView.findViewById(R.id.iv_gravatar);
        AvatarHandler.assignAvatar(ivGravatar, mIssue.user());
        ivGravatar.setTag(mIssue.user());
        ivGravatar.setOnClickListener(this);

        TextView tvExtra = mListHeaderView.findViewById(R.id.tv_extra);
        tvExtra.setText(ApiHelpers.getUserLoginWithType(getActivity(), mIssue.user()));
        tvExtra.setOnClickListener(this);
        tvExtra.setTag(mIssue.user());

        TextView tvTimestamp = mListHeaderView.findViewById(R.id.tv_timestamp);
        tvTimestamp.setText(StringUtils.formatRelativeTime(getActivity(),
                mIssue.createdAt(), true));

        String body = mIssue.bodyHtml();
        TextView descriptionView = mListHeaderView.findViewById(R.id.tv_desc);
        if (!StringUtils.isBlank(body)) {
            bindIssueBody(descriptionView, body);

            if (!isLocked()) {
                descriptionView.setCustomSelectionActionModeCallback(
                        new UiUtils.QuoteActionModeCallback(descriptionView) {
                    @Override
                    public void onTextQuoted(CharSequence text) {
                        quoteText(text);
                    }
                });
            } else {
                descriptionView.setCustomSelectionActionModeCallback(null);
            }
        } else {
            SpannableString noDescriptionString = new SpannableString(getString(R.string.issue_no_description));
            noDescriptionString.setSpan(new StyleSpan(Typeface.ITALIC), 0, noDescriptionString.length(), 0);
            descriptionView.setText(noDescriptionString);
        }

        View milestoneGroup = mListHeaderView.findViewById(R.id.milestone_container);
        if (mIssue.milestone() != null) {
            TextView tvMilestone = mListHeaderView.findViewById(R.id.tv_milestone);
            tvMilestone.setText(mIssue.milestone().title());
            milestoneGroup.setVisibility(View.VISIBLE);
        } else {
            milestoneGroup.setVisibility(View.GONE);
        }

        View assigneeGroup = mListHeaderView.findViewById(R.id.assignee_container);
        List<User> assignees = mIssue.assignees();
        if (assignees != null && !assignees.isEmpty()) {
            ViewGroup assigneeContainer = mListHeaderView.findViewById(R.id.assignee_list);
            LayoutInflater inflater = getLayoutInflater();
            assigneeContainer.removeAllViews();
            for (User assignee : assignees) {
                View row = inflater.inflate(R.layout.row_assignee, assigneeContainer, false);
                TextView tvAssignee = row.findViewById(R.id.tv_assignee);
                tvAssignee.setText(ApiHelpers.getUserLogin(getActivity(), assignee));

                ImageView ivAssignee = row.findViewById(R.id.iv_assignee);
                AvatarHandler.assignAvatar(ivAssignee, assignee);
                ivAssignee.setTag(assignee);
                ivAssignee.setOnClickListener(this);

                assigneeContainer.addView(row);
            }
            assigneeGroup.setVisibility(View.VISIBLE);
        } else {
            assigneeGroup.setVisibility(View.GONE);
        }

        ReactionBar reactions = mListHeaderView.findViewById(R.id.reactions);
        reactions.setCallback(this, this);
        reactions.setDetailsCache(mReactionDetailsCache);
        reactions.setReactions(mIssue.reactions());

        assignHighlightColor();
        bindSpecialViews(mListHeaderView);
    }

    /** Bind the issue/PR body, preferring the translated HTML when available. */
    private void bindIssueBody(TextView descriptionView, String bodyHtml) {
        String translated = mCommentTranslator != null ? mCommentTranslator.getTranslatedHtml(mIssue.id()) : null;
        if (translated != null) {
            mImageGetter.bind(descriptionView, translated, mIssue.id() + "-translated");
        } else {
            mImageGetter.bind(descriptionView, bodyHtml, mIssue.id());
        }
    }

    /** Refresh the issue/PR body after its translation finished or was toggled off. */
    private void refreshIssueBody() {
        if (mListHeaderView == null || mIssue == null || mImageGetter == null) {
            return;
        }
        String body = mIssue.bodyHtml();
        if (!StringUtils.isBlank(body)) {
            TextView descriptionView = mListHeaderView.findViewById(R.id.tv_desc);
            bindIssueBody(descriptionView, body);
        }
    }

    @Override
    public void translateBody(long id, String bodyHtml) {
        if (getActivity() == null || mCommentTranslator == null) {
            return;
        }
        mCommentTranslator.toggleTranslation(getActivity(), id, bodyHtml, () -> {
            if (id == mIssue.id()) {
                refreshIssueBody();
            }
            if (mAdapter != null) {
                mAdapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    public String getTranslatedBody(long id) {
        return mCommentTranslator != null ? mCommentTranslator.getTranslatedHtml(id) : null;
    }

    // ThreadTranslateController.Host

    @Override
    public CommentTranslator getTranslator() {
        return mCommentTranslator;
    }

    @Override
    public List<CommentTranslator.TranslatableItem> collectThreadItems() {
        List<CommentTranslator.TranslatableItem> items = new ArrayList<>();
        if (mIssue != null && !StringUtils.isBlank(mIssue.bodyHtml())) {
            items.add(new CommentTranslator.TranslatableItem(mIssue.id(), mIssue.bodyHtml()));
        }
        if (mAdapter != null) {
            for (int i = 0; i < mAdapter.getCount(); i++) {
                collectItem(mAdapter.getItem(i), items);
            }
        }
        return items;
    }

    private void collectItem(TimelineItem item,
            List<CommentTranslator.TranslatableItem> items) {
        if (item instanceof TimelineItem.TimelineComment) {
            GitHubCommentBase comment = ((TimelineItem.TimelineComment) item).comment();
            if (!StringUtils.isBlank(comment.bodyHtml())) {
                items.add(new CommentTranslator.TranslatableItem(
                        comment.id(), comment.bodyHtml()));
            }
        } else if (item instanceof TimelineItem.TimelineReview) {
            Review review = ((TimelineItem.TimelineReview) item).review();
            if (!StringUtils.isBlank(review.bodyHtml())) {
                items.add(new CommentTranslator.TranslatableItem(
                        review.id(), review.bodyHtml()));
            }
        }
    }

    @Override
    public void onTranslationsChanged() {
        refreshIssueBody();
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onTranslateStateChanged() {
        updateTranslateThreadItem();
    }

    private void fillLabels(List<Label> labels) {
        View labelGroup = mListHeaderView.findViewById(R.id.label_container);
        if (labels != null && !labels.isEmpty()) {
            TextView labelView = mListHeaderView.findViewById(R.id.labels);
            labelView.setText(UiUtils.formatLabelList(getActivity(), labels));
            labelGroup.setVisibility(View.VISIBLE);
        } else {
            labelGroup.setVisibility(View.GONE);
        }
    }

    @Override
    public Object getCacheKey() {
        return mIssue.id();
    }

    @Override
    public Single<List<Reaction>> loadReactionDetails(ReactionBar.Item item, boolean bypassCache) {
        var service = ServiceFactory.getForFullPagedLists(ReactionService.class, bypassCache);
        return ApiHelpers.PageIterator
                .toSingle(page -> service.getIssueReactions(mRepoOwner, mRepoName, mIssue.number(), page));
    }

    @Override
    public boolean canAddReaction() {
        return !isLocked();
    }

    @Override
    public Single<Reaction> addReaction(ReactionBar.Item item, String content) {
        ReactionService service = ServiceFactory.get(ReactionService.class, false);
        ReactionRequest request = ReactionRequest.builder().content(content).build();
        return service.createIssueReaction(mRepoOwner, mRepoName, mIssue.number(), request)
                .map(ApiHelpers::throwOnFailure)
                .compose(RxUtils.wrapWithErrorSnackbar(getBaseActivity(), R.string.add_reaction_error));
    }

    @Override
    public Single<Boolean> deleteReaction(ReactionBar.Item item, long reactionId) {
        ReactionService service = ServiceFactory.get(ReactionService.class, false);
        return service.deleteIssueReaction(mRepoOwner, mRepoName, mIssue.number(), reactionId)
                .map(ApiHelpers::mapToTrueOnSuccess)
                .compose(RxUtils.wrapWithErrorSnackbar(getBaseActivity(), R.string.remove_reaction_error));
    }

    @Override
    public Single<List<Reaction>> loadReactionDetails(final GitHubCommentBase comment,
            boolean bypassCache) {
        var service = ServiceFactory.getForFullPagedLists(ReactionService.class, bypassCache);
        return ApiHelpers.PageIterator
                .toSingle(page -> service.getIssueCommentReactions(mRepoOwner, mRepoName, comment.id(), page));
    }

    @Override
    public Single<Reaction> addReaction(GitHubCommentBase comment, String content) {
        ReactionService service = ServiceFactory.get(ReactionService.class, false);
        ReactionRequest request = ReactionRequest.builder().content(content).build();
        return service.createIssueCommentReaction(mRepoOwner, mRepoName, comment.id(), request)
                .map(ApiHelpers::throwOnFailure)
                .compose(RxUtils.wrapWithErrorSnackbar(getBaseActivity(), R.string.add_reaction_error));
    }

    @Override
    public Single<Boolean> deleteReaction(GitHubCommentBase comment, long reactionId) {
        ReactionService service = ServiceFactory.get(ReactionService.class, false);
        return service.deleteIssueCommentReaction(mRepoOwner, mRepoName, comment.id(), reactionId)
                .map(ApiHelpers::mapToTrueOnSuccess)
                .compose(RxUtils.wrapWithErrorSnackbar(getBaseActivity(), R.string.remove_reaction_error));
    }

    @Override
    public void onReactionsUpdated(ReactionBar.Item item, Reactions reactions) {
        mIssue = mIssue.toBuilder().reactions(reactions).build();
        if (mListHeaderView != null) {
            ReactionBar bar = mListHeaderView.findViewById(R.id.reactions);
            bar.setReactions(reactions);
        }
        if (mReactionMenuHelper != null) {
            mReactionMenuHelper.updateMenuItems();
            getActivity().invalidateOptionsMenu();
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.tv_extra) {
            User user = (User) v.getTag();
            addText(StringUtils.formatMention(getContext(), user));
            return;
        }
        Intent intent = UserActivity.makeIntent(getActivity(), (User) v.getTag());
        if (intent != null) {
            startActivity(intent);
        }
    }

    @Override
    public void quoteText(CharSequence text) {
        mBottomSheet.addQuote(text);
    }

    @Override
    public void addText(CharSequence text) {
        mBottomSheet.addText(text);
    }

    @Override
    public Single<?> onEditorDoSend(String comment) {
        IssueCommentService service = ServiceFactory.get(IssueCommentService.class, false);
        CommentRequest request = CommentRequest.builder().body(comment).build();
        return service.createIssueComment(mRepoOwner, mRepoName, mIssue.number(), request)
                .map(ApiHelpers::throwOnFailure);
    }

    @Override
    public void onEditorTextSent() {
        // reload comments
        if (isAdded()) {
            reloadEvents(false);
        }
        getActivity().setResult(Activity.RESULT_OK);
    }

    @Override
    public int getEditorErrorMessageResId() {
        return R.string.issue_error_comment;
    }

    @Override
    public void deleteComment(final GitHubCommentBase comment) {
        ConfirmationDialogFragment.show(this, R.string.delete_comment_message,
                R.string.delete, comment, "deleteconfirm");
    }

    @Override
    public void onConfirmed(String tag, Parcelable data) {
        GitHubCommentBase comment = (GitHubCommentBase) data;
        handleDeleteComment(comment);
    }

    @Override
    public String getShareSubject(GitHubCommentBase comment) {
        return getString(R.string.share_comment_subject, comment.id(), mIssue.number(),
                mRepoOwner + "/" + mRepoName);
    }

    @Override
    public void onToggleAdvancedMode(boolean advancedMode) {
        getBaseActivity().collapseAppBar();
        getBaseActivity().setAppBarLocked(advancedMode);
        mBottomSheet.resetPeekHeight(0);
    }

    @Override
    public void onScrollingInBasicEditor(boolean scrolling) {
        getBaseActivity().setAppBarLocked(scrolling);
    }

    @Override
    public void onReplyCommentSelected(long replyToId) {
        // Not used in this screen
    }

    @Override
    public long getSelectedReplyCommentId() {
        // Not used in this screen
        return 0;
    }

    protected abstract void bindSpecialViews(View headerView);
    protected abstract void assignHighlightColor();
    protected abstract Single<Response<Void>> doDeleteComment(GitHubCommentBase comment);

    private void handleDeleteComment(GitHubCommentBase comment) {
        doDeleteComment(comment)
                .map(ApiHelpers::mapToBooleanOrThrowOnFailure)
                .compose(RxUtils.wrapForBackgroundTask(getBaseActivity(),
                        R.string.deleting_msg, R.string.error_delete_comment))
                .subscribe(result -> {
                    reloadEvents(false);
                    getActivity().setResult(Activity.RESULT_OK);
                }, error -> handleActionFailure("Deleting comment failed", error));
    }
}
