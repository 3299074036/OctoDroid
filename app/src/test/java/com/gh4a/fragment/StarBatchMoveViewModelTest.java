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

import io.reactivex.Single;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
}
