package org.ethereum.beacon.wire.impl.libp2p;

import static io.netty.buffer.Unpooled.wrappedBuffer;

import io.libp2p.core.Connection;
import io.libp2p.core.P2PAbstractChannel;
import io.libp2p.core.Stream;
import io.libp2p.core.multistream.Mode;
import io.libp2p.core.multistream.Multistream;
import io.libp2p.core.multistream.ProtocolBinding;
import io.libp2p.core.multistream.ProtocolMatcher;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCounted;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.ethereum.beacon.wire.exceptions.WireRpcClosedException;
import org.ethereum.beacon.wire.exceptions.WireRpcException;
import org.ethereum.beacon.wire.exceptions.WireRpcMalformedException;
import org.ethereum.beacon.wire.impl.libp2p.Libp2pMethodHandler.Controller;
import org.ethereum.beacon.wire.impl.libp2p.encoding.MessageCodec;
import org.ethereum.beacon.wire.impl.libp2p.encoding.RpcMessageCodec;
import org.ethereum.beacon.wire.impl.libp2p.encoding.Util;
import org.javatuples.Pair;
import org.jetbrains.annotations.NotNull;

public abstract class Libp2pMethodHandler<TRequest, TResponse>
    implements ProtocolBinding<Controller<TRequest, TResponse>> {

  private final String methodMultistreamId;
  private final MessageCodec<TRequest> requestCodec;
  private final MessageCodec<Pair<TResponse, Throwable>> responseCodec;
  private boolean notification = false;

  public Libp2pMethodHandler(String methodMultistreamId,
      RpcMessageCodec<TRequest, TResponse> codec) {
    this.methodMultistreamId = methodMultistreamId;
    this.requestCodec = codec.getRequestMessageCodec();
    this.responseCodec = codec.getResponseMessageCodec();
  }

  public CompletableFuture<TResponse> invokeRemote(Connection connection, TRequest request) {
    return connection
        .getMuxerSession()
        .createStream(Multistream.create(this.toInitiator(methodMultistreamId)).toStreamHandler())
        .getControler()
        .thenCompose(ctr -> ctr.invoke(request));
  }

  protected abstract CompletableFuture<TResponse> invokeLocal(Connection connection, TRequest request);


  public Libp2pMethodHandler<TRequest, TResponse> setNotification() {
    this.notification = true;
    return this;
  }

  @NotNull
  @Override
  public String getAnnounce() {
    return methodMultistreamId;
  }

  @NotNull
  @Override
  public ProtocolMatcher getMatcher() {
    return new ProtocolMatcher(Mode.STRICT, getAnnounce(), null);
  }

  @NotNull
  @Override
  public CompletableFuture<AbstractHandler> initChannel(P2PAbstractChannel channel, String s) {
    // TODO timeout handlers
    AbstractHandler handler;
    if (channel.isInitiator()) {
      handler = new RequesterHandler();
    } else {
      handler = new ResponderHandler(((Stream)channel).getConn());
    }
    channel.getNettyChannel().pipeline().addLast(handler);
    return handler.activeFuture;
  }

  interface Controller<TRequest, TResponse> {
    CompletableFuture<TResponse> invoke(TRequest request);
  }

  abstract class AbstractHandler extends SimpleChannelInboundHandler<ByteBuf>
      implements Controller<TRequest, TResponse> {

    final CompletableFuture<AbstractHandler> activeFuture = new CompletableFuture<>();
  }

  class ResponderHandler extends AbstractHandler {
    private final Connection connection;

    public ResponderHandler(Connection connection) {
      this.connection = connection;
      activeFuture.complete(this);
    }

    @Override
    public CompletableFuture<TResponse> invoke(TRequest tRequest) {
      throw new IllegalStateException("This method shouldn't be called for Responder");
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf byteBuf) throws Exception {
      TRequest request = requestCodec.deserialize(byteBuf);
      invokeLocal(connection, request)
          .whenComplete(
              (resp, err) -> {
                ByteBuf respBuf = Unpooled.buffer();
                responseCodec.serialize(Pair.with(resp, err), respBuf);
                ctx.writeAndFlush(respBuf);
                ctx.channel().disconnect();
              });
    }
  }

  class RequesterHandler extends AbstractHandler {
    private ChannelHandlerContext ctx;
    private CompletableFuture<TResponse> respFuture;
    private List<ByteBuf> chunks = new ArrayList<>();
    private int remainingBytesToRead;
    private boolean responseComplete;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf byteBuf) throws Exception {
      if (respFuture == null) {
        throw new WireRpcMalformedException("Some data received prior to request: " + byteBuf);
      }
      if (responseComplete) {
        throw new WireRpcMalformedException("Extra message chunk");
      }

      if (chunks.isEmpty()) {
        // the beginning of response - read the total message size
        ByteBuf slice = byteBuf.slice().skipBytes(1);
        int lenPrefix = Util.readRawVarint32(slice);
        remainingBytesToRead = lenPrefix + (byteBuf.readableBytes() - slice.readableBytes());

      }

      byteBuf.retain();
      chunks.add(byteBuf);

      remainingBytesToRead -= byteBuf.readableBytes();

      if (remainingBytesToRead <= 0) {
        responseComplete = true;
        try {
          Pair<TResponse, Throwable> response = responseCodec
              .deserialize(wrappedBuffer(chunks.toArray(new ByteBuf[0])));
          if (response.getValue0() != null) {
            respFuture.complete(response.getValue0());
          } else {
            respFuture.completeExceptionally(response.getValue1());
          }
        } catch (Exception e) {
          respFuture.completeExceptionally(e);
        } finally {
          chunks.forEach(ReferenceCounted::release);
        }
      }
    }

    @Override
    public CompletableFuture<TResponse> invoke(TRequest tRequest) {
      ByteBuf reqByteBuf = Unpooled.buffer();
      requestCodec.serialize(tRequest, reqByteBuf);
      respFuture = new CompletableFuture<>();
      ctx.writeAndFlush(reqByteBuf);
      if (notification) {
        ctx.channel().close();
        return CompletableFuture.completedFuture(null);
      } else {
        ctx.channel().disconnect();
        return respFuture;
      }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
      this.ctx = ctx;
      activeFuture.complete(this);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
      WireRpcException exception = new WireRpcException("Channel exception", cause);
      activeFuture.completeExceptionally(exception);
      respFuture.completeExceptionally(exception);
      ctx.channel().close();
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
      WireRpcClosedException exception = new WireRpcClosedException("Stream closed.");
      activeFuture.completeExceptionally(exception);
      respFuture.completeExceptionally(exception);
      ctx.channel().close();
      if (!responseComplete) {
        chunks.forEach(ReferenceCounted::release);
      }
    }
  }
}
