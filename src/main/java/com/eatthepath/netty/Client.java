package com.eatthepath.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.ReferenceCounted;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

public class Client {

    private final Bootstrap bootstrapTemplate;

    private static final Logger log = LoggerFactory.getLogger(Client.class);

    Client(final SslContext sslContext, final InetSocketAddress serverAddress, final EventLoopGroup eventLoopGroup) {

        if (sslContext instanceof ReferenceCounted) {
            ((ReferenceCounted) sslContext).retain();
        }

        this.bootstrapTemplate = new Bootstrap();
        this.bootstrapTemplate.channel(NioSocketChannel.class);
        this.bootstrapTemplate.group(eventLoopGroup);
        this.bootstrapTemplate.option(ChannelOption.TCP_NODELAY, true);
        this.bootstrapTemplate.remoteAddress(serverAddress);

        this.bootstrapTemplate.handler(new ChannelInitializer<SocketChannel>() {

            @Override
            protected void initChannel(final SocketChannel channel) {
                final ChannelPipeline pipeline = channel.pipeline();

                final SslHandler sslHandler = sslContext.newHandler(channel.alloc());

                sslHandler.handshakeFuture().addListener(new GenericFutureListener<Future<Channel>>() {
                    @Override
                    public void operationComplete(final Future<Channel> handshakeFuture) {
                        if (handshakeFuture.isSuccess()) {
                            log.info("Handshake completed successfully");
                        } else {
                            log.warn("Handshake failed.", handshakeFuture.cause());
                        }
                    }
                });

                pipeline.addLast(sslHandler);
            }
        });
    }

    public void connect() {
        final Bootstrap bootstrap = this.bootstrapTemplate.clone();

        final ChannelFuture connectFuture = bootstrap.connect();

        connectFuture.addListener(new GenericFutureListener<ChannelFuture>() {

            @Override
            public void operationComplete(final ChannelFuture future) {
                if (future.isSuccess()) {
                    log.info("Client connected.");
                } else {
                    log.warn("Connection failed.", future.cause());
                }
            }
        });

        bootstrap.connect();
    }
}
