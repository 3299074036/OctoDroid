/*
 * Copyright 2026 OctoDroid contributors
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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.gh4a.Gh4Application;
import com.gh4a.R;
import com.gh4a.ServiceFactory;
import com.gh4a.widget.ZoomableImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Full-screen in-app image viewer: downloads the image (with auth for private
 * repos), decodes it on a background thread with downsampling for large
 * images, and shows it in a pinch-to-zoom view.
 */
public class ImageViewerActivity extends AppCompatActivity {
    private static final String EXTRA_IMAGE_URL = "image_url";
    private static final String EXTRA_FILE_NAME = "file_name";
    private static final String EXTRA_AUTH_REQUIRED = "auth_required";

    /** Max dimension used when downsampling very large images to avoid OOM. */
    private static final int MAX_DECODED_DIMENSION = 2048;

    /** Refuse to buffer more than this; huge files would OOM before downsampling. */
    private static final int MAX_IMAGE_BYTES = 32 * 1024 * 1024;

    public static Intent makeIntent(Context context, String imageUrl, String fileName,
            boolean authRequired) {
        return new Intent(context, ImageViewerActivity.class)
                .putExtra(EXTRA_IMAGE_URL, imageUrl)
                .putExtra(EXTRA_FILE_NAME, fileName)
                .putExtra(EXTRA_AUTH_REQUIRED, authRequired);
    }

    private String mImageUrl;
    private String mFileName;
    private boolean mAuthRequired;

    private Call mDownloadCall;
    private int mLoadGeneration;

    /**
     * Download state that survives rotation. The worker thread publishes the
     * result here; a rotated instance takes over an in-flight download by
     * waiting on {@link #lock} instead of starting over.
     */
    private static class DownloadState {
        final Object lock = new Object();
        boolean done;
        Bitmap bitmap;
        Exception error;
    }

    private DownloadState mDownloadState;

