package com.gh4a.model;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class NotificationListLoadResultTest {

    @Test
    public void constructor_storesListAndSetsLoadTime() {
        long before = System.currentTimeMillis();
        List<NotificationHolder> list = new ArrayList<>();
        NotificationListLoadResult result = new NotificationListLoadResult(list);
        long after = System.currentTimeMillis();

        assertSame(list, result.notifications);
        assertNotNull(result.loadTime);
        assertTrue(result.loadTime.getTime() >= before);
        assertTrue(result.loadTime.getTime() <= after);
    }

    @Test
    public void constructor_nullListStoredAsIs() {
        NotificationListLoadResult result = new NotificationListLoadResult(null);
        assertNull(result.notifications);
        assertNotNull(result.loadTime);
    }

    @Test
    public void constructor_emptyListAndLoadTimeIsNow() {
        NotificationListLoadResult result =
                new NotificationListLoadResult(Collections.emptyList());
        assertTrue(result.notifications.isEmpty());
        assertTrue(Math.abs(result.loadTime.getTime() - new Date().getTime()) < 5000);
    }
}
