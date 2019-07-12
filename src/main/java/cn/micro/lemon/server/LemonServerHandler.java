package cn.micro.lemon.server;

import cn.micro.lemon.LemonConfig;
import cn.micro.lemon.LemonStatusCode;
import cn.micro.lemon.filter.LemonChain;
import cn.micro.lemon.filter.LemonContext;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.*;
import org.micro.neural.common.thread.StandardThreadExecutor;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Lemon Server Handler
 *
 * @author lry
 */
public class LemonServerHandler extends ChannelInboundHandlerAdapter {

    private LemonConfig lemonConfig;
    private StandardThreadExecutor standardThreadExecutor = null;

    public LemonServerHandler(LemonConfig lemonConfig) {
        this.lemonConfig = lemonConfig;
        if (lemonConfig.getBizCoreThread() > 0) {
            ThreadFactoryBuilder bizBuilder = new ThreadFactoryBuilder();
            bizBuilder.setDaemon(true);
            bizBuilder.setNameFormat("lemon-biz");
            this.standardThreadExecutor = new StandardThreadExecutor(
                    lemonConfig.getBizCoreThread(),
                    lemonConfig.getBizMaxThread(),
                    lemonConfig.getBizKeepAliveTime(),
                    TimeUnit.MILLISECONDS,
                    lemonConfig.getBizQueueCapacity(),
                    bizBuilder.build());
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof FullHttpRequest) {
            long startTime = System.currentTimeMillis();

            FullHttpRequest request = (FullHttpRequest) msg;
            String uri = request.uri();
            if (!uri.startsWith("/" + lemonConfig.getApplication() + "/")) {
                LemonContext.LemonContextBuilder builder = LemonContext.builder();
                builder.lemonConfig(lemonConfig);
                builder.ctx(ctx);
                builder.startTime(startTime);
                LemonContext lemonContext = builder.build();
                lemonContext.writeAndFlush(LemonStatusCode.NO_HANDLER_FOUND_EXCEPTION);
                return;
            }

            LemonContext lemonContext = buildChainContext(startTime, ctx, request);
            if (standardThreadExecutor == null) {
                try {
                    LemonChain.processor(lemonContext);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            } else {
                standardThreadExecutor.execute(() -> {
                    try {
                        LemonChain.processor(lemonContext);
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                });
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.close();
    }

    private LemonContext buildChainContext(long startTime, ChannelHandlerContext ctx, FullHttpRequest request) {
        QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
        HttpHeaders httpHeaders = request.headers();

        LemonContext.LemonContextBuilder builder = LemonContext.builder();
        builder.startTime(startTime);
        builder.uri(request.uri());
        builder.path(decoder.path());
        builder.method(request.method().name());
        builder.keepAlive(HttpUtil.isKeepAlive(request));

        int contentLength;
        byte[] contentByte;
        ByteBuf byteBuf = null;
        try {
            byteBuf = request.content();
            contentLength = byteBuf.readableBytes();
            contentByte = new byte[contentLength];
            byteBuf.readBytes(contentByte);
        } finally {
            if (byteBuf != null) {
                byteBuf.release();
            }
        }

        builder.contentLength(contentLength);
        builder.contentByte(contentByte);
        builder.content(new String(contentByte, StandardCharsets.UTF_8));

        LemonContext lemonContext = builder.build();
        lemonContext.setLemonConfig(lemonConfig);
        lemonContext.setCtx(ctx);
        lemonContext.addPaths(decoder.path());
        lemonContext.getHeaderAll().addAll(httpHeaders.entries());
        lemonContext.getParameterAll().putAll(decoder.parameters());
        lemonContext.addHeaders(httpHeaders.entries());
        lemonContext.addParameters(decoder.parameters());
        return lemonContext;
    }

}