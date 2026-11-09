package com.gh4a.utils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 56：镜像 URL 校验的非规范回环形必须被拦截（isValidMirrorUrl 经
 * isLoopbackOrPrivateHost 归一化判定）。域名一律放行、不断言 DNS 结果。
 */
@RunWith(RobolectricTestRunner.class)
public class MirrorHelperTest {

    @Test
    public void standardLoopbackAndPrivateRejected() {
        assertFalse(MirrorHelper.isValidMirrorUrl("https://127.0.0.1/x"));
        assertFalse(MirrorHelper.isValidMirrorUrl("https://127.1.2.3/x"));
        assertFalse(MirrorHelper.isValidMirrorUrl("https://10.0.0.1/x"));
        assertFalse(MirrorHelper.isValidMirrorUrl("https://172.16.0.1/x"));
        assertFalse(MirrorHelper.isValidMirrorUrl("https://192.168.1.1/x"));
        assertFalse(MirrorHelper.isValidMirrorUrl("https://169.254.1.1/x"));
        assertFalse(MirrorHelper.isValidMirrorUrl("https://0.0.0.0/x"));
        assertFalse(MirrorHelper.isValidMirrorUrl("https://localhost/x"));
        assertFalse(MirrorHelper.isValidMirrorUrl("https://[::1]/x"));
        assertFalse(MirrorHelper.isValidMirrorUrl("https://[fe80::1]/x"));
        assertFalse(MirrorHelper.isValidMirrorUrl("https://[fc00::1]/x"));
    }

    @Test
    public void nonCanonicalLoopbackRejected() {
        // 单段十进制 = 127.0.0.1；八进制 0177 = 127；十六进制 0x7f = 127。
        assertFalse(MirrorHelper.isValidMirrorUrl("https://2130706433/x"));
        assertFalse(MirrorHelper.isValidMirrorUrl("https://0177.0.0.1/x"));
        assertFalse(MirrorHelper.isValidMirrorUrl("https://0x7f.0.0.1/x"));
        assertFalse(MirrorHelper.isValidMirrorUrl("https://127.1/x"));
        // 展开形 IPv6：::1 与 ::ffff:127.0.0.1 的展开写法。
        assertFalse(MirrorHelper.isValidMirrorUrl("https://[0:0:0:0:0:0:0:1]/x"));
        assertFalse(MirrorHelper.isValidMirrorUrl("https://[0:0:0:0:0:ffff:127.0.0.1]/x"));
        assertFalse(MirrorHelper.isValidMirrorUrl("https://[::ffff:127.0.0.1]/x"));
        assertFalse(MirrorHelper.isValidMirrorUrl("https://[::ffff:10.0.0.1]/x"));
    }

    @Test
    public void publicHostsAndDomainsAllowed() {
        assertTrue(MirrorHelper.isValidMirrorUrl("https://mirror.example.com/x"));
        assertTrue(MirrorHelper.isValidMirrorUrl("https://8.8.8.8/x"));
        assertTrue(MirrorHelper.isValidMirrorUrl("https://1.2.3.4/x"));
        assertTrue(MirrorHelper.isValidMirrorUrl("https://[2001:db8::1]/x"));
        assertTrue(MirrorHelper.isValidMirrorUrl("https://[::ffff:8.8.8.8]/x"));
    }

    @Test
    public void nonHttpsRejected() {
        assertFalse(MirrorHelper.isValidMirrorUrl("http://8.8.8.8/x"));
        assertFalse(MirrorHelper.isValidMirrorUrl(null));
        assertFalse(MirrorHelper.isValidMirrorUrl("https://"));
    }

    @Test
    public void userinfoUrlValidationIsAboutHostNotCredentials() {
        // 269（关联第四轮 214）：https://user:password@host/ 能通过校验——
        // 校验只判定 host 是否回环/内网，不管 userinfo；凭据进不进日志/
        // 崩溃文件是 redactUrlForLog（214）的职责，不归校验管。
        // 公共 host + userinfo 仍放行；回环 host + userinfo 仍拦截。
        assertTrue(MirrorHelper.isValidMirrorUrl(
                "https://user:password@mirror.example.com/x"));
        assertFalse(MirrorHelper.isValidMirrorUrl(
                "https://user:password@127.0.0.1/x"));
        assertFalse(MirrorHelper.isValidMirrorUrl(
                "https://user:password@192.168.1.1/x"));
    }
}
