package com.eatthepath.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.ReferenceCounted;
import io.netty.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLSession;
import java.util.concurrent.atomic.AtomicBoolean;

public class Server {
    private final SslContext sslContext;
    private final AtomicBoolean hasReleasedSslContext = new AtomicBoolean(false);

    private final ServerBootstrap bootstrap;
    private final boolean shouldShutDownEventLoopGroup;

    private final ChannelGroup allChannels;

    private static final Logger log = LoggerFactory.getLogger(Server.class);

    @ChannelHandler.Sharable
    private static class ConnectionNegotiationErrorHandler extends ChannelHandlerAdapter {

        static final ConnectionNegotiationErrorHandler INSTANCE = new ConnectionNegotiationErrorHandler();

        @Override
        public void exceptionCaught(final ChannelHandlerContext context, final Throwable cause) {
            log.debug("Server caught an exception before establishing an HTTP/2 connection.", cause);
        }
    }

    Server(final SslContext sslContext, final EventLoopGroup eventLoopGroup) {

        this.sslContext = sslContext;

        if (this.sslContext instanceof ReferenceCounted) {
            ((ReferenceCounted) this.sslContext).retain();
        }

        this.bootstrap = new ServerBootstrap();

        if (eventLoopGroup != null) {
            this.bootstrap.group(eventLoopGroup);
            this.shouldShutDownEventLoopGroup = false;
        } else {
            this.bootstrap.group(new NioEventLoopGroup(1));
            this.shouldShutDownEventLoopGroup = true;
        }

        this.allChannels = new DefaultChannelGroup(this.bootstrap.config().group().next());

        this.bootstrap.channel(NioServerSocketChannel.class);
        this.bootstrap.childHandler(new ChannelInitializer<SocketChannel>() {

            @Override
            protected void initChannel(final SocketChannel channel) {
                final SslHandler sslHandler = sslContext.newHandler(channel.alloc());
                channel.pipeline().addLast(sslHandler);
                channel.pipeline().addLast(ConnectionNegotiationErrorHandler.INSTANCE);

                sslHandler.handshakeFuture().addListener(new GenericFutureListener<Future<Channel>>() {
                    @Override
                    public void operationComplete(final Future<Channel> handshakeFuture) throws Exception {
                        if (handshakeFuture.isSuccess()) {
                            channel.pipeline().remove(ConnectionNegotiationErrorHandler.INSTANCE);

                            Server.this.allChannels.add(channel);
                        } else {
                            log.debug("TLS handshake failed.", handshakeFuture.cause());
                        }
                    }
                });
            }
        });
    }

    /**
     * Starts this mock server and listens for traffic on the given port.
     *
     * @param port the port to which this server should bind
     *
     * @return a {@code Future} that will succeed when the server has bound to the given port and is ready to accept
     * traffic
     */
    public Future<Void> start(final int port) {
        final ChannelFuture channelFuture = this.bootstrap.bind(port);
        this.allChannels.add(channelFuture.channel());

        return channelFuture;
    }

    /**
     * <p>Shuts down this server and releases the port to which this server was bound. If a {@code null} event loop
     * group was provided at construction time, the server will also shut down its internally-managed event loop
     * group.</p>
     *
     * <p>If a non-null {@code EventLoopGroup} was provided at construction time, mock servers may be reconnected and
     * reused after they have been shut down. If no event loop group was provided at construction time, mock servers may
     * not be restarted after they have been shut down via this method.</p>
     *
     * @return a {@code Future} that will succeed once the server has finished unbinding from its port and, if the
     * server was managing its own event loop group, its event loop group has shut down
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Future<Void> shutdown() {
        final Future<Void> channelCloseFuture = this.allChannels.close();

        final Future<Void> disconnectFuture;

        if (this.shouldShutDownEventLoopGroup) {
            // Wait for the channel to close before we try to shut down the event loop group
            channelCloseFuture.addListener(new GenericFutureListener<Future<Void>>() {

                @Override
                public void operationComplete(final Future<Void> future) throws Exception {
                    Server.this.bootstrap.config().group().shutdownGracefully();
                }
            });

            // Since the termination future for the event loop group is a Future<?> instead of a Future<Void>,
            // we'll need to create our own promise and then notify it when the termination future completes.
            disconnectFuture = new DefaultPromise<>(GlobalEventExecutor.INSTANCE);

            this.bootstrap.config().group().terminationFuture().addListener(new GenericFutureListener() {

                @Override
                public void operationComplete(final Future future) throws Exception {
                    ((Promise<Void>) disconnectFuture).trySuccess(null);
                }
            });
        } else {
            // We're done once we've closed all the channels, so we can return the closure future directly.
            disconnectFuture = channelCloseFuture;
        }

        disconnectFuture.addListener(new GenericFutureListener<Future<Void>>() {
            @Override
            public void operationComplete(final Future<Void> future) throws Exception {
                if (Server.this.sslContext instanceof ReferenceCounted) {
                    if (Server.this.hasReleasedSslContext.compareAndSet(false, true)) {
                        ((ReferenceCounted) Server.this.sslContext).release();
                    }
                }
            }
        });

        return disconnectFuture;
    }
}