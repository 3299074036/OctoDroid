package com.gh4a.fragment;

import android.os.Looper;

import com.gh4a.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.Single;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.MockedStatic;

/** StarBatchMoveViewModel：批量移组状态机（Robolectric 供 LiveData/主线程）。 */
@RunWith(RobolectricTestRunner.class)
public class StarBatchMoveViewModelTest {

    /** 泵主线程 looper 直到 SingleOp 出现或超时（observeOn mainThread 需它）。 */
    private static StarBatchMoveViewModel.SingleOp awaitSingleOp(
            StarBatchMoveViewModel vm) {
        return awaitSingleOp(vm, null);
    }

    /** 等待与 previous 不同的新 SingleOp；LiveData 旧值非空时不能直接返回。 */
    private static StarBatchMoveViewModel.SingleOp awaitSingleOp(
            StarBatchMoveViewModel vm, StarBatchMoveViewModel.SingleOp previous) {
        for (int i = 0; i < 100; i++) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            StarBatchMoveViewModel.SingleOp op = vm.getSingleOp().getValue();
            if (op != null && op != previous) {
                return op;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException ignored) {
            }
            Shadows.shadowOf(Looper.getMainLooper()).idle();
        }
        return vm.getSingleOp().getValue();
    }

    @Test
    public void state_initialValueIsNull() {
        StarBatchMoveViewModel vm = new StarBatchMoveViewModel();
        assertNull(vm.getState().getValue());
        assertNull(vm.getSingleOp().getValue());
        assertNull(vm.getStarLists().getValue());
        assertNotNull(vm.getState());
        assertNotNull(vm.getSingleOp());
        assertNotNull(vm.getStarLists());
    }

    @Test
    public void isResultConsumed_nullStateIsFalse() {
        StarBatchMoveViewModel vm = new StarBatchMoveViewModel();
        assertFalse(vm.isResultConsumed(null));
    }

    @Test
    public void markResultConsumed_nullIsNoop() {
        StarBatchMoveViewModel vm = new StarBatchMoveViewModel();
        vm.markResultConsumed(null); // 不抛异常
        StarBatchMoveViewModel.State s = new StarBatchMoveViewModel.State(
                3, 3, true, Collections.emptyList(), Collections.emptyList(), 7L);
        assertFalse(vm.isResultConsumed(s));
        vm.markResultConsumed(s);
        assertTrue(vm.isResultConsumed(s));
        StarBatchMoveViewModel.State other = new StarBatchMoveViewModel.State(
                3, 3, true, Collections.emptyList(), Collections.emptyList(), 8L);
        assertFalse(vm.isResultConsumed(other));
    }

    @Test
    public void state_ctorKeepsFields() {
        List<String> failed = Arrays.asList("a/b");
        List<String> targets = Arrays.asList("L1");
        StarBatchMoveViewModel.State s = new StarBatchMoveViewModel.State(
                5, 2, false, failed, targets, 42L);
        assertEquals(5, s.total);
        assertEquals(2, s.done);
        assertFalse(s.finished);
        assertEquals(failed, s.failed);
        assertEquals(targets, s.targetIds);
        assertEquals(42L, s.runId);
    }

    @Test
    public void start_nullOrEmptyIsNoop() {
        StarBatchMoveViewModel vm = new StarBatchMoveViewModel();
        vm.start(null, null);
        assertNull(vm.getState().getValue());
        vm.start(Collections.emptyList(), Collections.emptyList());
        assertNull(vm.getState().getValue());
    }

    @Test
    public void retryFailed_noStateIsNoop() {
        StarBatchMoveViewModel vm = new StarBatchMoveViewModel();
        vm.retryFailed();
        assertNull(vm.getState().getValue());
    }

    @Test
    public void runSingleOp_successPostsSuccessOp() {
        StarBatchMoveViewModel vm = new StarBatchMoveViewModel();
        vm.runSingleOp(Single.just("ok"), R.string.assign_official_success,
                R.string.assign_official_failed, "L1");
        StarBatchMoveViewModel.SingleOp op = awaitSingleOp(vm);
        assertNotNull(op);
        assertTrue(op.success);
        assertEquals(R.string.assign_official_success, op.messageRes);
        assertEquals("L1", op.clearExpandedId);
        assertEquals(1L, op.runId);
    }

    @Test
    public void runSingleOp_failurePostsFailureOpWithNullClearId() {
        StarBatchMoveViewModel vm = new StarBatchMoveViewModel();
        vm.runSingleOp(Single.error(new RuntimeException("boom")),
                R.string.assign_official_success,
                R.string.assign_official_failed, "L1");
        StarBatchMoveViewModel.SingleOp op = awaitSingleOp(vm);
        assertNotNull(op);
        assertFalse(op.success);
        assertEquals(R.string.assign_official_failed, op.messageRes);
        assertNull(op.clearExpandedId);
    }

    @Test
    public void runSingleOp_runIdsIncrementAcrossCalls() {
        StarBatchMoveViewModel vm = new StarBatchMoveViewModel();
        vm.runSingleOp(Single.just(1), 1, 2, null);
        StarBatchMoveViewModel.SingleOp first = awaitSingleOp(vm);
        assertNotNull(first);
        vm.runSingleOp(Single.just(2), 1, 2, null);
        StarBatchMoveViewModel.SingleOp second = awaitSingleOp(vm, first);
        assertNotNull(second);
        assertTrue(second != first);
        assertTrue(second.runId > first.runId);
    }

    @Test
    public void singleOp_consumedFlagRoundtrip() {
        StarBatchMoveViewModel vm = new StarBatchMoveViewModel();
        vm.runSingleOp(Single.just(1), 11, 22, null);
        StarBatchMoveViewModel.SingleOp op = awaitSingleOp(vm);
        assertNotNull(op);
        assertFalse(vm.isSingleOpConsumed(op));
        assertFalse(vm.isSingleOpConsumed(null));
        vm.markSingleOpConsumed(null); // 不抛异常
        vm.markSingleOpConsumed(op);
        assertTrue(vm.isSingleOpConsumed(op));
    }

    @Test
    public void singleOp_ctorKeepsFields() {
        StarBatchMoveViewModel.SingleOp op = new StarBatchMoveViewModel.SingleOp(
                9L, true, 123, "L9");
        assertEquals(9L, op.runId);
        assertTrue(op.success);
        assertEquals(123, op.messageRes);
        assertEquals("L9", op.clearExpandedId);
    }

    @Test
    public void createListAndStartMove_nullOrEmptyIsNoop() {
        StarBatchMoveViewModel vm = new StarBatchMoveViewModel();
        vm.createListAndStartMove(null, "g", false);
        vm.createListAndStartMove(Collections.emptyList(), "g", false);
        assertNull(vm.getSingleOp().getValue());
        assertNull(vm.getState().getValue());
    }

    @Test
    public void starListsResult_consumedFlagRoundtrip() {
        StarBatchMoveViewModel vm = new StarBatchMoveViewModel();
        assertFalse(vm.isStarListsConsumed(null));
        StarBatchMoveViewModel.StarListsResult r =
                new StarBatchMoveViewModel.StarListsResult(5L, new ArrayList<>(), false);
        assertEquals(5L, r.runId);
        assertFalse(r.failed);
        assertNotNull(r.lists);
        assertFalse(vm.isStarListsConsumed(r));
        vm.markStarListsConsumed(null); // 不抛异常
        vm.markStarListsConsumed(r);
        assertTrue(vm.isStarListsConsumed(r));
        StarBatchMoveViewModel.StarListsResult failed =
                new StarBatchMoveViewModel.StarListsResult(6L, null, true);
        assertTrue(failed.failed);
        assertNull(failed.lists);
        assertFalse(vm.isStarListsConsumed(failed));
    }

    @Test
    public void onCleared_doesNotThrow() throws Exception {
        StarBatchMoveViewModel vm = new StarBatchMoveViewModel();
        vm.runSingleOp(Single.just(1), 1, 2, null);
        awaitSingleOp(vm);
        java.lang.reflect.Method m =
                StarBatchMoveViewModel.class.getDeclaredMethod("onCleared");
        m.setAccessible(true);
        m.invoke(vm);
        m.invoke(vm); // 二次调用同样安全
    }

    // ---- 缺口补齐：start / retryFailed / fetchStarLists /
    // createListAndAssign / createListAndStartMove / moveNext ----

    /** 等待 finished 状态（每步需 IO 发射＋主线程排空，轮询）。 */
    private static StarBatchMoveViewModel.State awaitFinishedState(
            StarBatchMoveViewModel vm) {
        for (int i = 0; i < 250; i++) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            StarBatchMoveViewModel.State s = vm.getState().getValue();
            if (s != null && s.finished) {
                return s;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException ignored) {
            }
            Shadows.shadowOf(Looper.getMainLooper()).idle();
        }
        return vm.getState().getValue();
    }

    private static StarBatchMoveViewModel.StarListsResult awaitStarLists(
            StarBatchMoveViewModel vm) {
        for (int i = 0; i < 100; i++) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            StarBatchMoveViewModel.StarListsResult r = vm.getStarLists().getValue();
            if (r != null) {
                return r;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException ignored) {
            }
            Shadows.shadowOf(Looper.getMainLooper()).idle();
        }
        return vm.getStarLists().getValue();
    }

    private static void stubAssignRepoSuccess(MockedStatic<UserListsGraphQL> mocked) {
        mocked.when(() -> UserListsGraphQL.assignRepo(anyString(), any()))
                .thenReturn(Single.just(true));
    }

    @Test
    public void start_withReposRunsToFinished() {
        try (MockedStatic<UserListsGraphQL> mocked =
                     mockStatic(UserListsGraphQL.class)) {
            stubAssignRepoSuccess(mocked);
            StarBatchMoveViewModel vm = new StarBatchMoveViewModel();
            vm.start(Arrays.asList("a/b", "c/d"), Collections.singletonList("L1"));
            // 初始状态同步发出
            StarBatchMoveViewModel.State initial = vm.getState().getValue();
            assertNotNull(initial);
            assertEquals(2, initial.total);
            assertEquals(0, initial.done);
            assertFalse(initial.finished);
            assertEquals(Collections.singletonList("L1"), initial.targetIds);
            assertEquals(1L, initial.runId);
            StarBatchMoveViewModel.State done = awaitFinishedState(vm);
            assertNotNull(done);
            assertEquals(2, done.total);
            assertEquals(2, done.done);
            assertTrue(done.finished);
            assertTrue(done.failed.isEmpty());
            assertEquals(1L, done.runId);
        }
    }

    @Test
    public void start_nullTargetIdsBecomesEmptyList() {
        try (MockedStatic<UserListsGraphQL> mocked =
                     mockStatic(UserListsGraphQL.class)) {
            stubAssignRepoSuccess(mocked);
            StarBatchMoveViewModel vm = new StarBatchMoveViewModel();
            vm.start(Collections.singletonList("a/b"), null);
            StarBatchMoveViewModel.State initial = vm.getState().getValue();
            assertNotNull(initial);
            assertTrue(initial.targetIds.isEmpty());
            StarBatchMoveViewModel.State done = awaitFinishedState(vm);
            assertNotNull(done);
            assertTrue(done.finished);
            assertTrue(done.targetIds.isEmpty());
        }
    }

    @Test
    public void start_withFailingRepoRecordsFailed() {
        try (MockedStatic<UserListsGraphQL> mocked =
                     mockStatic(UserListsGraphQL.class)) {
            mocked.when(() -> UserListsGraphQL.assignRepo(eq("a/b"), any()))
                    .thenReturn(Single.error(new RuntimeException("boom")));
            mocked.when(() -> UserListsGraphQL.assignRepo(eq("c/d"), any()))
                    .thenReturn(Single.just(true));
            StarBatchMoveViewModel vm = new StarBatchMoveViewModel();
            vm.start(Arrays.asList("a/b", "c/d"), Collections.singletonList("L1"));
            StarBatchMoveViewModel.State done = awaitFinishedState(vm);
            assertNotNull(done);
            assertEquals(2, done.total);
            assertEquals(2, done.done);
            assertTrue(done.finished);
            assertEquals(Collections.singletonList("a/b"), done.failed);
        }
    }

    @Test
    public void start_newRunSupersedesPrevious() {
        try (MockedStatic<UserListsGraphQL> mocked =
                     mockStatic(UserListsGraphQL.class)) {
            mocked.when(() -> UserListsGraphQL.assignRepo(eq("a/b"), any()))
                    .thenReturn(Single.never());
            mocked.when(() -> UserListsGraphQL.assignRepo(eq("x/y"), any()))
                    .thenReturn(Single.just(true));
            StarBatchMoveViewModel vm = new StarBatchMoveViewModel();
            vm.start(Collections.singletonList("a/b"), Collections.singletonList("L1"));
            assertEquals(1L, vm.getState().getValue().runId);
            vm.start(Collections.singletonList("x/y"), Collections.singletonList("L2"));
            StarBatchMoveViewModel.State done = awaitFinishedState(vm);
            assertNotNull(done);
            assertEquals(2L, done.runId);
            assertEquals(1, done.total);
            assertTrue(done.finished);
        }
    }

    @Test
    public void retryFailed_notFinishedIsNoop() {
        try (MockedStatic<UserListsGraphQL> mocked =
                     mockStatic(UserListsGraphQL.class)) {
            mocked.when(() -> UserListsGraphQL.assignRepo(anyString(), any()))
                    .thenReturn(Single.never());
            StarBatchMoveViewModel vm = new StarBatchMoveViewModel();
            vm.start(Collections.singletonList("a/b"), Collections.singletonList("L1"));
            StarBatchMoveViewModel.State initial = vm.getState().getValue();
            assertNotNull(initial);
            assertFalse(initial.finished);
            vm.retryFailed();
            assertSame(initial, vm.getState().getValue());
        }
    }

    @Test
    public void retryFailed_finishedWithNoFailuresIsNoop() {
        try (MockedStatic<UserListsGraphQL> mocked =
                     mockStatic(UserListsGraphQL.class)) {
            stubAssignRepoSuccess(mocked);
            StarBatchMoveViewModel vm = new StarBatchMoveViewModel();
            vm.start(Collections.singletonList("a/b"), Collections.singletonList("L1"));
            StarBatchMoveViewModel.State done = awaitFinishedState(vm);
            assertNotNull(done);
            assertTrue(done.failed.isEmpty());
            vm.retryFailed();
            assertSame(done, vm.getState().getValue());
        }
    }

    @Test
    public void retryFailed_retriesOnlyFailedRepos() {
        try (MockedStatic<UserListsGraphQL> mocked =
                     mockStatic(UserListsGraphQL.class)) {
            AtomicInteger aCalls = new AtomicInteger();
            mocked.when(() -> UserListsGraphQL.assignRepo(eq("a/b"), any()))
                    .thenAnswer(inv -> aCalls.getAndIncrement() == 0
                            ? Single.error(new RuntimeException("boom"))
                            : Single.just(true));
            mocked.when(() -> UserListsGraphQL.assignRepo(eq("c/d"), any()))
                    .thenReturn(Single.just(true));
            StarBatchMoveViewModel vm = new StarBatchMoveViewModel();
            vm.start(Arrays.asList("a/b", "c/d"), Collections.singletonList("L1"));
            StarBatchMoveViewModel.State done = awaitFinishedState(vm);
            assertNotNull(done);
            assertEquals(Collections.singletonList("a/b"), done.failed);
            vm.retryFailed();
            StarBatchMoveViewModel.State retried = awaitFinishedState(vm);
            assertNotNull(retried);
            assertEquals(2L, retried.runId);
            assertEquals(1, retried.total);
            assertEquals(1, retried.done);
            assertTrue(retried.finished);
            assertTrue(retried.failed.isEmpty());
            assertEquals(Collections.singletonList("L1"), retried.targetIds);
        }
    }

    @Test
    public void fetchStarLists_successPostsResult() {
        try (MockedStatic<UserListsGraphQL> mocked =
                     mockStatic(UserListsGraphQL.class)) {
            List<UserListsGraphQL.UserList> lists = Collections.singletonList(
                    new UserListsGraphQL.UserList("L1", "My List", "",
                            Collections.emptyList()));
            mocked.when(() -> UserListsGraphQL.fetchLists(eq("alice")))
                    .thenReturn(Single.just(lists));
            StarBatchMoveViewModel vm = new StarBatchMoveViewModel();
            vm.fetchStarLists("alice");
            StarBatchMoveViewModel.StarListsResult r = awaitStarLists(vm);
            assertNotNull(r);
            assertEquals(1L, r.runId);
            assertEquals(lists, r.lists);
            assertFalse(r.failed);
        }
    }

    @Test
    public void fetchStarLists_errorPostsFailedResult() {
        try (MockedStatic<UserListsGraphQL> mocked =
                     mockStatic(UserListsGraphQL.class)) {
            mocked.when(() -> UserListsGraphQL.fetchLists(eq("alice")))
                    .thenReturn(Single.error(new RuntimeException("boom")));
            StarBatchMoveViewModel vm = new StarBatchMoveViewModel();
            vm.fetchStarLists("alice");
            StarBatchMoveViewModel.StarListsResult r = awaitStarLists(vm);
            assertNotNull(r);
            assertEquals(1L, r.runId);
            assertNull(r.lists);
            assertTrue(r.failed);
        }
    }

    @Test
    public void createListAndAssign_successPostsSingleOp() {
        // flatMap 内的 assignRepo 跑在 IO 线程，mockStatic 在该线程不生效：
        // 把 IO 调度器换成 trampoline，让整条链在当前（主）线程跑
        io.reactivex.plugins.RxJavaPlugins.setIoSchedulerHandler(
                s -> io.reactivex.schedulers.Schedulers.trampoline());
        try (MockedStatic<UserListsGraphQL> mocked =
                     mockStatic(UserListsGraphQL.class)) {
            mocked.when(() -> UserListsGraphQL.createList("g", false))
                    .thenReturn(Single.just(new UserListsGraphQL.UserList(
                            "L1", "g", "", Collections.emptyList())));
            mocked.when(() -> UserListsGraphQL.assignRepo("o/r",
                            Collections.singletonList("L1")))
                    .thenReturn(Single.just(true));
            StarBatchMoveViewModel vm = new StarBatchMoveViewModel();
            vm.createListAndAssign("o/r", "g", false);
            StarBatchMoveViewModel.SingleOp op = awaitSingleOp(vm);
            assertNotNull(op);
            assertTrue(op.success);
            assertEquals(R.string.assign_official_success, op.messageRes);
            assertNull(op.clearExpandedId);
            mocked.verify(() -> UserListsGraphQL.createList("g", false));
            mocked.verify(() -> UserListsGraphQL.assignRepo("o/r",
                    Collections.singletonList("L1")));
        } finally {
            io.reactivex.plugins.RxJavaPlugins.reset();
        }
    }

    @Test
    public void createListAndAssign_createFailurePostsFailureOp() {
        try (MockedStatic<UserListsGraphQL> mocked =
                     mockStatic(UserListsGraphQL.class)) {
            mocked.when(() -> UserListsGraphQL.createList(eq("g"), eq(false)))
                    .thenReturn(Single.error(new RuntimeException("boom")));
            StarBatchMoveViewModel vm = new StarBatchMoveViewModel();
            vm.createListAndAssign("o/r", "g", false);
            StarBatchMoveViewModel.SingleOp op = awaitSingleOp(vm);
            assertNotNull(op);
            assertFalse(op.success);
            assertEquals(R.string.assign_official_failed, op.messageRes);
        }
    }

    @Test
    public void createListAndStartMove_successStartsMove() {
        try (MockedStatic<UserListsGraphQL> mocked =
                     mockStatic(UserListsGraphQL.class)) {
            mocked.when(() -> UserListsGraphQL.createList(eq("g"), eq(false)))
                    .thenReturn(Single.just(new UserListsGraphQL.UserList(
                            "L1", "g", "", Collections.emptyList())));
            stubAssignRepoSuccess(mocked);
            StarBatchMoveViewModel vm = new StarBatchMoveViewModel();
            vm.createListAndStartMove(Arrays.asList("a/b", "c/d"), "g", false);
            StarBatchMoveViewModel.State done = awaitFinishedState(vm);
            assertNotNull(done);
            assertEquals(2, done.total);
            assertTrue(done.finished);
            assertTrue(done.failed.isEmpty());
            assertEquals(Collections.singletonList("L1"), done.targetIds);
            assertNull(vm.getSingleOp().getValue());
        }
    }

    @Test
    public void createListAndStartMove_createFailurePostsFailureOp() {
        try (MockedStatic<UserListsGraphQL> mocked =
                     mockStatic(UserListsGraphQL.class)) {
            mocked.when(() -> UserListsGraphQL.createList(eq("g"), eq(false)))
                    .thenReturn(Single.error(new RuntimeException("boom")));
            StarBatchMoveViewModel vm = new StarBatchMoveViewModel();
            vm.createListAndStartMove(Arrays.asList("a/b"), "g", false);
            StarBatchMoveViewModel.SingleOp op = awaitSingleOp(vm);
            assertNotNull(op);
            assertFalse(op.success);
            assertEquals(R.string.star_list_create_failed, op.messageRes);
            assertNull(vm.getState().getValue());
        }
    }

    @Test
    public void moveNext_staleRunIdIsNoop() throws Exception {
        StarBatchMoveViewModel vm = new StarBatchMoveViewModel();
        java.lang.reflect.Method m = StarBatchMoveViewModel.class.getDeclaredMethod(
                "moveNext", List.class, int.class, List.class, List.class, long.class);
        m.setAccessible(true);
        // 尚未跑过任何一轮：mRunId 为 0，传 999 即过期轮次
        m.invoke(vm, Collections.singletonList("a/b"), 0,
                Collections.singletonList("L1"), new ArrayList<>(), 999L);
        assertNull(vm.getState().getValue());
    }
}
