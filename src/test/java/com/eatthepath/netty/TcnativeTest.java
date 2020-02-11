package com.eatthepath.netty;

import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.ssl.*;
import io.netty.util.ReferenceCounted;
import org.junit.Test;

import javax.net.ssl.SSLException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

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

    @Test
    public void testServerSslContext() throws IOException {
        try (final InputStream certificateChainInputStream = getClass().getResourceAsStream("server-certs.pem");
             final InputStream privateKeyPkcs8InputStream = getClass().getResourceAsStream("server-key.pem")) {

            final SslContext sslContext;
            {
                final SslProvider sslProvider = SslProvider.OPENSSL;

                final SslContextBuilder sslContextBuilder =
                        SslContextBuilder.forServer(certificateChainInputStream, privateKeyPkcs8InputStream, null);

                sslContextBuilder.sslProvider(sslProvider)
                        .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                        .clientAuth(ClientAuth.OPTIONAL);

                sslContextBuilder.applicationProtocolConfig(new ApplicationProtocolConfig(
                        ApplicationProtocolConfig.Protocol.ALPN,
                        ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                        ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                        ApplicationProtocolNames.HTTP_2));

                sslContext = sslContextBuilder.build();
            }

            if (sslContext instanceof ReferenceCounted) {
                ((ReferenceCounted) sslContext).release();
            }

            assertTrue(sslContext.isServer());
        }
    }
}
