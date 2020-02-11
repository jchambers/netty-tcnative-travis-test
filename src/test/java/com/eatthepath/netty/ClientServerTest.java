package com.eatthepath.netty;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.ssl.*;
import io.netty.util.concurrent.Future;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;

import static org.junit.Assert.assertTrue;

public class ClientServerTest {

    private static final int PORT = 8080;

    @Test
    public void testConnectClient() throws IOException, InterruptedException {
        final EventLoopGroup eventLoopGroup = new NioEventLoopGroup(2);

        try {
            final Server server = new Server(getServerSslContext(), eventLoopGroup);

            assertTrue(server.start(PORT).await().isSuccess());

            final Client client = new Client(getClientSslContext(), InetSocketAddress.createUnresolved("localhost", PORT), eventLoopGroup);

            final ChannelFuture connectFuture = client.connect().await();

            assertTrue(connectFuture.isSuccess());

            final Future<Channel> handshakeFuture =
                    connectFuture.channel().pipeline().get(SslHandler.class).handshakeFuture().await();

            assertTrue(handshakeFuture.isSuccess());
        } finally {
            eventLoopGroup.shutdownGracefully().await();
        }
    }

    private SslContext getClientSslContext() throws IOException {
        try (final InputStream trustedServerCertificateInputStream = getClass().getResourceAsStream("ca.pem")) {
            final SslContext sslContext;
            {
                final SslContextBuilder sslContextBuilder = SslContextBuilder.forClient()
                        .sslProvider(SslProvider.JDK)
                        .trustManager(trustedServerCertificateInputStream)
                        .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE);

                sslContext = sslContextBuilder.build();
            }

            return sslContext;
        }
    }

    private SslContext getServerSslContext() throws IOException {
        try (final InputStream certificateChainInputStream = getClass().getResourceAsStream("server-certs.pem");
             final InputStream privateKeyPkcs8InputStream = getClass().getResourceAsStream("server-key.pem");
             final InputStream trustedClientCertificateInputStream = getClass().getResourceAsStream("ca.pem")) {

            final SslContext sslContext;
            {
                final SslProvider sslProvider = SslProvider.OPENSSL;

                final SslContextBuilder sslContextBuilder =
                        SslContextBuilder.forServer(certificateChainInputStream, privateKeyPkcs8InputStream, null);

                sslContextBuilder.sslProvider(sslProvider)
                        .clientAuth(ClientAuth.OPTIONAL);

                sslContextBuilder.trustManager(trustedClientCertificateInputStream);

                sslContext = sslContextBuilder.build();
            }

            return sslContext;
        }
    }
}
