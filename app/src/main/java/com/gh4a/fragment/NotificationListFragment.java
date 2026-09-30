package com.gh4a.fragment;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import com.gh4a.Gh4Application;
import com.gh4a.R;
import com.gh4a.ServiceFactory;
import com.gh4a.activities.RepositoryActivity;
import com.gh4a.adapter.NotificationAdapter;
import com.gh4a.adapter.RootAdapter;
import com.gh4a.model.NotificationHolder;
import com.gh4a.model.NotificationListLoadResult;
import com.gh4a.resolver.BrowseFilter;
import com.gh4a.utils.ApiHelpers;
import com.gh4a.utils.IntentUtils;
import com.gh4a.utils.RxUtils;
import com.gh4a.utils.SingleFactory;
import com.gh4a.worker.NotificationsWorker;
import com.meisolsson.githubsdk.model.NotificationSubject;
import com.meisolsson.githubsdk.model.NotificationThread;
import com.meisolsson.githubsdk.model.Repository;
import com.meisolsson.githubsdk.model.request.NotificationReadRequest;
import com.meisolsson.githubsdk.model.request.activity.SubscriptionRequest;
import com.meisolsson.githubsdk.service.activity.NotificationService;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import retrofit2.Response;

public class NotificationListFragment extends LoadingListFragmentBase implements
        RootAdapter.OnItemClickListener<NotificationHolder>,
        ConfirmationDialogFragment.Callback,
        NotificationAdapter.OnNotificationActionCallback {
    public static final String EXTRA_INITIAL_REPO_OWNER = "initial_notification_repo_owner";
    public static final String EXTRA_INITIAL_REPO_NAME = "initial_notification_repo_name";

    public static NotificationListFragment newInstance() {
        return new NotificationListFragment();
    }

    private NotificationAdapter mAdapter;
    private Date mNotificationsLoadTime;
    private MenuItem mMarkAllAsReadMenuItem;
    private ParentCallback mCallback;
    private boolean mAll;
    private boolean mParticipating;

    private final List<NotificationThread> mLoadedNotifications = new ArrayList<>();
    private final CompositeDisposable mLoadDisposables = new CompositeDisposable();
    private boolean mFirstPagePending;
    private boolean mHasRestoredData;
    private boolean mLoadInProgress;

    private static final String STATE_KEY_NOTIFICATIONS = "loaded_notifications";
    private static final String STATE_KEY_LOAD_TIME = "notifications_load_time";
    private static final String STATE_KEY_LOAD_IN_PROGRESS = "load_in_progress";
    private static final String STATE_KEY_TRUNCATED = "list_truncated";

    public interface ParentCallback {
        void setNotificationsIndicatorVisible(boolean visible);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        if (!(context instanceof ParentCallback)) {
            throw new IllegalStateException("context must implement ParentCallback");
        }

        mCallback = (ParentCallback) context;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        if (savedInstanceState != null) {
            // Rotation / process recreation: restore the already loaded
            // notifications instead of refetching all pages from the network
            // (the old Loader-based implementation retained them, too).
            ArrayList<NotificationThread> restored = savedInstanceState
                    .getParcelableArrayList(STATE_KEY_NOTIFICATIONS);
            long loadTime = savedInstanceState.getLong(STATE_KEY_LOAD_TIME, 0);
            boolean loadWasInProgress =
                    savedInstanceState.getBoolean(STATE_KEY_LOAD_IN_PROGRESS, false);
            boolean truncated =
                    savedInstanceState.getBoolean(STATE_KEY_TRUNCATED, false);
            if (loadWasInProgress || truncated) {
                // Rotation happened mid-load, or the saved list was capped to
                // avoid TransactionTooLargeException: either way the saved data
                // is incomplete. Discard it so onStart() performs a full fresh
                // load instead of presenting a partial list as complete.
                mNotificationsLoadTime = null;
            } else if (restored != null && !restored.isEmpty()) {
                mLoadedNotifications.addAll(restored);
                mNotificationsLoadTime = loadTime != 0 ? new Date(loadTime) : null;
                mHasRestoredData = true;
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // Cap the saved list: a huge Parcelable list would exceed the ~1MB
        // Binder transaction limit and crash rotation with
        // TransactionTooLargeException. 200 items are plenty to restore the
        // visible list; the rest reloads on demand.
        int saveCount = Math.min(mLoadedNotifications.size(), 200);
        outState.putParcelableArrayList(STATE_KEY_NOTIFICATIONS,
                new ArrayList<>(mLoadedNotifications.subList(0, saveCount)));
        outState.putBoolean(STATE_KEY_TRUNCATED, mLoadedNotifications.size() > saveCount);
        outState.putLong(STATE_KEY_LOAD_TIME,
                mNotificationsLoadTime != null ? mNotificationsLoadTime.getTime() : 0);
        outState.putBoolean(STATE_KEY_LOAD_IN_PROGRESS, mLoadInProgress);
    }

    @Override
    public void onStart() {
        super.onStart();
        long lastCheck = NotificationsWorker.getLastCheckTimestamp(getActivity());
        long lastFetch = mNotificationsLoadTime != null ? mNotificationsLoadTime.getTime() : 0;
        if (lastFetch == 0 || (lastCheck != 0 && lastCheck > lastFetch)) {
            setContentShown(false);
            // If we know our last fetch is stale, force the reload to make to to not get
            // outdated notifications
            loadNotifications(lastFetch != 0);
            NotificationsWorker.markNotificationsAsSeen(getActivity());
        }
    }

    @Override
    protected int getEmptyTextResId() {
        return R.string.no_notifications_found;
    }

    @Override
    public void onRefresh() {
        if (mAdapter != null) {
            mAdapter.clear();
        }
        setContentShown(false);
        loadNotifications(true);
        updateMenuItemVisibility();
    }

    @Override
    protected void onRecyclerViewInflated(RecyclerView view, LayoutInflater inflater) {
        super.onRecyclerViewInflated(view, inflater);
        mAdapter = new NotificationAdapter(getActivity(), this);
        mAdapter.setOnItemClickListener(this);
        view.setAdapter(mAdapter);
        if (mHasRestoredData) {
            mHasRestoredData = false;
            NotificationListLoadResult result =
                    SingleFactory.notificationsToResult(mLoadedNotifications);
            mAdapter.addAll(result.notifications);
            updateMenuItemVisibility();
            if (!mAll && !mParticipating) {
                mCallback.setNotificationsIndicatorVisible(!result.notifications.isEmpty());
            }
            setContentShown(true);
        }
        updateEmptyState();
    }

    @Override
    protected boolean hasDividers() {
        return false;
    }

    @Override
    protected boolean hasCards() {
        return true;
    }

    @Override
    public void onItemClick(NotificationHolder item) {
        if (item.notification == null) {
            var intent = RepositoryActivity.makeIntent(getActivity(), item.repository);
            startActivity(intent);
            return;
        }

        NotificationSubject subject = item.notification.subject();
        String url = subject.url();
        final Intent intent;
        if (url != null) {
            Uri uri = ApiHelpers.normalizeUri(Uri.parse(url));
            intent = BrowseFilter.makeRedirectionIntent(getActivity(), uri,
                    new IntentUtils.InitialCommentMarker(item.notification.updatedAt()));
        } else {
            intent = null;
        }

        if (intent != null) {
            markAsRead(null, item.notification);
            startActivity(intent);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.notification_list_menu, menu);
        mMarkAllAsReadMenuItem = menu.findItem(R.id.mark_all_as_read);
        updateMenuItemVisibility();

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        switch (itemId) {
            case R.id.mark_all_as_read:
                ConfirmationDialogFragment.show(this, R.string.mark_all_as_read_question,
                        R.string.mark_all_as_read, null, "markallreadconfirm");
                return true;
            case R.id.notification_filter_unread:
            case R.id.notification_filter_all:
            case R.id.notification_filter_participating:
                mAll = itemId == R.id.notification_filter_all;
                mParticipating = itemId == R.id.notification_filter_participating;
                item.setChecked(true);
                onRefresh();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void markAsRead(NotificationHolder notificationHolder) {
        if (notificationHolder.notification == null) {
            final Repository repository = notificationHolder.repository;

            String title = getString(R.string.mark_repository_as_read_question,
                    ApiHelpers.formatRepoName(getActivity(), repository));

            ConfirmationDialogFragment.show(this, title,
                    R.string.mark_as_read, repository, "markreadconfirm");
        } else {
            markAsRead(null, notificationHolder.notification);
        }
    }

    @Override
    public void unsubscribe(NotificationHolder notificationHolder) {
        NotificationThread notification = notificationHolder.notification;
        NotificationService service = ServiceFactory.get(NotificationService.class, false);
        SubscriptionRequest request = SubscriptionRequest.builder()
                .ignored(true)
                .build();
        service.setNotificationThreadSubscription(notification.id(), request)
                .map(ApiHelpers::throwOnFailure)
                .compose(RxUtils::doInBackground)
                .subscribe(result -> Toast.makeText(getContext(), R.string.unsubscribe_success, Toast.LENGTH_SHORT).show(),
                        error -> handleActionFailure("Unsubscribing notification failed", error));
    }

    @Override
    public void onConfirmed(String tag, Parcelable data) {
        Repository repository = (Repository) data;
        markAsRead(repository, null);
    }

    private void updateMenuItemVisibility() {
        if (mMarkAllAsReadMenuItem == null) {
            return;
        }

        mMarkAllAsReadMenuItem.setVisible(isContentShown() && mAdapter.hasUnreadNotifications());
    }

    /**
     * Diff callback for incremental notification list updates. Items are
     * repository group headers (notification == null) or individual
     * notifications; identity is by repo full name / notification id.
     */
    private static class NotificationDiffCallback extends DiffUtil.Callback {
        private final List<NotificationHolder> mOldItems;
        private final List<NotificationHolder> mNewItems;

        NotificationDiffCallback(List<NotificationHolder> oldItems,
                List<NotificationHolder> newItems) {
            mOldItems = oldItems;
            mNewItems = newItems;
        }

        @Override
        public int getOldListSize() {
            return mOldItems.size();
        }

        @Override
        public int getNewListSize() {
            return mNewItems.size();
        }

        @Override
        public boolean areItemsTheSame(int oldPos, int newPos) {
            NotificationHolder oldItem = mOldItems.get(oldPos);
            NotificationHolder newItem = mNewItems.get(newPos);
            if (oldItem.notification == null || newItem.notification == null) {
                return oldItem.notification == null && newItem.notification == null
                        && oldItem.repository.fullName().equals(newItem.repository.fullName());
            }
            return oldItem.notification.id().equals(newItem.notification.id());
        }

        @Override
        public boolean areContentsTheSame(int oldPos, int newPos) {
            NotificationHolder oldItem = mOldItems.get(oldPos);
            NotificationHolder newItem = mNewItems.get(newPos);
            return oldItem.isRead() == newItem.isRead()
                    && oldItem.isLastRepositoryNotification()
                            == newItem.isLastRepositoryNotification();
        }
    }

    private void scrollToInitialNotification(List<NotificationHolder> notifications) {
        Bundle extras = getActivity().getIntent().getExtras();
        if (extras == null) {
            return;
        }

        String repoOwner = extras.getString(EXTRA_INITIAL_REPO_OWNER);
        String repoName = extras.getString(EXTRA_INITIAL_REPO_NAME);
        extras.remove(EXTRA_INITIAL_REPO_OWNER);
        extras.remove(EXTRA_INITIAL_REPO_NAME);

        if (repoOwner == null || repoName == null) {
            return;
        }

        for (int i = 0; i < notifications.size(); i++) {
            NotificationHolder holder = notifications.get(i);
            if (holder.notification == null) {
                Repository repo = holder.repository;
                if (repoOwner.equals(repo.owner().login())
                        && repoName.equals(repo.name())) {
                    scrollToAndHighlightPosition(i);
                    break;
                }
            }
        }
    }

    private void markAsRead(Repository repository, NotificationThread notification) {
        NotificationService service = ServiceFactory.get(NotificationService.class, false);
        final Single<Response<Void>> responseSingle;
        if (notification != null) {
            if (!notification.unread()) {
                return;
            }
            responseSingle = service.markNotificationRead(notification.id());
        } else {
            NotificationReadRequest request = NotificationReadRequest.builder()
                    .lastReadAt(mNotificationsLoadTime)
                    .build();
            if (repository != null) {
                responseSingle = service.markAllRepositoryNotificationsRead(
                        repository.owner().login(), repository.name(), request);
            } else {
                responseSingle = service.markAllNotificationsRead(request);
            }
        }

        responseSingle
                .map(ApiHelpers::mapToBooleanOrThrowOnFailure)
                .compose(RxUtils::doInBackground)
                .subscribe(result -> handleMarkAsRead(repository, notification),
                        error -> handleActionFailure("Mark notifications as read failed", error));
    }

    private void handleMarkAsRead(Repository repository, NotificationThread notification) {
        if (mAdapter.markAsRead(repository, notification)) {
            if (!mAll && !mParticipating) {
                mCallback.setNotificationsIndicatorVisible(false);
            }
        }
        updateMenuItemVisibility();
    }

    private void loadNotifications(boolean force) {
        // Progressive loading: render the first page as soon as it arrives instead of
        // waiting for all pages. The old code fetched every page sequentially before
        // showing anything, which felt slow with many notifications.
        mLoadedNotifications.clear();
        mLoadDisposables.clear();
        mFirstPagePending = true;
        mLoadInProgress = true;

        mLoadDisposables.add(SingleFactory.getNotificationsPaged(mAll, mParticipating, force)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doFinally(() -> mLoadInProgress = false)
                .subscribe(page -> {
                    if (!isAdded()) {
                        return;
                    }
                    mLoadedNotifications.addAll(page);
                    NotificationListLoadResult result =
                            SingleFactory.notificationsToResult(mLoadedNotifications);
                    mNotificationsLoadTime = result.loadTime;
                    // Incremental updates: each page used to trigger a full
                    // notifyDataSetChanged(), which dropped frames with many
                    // notifications. Diff against the previous list instead.
                    List<NotificationHolder> oldItems = new ArrayList<>(mAdapter.getCount());
                    for (int i = 0; i < mAdapter.getCount(); i++) {
                        oldItems.add(mAdapter.getItem(i));
                    }
                    DiffUtil.DiffResult diff = DiffUtil.calculateDiff(
                            new NotificationDiffCallback(oldItems, result.notifications));
                    mAdapter.replaceAllSilently(result.notifications);
                    diff.dispatchUpdatesTo(mAdapter);
                    setContentShown(true);
                    updateEmptyState();
                    updateMenuItemVisibility();
                    if (!mAll && !mParticipating) {
                        mCallback.setNotificationsIndicatorVisible(!result.notifications.isEmpty());
                    }
                    if (mFirstPagePending) {
                        mFirstPagePending = false;
                        scrollToInitialNotification(result.notifications);
                    }
                }, error -> {
                    if (mLoadedNotifications.isEmpty()) {
                        handleLoadFailure(error);
                    } else {
                        // First pages are already visible; a later page failing
                        // shouldn't wipe out the displayed list
                        Log.d(Gh4Application.LOG_TAG,
                                "Loading further notification pages failed", error);
                    }
                }));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mLoadDisposables.clear();
    }
}