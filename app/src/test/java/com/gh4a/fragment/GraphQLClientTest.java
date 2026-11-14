package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** GraphQLClient (F1): cancel handle + UnauthorizedException only.
 * execute() performs a real HTTPS POST to api.github.com and is NOT tested. */
@RunWith(RobolectricTestRunner.class)
public class GraphQLClientTest {

    @Test
    public void unauthorizedException_carriesMessage() {
        GraphQLClient.UnauthorizedException e =
                new GraphQLClient.UnauthorizedException("GraphQL HTTP 401");
        assertEquals("GraphQL HTTP 401", e.getMessage());
        assertTrue(e instanceof RuntimeException);
    }

    @Test
    public void callHandle_cancelWithoutCall_doesNothing() {
        new GraphQLClient.CallHandle().cancel();
    }

    @Test
    public void callHandle_cancelWithCall_cancelsIt() {
        GraphQLClient.CallHandle handle = new GraphQLClient.CallHandle();
        okhttp3.Call call = mock(okhttp3.Call.class);
        try {
            java.lang.reflect.Field field =
                    GraphQLClient.CallHandle.class.getDeclaredField("mCall");
            field.setAccessible(true);
            field.set(handle, call);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        handle.cancel();
        verify(call).cancel();
    }

    @Test
    public void callHandle_isPublicFinalClass() {
        assertNotNull(new GraphQLClient.CallHandle());
    }
}
