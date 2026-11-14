package com.gh4a.utils;

import android.content.Context;

import com.gh4a.BaseActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import androidx.test.core.app.ApplicationProvider;

import java.io.File;
import java.io.FileWriter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * M-NEW-1：下载文件名必须经白名单清洗——恶意镜像伪造的 release tag
 * 不能拼出路径穿越或特殊字符，否则 sanitizeFileName 会抛异常导致主线程崩溃。
 * A 组扩充：checkDownloadedApkSignature（不可解析的包一律 MISMATCH）与
 * downloadAndInstall 的入口拒绝路径（非 HTTPS / 非法文件名直接 toast 返回）。
 * downloadAndInstall 需 BaseActivity，用空子类 + Robolectric 建。
 */
@RunWith(RobolectricTestRunner.class)
public class ApkUpdateDownloaderTest {

    /** BaseActivity 无抽象方法，空子类即可建实例。 */
    public static class TestActivity extends BaseActivity {
        @Override
        protected void onCreate(android.os.Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            // BaseActivity.onStart 要求 content container 至少一个子 view，
            // 否则抛 "Content view must be initialized before"。
            setContentView(new android.view.View(this));
        }
    }

    private Context context;
    private TestActivity activity;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        DownloadRecordManager.clear(context);
    }

    private TestActivity activity() {
        if (activity == null) {
            activity = Robolectric.buildActivity(TestActivity.class).setup().get();
        }
        return activity;
    }

    @Test
    public void buildApkFileName_normalVersion() {
        assertEquals("OctoDroid_0.0.53.apk",
                ApkUpdateDownloader.buildApkFileName("0.0.53"));
    }

    @Test
    public void buildApkFileName_pathTraversalNeutralized() {
        // ".." 会被清洗掉，不再触发 sanitizeFileName 的路径穿越拒绝
        assertEquals("OctoDroid_0.0.99_.apk",
                ApkUpdateDownloader.buildApkFileName("0.0.99.."));
        assertEquals("OctoDroid_______etc_passwd.apk",
                ApkUpdateDownloader.buildApkFileName("../../../etc/passwd"));
    }

    @Test
    public void buildApkFileName_specialCharsAndEmpty() {
        assertEquals("OctoDroid_v1_0_x.apk",
                ApkUpdateDownloader.buildApkFileName("v1/0:x"));
        assertEquals("OctoDroid_update.apk",
                ApkUpdateDownloader.buildApkFileName(""));
        assertEquals("OctoDroid_update.apk",
                ApkUpdateDownloader.buildApkFileName(null));
    }

    @Test
    public void buildApkFileName_leadingDotsStripped() {
        // ".." 先被整段换成 "_"（"...abc" → "_.abc"），再去行首 "."；
        // "_.abc" 行首是 "_" 不是 "."，故不再剥。
        assertEquals("OctoDroid__.abc.apk",
                ApkUpdateDownloader.buildApkFileName("...abc"));
    }

    @Test
    public void buildApkFileName_onlyDotsFallsBackToUpdate() {
        // "..." → ".." 换 "_" 得 "_."，不为空，故不走 "update" 兜底。
        assertEquals("OctoDroid__..apk",
                ApkUpdateDownloader.buildApkFileName("..."));
    }

    @Test
    public void buildApkFileName_preservesDotsDashesUnderscore() {
        assertEquals("OctoDroid_1.2.3-beta_1.apk",
                ApkUpdateDownloader.buildApkFileName("1.2.3-beta_1"));
    }

    // ---- checkDownloadedApkSignature ----

    @Test
    public void checkSignature_missingFileIsMismatch() {
        File missing = new File(context.getFilesDir(), "no-such.apk");
        assertEquals(ApkUpdateDownloader.ApkSignatureCheck.MISMATCH,
                ApkUpdateDownloader.checkDownloadedApkSignature(context, missing));
    }

    @Test
    public void checkSignature_nonApkFileIsMismatch() throws Exception {
        File junk = new File(context.getFilesDir(), "junk.apk");
        try (FileWriter w = new FileWriter(junk)) {
            w.write("this is not an apk");
        }
        assertEquals(ApkUpdateDownloader.ApkSignatureCheck.MISMATCH,
                ApkUpdateDownloader.checkDownloadedApkSignature(context, junk));
    }

    @Test
    public void checkSignature_nonApkFileWithExpectedPackageIsMismatch()
            throws Exception {
        File junk = new File(context.getFilesDir(), "junk2.apk");
        try (FileWriter w = new FileWriter(junk)) {
            w.write("this is not an apk");
        }
        assertEquals(ApkUpdateDownloader.ApkSignatureCheck.MISMATCH,
                ApkUpdateDownloader.checkDownloadedApkSignature(context, junk,
                        "com.gh4a"));
    }

    @Test
    public void checkSignature_missingFileWithExpectedPackageIsMismatch() {
        File missing = new File(context.getFilesDir(), "no-such2.apk");
        assertEquals(ApkUpdateDownloader.ApkSignatureCheck.MISMATCH,
                ApkUpdateDownloader.checkDownloadedApkSignature(context, missing,
                        "com.gh4a"));
    }

    @Test
    public void signatureCheckEnum_hasThreeValues() {
        assertEquals(3, ApkUpdateDownloader.ApkSignatureCheck.values().length);
    }

    // ---- downloadAndInstall 入口拒绝路径 ----

    @Test
    public void downloadAndInstall_httpUrlRejectedWithoutEnqueue() {
        ApkUpdateDownloader.downloadAndInstall(activity(),
                "http://example.com/OctoDroid_1.apk", "OctoDroid_1.apk");
        assertTrue(DownloadRecordManager.getRecords(context).isEmpty());
    }

    @Test
    public void downloadAndInstall_nullUrlRejectedWithoutEnqueue() {
        ApkUpdateDownloader.downloadAndInstall(activity(), null, "OctoDroid_1.apk");
        assertTrue(DownloadRecordManager.getRecords(context).isEmpty());
    }

    @Test
    public void downloadAndInstall_emptyUrlRejectedWithoutEnqueue() {
        ApkUpdateDownloader.downloadAndInstall(activity(), "", "OctoDroid_1.apk");
        assertTrue(DownloadRecordManager.getRecords(context).isEmpty());
    }

    @Test
    public void downloadAndInstall_pathTraversalFileNameSanitizedAndEnqueued() {
        // "../evil.apk" 不被拒：sanitizeFileName 先剥目录段再校验，
        // 得 "evil.apk" 后正常入队，文件落应用私有下载目录，不越界。
        ApkUpdateDownloader.downloadAndInstall(activity(),
                "https://example.com/OctoDroid_1.apk", "../evil.apk");
        assertEquals(1, DownloadRecordManager.getRecords(context).size());
        assertEquals("evil.apk",
                DownloadRecordManager.getRecords(context).get(0).fileName);
    }

    @Test
    public void downloadAndInstall_nullFileNameRejectedWithoutEnqueue() {
        ApkUpdateDownloader.downloadAndInstall(activity(),
                "https://example.com/OctoDroid_1.apk", null);
        assertTrue(DownloadRecordManager.getRecords(context).isEmpty());
    }
}
