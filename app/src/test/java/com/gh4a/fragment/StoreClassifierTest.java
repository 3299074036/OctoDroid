package com.gh4a.fragment;

import com.meisolsson.githubsdk.model.ReleaseAsset;
import com.meisolsson.githubsdk.model.Repository;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.Assert.assertNotNull;

/** StoreClassifier：平台识别 + 发现分类码（纯 JVM）。 */
public class StoreClassifierTest {

    private static ReleaseAsset assetNamed(String name) {
        ReleaseAsset asset = mock(ReleaseAsset.class);
        when(asset.name()).thenReturn(name);
        return asset;
    }

    @Test
    public void platformsForAsset_androidAndIos() {
        assertEquals(EnumSet.of(StoreClassifier.Platform.ANDROID),
                StoreClassifier.platformsForAsset("app-release.apk"));
        assertEquals(EnumSet.of(StoreClassifier.Platform.ANDROID),
                StoreClassifier.platformsForAsset("APP.APK"));
        assertEquals(EnumSet.of(StoreClassifier.Platform.IOS),
                StoreClassifier.platformsForAsset("app.ipa"));
    }

    @Test
    public void platformsForAsset_windowsVariants() {
        assertEquals(EnumSet.of(StoreClassifier.Platform.WINDOWS),
                StoreClassifier.platformsForAsset("setup.exe"));
        assertEquals(EnumSet.of(StoreClassifier.Platform.WINDOWS),
                StoreClassifier.platformsForAsset("setup.msi"));
        assertEquals(EnumSet.of(StoreClassifier.Platform.WINDOWS),
                StoreClassifier.platformsForAsset("setup.msix"));
        assertEquals(EnumSet.of(StoreClassifier.Platform.WINDOWS),
                StoreClassifier.platformsForAsset("setup.appx"));
        assertEquals(EnumSet.of(StoreClassifier.Platform.WINDOWS),
                StoreClassifier.platformsForAsset("install.ps1"));
    }

    @Test
    public void platformsForAsset_macosAndLinux() {
        assertEquals(EnumSet.of(StoreClassifier.Platform.MACOS),
                StoreClassifier.platformsForAsset("app.dmg"));
        assertEquals(EnumSet.of(StoreClassifier.Platform.MACOS),
                StoreClassifier.platformsForAsset("app.pkg"));
        assertEquals(EnumSet.of(StoreClassifier.Platform.LINUX),
                StoreClassifier.platformsForAsset("app.AppImage"));
        assertEquals(EnumSet.of(StoreClassifier.Platform.LINUX),
                StoreClassifier.platformsForAsset("app.deb"));
        assertEquals(EnumSet.of(StoreClassifier.Platform.LINUX),
                StoreClassifier.platformsForAsset("app.rpm"));
        assertEquals(EnumSet.of(StoreClassifier.Platform.LINUX),
                StoreClassifier.platformsForAsset("install.sh"));
    }

    @Test
    public void platformsForAsset_harmonyExtensionArchive() {
        assertEquals(EnumSet.of(StoreClassifier.Platform.HARMONY),
                StoreClassifier.platformsForAsset("app.hap"));
        assertEquals(EnumSet.of(StoreClassifier.Platform.EXTENSION),
                StoreClassifier.platformsForAsset("ext.crx"));
        assertEquals(EnumSet.of(StoreClassifier.Platform.EXTENSION),
                StoreClassifier.platformsForAsset("ext.xpi"));
        assertEquals(EnumSet.of(StoreClassifier.Platform.ARCHIVE),
                StoreClassifier.platformsForAsset("src.zip"));
        assertEquals(EnumSet.of(StoreClassifier.Platform.ARCHIVE),
                StoreClassifier.platformsForAsset("src.tar.gz"));
        assertEquals(EnumSet.of(StoreClassifier.Platform.ARCHIVE),
                StoreClassifier.platformsForAsset("src.tgz"));
        assertEquals(EnumSet.of(StoreClassifier.Platform.ARCHIVE),
                StoreClassifier.platformsForAsset("src.7z"));
        assertEquals(EnumSet.of(StoreClassifier.Platform.ARCHIVE),
                StoreClassifier.platformsForAsset("pkg.whl"));
        assertEquals(EnumSet.of(StoreClassifier.Platform.ARCHIVE),
                StoreClassifier.platformsForAsset("app.jar"));
        assertEquals(EnumSet.of(StoreClassifier.Platform.ARCHIVE),
                StoreClassifier.platformsForAsset("app.war"));
        assertEquals(EnumSet.of(StoreClassifier.Platform.ARCHIVE),
                StoreClassifier.platformsForAsset("os.iso"));
        assertEquals(EnumSet.of(StoreClassifier.Platform.ARCHIVE),
                StoreClassifier.platformsForAsset("disk.img"));
        assertEquals(EnumSet.of(StoreClassifier.Platform.ARCHIVE),
                StoreClassifier.platformsForAsset("src.xz"));
        assertEquals(EnumSet.of(StoreClassifier.Platform.ARCHIVE),
                StoreClassifier.platformsForAsset("src.bz2"));
        assertEquals(EnumSet.of(StoreClassifier.Platform.ARCHIVE),
                StoreClassifier.platformsForAsset("src.rar"));
    }

