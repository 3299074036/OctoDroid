package com.gh4a.fragment;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

/**
 * 0.2.2：批量移组的状态机（B-M7）。
 * <p>
 * 之前进度与失败列表只存在 Fragment 的局部变量里，Rx 链挂在 Fragment 的
 * mDisposables 上；旋转屏幕时 onDestroyView() 会 clear() 掉订阅、进度框
 * 随视图销毁，移动中途断掉、失败的也无法重试。现在状态（进度／失败／
 * 目标分组）存在 ViewModel 里：ViewModel 随 FragmentManager 在配置变更
 * 时保留，Rx 链不中断；Fragment 重建后重新观察同一份状态，把进度框
 * 与结果框按最新状态重建出来，失败重试也能继续。
 * <p>
 * ViewModel 只持数据、不持 Context/View/Dialog；对话框由 Fragment 按
 * 状态创建与销毁。
 */
public class StarBatchMoveViewModel extends ViewModel {
    /** 一次批量移动的运行状态（不可变快照，Fragment 只读）。 */
    public static final class State {
        /** 本轮总仓库数。 */
        public final int total;
        /** 已处理完的仓库数（成功＋失败）。 */
        public final int done;
        /** 本轮是否已跑完（全部成功或有失败）。 */
        public final boolean finished;
        /** 失败的仓库 fullName（只重试这些）。 */
        public final List<String> failed;
        /** 目标分组 id（空表示移出全部分组）。 */
        public final List<String> targetIds;
        /** 轮次 id：区分“新一轮”与“已展示过的结果”。 */
        public final long runId;

        State(int total, int done, boolean finished, List<String> failed,
                List<String> targetIds, long runId) {
            this.total = total;
            this.done = done;
            this.finished = finished;
            this.failed = failed;
            this.targetIds = targetIds;
            this.runId = runId;
        }
    }

    private final MutableLiveData<State> mState = new MutableLiveData<>();
    private final CompositeDisposable mDisposables = new CompositeDisposable();
    private long mRunId;
    /** 已被 Fragment 展示／处理过的 finished 轮次（旋转重建后不再重复弹框）。 */
    private long mConsumedRunId = -1;

    public LiveData<State> getState() {
        return mState;
    }

    /** 本轮结果是否已被 Fragment 处理过（旋转重建后不再重复弹结果框）。 */
    public boolean isResultConsumed(State state) {
        return state != null && state.runId == mConsumedRunId;
    }

    /** 标记本轮结果已处理（弹过结果框／Toast、或已退出多选）。 */
    public void markResultConsumed(State state) {
        if (state != null) {
            mConsumedRunId = state.runId;
        }
    }

    /**
     * 开始一轮批量移动（N 个仓库 = N 次顺序调用，官方无批量接口）。
     * 新一轮会取消上一轮未跑完的链（若有）。
     */
    public void start(List<String> repos, List<String> targetIds) {
        if (repos == null || repos.isEmpty()) {
            return;
        }
        mDisposables.clear();
        mRunId++;
        final List<String> reposCopy = new ArrayList<>(repos);
        final List<String> targetsCopy = targetIds != null
                ? new ArrayList<>(targetIds) : new ArrayList<>();
        final List<String> failed = new ArrayList<>();
        final long runId = mRunId;
        post(new State(reposCopy.size(), 0, false,
                new ArrayList<>(failed), targetsCopy, runId));
        moveNext(reposCopy, 0, targetsCopy, failed, runId);
    }

    /** 只重试上一轮失败的仓库（目标分组不变）。 */
    public void retryFailed() {
        State state = mState.getValue();
        if (state == null || !state.finished || state.failed.isEmpty()) {
            return;
        }
        start(new ArrayList<>(state.failed), state.targetIds);
    }

    /**
     * 0.2.4（58）：单次操作（入组单发／改名／删组）的结果。
     * 批量移组有进度框可重建；单发操作只有一个 Toast＋重拉，结果同样经
     * LiveData 发给旋转重建后的 Fragment，消费后不再重放。
     */
    public static final class SingleOp {
        /** 轮次 id：区分"新一次"与"已展示过的结果"。 */
        public final long runId;
        /** 是否成功。 */
        public final boolean success;
        /** 成功／失败时 Fragment 要弹的 Toast 文案（string res id）。 */
        public final int messageRes;
        /** 成功后要清掉的已展开官方列表 id（仅删组用，其余为 null）。 */
        public final String clearExpandedId;

        SingleOp(long runId, boolean success, int messageRes, String clearExpandedId) {
            this.runId = runId;
            this.success = success;
            this.messageRes = messageRes;
            this.clearExpandedId = clearExpandedId;
        }
    }

    private final MutableLiveData<SingleOp> mSingleOp = new MutableLiveData<>();
    private final CompositeDisposable mSingleOpDisposables = new CompositeDisposable();
    private long mSingleOpRunId;
    /** 已被 Fragment 展示／处理过的单发结果（旋转重建后不再重复弹 Toast）。 */
    private long mSingleOpConsumedRunId = -1;

    public LiveData<SingleOp> getSingleOp() {
        return mSingleOp;
    }

    /** 单发结果是否已被 Fragment 处理过。 */
    public boolean isSingleOpConsumed(SingleOp op) {
        return op != null && op.runId == mSingleOpConsumedRunId;
    }

    /** 标记单发结果已处理。 */
    public void markSingleOpConsumed(SingleOp op) {
        if (op != null) {
            mSingleOpConsumedRunId = op.runId;
        }
    }

    /**
     * 跑一次单发操作（入组单发／改名／删组）：Rx 链挂 ViewModel 自有的
     * CompositeDisposable，旋转不中断；结果经 LiveData 发给重建后的
     * Fragment（Toast＋重拉）。与批量移组用不同的 disposable 与轮次
     * 计数，互不干扰。
     */
    public void runSingleOp(Single<?> op, int successRes, int failureRes,
            String clearExpandedId) {
        mSingleOpRunId++;
        final long runId = mSingleOpRunId;
        mSingleOpDisposables.add(op
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(ignored -> mSingleOp.setValue(
                        new SingleOp(runId, true, successRes, clearExpandedId)),
                        error -> mSingleOp.setValue(
                                new SingleOp(runId, false, failureRes, null))));
    }

    private void moveNext(List<String> repos, int index, List<String> targetIds,
            List<String> failed, long runId) {
        if (runId != mRunId) {
            return; // 已被新一轮取代
        }
        if (index >= repos.size()) {
            post(new State(repos.size(), repos.size(), true,
                    new ArrayList<>(failed), targetIds, runId));
            return;
        }
        final String fullName = repos.get(index);
        final int done = index;
        mDisposables.add(UserListsGraphQL.assignRepo(fullName, targetIds)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(ok -> {
                    post(new State(repos.size(), done + 1, false,
                            new ArrayList<>(failed), targetIds, runId));
                    moveNext(repos, index + 1, targetIds, failed, runId);
                }, error -> {
                    failed.add(fullName);
                    post(new State(repos.size(), done + 1, false,
                            new ArrayList<>(failed), targetIds, runId));
                    moveNext(repos, index + 1, targetIds, failed, runId);
                }));
    }

    private void post(State state) {
        mState.setValue(state);
    }

    @Override
    protected void onCleared() {
        mDisposables.clear();
        mSingleOpDisposables.clear();
        super.onCleared();
    }
}
