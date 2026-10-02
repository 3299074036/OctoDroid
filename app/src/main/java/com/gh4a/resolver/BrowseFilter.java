package com.gh4a.resolver;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
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
        // M-6 纵深防御：manifest 的 intent-filter 只声明了 http/https，
        // 但显式 intent 可绕过 filter 约束，这里再卡一次 scheme 和 host。
        // 经确认所有 deep link 触发的后台任务均为只读 GET（UrlLoadTask 子类），
        // 不存在外部网页可触发的写操作；此处收紧入口即可。
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (!("http".equals(scheme) || "https".equals(scheme))
                || !("github.com".equals(host) || "gist.github.com".equals(host)
                        || "blog.github.com".equals(host))) {
            finish();
            return;
        }

        int flags = getIntent().getFlags() & ~Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS;
        if ((flags & (Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT)) != 0) {
            flags |= Intent.FLAG_ACTIVITY_MULTIPLE_TASK;
        }
        IntentUtils.InitialCommentMarker initialComment =
                getIntent().getParcelableExtra(EXTRA_INITIAL_COMMENT);

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
