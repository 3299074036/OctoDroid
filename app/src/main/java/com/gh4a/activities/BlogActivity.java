/*
 * Copyright 2011 Azwan Adli Abdullah
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gh4a.activities;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import androidx.annotation.Nullable;

import com.gh4a.R;
import com.gh4a.model.Feed;
import com.gh4a.utils.IntentUtils;

public class BlogActivity extends WebViewerActivity {
    public static Intent makeIntent(Context context, Feed blog) {
        return new Intent(context, BlogActivity.class)
                .putExtra("title", blog.getTitle())
                .putExtra("content", blog.getContent())
                .putExtra("link", blog.getLink());
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        onDataReady();
    }

    @Nullable
    @Override
    protected String getActionBarTitle() {
        return getDocumentTitle();
    }

    @Nullable
    @Override
    protected String getActionBarSubtitle() {
        return getString(R.string.blog);
    }

    @Override
    protected boolean canSwipeToRefresh() {
        // content is contained in the intent extras
        return false;
    }

    @Override
    protected String generateHtml(String cssTheme, boolean addTitleHeader) {
        String title = addTitleHeader ? getDocumentTitle() : null;
        return wrapWithMarkdownStyling(getIntent().getStringExtra("content"), cssTheme, title);
    }

    @Override
    protected String getDocumentTitle() {
        return getIntent().getStringExtra("title");
    }

    @Override
    protected Intent navigateUp() {
        return new Intent(this, BlogListActivity.class);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // 博客文章加分享入口，分享原文链接
        if (getIntent().getStringExtra("link") != null) {
            MenuItem shareItem = menu.add(Menu.NONE, R.id.share, Menu.NONE, R.string.share);
            shareItem.setIcon(R.drawable.menu_share);
            shareItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.share) {
            String link = getIntent().getStringExtra("link");
            if (link != null) {
                IntentUtils.share(this, getDocumentTitle(), Uri.parse(link));
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
