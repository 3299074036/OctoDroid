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
package com.gh4a.fragment;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Pair;

import androidx.annotation.Nullable;

import com.gh4a.activities.CommitActivity;
import com.gh4a.activities.CompareActivity;
import com.gh4a.activities.GistActivity;
import com.gh4a.activities.IssueActivity;
import com.gh4a.activities.PullRequestActivity;
import com.gh4a.activities.ReleaseInfoActivity;
import com.gh4a.activities.RepositoryActivity;
import com.gh4a.activities.ReviewActivity;
import com.gh4a.activities.UserActivity;
import com.gh4a.activities.WikiListActivity;
import com.gh4a.adapter.EventAdapter;
import com.gh4a.utils.IntentUtils;
import com.meisolsson.githubsdk.model.Discussion;
import com.meisolsson.githubsdk.model.GitHubEvent;
import com.meisolsson.githubsdk.model.GitHubWikiPage;
import com.meisolsson.githubsdk.model.Issue;
import com.meisolsson.githubsdk.model.PullRequest;
import com.meisolsson.githubsdk.model.ReferenceType;
import com.meisolsson.githubsdk.model.Release;
import com.meisolsson.githubsdk.model.Repository;
import com.meisolsson.githubsdk.model.Team;
import com.meisolsson.githubsdk.model.User;
import com.meisolsson.githubsdk.model.git.GitCommit;
import com.meisolsson.githubsdk.model.payload.CreatePayload;
import com.meisolsson.githubsdk.model.payload.DiscussionPayload;
import com.meisolsson.githubsdk.model.payload.FollowPayload;
import com.meisolsson.githubsdk.model.payload.ForkPayload;
import com.meisolsson.githubsdk.model.payload.GistPayload;
import com.meisolsson.githubsdk.model.payload.GollumPayload;
import com.meisolsson.githubsdk.model.payload.IssueCommentPayload;
import com.meisolsson.githubsdk.model.payload.IssuesPayload;
import com.meisolsson.githubsdk.model.payload.MembershipPayload;
import com.meisolsson.githubsdk.model.payload.PullRequestPayload;
import com.meisolsson.githubsdk.model.payload.PullRequestReviewPayload;
import com.meisolsson.githubsdk.model.payload.PushPayload;
import com.meisolsson.githubsdk.model.payload.ReleasePayload;
import com.meisolsson.githubsdk.model.payload.TeamAddPayload;

import java.util.List;

/**
 * D-E4：事件 → 目的地映射的唯一入口。
 *
 * 原先这份映射在 {@link EventListFragment} 里写了两遍：点按
 * {@code onItemClick} 的 switch 与长按菜单 {@code onCreateContextMenu} 的
 * switch。点按与长按菜单中目的地相同的分支（Follow/Fork/Gist/Issues/
 * PullRequest）现在都走这里；菜单里目的地不同的分支（Gollum 跳网页、
 * IssueComment 跳列表、Push 的多条目、Release/Download 的下载项、
 * CommitComment 的直接跳转）仍留在菜单方法里。
 *
 * 契约：
 * <ul>
 * <li>返回 null 表示没有单个同步直达 Intent，调用方按原先逻辑处理：
 * CommitCommentEvent 与 PullRequestReviewCommentEvent 需要异步加载
 * （LoadTask）才能确定 Intent；DownloadEvent 是下载副作用而非跳转；
 * 内层判空失败（gist/release/number 等缺失）时同样返回 null。</li>
 * <li>仓库 owner/name 沿用点按原先的约定：事件带不上可解析的仓库标识时
 * 取 ""，而不是 null。长按菜单原先在该退化情形下传 null，改走这里后
 *  extras 由 null 变 ""，下游仍是加载失败的错误页，无其他可见变化。</li>
 * <li>ForkEvent 的 forkee 缺失时返回 null：点按方随后提示 repo 缺失，
 * 菜单方不加该项，与原先两边的行为一致。</li>
 * </ul>
 */
public final class EventNavigation {
    private EventNavigation() {
    }

