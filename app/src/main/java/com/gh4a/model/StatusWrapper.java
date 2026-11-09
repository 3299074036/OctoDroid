package com.gh4a.model;

import android.content.Context;

import com.gh4a.R;
import com.meisolsson.githubsdk.model.CheckRun;
import com.meisolsson.githubsdk.model.Status;

import java.util.Date;

import androidx.annotation.Nullable;

public class StatusWrapper {
    public enum State {
        Success,
        Failed,
        Unknown
    }

    private String mLabel;
    private String mDescription;
    private State mState;
    private String mTargetUrl;

    public StatusWrapper(Status status) {
        mLabel = status.context();
        mDescription = status.description();
        mTargetUrl = status.targetUrl();
        // 160：state() 可空（缺 state 字段），switch 前判空走未知分支。
        if (status.state() == null) {
            mState = State.Unknown;
            return;
        }
        switch (status.state()) {
            case Error:
            case Failure:
                mState = State.Failed;
                break;
            case Success:
                mState = State.Success;
                break;
            default:
                mState = State.Unknown;
                break;
        }
    }

    public StatusWrapper(Context context, CheckRun checkRun) {
        mLabel = checkRun.name();
        mTargetUrl = checkRun.detailsUrl();

        // 160：state() 可空（缺 state 字段），先判空走未知分支。
        if (checkRun.state() == null) {
            mState = State.Unknown;
            mDescription = context.getString(R.string.check_pending_description);
            return;
        }
        switch (checkRun.state()) {
            case Waiting:
            case Requested:
            case Queued:
                mState = State.Unknown;
                mDescription = context.getString(R.string.check_pending_description);
                break;
            case InProgress:
                mState = State.Unknown;
                mDescription = context.getString(R.string.check_running_description);
                break;
            case Completed: {
                String runtime = formatTimeDelta(context, checkRun.startedAt(), checkRun.completedAt());
                String runtimeDesc = runtime != null ? context.getString(R.string.check_runtime_description, runtime) : null;
                String title = checkRun.output() != null ? checkRun.output().title() : null;
                if (title != null && runtimeDesc != null) {
                    mDescription = title + " — " + runtimeDesc;
                } else if (title != null) {
                    mDescription = title;
                } else if (runtimeDesc != null) {
                    mDescription = runtimeDesc;
                }
                // 160：conclusion() 在 Completed 分支内仍可空，缺失按未知计。
                if (checkRun.conclusion() == null) {
                    mState = State.Unknown;
                } else switch (checkRun.conclusion()) {
                    case Failure:
                    case TimedOut:
                    case Cancelled:
                        mState = State.Failed;
                        break;
                    case Success:
                        mState = State.Success;
                        break;
                    default:
                        mState = State.Unknown;
                        break;
                }
                break;
            }
        }
    }

    public State state() {
        return mState;
    }

    public String label() {
        return mLabel;
    }

    public String description() {
        return mDescription;
    }

    public String targetUrl() {
        return mTargetUrl;
    }

    @Nullable
    private static String formatTimeDelta(Context context, @Nullable Date start, @Nullable Date end) {
        if (start == null || end == null) {
            return null;
        }

        long deltaSeconds = (end.getTime() - start.getTime()) / 1000;
        long seconds = deltaSeconds % 60;
        long minutes = (deltaSeconds % 3600) / 60;
        long hours = deltaSeconds / 3600;

        // B-L10：每档只传该格式串实际用的参数（之前恒传 3 个，
        // 小时档只有 2 个占位符，lint StringFormatMatches 报错）。
        if (hours == 0 && minutes == 0) {
            return context.getString(R.string.check_runtime_format_seconds, seconds);
        } else if (hours == 0) {
            return context.getString(R.string.check_runtime_format_minutes, minutes, seconds);
        } else {
            return context.getString(R.string.check_runtime_format_hours, hours, minutes);
        }
    }
}
