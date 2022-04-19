package jp.co.www.resolver;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import javax.annotation.concurrent.GuardedBy;

import org.springframework.stereotype.Component;

import com.google.gson.reflect.TypeToken;

import io.grpc.EquivalentAddressGroup;
import io.grpc.NameResolver;
import io.grpc.internal.SharedResourceHolder;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Endpoints;
import io.kubernetes.client.openapi.models.V1EndpointsList;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.Watch;
import io.kubernetes.client.util.Watch.Response;
import okhttp3.Call;

@Component
public class K8sClientNameResolver2 extends NameResolver {
	private String namespace;
	private String name;
	private int port;
	private Args args;
	private SharedResourceHolder.Resource<ScheduledExecutorService> timeServiceResource;
	private SharedResourceHolder.Resource<Executor> sharedChannelExecutorResource;
	private Listener2 listener;
	private ApiClient client;
	private ExecutorService executorService;

	private volatile boolean refreshing = false;
	private volatile boolean watching = false;

	public void init(String namespace, String name, int port, Args args,
			SharedResourceHolder.Resource<ScheduledExecutorService> timeServiceResource,
			SharedResourceHolder.Resource<Executor> sharedChannelExecutorResource) {
		this.namespace = namespace;
		this.name = name;
		this.port = port;
		this.args = args;
		this.timeServiceResource = timeServiceResource;
		this.sharedChannelExecutorResource = sharedChannelExecutorResource;
	}

	@Override
	public String getServiceAuthority() {
		return "AUTHORITY";
	}

	@Override
	public void start(Listener2 listener) {
		System.out.println("start...");
		try {
			client = Config.fromCluster();
		} catch (IOException e) {
			e.printStackTrace();
		}
		this.listener = listener;
		refresh();
	}

	@Override
	public void shutdown() {
//		this.executorService.shutdownNow();
		System.out.println("SHUTDOWN!!!!!!!!!");
	}

	@Override
	@GuardedBy("this")
	public void refresh() {
		System.out.println(LocalDateTime.now() + " @@@ refresh " + namespace + " " + name + " " + port);
		if (refreshing)
			return;
		try {
			refreshing = true;

			CoreV1Api api = new CoreV1Api(client);

			V1EndpointsList endpointsList = api.listNamespacedEndpoints(namespace, null, null, null, null, null, null,
					null, null, 10, Boolean.FALSE);

			endpointsList.getItems().stream().filter(endpoints -> name.equals(endpoints.getMetadata().getName()))
					.forEach(endpoints -> update(endpoints));

//			watch();
//			executorService = Executors.newSingleThreadExecutor();
//			executorService.execute(new WatchRunnable());

		} catch (ApiException e) {
			e.printStackTrace();
		} finally {
			refreshing = false;
			System.out.println("refresh finished!");
		}
	}

	private void update(V1Endpoints endpoints) {
		System.out.println("update...");

		List<EquivalentAddressGroup> servers = new ArrayList<>();
		if (endpoints.getSubsets() == null)
			return;

		endpoints.getSubsets().stream().forEach(subset -> {
			long matchingPorts = subset.getPorts().stream().filter(p -> {
				return p != null && p.getPort() == port;
			}).count();
			if (matchingPorts > 0) {
				subset.getAddresses().stream().map(address -> {
					System.out.println("  IP = " + address.getIp());
					return new EquivalentAddressGroup(new InetSocketAddress(address.getIp(), port));
				}).forEach(address -> {
					servers.add(address);
				});
			}
		});

		ResolutionResult result = ResolutionResult.newBuilder().setAddresses(servers).build();

		listener.onResult(result);
//		listener.onAddresses(servers, Attributes.EMPTY);
		System.out.println("update finished!");
	}

	@GuardedBy("this")
	private void watch() throws ApiException {
		System.out.println("watch...");
		while (true) {
			if (watching)
				return;
			try {
				watching = true;

				CoreV1Api api = new CoreV1Api(client);

				Call call = api.listNamespacedEndpointsCall(namespace, null, null, null, null, null, null, null, null,
						5, Boolean.TRUE, null);

				Watch<V1Endpoints> watch = Watch.createWatch(client, call,
						new TypeToken<Watch.Response<V1Endpoints>>() {
						}.getType());

				for (Response<V1Endpoints> event : watch) {
//					System.out.println(
//							"!!!! watch event !!!! " + event.object.getMetadata().getName() + " " + event.type);
					V1Endpoints endpoints = event.object;
					if (name.equals(endpoints.getMetadata().getName())) {
						switch (event.type) {
						case "ADDED":
							System.out.println("++++ ADDED ++++");
							// update(endpoints);
							break;
						case "MODIFIED":
							System.out.println("++++ MODIFIED ++++");
							update(endpoints);
							break;
						case "DELETED":
							System.out.println("++++ DELETED ++++");
							// listener.onAddresses(Collections.emptyList(), Attributes.EMPTY);
							break;
						default:
							System.out.println("++++ default ++++");
						}
					}
				}
			} finally {
				watching = false;
				System.out.println("watch finished!");
			}
		}
	}

//	private class WatchRunnable implements Runnable {
//
//		@Override
//		public void run() {
//			while (true) {
//				if (watching)
//					return;
//				try {
//					watching = true;
//
//					CoreV1Api api = new CoreV1Api(client);
//
//					Call call = api.listNamespacedEndpointsCall(namespace, null, null, null, null, null, null, null,
//							null, 5, Boolean.TRUE, null);
//
//					Watch<V1Endpoints> watch = Watch.createWatch(client, call,
//							new TypeToken<Watch.Response<V1Endpoints>>() {
//							}.getType());
//
//					for (Response<V1Endpoints> event : watch) {
////						System.out.println(
////								"!!!! watch event !!!! " + event.object.getMetadata().getName() + " " + event.type);
//						V1Endpoints endpoints = event.object;
//						if (name.equals(endpoints.getMetadata().getName())) {
//							switch (event.type) {
////							case "ADDED":
////								System.out.println("++++ ADDED ++++");
////								update(endpoints);
////								break;
//							case "MODIFIED":
//								System.out.println("++++ MODIFIED ++++");
//								update(endpoints);
//								break;
////							case "DELETED":
////								System.out.println("++++ DELETED ++++");
////								listener.onAddresses(Collections.emptyList(), Attributes.EMPTY);
////								break;
//							default:
//							}
//						}
//					}
//					Thread.sleep(1000);
//				} catch (Exception e) {
//					e.printStackTrace();
//				} finally {
//					watching = false;
//				}
//			}
//		}
//
//	}

}
