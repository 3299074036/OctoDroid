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
package com.gh4a.model;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import com.gh4a.utils.StringUtils;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Path;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.core.Commit;

import java.util.Date;

import androidx.annotation.Nullable;

@Root(name = "entry", strict = false)
public class Feed implements Parcelable {
    @Element(name = "id")
    private String id;

    @Nullable
    @Element(name = "published", required = false)
    private Date published;

    @Path(value = "link")
    @Attribute(name = "href")
    private String link;

    @Nullable
    @Element(name = "title", required = false)
    private String title;

    @Element(name = "content")
    private String content;

    @Nullable
    @Path(value = "author")
    @Element(name = "name", required = false)
    private String author;

    @Path(value = "thumbnail")
    @Attribute(name = "url")
    private String avatarUrl;

    @Nullable
    @Element(name = "updated", required = false)
    private Date updated;

    private int userId;
    private String preview;

    @Commit
    private void apply() {
        userId = determineUserId(avatarUrl, author);
        preview = generatePreview(content);
        if (StringUtils.isBlank(title)) {
            title = getTitleFromUrl(link);
        } else {
            title = StringUtils.unescapeCommonHtmlEntities(title);
        }
    }

    private static String generatePreview(String content) {
        if (content == null) {
            return null;
        }
        String preview = content.length() > 2000 ? content.substring(0, 2000) : content;
        preview = StringUtils.unescapeCommonHtmlEntities(preview.replaceAll("<(.|\n)*?>", "")).trim();
        if (preview.length() > 500) {
            preview = preview.substring(0, 500);
        }
        return preview;
    }

    private static int determineUserId(String url, String userName) {
        if (url == null) {
            return -1;
        }

        Uri uri = Uri.parse(url);
        String host = uri.getHost();

        // 119：相对路径/畸形 thumbnail URL 时 getHost() 为 null，
        // 直接 startsWith 即 NPE，且异常发生在 @Commit 里会 abort 整篇 feed 解析
        if (host == null) {
            return -1;
        }
        if (host.startsWith("avatars") && host.contains("githubusercontent.com")) {
            if (uri.getPathSegments().size() == 2) {
                try {
                    return Integer.valueOf(uri.getLastPathSegment());
                } catch (NumberFormatException e) {
                    // 119：末段非数字（如用户名路径）时不抛，返回 -1
                    return -1;
                }
            }
        }

        // We couldn't parse the user ID from the avatar, so construct a fake
        // user ID for identification purposes in GravatarHandler
        if (userName != null) {
            return userName.hashCode();
        }
        return -1;
    }

    private static String getTitleFromUrl(String url) {
        if (url == null) {
            return null;
        }
        return url.substring(url.lastIndexOf("/") + 1).replaceAll("-", " ");
    }

    public String getId() {
        return id;
    }

    @Nullable
    public Date getPublished() {
        return published;
    }

    @Nullable
    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public String getPreview() {
        return preview;
    }

    @Nullable
    public String getAuthor() {
        return author;
    }

    public int getUserId() {
        return userId;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    @Nullable
    public Date getUpdated() {
        return updated;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(link);
        dest.writeString(title);
        dest.writeString(content);
        dest.writeString(author);
        dest.writeString(avatarUrl);
        dest.writeInt(userId);
        dest.writeString(preview);
        // 119：Parcel 往返丢日期——补写 published/updated（null 记 -1），
        // 追加在末尾，不打乱已有字段顺序
        dest.writeLong(published != null ? published.getTime() : -1);
        dest.writeLong(updated != null ? updated.getTime() : -1);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<Feed> CREATOR = new Creator<Feed>() {
        @Override
        public Feed createFromParcel(Parcel in) {
            Feed feed = new Feed();
            feed.id = in.readString();
            feed.link = in.readString();
            feed.title = in.readString();
            feed.content = in.readString();
            feed.author = in.readString();
            feed.avatarUrl = in.readString();
            feed.userId = in.readInt();
            feed.preview = in.readString();
            // 119：与 writeToParcel 同顺序读回 published/updated
            long publishedTime = in.readLong();
            feed.published = publishedTime != -1 ? new Date(publishedTime) : null;
            long updatedTime = in.readLong();
            feed.updated = updatedTime != -1 ? new Date(updatedTime) : null;
            return feed;
        }

        @Override
        public Feed[] newArray(int size) {
            return new Feed[size];
        }
    };
}
