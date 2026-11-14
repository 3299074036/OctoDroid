package com.gh4a.adapter.timeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.gh4a.R;
import com.gh4a.adapter.AdapterTestSupport;
import com.gh4a.model.TimelineItem;
import com.meisolsson.githubsdk.model.ReviewComment;
import com.meisolsson.githubsdk.model.User;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class ReplyViewHolderTest {
    private Context context;
    private List<Long> repliedTo;
    private long selectedId;

    @Before
    public void setUp() {
        context = AdapterTestSupport.context();
        repliedTo = new ArrayList<>();
        selectedId = 0;
    }

    private ReplyViewHolder.Callback callback() {
        return new ReplyViewHolder.Callback() {
            @Override
            public long getSelectedCommentId() {
                return selectedId;
            }

            @Override
            public void reply(long replyToId) {
                repliedTo.add(replyToId);
            }
        };
    }

    private ReplyViewHolder holder() {
        View view = LayoutInflater.from(context)
                .inflate(R.layout.row_timeline_reply, null, false);
        return new ReplyViewHolder(view, callback());
    }

    private TimelineItem.Reply replyItem(Long commentId) {
        ReviewComment rc = AdapterTestSupport.reviewComment(commentId,
                User.builder().login("octocat").build(), "<p>hi</p>", null, null, null, null,
                null, null, null, null, null);
        return new TimelineItem.Reply(new TimelineItem.TimelineComment(rc));
    }

    @Test
    public void bind_notSelected_showsReply() {
        selectedId = 0;
        ReplyViewHolder h = holder();
        h.bind(replyItem(5L));
        assertEquals(context.getString(R.string.reply),
                ((Button) h.itemView.findViewById(R.id.btn_reply)).getText().toString());
    }

    @Test
    public void bind_selected_showsReplySelected() {
        selectedId = 5L;
        ReplyViewHolder h = holder();
        h.bind(replyItem(5L));
        assertEquals(context.getString(R.string.reply_selected),
                ((Button) h.itemView.findViewById(R.id.btn_reply)).getText().toString());
    }

    @Test
    public void bind_nullCommentId_notSelected() {
        selectedId = 5L;
        ReplyViewHolder h = holder();
        h.bind(replyItem(null));
        assertEquals(context.getString(R.string.reply),
                ((Button) h.itemView.findViewById(R.id.btn_reply)).getText().toString());
    }

    @Test
    public void onClick_replyButton_callsCallbackWithCommentId() {
        ReplyViewHolder h = holder();
        h.bind(replyItem(7L));
        h.itemView.findViewById(R.id.btn_reply).performClick();
        assertEquals(1, repliedTo.size());
        assertEquals(Long.valueOf(7L), repliedTo.get(0));
    }

    @Test
    public void onClick_replyButton_nullCommentId_repliesZero() {
        ReplyViewHolder h = holder();
        h.bind(replyItem(null));
        h.itemView.findViewById(R.id.btn_reply).performClick();
        assertEquals(Long.valueOf(0L), repliedTo.get(0));
    }

    @Test
    public void onClick_otherView_noop() {
        ReplyViewHolder h = holder();
        h.bind(replyItem(7L));
        // the row layout root IS the btn_reply Button, so a genuinely
        // different view is needed to exercise the non-reply branch
        h.onClick(new View(context));
        assertTrue(repliedTo.isEmpty());
    }
}
