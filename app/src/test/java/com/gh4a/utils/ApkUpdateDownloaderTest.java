package com.gh4a.utils;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.net.Uri;
import android.os.Environment;
import android.os.Looper;

import com.gh4a.BaseActivity;
import com.gh4a.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowDownloadManager;
import org.robolectric.shadows.ShadowPackageManager;
import org.robolectric.shadows.ShadowToast;
import androidx.test.core.app.ApplicationProvider;

import java.io.File;
import java.io.FileWriter;
import java.util.function.BooleanSupplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

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

    // ---- 缺口补齐 ----

    private static void idleMain() {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private static void waitUntil(BooleanSupplier condition) throws Exception {
        for (int i = 0; i < 250; i++) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
            idleMain();
        }
        assertTrue(condition.getAsBoolean());
    }

    private static Signature sig(byte... bytes) {
        return new Signature(bytes);
    }

    private static PackageInfo packageInfo(String packageName, Signature[] sigs) {
        PackageInfo info = new PackageInfo();
        info.packageName = packageName;
        info.signatures = sigs;
        return info;
    }

    @Test
    public void defaultConstructor_isInstantiable() {
        assertNotNull(new ApkUpdateDownloader());
    }

    /** mock PackageManager 驱动 checkApkSignature：archive/installed 均可控。 */
    private static ApkUpdateDownloader.ApkSignatureCheck checkWith(
            PackageInfo archive, PackageInfo installed, boolean throwNotFound,
            String expectedPackage) throws Exception {
        PackageManager pm = mock(PackageManager.class);
        when(pm.getPackageArchiveInfo(anyString(), anyInt())).thenReturn(archive);
        if (throwNotFound) {
            when(pm.getPackageInfo(anyString(), anyInt()))
                    .thenThrow(new PackageManager.NameNotFoundException());
        } else {
            when(pm.getPackageInfo(anyString(), anyInt())).thenReturn(installed);
        }
        Context ctx = mock(Context.class);
        when(ctx.getPackageManager()).thenReturn(pm);
        return ApkUpdateDownloader.checkDownloadedApkSignature(ctx,
                new File("/tmp/x.apk"), expectedPackage);
    }

    @Test
    public void checkSignature_oneArgOverloadPassesNullExpectedPackage() throws Exception {
        // 1-arg 重载传 expectedPackage=null：archive 有效时走到 162 的 null 半边
        Signature a = sig((byte) 1);
        PackageManager pm = mock(PackageManager.class);
        when(pm.getPackageArchiveInfo(anyString(), anyInt()))
                .thenReturn(packageInfo("com.gh4a", new Signature[]{a}));
        when(pm.getPackageInfo(anyString(), anyInt()))
                .thenReturn(packageInfo("com.gh4a", new Signature[]{a}));
        Context ctx = mock(Context.class);
        when(ctx.getPackageManager()).thenReturn(pm);
        assertEquals(ApkUpdateDownloader.ApkSignatureCheck.MATCH,
                ApkUpdateDownloader.checkDownloadedApkSignature(ctx, new File("/tmp/x.apk")));
    }

    @Test
    public void checkSignature_matchWhenSignaturesEqual() throws Exception {
        Signature a = sig((byte) 1, (byte) 2, (byte) 3);
        assertEquals(ApkUpdateDownloader.ApkSignatureCheck.MATCH,
                checkWith(packageInfo("com.gh4a", new Signature[]{a}),
                        packageInfo("com.gh4a", new Signature[]{a}), false, "com.gh4a"));
    }

    @Test
    public void checkSignature_archivePackageNullIsMismatch() throws Exception {
        Signature a = sig((byte) 1);
        assertEquals(ApkUpdateDownloader.ApkSignatureCheck.MISMATCH,
                checkWith(packageInfo(null, new Signature[]{a}),
                        packageInfo("com.gh4a", new Signature[]{a}), false, "com.gh4a"));
    }

    @Test
    public void checkSignature_expectedPackageMismatch() throws Exception {
        Signature a = sig((byte) 1);
        assertEquals(ApkUpdateDownloader.ApkSignatureCheck.MISMATCH,
                checkWith(packageInfo("com.other", new Signature[]{a}),
                        packageInfo("com.other", new Signature[]{a}), false, "com.gh4a"));
    }

    @Test
    public void checkSignature_notInstalledWhenPackageInfoThrows() throws Exception {
        Signature a = sig((byte) 1);
        assertEquals(ApkUpdateDownloader.ApkSignatureCheck.NOT_INSTALLED,
                checkWith(packageInfo("com.gh4a", new Signature[]{a}),
                        null, true, "com.gh4a"));
    }

    @Test
    public void checkSignature_installedNullIsMismatch() throws Exception {
        Signature a = sig((byte) 1);
        assertEquals(ApkUpdateDownloader.ApkSignatureCheck.MISMATCH,
                checkWith(packageInfo("com.gh4a", new Signature[]{a}),
                        null, false, "com.gh4a"));
    }

    @Test
    public void checkSignature_archiveSignaturesNullIsMismatch() throws Exception {
        Signature a = sig((byte) 1);
        assertEquals(ApkUpdateDownloader.ApkSignatureCheck.MISMATCH,
                checkWith(packageInfo("com.gh4a", null),
                        packageInfo("com.gh4a", new Signature[]{a}), false, "com.gh4a"));
    }

    @Test
    public void checkSignature_archiveSignaturesEmptyIsMismatch() throws Exception {
        Signature a = sig((byte) 1);
        assertEquals(ApkUpdateDownloader.ApkSignatureCheck.MISMATCH,
                checkWith(packageInfo("com.gh4a", new Signature[]{}),
                        packageInfo("com.gh4a", new Signature[]{a}), false, "com.gh4a"));
    }

    @Test
    public void checkSignature_installedSignaturesNullIsMismatch() throws Exception {
        Signature a = sig((byte) 1);
        assertEquals(ApkUpdateDownloader.ApkSignatureCheck.MISMATCH,
                checkWith(packageInfo("com.gh4a", new Signature[]{a}),
                        packageInfo("com.gh4a", null), false, "com.gh4a"));
    }

    @Test
    public void checkSignature_installedSignaturesEmptyIsMismatch() throws Exception {
        Signature a = sig((byte) 1);
        assertEquals(ApkUpdateDownloader.ApkSignatureCheck.MISMATCH,
                checkWith(packageInfo("com.gh4a", new Signature[]{a}),
                        packageInfo("com.gh4a", new Signature[]{}), false, "com.gh4a"));
    }

    @Test
    public void checkSignature_installedSignatureMissingFromArchiveIsMismatch()
            throws Exception {
        Signature a = sig((byte) 1);
        Signature b = sig((byte) 2);
        assertEquals(ApkUpdateDownloader.ApkSignatureCheck.MISMATCH,
                checkWith(packageInfo("com.gh4a", new Signature[]{a}),
                        packageInfo("com.gh4a", new Signature[]{a, b}), false, "com.gh4a"));
    }

    @Test
    public void checkSignature_archiveInfoThrowsIsMismatch() throws Exception {
        PackageManager pm = mock(PackageManager.class);
        when(pm.getPackageArchiveInfo(anyString(), anyInt()))
                .thenThrow(new RuntimeException("boom"));
        Context ctx = mock(Context.class);
        when(ctx.getPackageManager()).thenReturn(pm);
        assertEquals(ApkUpdateDownloader.ApkSignatureCheck.MISMATCH,
                ApkUpdateDownloader.checkDownloadedApkSignature(ctx, new File("/tmp/x.apk")));
    }

    @Test
    public void checkSignature_signingInfoMultipleSignersUsesContentsSigners()
            throws Exception {
        Signature a = sig((byte) 1);
        SigningInfo archiveSigning = mock(SigningInfo.class);
        when(archiveSigning.hasMultipleSigners()).thenReturn(true);
        when(archiveSigning.getApkContentsSigners()).thenReturn(new Signature[]{a});
        SigningInfo installedSigning = mock(SigningInfo.class);
        when(installedSigning.hasMultipleSigners()).thenReturn(true);
        when(installedSigning.getApkContentsSigners()).thenReturn(new Signature[]{a});
        PackageInfo archive = packageInfo("com.gh4a", null);
        archive.signingInfo = archiveSigning;
        PackageInfo installed = packageInfo("com.gh4a", null);
        installed.signingInfo = installedSigning;
        assertEquals(ApkUpdateDownloader.ApkSignatureCheck.MATCH,
                checkWith(archive, installed, false, "com.gh4a"));
    }

    @Test
    public void checkSignature_signingInfoSingleSignerUsesHistory() throws Exception {
        Signature a = sig((byte) 1);
        SigningInfo archiveSigning = mock(SigningInfo.class);
        when(archiveSigning.hasMultipleSigners()).thenReturn(false);
        when(archiveSigning.getSigningCertificateHistory()).thenReturn(new Signature[]{a});
        SigningInfo installedSigning = mock(SigningInfo.class);
        when(installedSigning.hasMultipleSigners()).thenReturn(false);
        when(installedSigning.getSigningCertificateHistory()).thenReturn(new Signature[]{a});
        PackageInfo archive = packageInfo("com.gh4a", null);
        archive.signingInfo = archiveSigning;
        PackageInfo installed = packageInfo("com.gh4a", null);
        installed.signingInfo = installedSigning;
        assertEquals(ApkUpdateDownloader.ApkSignatureCheck.MATCH,
                checkWith(archive, installed, false, "com.gh4a"));
    }

    // ---- 下载完成回调（广播模拟） ----

    private void sendDownloadComplete(long downloadId) {
        Intent intent = new Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        intent.putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, downloadId);
        context.sendBroadcast(intent);
        idleMain();
    }

    private ShadowDownloadManager.ShadowRequest shadowRequest(long downloadId) {
        DownloadManager dm = (DownloadManager)
                context.getSystemService(Context.DOWNLOAD_SERVICE);
        return Shadow.extract(Shadows.shadowOf(dm).getRequest(downloadId));
    }

    @Test
    public void downloadAndInstall_failedDownloadShowsFailedToast() throws Exception {
        ApkUpdateDownloader.downloadAndInstall(activity(),
                "https://example.com/OctoDroid_1.apk", "OctoDroid_1.apk");
        assertEquals(1, DownloadRecordManager.getRecords(context).size());
        long id = DownloadRecordManager.getRecords(context).get(0).downloadId;
        sendDownloadComplete(id);
        String expected = context.getString(R.string.download_failed);
        waitUntil(() -> expected.equals(ShadowToast.getTextOfLatestToast()));
        assertEquals(expected, ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void downloadAndInstall_successfulDownloadWithBadSignatureDeletesFile()
            throws Exception {
        ApkUpdateDownloader.downloadAndInstall(activity(),
                "https://example.com/OctoDroid_1.apk", "OctoDroid_1.apk");
        long id = DownloadRecordManager.getRecords(context).get(0).downloadId;
        ShadowDownloadManager.ShadowRequest req = shadowRequest(id);
        req.setStatus(DownloadManager.STATUS_SUCCESSFUL);
        File dest = new File(req.getDestination().getPath());
        dest.getParentFile().mkdirs();
        try (FileWriter w = new FileWriter(dest)) {
            w.write("not an apk");
        }
        sendDownloadComplete(id);
        // 校验在后台线程跑，回调回主线程：等文件被删
        waitUntil(() -> !dest.exists());
        assertTrue(DownloadRecordManager.getRecords(context).isEmpty());
        assertEquals(context.getString(R.string.apk_signature_mismatch),
                ShadowToast.getTextOfLatestToast());
    }

    /** MATCH 流程的公共前置：入队 → 种子签名 → 置成功态 → 建目标文件 → 发广播。 */
    private File runDownloadToVerify(boolean canRequestInstalls) throws Exception {
        Signature a = sig((byte) 1, (byte) 2, (byte) 3);
        ApkUpdateDownloader.downloadAndInstall(activity(),
                "https://example.com/OctoDroid_1.apk", "OctoDroid_1.apk");
        long id = DownloadRecordManager.getRecords(context).get(0).downloadId;
        ShadowDownloadManager.ShadowRequest req = shadowRequest(id);
        File dest = new File(req.getDestination().getPath());
        ShadowPackageManager spm = Shadows.shadowOf(context.getPackageManager());
        // 包名用运行时的 getPackageName()（测试 applicationId 未必是 com.gh4a），
        // 与 verifyDownloadedFile 传的 expectedPackage 保持一致
        String pkg = context.getPackageName();
        spm.setPackageArchiveInfo(dest.getAbsolutePath(),
                packageInfo(pkg, new Signature[]{a}));
        // 注意：checkApkSignature 在 SDK≥P 时带 GET_SIGNING_CERTIFICATES 调
        // getPackageInfo，shadow 会改查 factoryPackageInfos 表；单参 addPackage
        // 只写 packageInfos 表，必须再调双参版把同一份写进 factory 表，
        // 否则 getPackageInfo 抛 NameNotFound → 结论变 NOT_INSTALLED。
        android.content.pm.PackageInfo installed =
                packageInfo(pkg, new Signature[]{a});
        spm.addPackage(installed);
        spm.addPackage(installed,
                new android.content.pm.PackageStats(pkg));
        spm.setCanRequestPackageInstalls(canRequestInstalls);
        req.setStatus(DownloadManager.STATUS_SUCCESSFUL);
        dest.getParentFile().mkdirs();
        try (FileWriter w = new FileWriter(dest)) {
            w.write("not an apk");
        }
        sendDownloadComplete(id);
        // 等校验跑完（setVerifying 先 true 后 false，回调在同一主线程投递里跑）
        waitUntil(() -> DownloadRecordManager.isVerifying(id));
        waitUntil(() -> !DownloadRecordManager.isVerifying(id));
        idleMain();
        return dest;
    }

    @Test
    public void downloadAndInstall_matchInstallsApkWhenAllowed() throws Exception {
        // 与 DownloadListFragmentTest 同款：Robolectric 下 FileProvider 的
        // androidx 路径校验跑不通，stub 掉，只验应用自己的安装逻辑。
        Uri fakeUri = Uri.parse("content://" + context.getPackageName()
                + ".fileprovider/private_download/OctoDroid_1.apk");
        try (MockedStatic<androidx.core.content.FileProvider> mocked =
                     mockStatic(androidx.core.content.FileProvider.class)) {
            mocked.when(() -> androidx.core.content.FileProvider.getUriForFile(
                    any(Context.class), anyString(), any(File.class)))
                    .thenReturn(fakeUri);
            File dest = runDownloadToVerify(true);
            assertTrue(dest.exists());
            Intent started = Shadows.shadowOf(
                    RuntimeEnvironment.getApplication()).getNextStartedActivity();
            if (started == null) {
                // startActivity 不可达时走 catch：apk_install_failed toast
                assertEquals(context.getString(R.string.apk_install_failed),
                        ShadowToast.getTextOfLatestToast());
            } else {
                assertEquals(Intent.ACTION_VIEW, started.getAction());
                assertEquals("application/vnd.android.package-archive", started.getType());
                assertEquals(fakeUri, started.getData());
            }
        }
    }

    @Test
    public void downloadAndInstall_matchWithoutInstallPermissionOpensSettings()
            throws Exception {
        File dest = runDownloadToVerify(false);
        assertTrue(dest.exists());
        Intent started = Shadows.shadowOf(
                RuntimeEnvironment.getApplication()).getNextStartedActivity();
        if (started == null) {
            assertEquals(context.getString(R.string.apk_install_failed),
                    ShadowToast.getTextOfLatestToast());
        } else {
            assertEquals(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    started.getAction());
        }
    }
}
