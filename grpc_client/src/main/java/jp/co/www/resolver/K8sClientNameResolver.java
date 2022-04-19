package jp.co.www.resolver;

import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.concurrent.GuardedBy;

import com.google.gson.reflect.TypeToken;

import io.grpc.EquivalentAddressGroup;
import io.grpc.NameResolver;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Endpoints;
import io.kubernetes.client.openapi.models.V1EndpointsList;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.Watch;
import okhttp3.Call;

public class K8sClientNameResolver extends NameResolver {
	private final String namespace;
	private final String name;
	private final int port;
	private final String authority;

	private Listener2 listener;
	private ApiClient client;
	private ExecutorService executorService;

	private volatile boolean refreshing = false;
	private volatile boolean watching = false;

	public K8sClientNameResolver(String namespace, String name, int port) {
		System.out.println("===== new K8sClientNameResolver =====");
		this.namespace = namespace;
		this.name = name;
		this.port = port;
		this.authority = K8sClientNameResolverProvider.SCHEME + "." + this.namespace + "." + this.name;
	}

	@Override
	public String getServiceAuthority() {
		return this.authority;
	}

	@Override
	public void start(Listener2 listener) {
		System.out.println("start...");
		if (this.listener != null) {
			System.out.println(" already started !!!");
			return;
		}
		try {
//			client = Config.fromCluster();
			client = Config.defaultClient();
			Configuration.setDefaultApiClient(client);
			System.out.println(" client = " + client.getBasePath());

			this.executorService = Executors.newSingleThreadExecutor();
			this.listener = listener;
			refresh();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	@Override
	public void shutdown() {
		if (this.executorService != null) {
			System.out.println("shutdownNow()");
			this.executorService.shutdown();
		}
		System.out.println(
				"SHUTDOWN!!!!!!!!! " + this.executorService.isTerminated() + " " + this.executorService.isShutdown());
	}

	@Override
	@GuardedBy("this")
	public void refresh() {
		System.out.println(LocalDateTime.now() + " refresh...");
		if (refreshing)
			return;
		try {
			refreshing = true;
			CoreV1Api api = new CoreV1Api(client);

			V1EndpointsList endpointsList = api.listNamespacedEndpoints(namespace, null, null, null, null, null, null,
					null, null, 10, Boolean.FALSE);

			endpointsList.getItems().stream().filter(endpoints -> name.equals(endpoints.getMetadata().getName()))
					.forEach(endpoints -> update(endpoints));

			executorService.execute(new WatchRunnable());
		} catch (ApiException e) {
			throw new RuntimeException(e);
		} finally {
			refreshing = false;
			System.out.println("refresh finished!");
		}
	}

	private void update(V1Endpoints endpoints) {
		ResolutionResult.Builder resolutionResultBuilder = ResolutionResult.newBuilder();

		if (endpoints.getSubsets() == null) {
			listener.onResult(resolutionResultBuilder.build());
			return;
		}

		List<EquivalentAddressGroup> servers = new ArrayList<>();
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

		listener.onResult(resolutionResultBuilder.setAddresses(servers).build());
	}

	private class WatchRunnable implements Runnable {

		@Override
		public void run() {
			while (!Thread.currentThread().isInterrupted()) {
				if (watching)
					return;
				try {
					watching = true;

					CoreV1Api api = new CoreV1Api(client);

					Call call = api.listNamespacedEndpointsCall(namespace, null, null, null, null, null, null, null,
							null, 5, Boolean.TRUE, null);

					Watch<V1Endpoints> watch = Watch.createWatch(client, call,
							new TypeToken<Watch.Response<V1Endpoints>>() {
							}.getType());

//					for (Response<V1Endpoints> event : watch) {
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
//							case "DELETED":
//								System.out.println("++++ DELETED ++++");
//								listener.onResult(ResolutionResult.newBuilder().build());
//								break;
//							default:
//								break;
//							}
//						}
//					}

					watch.forEachRemaining(w -> {
						if (name.equals(w.object.getMetadata().getName())) {
							switch (w.type) {
							case "MODIFIED":
								System.out.println("++++ MODIFIED ++++");
								update(w.object);
								break;
							case "DELETED":
								System.out.println("++++ DELETED ++++");
								listener.onResult(ResolutionResult.newBuilder().build());
								break;
							default:
								break;
							}
						}
					});

				} catch (Exception e) {
					if (!executorService.isTerminated()) {
						throw new RuntimeException(e);
					} else {
						System.out.println("executorService.isTerminated!!!!!!!!");
					}
				} finally {
					watching = false;
				}
			}
		}

	}

}
