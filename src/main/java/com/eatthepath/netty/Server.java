package com.eatthepath.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.ReferenceCounted;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Server {

    private final ServerBootstrap bootstrap;

    private static final Logger log = LoggerFactory.getLogger(Server.class);

    Server(final SslContext sslContext, final EventLoopGroup eventLoopGroup) {

        if (sslContext instanceof ReferenceCounted) {
            ((ReferenceCounted) sslContext).retain();
        }

        this.bootstrap = new ServerBootstrap();
        this.bootstrap.group(eventLoopGroup);

        this.bootstrap.channel(NioServerSocketChannel.class);
        this.bootstrap.childHandler(new ChannelInitializer<SocketChannel>() {

            @Override
            protected void initChannel(final SocketChannel channel) {
                final SslHandler sslHandler = sslContext.newHandler(channel.alloc());
                channel.pipeline().addLast(sslHandler);

                sslHandler.handshakeFuture().addListener(new GenericFutureListener<Future<Channel>>() {
                    @Override
                    public void operationComplete(final Future<Channel> handshakeFuture) {
                        if (handshakeFuture.isSuccess()) {
                            log.info("Handshake completed.");
                        } else {
                            log.debug("Handshake failed.", handshakeFuture.cause());
                        }
                    }
                });
            }
        });
    }

    public Future<Void> start(final int port) {
        return this.bootstrap.bind(port);
    }
}