    @Test
    public void platformsForAsset_nonInstallableSuffixesReturnEmpty() {
        assertTrue(StoreClassifier.platformsForAsset("app-release.apk.sha256").isEmpty());
        assertTrue(StoreClassifier.platformsForAsset("app.sig").isEmpty());
        assertTrue(StoreClassifier.platformsForAsset("notes.txt").isEmpty());
        assertTrue(StoreClassifier.platformsForAsset("README.md").isEmpty());
        assertTrue(StoreClassifier.platformsForAsset("app").isEmpty());
        assertTrue(StoreClassifier.platformsForAsset("").isEmpty());
        assertTrue(StoreClassifier.platformsForAsset(null).isEmpty());
    }

    @Test
    public void platformsForAsset_suffixMustBeAtEnd() {
        // ".apk" 出现在中间不算安装包。
        assertTrue(StoreClassifier.platformsForAsset("app.apk.zip").isEmpty() == false
                || StoreClassifier.platformsForAsset("app.apk.zip")
                        .equals(EnumSet.of(StoreClassifier.Platform.ARCHIVE)));
        assertEquals(EnumSet.of(StoreClassifier.Platform.ARCHIVE),
                StoreClassifier.platformsForAsset("app.apk.zip"));
        assertTrue(StoreClassifier.platformsForAsset("apk").isEmpty());
    }

    @Test
    public void isInstallable_nullAssetFalse() {
        assertFalse(StoreClassifier.isInstallable(null));
    }

    @Test
    public void isInstallable_bySuffix() {
        assertTrue(StoreClassifier.isInstallable(assetNamed("x.apk")));
        assertTrue(StoreClassifier.isInstallable(assetNamed("x.dmg")));
        assertTrue(StoreClassifier.isInstallable(assetNamed("x.zip")));
        assertFalse(StoreClassifier.isInstallable(assetNamed("x.sha256")));
        assertFalse(StoreClassifier.isInstallable(assetNamed(null)));
    }

    @Test
    public void topicCodesFor_allCategories() {
        Set<String> codes = StoreClassifier.topicCodesFor(
                Arrays.asList("ai", "vpn", "rss", "podcast", "mastodon", "ebook", "backup"));
        assertEquals(new java.util.HashSet<>(Arrays.asList(
                StoreClassifier.CAT_AI, StoreClassifier.CAT_PRIVACY,
                StoreClassifier.CAT_NETWORKING, StoreClassifier.CAT_MEDIA,
                StoreClassifier.CAT_SOCIAL, StoreClassifier.CAT_READING,
                StoreClassifier.CAT_TOOLS)), codes);
    }

    @Test
    public void topicCodesFor_caseInsensitiveAndMultiMatch() {
        // 一个 topic 只进一个分类码（同分类内命中即 break），但不同分类可多归。
        Set<String> codes = StoreClassifier.topicCodesFor(Arrays.asList("AI", "ChatGPT", "agent"));
        assertEquals(Collections.singleton(StoreClassifier.CAT_AI), codes);
        Set<String> multi = StoreClassifier.topicCodesFor(Arrays.asList("browser", "video"));
        assertEquals(new java.util.HashSet<>(Arrays.asList(
                StoreClassifier.CAT_NETWORKING, StoreClassifier.CAT_MEDIA)), multi);
    }

    @Test
    public void topicCodesFor_nullAndEmptyInputs() {
        assertTrue(StoreClassifier.topicCodesFor((java.util.List<String>) null).isEmpty());
        assertTrue(StoreClassifier.topicCodesFor(Collections.emptyList()).isEmpty());
        assertTrue(StoreClassifier.topicCodesFor(
                Arrays.asList(null, "", "not-a-topic")).isEmpty());
        assertTrue(StoreClassifier.topicCodesFor(Arrays.asList("ais", "vpnx")).isEmpty());
    }

    @Test
    public void topicCodesFor_repositoryOverload() {
        Repository repo = Repository.builder().topics(Arrays.asList("llm", "notes")).build();
        Set<String> codes = StoreClassifier.topicCodesFor(repo);
        assertEquals(new java.util.HashSet<>(Arrays.asList(
                StoreClassifier.CAT_AI, StoreClassifier.CAT_READING)), codes);
        assertTrue(StoreClassifier.topicCodesFor((Repository) null).isEmpty());
        Repository noTopics = Repository.builder().build();
        assertTrue(StoreClassifier.topicCodesFor(noTopics).isEmpty());
    }

    @Test
    public void privateConstructor_isCallableViaReflection() throws Exception {
        java.lang.reflect.Constructor<StoreClassifier> c = StoreClassifier.class.getDeclaredConstructor();
        c.setAccessible(true);
        assertNotNull(c.newInstance());
    }

}