    @Nullable
    public static Intent resolveIntent(Context context, GitHubEvent event) {
        if (context == null || event == null || event.type() == null
                || EventAdapter.hasInvalidPayload(event)) {
            return null;
        }

        Pair<String, String> repoOwnerAndName =
                EventListFragment.parseRepoIdentifierToOwnerAndName(event.repo());
        String repoOwner = repoOwnerAndName != null ? repoOwnerAndName.first : "";
        String repoName = repoOwnerAndName != null ? repoOwnerAndName.second : "";

        switch (event.type()) {
            case CommitCommentEvent:
                // 需要异步加载（CommitCommentLoadTask）才能确定 Intent，由调用方处理
                return null;

            case CreateEvent: {
                CreatePayload payload = (CreatePayload) event.payload();
                String ref = null;
                if (payload.refType() == ReferenceType.Branch
                        || payload.refType() == ReferenceType.Tag) {
                    ref = payload.ref();
                }
                return RepositoryActivity.makeIntent(context, repoOwner, repoName, ref);
            }

            case DeleteEvent:
                return RepositoryActivity.makeIntent(context, repoOwner, repoName);

            case DeploymentEvent:
            case DeploymentStatusEvent:
                // 部署事件没有可直达的详情页，退化为打开仓库页
                return RepositoryActivity.makeIntent(context, repoOwner, repoName);

            case DownloadEvent:
                // 下载副作用而非跳转，由调用方处理
                return null;

            case DiscussionEvent: {
                DiscussionPayload payload = (DiscussionPayload) event.payload();
                Discussion discussion = payload.discussion();
                if (discussion != null && discussion.htmlUrl() != null) {
                    return new Intent(Intent.ACTION_VIEW, Uri.parse(discussion.htmlUrl()));
                } else {
                    return RepositoryActivity.makeIntent(context, repoOwner, repoName);
                }
            }

            case FollowEvent: {
                FollowPayload payload = (FollowPayload) event.payload();
                // UserActivity.makeIntent 对 null target / null login 返回 null，
                // 与点按原先的行为一致（不跳转）
                return UserActivity.makeIntent(context, payload.target());
            }

            case ForkEvent: {
                ForkPayload payload = (ForkPayload) event.payload();
                Repository forkee = payload.forkee();
                if (forkee == null) {
                    return null;
                }
                return RepositoryActivity.makeIntent(context, forkee);
            }

            case ForkApplyEvent:
                return RepositoryActivity.makeIntent(context, repoOwner, repoName);

            case GistEvent: {
                GistPayload payload = (GistPayload) event.payload();
                // 117：gist 可空，gist().id() 即 NPE
                if (payload.gist() == null) {
                    return null;
                }
                return GistActivity.makeIntent(context, payload.gist().id());
            }

            case GollumEvent: {
                GollumPayload payload = (GollumPayload) event.payload();
                // 117：pages() 可空，isEmpty() 即 NPE
                List<GitHubWikiPage> pages = payload.pages();
                return WikiListActivity.makeIntent(context, repoOwner, repoName,
                        pages == null || pages.isEmpty() ? null : pages.get(0));
            }

            case IssueCommentEvent: {
                IssueCommentPayload payload = (IssueCommentPayload) event.payload();
                Issue issue = payload.issue();
                PullRequest request = issue != null ? issue.pullRequest() : null;
                // 117：comment.id() 为 Long，InitialCommentMarker(long) 会拆箱
                IntentUtils.InitialCommentMarker initialComment = payload.comment() != null
                        && payload.comment().id() != null
                        ? new IntentUtils.InitialCommentMarker(payload.comment().id()) : null;

                // 117：issue.number() 为 Integer，makeIntent(int) 会拆箱
                if (issue != null && issue.number() == null) {
                    return null;
                }
                if (request != null && request.htmlUrl() != null) {
                    return PullRequestActivity.makeIntent(context,
                            repoOwner, repoName, issue.number(),
                            initialComment != null ? PullRequestActivity.PAGE_CONVERSATION : -1,
                            initialComment);
                } else if (issue != null) {
                    return IssueActivity.makeIntent(context,
                            repoOwner, repoName, issue.number(), initialComment);
                }
                return null;
            }

            case IssuesEvent: {
                IssuesPayload payload = (IssuesPayload) event.payload();
                // 117：issue/number 可空，number() 即 NPE/拆箱 NPE
                if (payload.issue() == null || payload.issue().number() == null) {
                    return null;
                }
                return IssueActivity.makeIntent(context, repoOwner, repoName,
                        payload.issue().number());
            }

            case MemberEvent:
                return RepositoryActivity.makeIntent(context, repoOwner, repoName);

            case MembershipEvent: {
                MembershipPayload payload = (MembershipPayload) event.payload();
                User member = payload.member();
                if (member != null) {
                    return UserActivity.makeIntent(context, member);
                } else {
                    return RepositoryActivity.makeIntent(context, repoOwner, repoName);
                }
            }

            case PageBuildEvent:
                // Pages 构建事件没有可直达的详情页，退化为打开仓库页
                return RepositoryActivity.makeIntent(context, repoOwner, repoName);

            case PublicEvent:
                return RepositoryActivity.makeIntent(context, repoOwner, repoName);

            case PullRequestEvent: {
                PullRequestPayload payload = (PullRequestPayload) event.payload();
                // 115：number() 为 Integer，makeIntent(int) 会拆箱
                if (payload.number() == null) {
                    return null;
                }
                return PullRequestActivity.makeIntent(context,
                        repoOwner, repoName, payload.number());
            }

            case PullRequestReviewEvent: {
                PullRequestReviewPayload payload = (PullRequestReviewPayload) event.payload();
                // 117：pullRequest/number 可空，number() 即 NPE/拆箱 NPE
                if (payload.pullRequest() == null || payload.pullRequest().number() == null) {
                    return null;
                }
                return ReviewActivity.makeIntent(context, repoOwner, repoName,
                        payload.pullRequest().number(), payload.review(), null);
            }

            case PullRequestReviewCommentEvent:
                // 含异步加载分支（有 marker 时走 PullRequestReviewCommentLoadTask），
                // 整支留在调用方，不由本方法返回
                return null;

            case PushEvent: {
                PushPayload payload = (PushPayload) event.payload();
                List<GitCommit> commits = payload.commits();

                if (commits != null && !commits.isEmpty()) {
                    if (commits.size() > 1) {
                        // if commit > 1, then show compare activity
                        return CompareActivity.makeIntent(context, repoOwner, repoName,
                                payload.before(), payload.head());
                    } else {
                        // only 1 commit, then show the commit details
                        return CommitActivity.makeIntent(context,
                                repoOwner, repoName, commits.get(0).sha());
                    }
                } else {
                    return RepositoryActivity.makeIntent(context, repoOwner, repoName);
                }
            }

            case ReleaseEvent: {
                ReleasePayload payload = (ReleasePayload) event.payload();
                Release release = payload.release();
                // 117：release.id() 为 Long，makeIntent(long) 会拆箱
                if (release != null && release.id() != null) {
                    return ReleaseInfoActivity.makeIntent(context,
                            repoOwner, repoName, release.id());
                }
                return null;
            }

            case RepositoryEvent:
                // 仓库创建/改名/归档等事件，退化为打开仓库页
                return RepositoryActivity.makeIntent(context, repoOwner, repoName);

            case StatusEvent:
                // commit 状态事件，退化为打开仓库页
                return RepositoryActivity.makeIntent(context, repoOwner, repoName);

            case TeamAddEvent: {
                TeamAddPayload payload = (TeamAddPayload) event.payload();
                Team team = payload.team();
                if (team != null && team.organization() != null) {
                    return UserActivity.makeIntent(context, team.organization());
                } else {
                    return RepositoryActivity.makeIntent(context, repoOwner, repoName);
                }
            }

            case WatchEvent:
                return RepositoryActivity.makeIntent(context, repoOwner, repoName);
        }

        // 未列出的事件类型无直达目的地
        return null;
    }
}
