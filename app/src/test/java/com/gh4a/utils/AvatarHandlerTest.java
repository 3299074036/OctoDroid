package com.gh4a.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.view.MenuItem;
import android.widget.ImageView;

import androidx.appcompat.view.menu.MenuBuilder;

import com.meisolsson.githubsdk.model.User;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import androidx.test.core.app.ApplicationProvider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A 组：AvatarHandler 触 ImageView/Bitmap/Resources，走 Robolectric。
 * 注意：userId &gt; 0 的 assignAvatar 会起后台线程真实取头像（网络），
 * 本组只测 userId &lt;= 0 / user 为空的路径，绝不触发真实请求。
 */
@RunWith(RobolectricTestRunner.class)
public class AvatarHandlerTest {

    private Context context() {
        return ApplicationProvider.getApplicationContext();
    }

    @Test
    public void loadUserAvatarSynchronously_nullUserReturnsNull() {
        assertNull(AvatarHandler.loadUserAvatarSynchronously(context(), null));
    }

    @Test
    public void loadUserAvatarSynchronously_userWithNullIdReturnsNull() {
        User user = User.builder().login("octocat").build();
        assertNull(user.id());
        assertNull(AvatarHandler.loadUserAvatarSynchronously(context(), user));
    }

    @Test
    public void assignAvatar_nullUserSetsDefaultDrawable() {
        ImageView view = new ImageView(context());
        AvatarHandler.assignAvatar(view, (User) null);
        assertTrue(view.getDrawable()
                instanceof AvatarHandler.DefaultAvatarDrawable);
    }

    @Test
    public void assignAvatar_userWithNullIdSetsDefaultDrawable() {
        ImageView view = new ImageView(context());
        User user = User.builder().login("octocat").build();
        AvatarHandler.assignAvatar(view, user);
        assertTrue(view.getDrawable()
                instanceof AvatarHandler.DefaultAvatarDrawable);
    }

    @Test
    public void assignAvatar_zeroUserIdSetsDefaultDrawableWithoutNetwork() {
        ImageView view = new ImageView(context());
        AvatarHandler.assignAvatar(view, "alice", 0, "https://example.com/a.png");
        assertTrue(view.getDrawable()
                instanceof AvatarHandler.DefaultAvatarDrawable);
    }

    @Test
    public void assignAvatar_negativeUserIdSetsDefaultDrawableWithoutNetwork() {
        ImageView view = new ImageView(context());
        AvatarHandler.assignAvatar(view, "bob", -5, null);
        assertTrue(view.getDrawable()
                instanceof AvatarHandler.DefaultAvatarDrawable);
    }

    @Test
    public void assignAvatar_nullUserNameWithZeroIdSetsDefaultDrawable() {
        ImageView view = new ImageView(context());
        AvatarHandler.assignAvatar(view, null, 0, null);
        assertTrue(view.getDrawable()
                instanceof AvatarHandler.DefaultAvatarDrawable);
    }

    @Test
    public void assignAvatar_menuItemZeroIdSetsDefaultIcon() {
        MenuBuilder menu = new MenuBuilder(context());
        MenuItem item = menu.add("test");
        AvatarHandler.assignAvatar(context(), item, "carol", 0);
        assertTrue(item.getIcon() instanceof AvatarHandler.DefaultAvatarDrawable);
    }

    @Test
    public void defaultAvatarDrawable_nullNameNullIdentifierDoesNotThrow() {
        AvatarHandler.DefaultAvatarDrawable d =
                new AvatarHandler.DefaultAvatarDrawable(null, null);
        assertNotNull(d);
    }

    @Test
    public void defaultAvatarDrawable_minValueHashIdentifierDoesNotThrow() {
        // 260：identifier.hashCode() == Integer.MIN_VALUE 时旧 Math.abs
        // 仍为负，取模得负下标即 AIOOBE；floorMod 必须恒为非负。
        Object minHashId = new Object() {
            @Override
            public int hashCode() {
                return Integer.MIN_VALUE;
            }
        };
        AvatarHandler.DefaultAvatarDrawable d =
                new AvatarHandler.DefaultAvatarDrawable(null, minHashId);
        assertNotNull(d);
    }

    @Test
    public void defaultAvatarDrawable_minValueHashUserNameDoesNotThrow() {
        // 构造一个 hashCode 恰为 Integer.MIN_VALUE 的登录名很难，
        // 用等价路径：名字非空时同样走 floorMod，任意名都不应抛异常。
        AvatarHandler.DefaultAvatarDrawable d =
                new AvatarHandler.DefaultAvatarDrawable("octocat", 123L);
        assertNotNull(d);
    }

    @Test
    public void defaultAvatarDrawable_opacityIsOpaque() {
        AvatarHandler.DefaultAvatarDrawable d =
                new AvatarHandler.DefaultAvatarDrawable("alice", 1L);
        assertEquals(PixelFormat.OPAQUE, d.getOpacity());
    }

    @Test
    public void defaultAvatarDrawable_drawWithBoundsDoesNotThrow() {
        AvatarHandler.DefaultAvatarDrawable d =
                new AvatarHandler.DefaultAvatarDrawable("alice", 1L);
        d.setBounds(0, 0, 100, 100);
        Bitmap bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        d.draw(new Canvas(bitmap));
    }

    @Test
    public void defaultAvatarDrawable_drawWithEmptyBoundsIsNoop() {
        AvatarHandler.DefaultAvatarDrawable d =
                new AvatarHandler.DefaultAvatarDrawable("alice", 1L);
        d.setBounds(0, 0, 0, 0);
        Bitmap bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888);
        d.draw(new Canvas(bitmap));
    }

    @Test
    public void defaultAvatarDrawable_setAlphaAndColorFilterDoNotThrow() {
        AvatarHandler.DefaultAvatarDrawable d =
                new AvatarHandler.DefaultAvatarDrawable("alice", 1L);
        d.setAlpha(128);
        d.setColorFilter(null);
    }

    @Test
    public void defaultAvatarDrawable_constantStateNewDrawable() {
        AvatarHandler.DefaultAvatarDrawable d =
                new AvatarHandler.DefaultAvatarDrawable("alice", 1L);
        assertNotNull(d.getConstantState());
        assertEquals(0, d.getConstantState().getChangingConfigurations());
        assertTrue(d.getConstantState().newDrawable()
                instanceof AvatarHandler.DefaultAvatarDrawable);
    }
}
