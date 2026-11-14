package com.gh4a.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.Parcel;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.gh4a.R;
import com.meisolsson.githubsdk.model.ReleaseAsset;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Date;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
public class ReleaseAssetAdapterTest {
    private Context context;
    private ReleaseAssetAdapter adapter;
    private LayoutInflater inflater;
    private ViewGroup parent;

    @Before
    public void setUp() {
        context = AdapterTestSupport.context();
        adapter = new ReleaseAssetAdapter(context);
        inflater = AdapterTestSupport.inflater(context);
        parent = AdapterTestSupport.parent(context);
    }

    private ReleaseAssetAdapter.ViewHolder holder() {
        return adapter.onCreateViewHolder(inflater, parent, 2);
    }

    private ReleaseAsset asset(String name, String label, Integer size, Integer downloads) {
        ReleaseAsset a = mock(ReleaseAsset.class);
        when(a.name()).thenReturn(name);
        when(a.label()).thenReturn(label);
        when(a.size()).thenReturn(size);
        when(a.downloadCount()).thenReturn(downloads);
        when(a.createdAt()).thenReturn(new Date(System.currentTimeMillis() - 1000L));
        return a;
    }

    private String text(ReleaseAssetAdapter.ViewHolder h, int id) {
        return ((TextView) h.itemView.findViewById(id)).getText().toString();
    }

    @Test
    public void bind_withLabelAndCounts() {
        ReleaseAssetAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, asset("app.apk", "release apk", 2048, 7));
        assertEquals("app.apk", text(h, R.id.tv_title));
        assertEquals(View.VISIBLE, h.itemView.findViewById(R.id.tv_desc).getVisibility());
        assertEquals("release apk", text(h, R.id.tv_desc));
        assertEquals(View.VISIBLE, h.itemView.findViewById(R.id.tv_size).getVisibility());
        assertEquals(View.VISIBLE, h.itemView.findViewById(R.id.tv_downloads).getVisibility());
        assertEquals("7", text(h, R.id.tv_downloads));
    }

    @Test
    public void bind_blankLabel_hidesDesc() {
        ReleaseAssetAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, asset("app.apk", "  ", 100, 1));
        assertEquals(View.GONE, h.itemView.findViewById(R.id.tv_desc).getVisibility());
    }

    @Test
    public void bind_nullLabel_hidesDesc() {
        ReleaseAssetAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, asset("app.apk", null, 100, 1));
        assertEquals(View.GONE, h.itemView.findViewById(R.id.tv_desc).getVisibility());
    }

    @Test
    public void bind_nullSizeAndNullDownloads_sizeZeroDownloadsHidden() {
        ReleaseAssetAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, asset("app.apk", null, null, null));
        assertEquals(View.VISIBLE, h.itemView.findViewById(R.id.tv_size).getVisibility());
        assertEquals(View.GONE, h.itemView.findViewById(R.id.tv_downloads).getVisibility());
    }

    @Test
    public void bind_sourceCodeAsset_hidesSizeAndDownloads() {
        Date created = new Date(System.currentTimeMillis() - 1000L);
        ReleaseAssetAdapter.SourceCodeAsset a = new ReleaseAssetAdapter.SourceCodeAsset(
                "source.zip", "https://example.com/s.zip", "application/zip", created);
        ReleaseAssetAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, a);
        assertEquals("source.zip", text(h, R.id.tv_title));
        assertEquals(View.GONE, h.itemView.findViewById(R.id.tv_size).getVisibility());
        assertEquals(View.GONE, h.itemView.findViewById(R.id.tv_downloads).getVisibility());
        assertEquals(View.GONE, h.itemView.findViewById(R.id.tv_desc).getVisibility());
    }

    @Test
    public void sourceCodeAsset_fieldsAndParcelRoundTrip() {
        Date created = new Date(1_700_000_000_000L);
        ReleaseAssetAdapter.SourceCodeAsset a = new ReleaseAssetAdapter.SourceCodeAsset(
                "src.tar.gz", "https://example.com/s.tgz", "application/gzip", created);
        assertEquals("src.tar.gz", a.name());
        assertEquals("https://example.com/s.tgz", a.url());
        assertEquals("https://example.com/s.tgz", a.browserDownloadUrl());
        assertEquals("application/gzip", a.contentType());
        assertEquals("uploaded", a.state());
        assertEquals(Long.valueOf(0L), a.id());
        assertEquals(Integer.valueOf(0), a.size());
        assertEquals(Integer.valueOf(0), a.downloadCount());
        assertEquals(created, a.createdAt());
        assertEquals(created, a.updatedAt());
        assertEquals(0, a.describeContents());

        Parcel parcel = Parcel.obtain();
        a.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        ReleaseAssetAdapter.SourceCodeAsset restored =
                ReleaseAssetAdapter.SourceCodeAsset.CREATOR.createFromParcel(parcel);
        assertEquals("src.tar.gz", restored.name());
        assertEquals("https://example.com/s.tgz", restored.url());
        assertEquals(created, restored.createdAt());
        assertEquals(1, ReleaseAssetAdapter.SourceCodeAsset.CREATOR.newArray(1).length);
        parcel.recycle();
    }

    @Test
    public void sourceCodeAsset_nullCreatedAt_parcelRoundTripGivesNull() {
        ReleaseAssetAdapter.SourceCodeAsset a = new ReleaseAssetAdapter.SourceCodeAsset(
                "n", "u", "c", null);
        Parcel parcel = Parcel.obtain();
        a.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        ReleaseAssetAdapter.SourceCodeAsset restored =
                ReleaseAssetAdapter.SourceCodeAsset.CREATOR.createFromParcel(parcel);
        assertEquals(null, restored.createdAt());
        parcel.recycle();
    }

    @Test
    public void bind_sourceCodeAsset_nullCreatedAt_noCrash() {
        ReleaseAssetAdapter.SourceCodeAsset a = new ReleaseAssetAdapter.SourceCodeAsset(
                "n", "u", "c", null);
        ReleaseAssetAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, a);
        assertEquals("n", text(h, R.id.tv_title));
        assertTrue(text(h, R.id.tv_created_at).length() >= 0);
    }

    @Test
    public void sourceCodeAsset_uploaderIsNull() {
        ReleaseAssetAdapter.SourceCodeAsset a = new ReleaseAssetAdapter.SourceCodeAsset(
                "source.zip", "https://example.com/s.zip", "application/zip", new Date());
        assertNull(a.uploader());
    }
}
