package com.gh4a.utils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * B 组试点：FileUtils。扩展名/MIME/分类走 Robolectric（MimeTypeMap 与
 * android.net.Uri 需 android 桩）；sanitizeFileName 是纯逻辑。
 */
@RunWith(RobolectricTestRunner.class)
public class FileUtilsTest {

    @Test
    public void getFileExtension_normalAndEdge() {
        assertEquals("txt", FileUtils.getFileExtension("a.txt"));
        assertEquals("gz", FileUtils.getFileExtension("archive.tar.gz"));
        assertEquals("", FileUtils.getFileExtension("noext"));
        assertEquals("hidden", FileUtils.getFileExtension(".hidden"));
        assertEquals("", FileUtils.getFileExtension("a."));
        assertEquals("TXT", FileUtils.getFileExtension("a.TXT"));
    }

    @Test(expected = NullPointerException.class)
    public void getFileExtension_nullThrows() {
        FileUtils.getFileExtension(null);
    }

    @Test
    public void getFileName_normalAndEdge() {
        assertEquals("", FileUtils.getFileName(null));
        assertEquals("", FileUtils.getFileName(""));
        assertEquals("", FileUtils.getFileName("   "));
        assertEquals("c.txt", FileUtils.getFileName("a/b/c.txt"));
        assertEquals("noslash", FileUtils.getFileName("noslash"));
        assertEquals("", FileUtils.getFileName("a/b/"));
        assertEquals("c.txt", FileUtils.getFileName("/c.txt"));
    }

    @Test
    public void getFolderPath_normalAndEdge() {
        assertEquals("a/b", FileUtils.getFolderPath("a/b/c.txt"));
        assertEquals("a", FileUtils.getFolderPath("a/b.txt"));
        assertEquals("", FileUtils.getFolderPath("file.txt"));
        assertEquals("a/b", FileUtils.getFolderPath("/a/b/c"));
    }

    @Test
    public void getFolderPath_emptyPathThrows() {
        // 空路径的 pathSegments 为空，remove(size-1) 越界；当前实现无守卫。
        try {
            FileUtils.getFolderPath("");
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) {
        }
    }

    @Test
    public void isImage_types() {
        assertTrue(FileUtils.isImage("a.png"));
        assertTrue(FileUtils.isImage("a.jpg"));
        assertTrue(FileUtils.isImage("a.gif"));
        // 注意：Robolectric 的 MimeTypeMap 内置表不含 svg，真机返回
        // image/svg+xml、此处返回 null；不断言 svg（见 isBitmapImage 注释）。
        assertFalse(FileUtils.isImage("a.txt"));
        assertFalse(FileUtils.isImage("a.mp4"));
        assertFalse(FileUtils.isImage("a.pdf"));
        assertFalse(FileUtils.isImage(null));
        assertFalse(FileUtils.isImage("noext"));
    }

    @Test
    public void isBitmapImage_excludesGifAndSvg() {
        assertTrue(FileUtils.isBitmapImage("a.png"));
        assertTrue(FileUtils.isBitmapImage("a.jpg"));
        assertFalse(FileUtils.isBitmapImage("a.gif"));
        assertFalse(FileUtils.isBitmapImage("a.svg"));
        assertFalse(FileUtils.isBitmapImage("a.txt"));
        assertFalse(FileUtils.isBitmapImage(null));
    }

    @Test
    public void isBinaryFormat_types() {
        assertTrue(FileUtils.isBinaryFormat("a.png"));
        assertTrue(FileUtils.isBinaryFormat("a.pdf"));
        assertTrue(FileUtils.isBinaryFormat("a.zip"));
        assertFalse(FileUtils.isBinaryFormat("a.txt"));
        assertFalse(FileUtils.isBinaryFormat("a.html"));
        assertFalse(FileUtils.isBinaryFormat("a.json"));
        // application/xhtml+xml 以 xml 结尾，按文本处理
        assertFalse(FileUtils.isBinaryFormat("a.xhtml"));
        // image/svg+xml 以 xml 结尾，按文本处理
        assertFalse(FileUtils.isBinaryFormat("a.svg"));
        assertFalse(FileUtils.isBinaryFormat(null));
    }

