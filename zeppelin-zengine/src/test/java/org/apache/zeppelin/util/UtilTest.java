package org.apache.zeppelin.util;

import org.junit.Test;
import static org.junit.Assert.assertNotNull;

public class UtilTest {

    @Test
    public void getVersionTest() {
        assertNotNull(Util.getVersion());
    }

    @Test
    public void getGitInfoTest() {
        assertNotNull(Util.getGitCommitId());
        assertNotNull(Util.getGitTimestamp());
    }
}