    private ZoomableImageView mImageView;
    private ProgressBar mProgressBar;
    private View mErrorView;
    private TextView mErrorText;
    private Button mRetryButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_viewer);

        Intent intent = getIntent();
        mImageUrl = intent.getStringExtra(EXTRA_IMAGE_URL);
        mFileName = intent.getStringExtra(EXTRA_FILE_NAME);
        mAuthRequired = intent.getBooleanExtra(EXTRA_AUTH_REQUIRED, false);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(mFileName);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        mImageView = findViewById(R.id.image_view);
        mProgressBar = findViewById(R.id.progress_bar);
        mErrorView = findViewById(R.id.error_view);
        mErrorText = findViewById(R.id.error_text);
        mRetryButton = findViewById(R.id.retry_button);

        mRetryButton.setOnClickListener(v -> loadImage());
        mImageView.setOnClickListener(v -> finish());

        DownloadState retained = (DownloadState) getLastCustomNonConfigurationInstance();
        if (retained != null && retained.done) {
            // Rotation after completion: reuse the result, no re-download.
            mDownloadState = retained;
            deliverResult(retained.bitmap, retained.error);
        } else if (retained != null) {
            // Rotation mid-download: take over the in-flight download instead
            // of cancelling and starting over.
            mDownloadState = retained;
            takeOverDownload(retained);
        } else {
            loadImage();
        }
    }

    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        return mDownloadState;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadImage() {
        // Cancel any previous in-flight download (e.g. rapid retry taps) so
        // concurrent downloads can't pile up; the stale worker's result is
        // dropped via the generation check below.
        if (mDownloadCall != null) {
            mDownloadCall.cancel();
            mDownloadCall = null;
        }
        final int generation = ++mLoadGeneration;

        mImageView.setVisibility(View.GONE);
        mErrorView.setVisibility(View.GONE);
        mProgressBar.setVisibility(View.VISIBLE);

        final DownloadState state = new DownloadState();
        mDownloadState = state;
        final Call call = createDownloadCall();
        mDownloadCall = call;
        new Thread(() -> {
            Bitmap bitmap = null;
            Exception error = null;
            try {
                bitmap = downloadAndDecodeImage(call);
            } catch (Exception e) {
                error = e;
            } finally {
                if (mDownloadCall == call) {
                    mDownloadCall = null;
                }
            }
            synchronized (state.lock) {
                state.bitmap = bitmap;
                state.error = error;
                state.done = true;
                state.lock.notifyAll();
            }

            final Bitmap result = bitmap;
            final Exception failure = error;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || generation != mLoadGeneration) {
                    return;
                }
                deliverResult(result, failure);
            });
        }, "image-viewer-download").start();
    }

    /**
     * Waits for an in-flight download owned by the pre-rotation instance and
     * delivers its result, so rotating mid-download doesn't waste the transfer.
     */
    private void takeOverDownload(DownloadState state) {
        final int generation = ++mLoadGeneration;

        mImageView.setVisibility(View.GONE);
        mErrorView.setVisibility(View.GONE);
        mProgressBar.setVisibility(View.VISIBLE);

        new Thread(() -> {
            final Bitmap result;
            final Exception failure;
            synchronized (state.lock) {
                while (!state.done) {
                    try {
                        state.lock.wait();
                    } catch (InterruptedException e) {
                        return;
                    }
                }
                result = state.bitmap;
                failure = state.error;
            }
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || generation != mLoadGeneration) {
                    return;
                }
                deliverResult(result, failure);
            });
        }, "image-viewer-download-takeover").start();
    }

    private void deliverResult(Bitmap bitmap, Exception error) {
        if (bitmap != null) {
            mImageView.setImageBitmap(bitmap);
            mImageView.setVisibility(View.VISIBLE);
            mProgressBar.setVisibility(View.GONE);
        } else {
            showError(error);
        }
    }

    @Override
    protected void onDestroy() {
        // Cancel the in-flight download only when truly leaving: on rotation
        // the download is handed over to the new instance via the retained
        // DownloadState, so cancelling here would just waste the transfer.
        // Thread.interrupt() alone cannot unblock OkHttp's socket read, so
        // cancel the Call itself; the worker thread then dies from the
        // resulting IOException.
        if (isFinishing() && mDownloadCall != null) {
            mDownloadCall.cancel();
            mDownloadCall = null;
        }
        super.onDestroy();
    }

    private void showError(Exception error) {
        mProgressBar.setVisibility(View.GONE);
        mImageView.setVisibility(View.GONE);
        String message = error != null && error.getMessage() != null
                ? error.getMessage()
                : getString(R.string.image_viewer_error_unknown);
        mErrorText.setText(getString(R.string.image_viewer_error_format, message));
        mErrorView.setVisibility(View.VISIBLE);
    }

    private Call createDownloadCall() {
        Request.Builder requestBuilder = new Request.Builder().url(mImageUrl);
        if (mAuthRequired) {
            String token = Gh4Application.get().getAuthToken();
            if (token != null) {
                requestBuilder.header("Authorization", "Token " + token);
            }
            // Never cache responses to authenticated requests in the shared
            // HTTP cache: the cache key is the URL alone, so a revoked token
            // or a different account could otherwise read stale private data.
            requestBuilder.header("Cache-Control", "no-store");
        }
        // Shared singleton: reuses the connection pool and the HTTP cache
        // instead of building a throwaway client per download.
        return ServiceFactory.getImageHttpClient().newCall(requestBuilder.build());
    }

    private Bitmap downloadAndDecodeImage(Call call) throws IOException {
        Response response = call.execute();
        try (ResponseBody body = response.body()) {
            if (!response.isSuccessful() || body == null) {
                throw new IOException(getString(R.string.image_viewer_error_http,
                        response.code()));
            }
            try (InputStream in = body.byteStream()) {
                File tempFile = downloadToTempFile(in);
                try {
                    return decodeFileWithDownsampling(tempFile);
                } finally {
                    tempFile.delete();
                }
            }
        }
    }

    /**
     * Streams the response into a temp file instead of buffering it on the
     * heap, so even images close to {@link #MAX_IMAGE_BYTES} don't risk OOM
     * on low-memory devices.
     */
    private File downloadToTempFile(InputStream in) throws IOException {
        File tempFile = File.createTempFile("image-viewer", ".tmp", getCacheDir());
        try (OutputStream out = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > MAX_IMAGE_BYTES) {
                    throw new IOException(getString(R.string.image_viewer_error_too_large));
                }
                out.write(buffer, 0, read);
            }
            if (total == 0) {
                throw new IOException(getString(R.string.image_viewer_error_empty));
            }
        } catch (IOException e) {
            tempFile.delete();
            throw e;
        }
        return tempFile;
    }

    /**
     * Decodes the image with inSampleSize downsampling if it exceeds
     * {@link #MAX_DECODED_DIMENSION} in either dimension, to avoid OOM.
     */
    private Bitmap decodeFileWithDownsampling(File file) throws IOException {
        BitmapFactory.Options boundsOptions = new BitmapFactory.Options();
        boundsOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), boundsOptions);

        int inSampleSize = 1;
        int width = boundsOptions.outWidth;
        int height = boundsOptions.outHeight;
        while (width / (inSampleSize * 2) >= MAX_DECODED_DIMENSION
                || height / (inSampleSize * 2) >= MAX_DECODED_DIMENSION) {
            inSampleSize *= 2;
        }

        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        decodeOptions.inSampleSize = inSampleSize;
        decodeOptions.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), decodeOptions);
        if (bitmap == null) {
            throw new IOException(getString(R.string.image_viewer_error_decode));
        }
        return bitmap;
    }
}
