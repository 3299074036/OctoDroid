package com.gh4a.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * M-NEW-1：下载文件名必须经白名单清洗——恶意镜像伪造的 release tag
 * 不能拼出路径穿越或特殊字符，否则 sanitizeFileName 会抛异常导致主线程崩溃。
 */
public class ApkUpdateDownloaderTest {

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
}
