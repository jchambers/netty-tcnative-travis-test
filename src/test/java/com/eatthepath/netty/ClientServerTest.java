package com.eatthepath.netty;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslProvider;
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
            return SslContextBuilder.forClient()
                    .sslProvider(SslProvider.JDK)
                    .trustManager(trustedServerCertificateInputStream)
                    .build();
        }
    }

    private SslContext getServerSslContext() throws IOException {
        try (final InputStream certificateChainInputStream = getClass().getResourceAsStream("server-certs.pem");
             final InputStream privateKeyPkcs8InputStream = getClass().getResourceAsStream("server-key.pem");
             final InputStream trustedClientCertificateInputStream = getClass().getResourceAsStream("ca.pem")) {

            return SslContextBuilder.forServer(certificateChainInputStream, privateKeyPkcs8InputStream, null)
                    .sslProvider(SslProvider.OPENSSL)
                    .trustManager(trustedClientCertificateInputStream)
                    .build();
        }
    }
}
