package jp.co.www.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.grpc.ManagedChannel;
import jp.co.www.config.GrpcChannel;
import jp.co.www.protodefine.Hello.HelloRequest;
import jp.co.www.protodefine.Hello.HelloResponse;
import jp.co.www.protodefine.HelloServiceGrpc;

@RestController
public class testController {

	@Autowired
	private GrpcChannel grpcChannel;

	@GetMapping("/hello")
	public ResponseEntity<String> hello(String name) {

		ManagedChannel channel = null;
		try {
//			SslContext sslc = GrpcSslContexts.forClient().trustManager(new File("src/main/resources/tls/cert.pem"))
//					.build();
//			channel = NettyChannelBuilder.forTarget(target).sslContext(sslc).overrideAuthority("hostname").build();

//			NameResolverRegistry.getDefaultRegistry().register(new K8sClientNameResolverProvider());
//
//			channel = ManagedChannelBuilder.forTarget(target).defaultLoadBalancingPolicy("round_robin").usePlaintext()
//					.build();

			System.out.println("GrpcChannel = " + this.grpcChannel);
			channel = grpcChannel.get();

			System.out.println("%%% authority = " + channel.authority());

			HelloServiceGrpc.HelloServiceBlockingStub stub = HelloServiceGrpc.newBlockingStub(channel);

			for (int i = 1; i < 128; i++) {
				HelloRequest request = HelloRequest.newBuilder().setName(name + i).build();
				HelloResponse response = stub.sayHello(request);
				System.out.println(response.getGreeting());
				Thread.sleep(1000);
			}

			System.out.println("%%% authority = " + channel.authority());

//			List<ListenableFuture<HelloResponse>> futureList = new ArrayList<>();
//			HelloServiceGrpc.HelloServiceFutureStub futureStub = HelloServiceGrpc.newFutureStub(channel);
//
//			for (int i = 1; i < 128; i++) {
//				HelloRequest request = HelloRequest.newBuilder().setName(name + i).build();
//				ListenableFuture<HelloResponse> response = futureStub.sayHello(request);
//				futureList.add(response);
//				Thread.sleep(1000);
//			}
//
//			for (ListenableFuture<HelloResponse> future : futureList) {
//				System.out.println(future.get().getGreeting());
//			}

			return ResponseEntity.ok("OK!!");
		} catch (Exception e) {
			e.printStackTrace();
			return new ResponseEntity<>(e.toString(), HttpStatus.EXPECTATION_FAILED);
		} finally {
//			if (channel != null)
//				channel.shutdown();
		}
	}

	@GetMapping("/")
	public ResponseEntity<String> hello() {
		return ResponseEntity.ok("OK!");
	}
}
