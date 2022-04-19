package jp.co.www.interceptor;

import java.net.InetSocketAddress;

import io.grpc.ForwardingServerCall;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import jp.co.www.protodefine.Hello.HelloResponse;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;

@GrpcGlobalServerInterceptor
public class GrpcServerInterceptor implements ServerInterceptor {

	@Override
	public <ReqT, RespT> Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers,
			ServerCallHandler<ReqT, RespT> next) {

		ServerCall<ReqT, RespT> newCall = new ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(call) {
			@SuppressWarnings("unchecked")
			@Override
			public void sendMessage(RespT message) {

				if (message instanceof HelloResponse) {
					InetSocketAddress remoteAddress = (InetSocketAddress) call.getAttributes()
							.get(Grpc.TRANSPORT_ATTR_LOCAL_ADDR);
					HelloResponse response = (HelloResponse) message;
					HelloResponse.Builder responseBuilder = HelloResponse.newBuilder();
					responseBuilder.setGreeting(response.getGreeting() + " " + remoteAddress);
					message = (RespT) responseBuilder.build();
				}

				super.sendMessage(message);
			}

			@Override
			public void sendHeaders(Metadata headers) {
//				InetSocketAddress remoteAddress = (InetSocketAddress) call.getAttributes()
//						.get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
//				headers.put(Metadata.Key.of("address", Metadata.ASCII_STRING_MARSHALLER), remoteAddress.toString());
				super.sendHeaders(headers);
			}
		};

		ServerCall.Listener<ReqT> listener = next.startCall(newCall, headers);

		return listener;
	}

}
