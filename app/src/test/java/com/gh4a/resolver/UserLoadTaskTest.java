package com.gh4a.resolver;

import android.content.Intent;
import android.net.Uri;

import androidx.fragment.app.FragmentActivity;

import com.gh4a.ServiceFactory;
import com.gh4a.activities.UserActivity;
import com.meisolsson.githubsdk.model.User;
import com.meisolsson.githubsdk.service.users.UserService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.util.Optional;

import io.reactivex.Single;
import okhttp3.ResponseBody;
import retrofit2.Response;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class UserLoadTaskTest {
    private static final Uri URL = Uri.parse("https://github.com/octocat");

    static class StubUserTask extends UserLoadTask {
        StubUserTask(FragmentActivity activity, String login) {
            super(activity, URL, login);
        }

        @Override
        protected Intent getIntent(User user) {
            return UserActivity.makeIntent(mActivity, user);
        }
    }

    private FragmentActivity activity() {
        return Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    @Test
    public void constructor_storesUserLogin() {
        assertEquals("octocat", new StubUserTask(activity(), "octocat").mUserLogin);
    }

    @Test
    public void getSingle_userFound_returnsIntentFromGetIntent() {
        FragmentActivity activity = activity();
        UserService service = mock(UserService.class);
        User user = mock(User.class);
        when(user.login()).thenReturn("octocat");
        when(service.getUser(anyString())).thenReturn(Single.just(Response.success(user)));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(eq(UserService.class), eq(false)))
                    .thenReturn(service);
            Optional<Intent> result = new StubUserTask(activity, "octocat")
                    .getSingle().blockingGet();
            assertTrue(result.isPresent());
            assertEquals(UserActivity.class.getName(),
                    result.get().getComponent().getClassName());
            assertEquals("octocat", result.get().getStringExtra("login"));
        }
    }

    @Test
    public void getSingle_userNotFound_propagatesApiRequestException() {
        FragmentActivity activity = activity();
        UserService service = mock(UserService.class);
        when(service.getUser(anyString())).thenReturn(Single.just(Response.error(404,
                ResponseBody.create(null, new byte[0]))));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(eq(UserService.class), eq(false)))
                    .thenReturn(service);
            try {
                new StubUserTask(activity, "ghost").getSingle().blockingGet();
                fail("expected ApiRequestException");
            } catch (com.gh4a.ApiRequestException e) {
                assertEquals(404, e.getStatus());
            }
        }
    }

    @Test
    public void getSingle_serviceError_propagatesError() {
        FragmentActivity activity = activity();
        UserService service = mock(UserService.class);
        when(service.getUser(anyString()))
                .thenReturn(Single.error(new RuntimeException("down")));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(eq(UserService.class), eq(false)))
                    .thenReturn(service);
            try {
                new StubUserTask(activity, "octocat").getSingle().blockingGet();
                fail("expected error");
            } catch (RuntimeException e) {
                assertEquals("down", e.getMessage());
            }
        }
    }
}
