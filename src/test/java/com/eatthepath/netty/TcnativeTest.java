package com.eatthepath.netty;

import io.netty.handler.ssl.OpenSsl;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class TcnativeTest {

    @Test
    public void testTcnativePresent() {
        assertTrue(OpenSsl.isAvailable());
        assertTrue(OpenSsl.isAlpnSupported());
    }
}
