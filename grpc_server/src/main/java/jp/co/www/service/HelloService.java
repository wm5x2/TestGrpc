package jp.co.www.service;

import jp.co.www.interceptor.GrpcServerInterceptor;
import jp.co.www.protodefine.Hello.HelloRequest;
import jp.co.www.protodefine.Hello.HelloResponse;
import jp.co.www.protodefine.HelloServiceGrpc.HelloServiceImplBase;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService//(interceptors = { GrpcServerInterceptor.class })
public class HelloService extends HelloServiceImplBase {

	@Override
	public void sayHello(HelloRequest request, io.grpc.stub.StreamObserver<HelloResponse> responseObserver) {
		System.out.println("request = " + request);
		
		HelloResponse.Builder responseBuilder = HelloResponse.newBuilder();
		responseBuilder.setGreeting("hello " + request.getName() + "!");

		responseObserver.onNext(responseBuilder.build());
		responseObserver.onCompleted();
	}
}
