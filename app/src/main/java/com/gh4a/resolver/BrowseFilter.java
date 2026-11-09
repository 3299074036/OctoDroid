package com.gh4a.resolver;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import com.gh4a.R;
import com.gh4a.utils.IntentUtils;

public class BrowseFilter extends AppCompatActivity {
    private static final String EXTRA_INITIAL_COMMENT = "initial_comment";

    public static Intent makeRedirectionIntent(Context context, Uri uri,
            IntentUtils.InitialCommentMarker initialComment) {
        Intent intent = new Intent(context, BrowseFilter.class);
        intent.setData(uri);
        intent.putExtra(EXTRA_INITIAL_COMMENT, initialComment);
        return intent;
    }

    public void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.TransparentTheme);

        super.onCreate(savedInstanceState);

        Uri uri = getIntent().getData();
        if (uri == null) {
            finish();
            return;
        }
        // M-6 纵深防御：manifest 的 intent-filter 已是 https-only（SEC-11），
        // 0.1.20 F-13: 代码内关卡同步收紧为 https-only，显式 intent 的
        // 明文 http://github.com 链接不再在应用内处理。
        // 经确认所有 deep link 触发的后台任务均为只读 GET（UrlLoadTask 子类），
        // 不存在外部网页可触发的写操作；此处收紧入口即可。
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (!("https".equals(scheme))
                || !("github.com".equals(host) || "gist.github.com".equals(host)
                        || "blog.github.com".equals(host))) {
            finish();
            return;
        }

        int flags = getIntent().getFlags() & ~Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS;
        if ((flags & (Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT)) != 0) {
            flags |= Intent.FLAG_ACTIVITY_MULTIPLE_TASK;
        }
        // 151：本 Activity 是导出的，任意应用可发 VIEW intent 附带
        // EXTRA_INITIAL_COMMENT；API <33 的无类型反序列化会按名字实例化
        // classpath 内任意 Parcelable 的 CREATOR。先限定 extras 的类加载器，
        // 再读 extra（android-intent-security skill 同款纵深防御做法）。
        // S-L6：API 33+ 用带 Class 参数的 typed API 取 Parcelable，避免
        // 反序列化时的类混淆；低版本走旧 API（minSdk 23）。
        getIntent().setExtrasClassLoader(
                IntentUtils.InitialCommentMarker.class.getClassLoader());
        IntentUtils.InitialCommentMarker initialComment;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            initialComment = getIntent().getParcelableExtra(
                    EXTRA_INITIAL_COMMENT, IntentUtils.InitialCommentMarker.class);
        } else {
            initialComment = getIntent().getParcelableExtra(EXTRA_INITIAL_COMMENT);
        }

        LinkParser.ParseResult result = LinkParser.parseUri(this, uri, initialComment);
        if (result == null) {
            IntentUtils.launchBrowser(this, uri, flags);
            finish();
            return;
        }

        if (result.intent != null) {
            startActivity(result.intent.setFlags(flags));
            finish();
            return;
        }

        result.loadTask.setIntentFlags(flags);
        result.loadTask.setCompletionCallback(this::finish);
        result.loadTask.execute();
    }
}
