package com.eatthepath.netty;

import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.ssl.*;
import io.netty.util.ReferenceCounted;
import org.junit.Test;

import javax.net.ssl.SSLException;

import static org.junit.Assert.assertTrue;

public class TcnativeTest {

    @Test
    public void testTcnativePresent() {
        assertTrue(OpenSsl.isAvailable());
        assertTrue(OpenSsl.isAlpnSupported());
    }

    @Test
    public void testClientSslContext() throws SSLException {
        final SslContext sslContext;
        {
            final SslContextBuilder sslContextBuilder = SslContextBuilder.forClient()
                    .sslProvider(SslProvider.OPENSSL_REFCNT)
                    .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE);

            sslContext = sslContextBuilder.build();
        }

        if (sslContext instanceof ReferenceCounted) {
            ((ReferenceCounted) sslContext).release();
        }

        assertTrue(sslContext.isClient());
    }
}
