package jp.co.www.config;

import javax.annotation.PreDestroy;

import org.springframework.stereotype.Component;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.NameResolverRegistry;
import jp.co.www.resolver.K8sClientNameResolverProvider;

@Component
public class GrpcChannel {

//	String target = "localhost:9002"; // local
//	String target = "localhost:31272"; // ingress
	String target = "kubernetes:///default/headless-service/9002"; // nameresolver

//	@Autowired
//	K8sClientNameResolverProvider k8sClientNameResolverProvider;

	private ManagedChannel channel;

	public ManagedChannel get() {
		if (channel == null)
			return creatChannel();
		else
			return channel;
	}

	private ManagedChannel creatChannel() {
		System.out.println("Create Channel -------");

		NameResolverRegistry.getDefaultRegistry().register(new K8sClientNameResolverProvider());

		channel = ManagedChannelBuilder.forTarget(target).defaultLoadBalancingPolicy("round_robin")
				.usePlaintext().build();

//		ManagedChannel channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();

		return channel;
	}

	@PreDestroy
	private void destroy() {
		if (channel != null)
			channel.shutdown();
	}

}