    @Test
    public void isMarkdown_extensions() {
        assertTrue(FileUtils.isMarkdown("a.md"));
        assertTrue(FileUtils.isMarkdown("a.markdown"));
        assertTrue(FileUtils.isMarkdown("a.mdown"));
        assertTrue(FileUtils.isMarkdown("a.mkdn"));
        assertTrue(FileUtils.isMarkdown("a.mkd"));
        assertTrue(FileUtils.isMarkdown("a.MD"));
        assertFalse(FileUtils.isMarkdown("a.txt"));
        assertFalse(FileUtils.isMarkdown("a"));
        assertFalse(FileUtils.isMarkdown(null));
    }

    @Test
    public void getMimeTypeFor_overrides() {
        assertEquals("text/x-typescript", FileUtils.getMimeTypeFor("a.ts"));
        assertEquals("text/plain", FileUtils.getMimeTypeFor("a.pot"));
        assertEquals("text/x-java-properties", FileUtils.getMimeTypeFor("a.properties"));
        assertEquals("text/javascript", FileUtils.getMimeTypeFor("a.js"));
        assertEquals("text/javascript", FileUtils.getMimeTypeFor("a.mjs"));
        assertEquals("text/x-ruby", FileUtils.getMimeTypeFor("a.rb"));
        assertEquals("text/x-latex", FileUtils.getMimeTypeFor("a.latex"));
        assertEquals("text/x-sql", FileUtils.getMimeTypeFor("a.sql"));
        assertEquals("text/x-json", FileUtils.getMimeTypeFor("a.json"));
        assertEquals("text/x-yaml", FileUtils.getMimeTypeFor("a.yml"));
        assertEquals("text/x-yaml", FileUtils.getMimeTypeFor("a.yaml"));
        assertEquals("text/x-dart", FileUtils.getMimeTypeFor("a.dart"));
        assertEquals("text/plain", FileUtils.getMimeTypeFor("a.m3u"));
        assertEquals("text/plain", FileUtils.getMimeTypeFor("a.bat"));
    }

    @Test
    public void getMimeTypeFor_overrideCaseInsensitive() {
        assertEquals("text/x-typescript", FileUtils.getMimeTypeFor("A.TS"));
        assertEquals("text/javascript", FileUtils.getMimeTypeFor("a.JS"));
    }

    @Test
    public void getMimeTypeFor_frameworkAndUnknown() {
        assertEquals("image/png", FileUtils.getMimeTypeFor("a.png"));
        assertEquals("image/jpeg", FileUtils.getMimeTypeFor("a.jpg"));
        assertEquals("text/plain", FileUtils.getMimeTypeFor("a.txt"));
        assertNull(FileUtils.getMimeTypeFor("a.unknownxyz"));
        assertNull(FileUtils.getMimeTypeFor(null));
        assertNull(FileUtils.getMimeTypeFor("noext"));
        assertNull(FileUtils.getMimeTypeFor("a."));
    }

    @Test
    public void sanitizeFileName_normal() {
        assertEquals("app.apk", FileUtils.sanitizeFileName("app.apk"));
        assertEquals("app.apk", FileUtils.sanitizeFileName("dir/sub/app.apk"));
        assertEquals("app.apk", FileUtils.sanitizeFileName("dir\\sub\\app.apk"));
        assertEquals("app.apk", FileUtils.sanitizeFileName("/app.apk"));
        // 路径穿越靠取 basename 中和：目录部分直接丢弃，只留最后一段
        assertEquals("app.apk", FileUtils.sanitizeFileName("../app.apk"));
        assertEquals("passwd", FileUtils.sanitizeFileName("a/../../etc/passwd"));
        assertEquals("c", FileUtils.sanitizeFileName("a/b/../c"));
    }

    @Test
    public void sanitizeFileName_rejectsTraversal() {
        assertThrowsIAE("..");
        assertThrowsIAE("....");
        assertThrowsIAE("a/..");
        // 含 ".." 的 basename 整体拒绝（即使不是路径穿越形）
        assertThrowsIAE("file..txt");
    }

    @Test
    public void sanitizeFileName_rejectsNullAndEmpty() {
        assertThrowsIAE(null);
        assertThrowsIAE("");
        assertThrowsIAE("/");
        assertThrowsIAE("dir/");
    }

    private static void assertThrowsIAE(String fileName) {
        try {
            FileUtils.sanitizeFileName(fileName);
            fail("expected IllegalArgumentException for: " + fileName);
        } catch (IllegalArgumentException expected) {
        }
    }
}
