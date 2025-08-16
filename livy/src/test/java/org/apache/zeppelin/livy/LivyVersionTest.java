package org.apache.zeppelin.livy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LivyVersionTest {

    @Test
    void testVersionParsing() {
        LivyVersion v = new LivyVersion("1.6.2");
        assertEquals(10602, v.toNumber());
        assertEquals("1.6.2", v.toString());
    }

    @Test
    void testPreReleaseVersionParsing() {
        LivyVersion v = new LivyVersion("0.6.0-SNAPSHOT");
        assertEquals(600, v.toNumber());
        assertEquals("0.6.0-SNAPSHOT", v.toString());
    }

    @Test
    void testComparsionLogic() {
        LivyVersion v1 = new LivyVersion("0.5.0");
        LivyVersion v2 = new LivyVersion("0.4.0");
        assertTrue(v1.newerThan(v2));
        assertTrue(v2.olderThan(v1));
        assertFalse(v2.newerThan(v1));
        assertFalse(v1.olderThan(v2));
    }

    @Test
    void testInvalidVersionString() {
        LivyVersion v1 = new LivyVersion("invalid");
        assertEquals(99999, v1.toNumber());

        LivyVersion v2 = new LivyVersion(null);
        assertEquals(99999, v2.toNumber());
    }

    @Test
    void isCancelSupported() {
        assertTrue(new LivyVersion("0.3.0").isCancelSupported());
        assertFalse(new LivyVersion("0.2.0").isCancelSupported());
    }

    @Test
    void isSharedSupported() {
        assertTrue(new LivyVersion("0.5.0").isSharedSupported());
        assertFalse(new LivyVersion("0.4.0").isSharedSupported());
    }

    @Test
    void isGetProgressSupported() {
        assertTrue(new LivyVersion("0.4.0").isGetProgressSupported());
        assertFalse((new LivyVersion("0.3.0")).isGetProgressSupported());
    }
}